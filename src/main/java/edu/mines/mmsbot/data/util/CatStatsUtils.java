package edu.mines.mmsbot.data.util;

import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class CatStatsUtils implements MMSContext {

    private final Connection conn;

    public CatStatsUtils(Connection conn) {
        this.conn = conn;
    }
    
    public CompletableFuture<AtomicInteger> populateTable() {
        TextChannel channel = runtime().getServer().getLockChannel();
        AtomicInteger count = new AtomicInteger(0);
        long selfId = runtime().getJda().getSelfUser().getIdLong();

        return channel.getIterableHistory()
                .forEachAsync(message -> {
                    if (message.getAuthor().getIdLong() == selfId) {
                        updateMessage(message);
                        count.incrementAndGet();
                    }
                    return true;
                })
                .thenApply(v -> count);
    }


    public CompletableFuture<AtomicInteger> updateTable() {
        AtomicInteger count = new AtomicInteger(0);
        TextChannel channel = runtime().getServer().getLockChannel();

        List<CompletableFuture<Void>> futures = pullMessages().stream()
                .map(id ->
                        channel.retrieveMessageById(id)
                                .submit()
                                .thenAccept(msg -> {
                                    updateMessage(msg);
                                    count.incrementAndGet();
                                })
                )
                .toList();

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> count);
    }

    public List<MessageCatStat> getTopMessages(int limit) {
        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT message_id, reacted_users FROM message_reacts
        """)) {

            try (ResultSet rs = stmt.executeQuery()) {
                List<MessageCatStat> stats = new ArrayList<>();

                while (rs.next()) {
                    long messageId = rs.getLong(1);
                    List<Long> users = extractList(rs.getString(2));
                    stats.add(new MessageCatStat(messageId, users.size()));
                }

                return stats.stream()
                        .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                        .limit(limit)
                        .toList();
            }

        } catch (SQLException ex) {
            log().error("Error retrieving top cat messages", ex);
            return List.of();
        }
    }

    public List<java.util.Map.Entry<Long, Integer>> getTopUsers(int limit) {
        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT reacted_users FROM message_reacts
        """)) {

            try (ResultSet rs = stmt.executeQuery()) {
                java.util.Map<Long, Integer> counts = new java.util.HashMap<>();

                while (rs.next()) {
                    List<Long> users = extractList(rs.getString(1));

                    for (Long user : users) {
                        counts.merge(user, 1, Integer::sum);
                    }
                }

                return counts.entrySet().stream()
                        .sorted(java.util.Map.Entry.<Long, Integer>comparingByValue().reversed())
                        .limit(limit)
                        .toList();
            }

        } catch (SQLException ex) {
            log().error("Error retrieving top cat users", ex);
            return List.of();
        }
    }


    public List<Long> pullMessages() {
        try (PreparedStatement stmt = conn.prepareStatement("""
            SELECT * FROM message_reacts
        """)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Long> messages = new ArrayList<>();
                
                while (rs.next()) {
                    messages.add(rs.getLong(1));
                }

                return messages;
            }
        } catch (SQLException ex) {
            log().error("Error while selecting all message IDs",ex);
            return null;
        }
    }
    
    public void updateMessage(Message message) {
        message.getReactions().forEach(reaction->{
            if (!reaction.getEmoji().equals(Emoji.fromUnicode("🐈"))) return;

            reaction.retrieveUsers()
                    .map(userList -> {
                        updateReactions(message.getIdLong(),userList.stream()
                                .map(User::getIdLong)
                                .toList());

                        return userList;
                    }).queue();
        });
    }

    public void storeMessage(long messageID) {
        try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT INTO message_reacts ( message_id, reacted_users ) values ( ?, ? )
            ON CONFLICT(message_id) DO UPDATE SET reacted_users = excluded.reacted_users
        """)) {
            stmt.setLong(1,messageID);
            stmt.setString(2,"NO_USERS");

            stmt.executeUpdate();
        } catch (SQLException ex) {
            log().error("Error logging message to cat table {}: ",messageID,ex);
        }
    }

    public void updateReactions(long messageID, List<Long> users) {
        try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT INTO message_reacts ( message_id, reacted_users ) values ( ?, ? )
            ON CONFLICT(message_id) DO UPDATE SET reacted_users = excluded.reacted_users
        """)) {
            stmt.setLong(1,messageID);
            stmt.setString(2,packList(users));
            
            stmt.executeUpdate();
        } catch (SQLException ex) {
            log().error("Error updating cat emoji on message {} for users {}: ",messageID,users.toString(),ex);
        }
    }

    public String packList(Collection<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }


    public List<Long> extractList(String value) {
        if (value == null || value.isBlank() || value.contains("NO_USERS")) return List.of();

        return Stream.of(value.split(","))
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    public record MessageCatStat(long messageId, int count) {}

}