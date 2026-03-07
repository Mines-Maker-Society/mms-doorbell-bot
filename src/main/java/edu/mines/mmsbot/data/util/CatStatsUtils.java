package edu.mines.mmsbot.data.util;

import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class CatStatsUtils implements MMSContext {

    private final Connection conn;

    public CatStatsUtils(Connection conn) {
        this.conn = conn;
    }

    /**
     * Scans the lock channel for all MMS bot messages, stores them to the database, then check's the reactions on it
     */
    public CompletableFuture<AtomicInteger> populateTable() {
        TextChannel channel = runtime().getServer().getLockChannel();
        long selfId = runtime().getJda().getSelfUser().getIdLong();
        AtomicInteger count = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        return channel.getIterableHistory()
                .forEachAsync(message -> {
                    if (message.getAuthor().getIdLong() == selfId) {
                        log().info("Found message {}",message.getIdLong());
                        storeMessage(message.getIdLong());
                        CompletableFuture<Void> future = updateMessage(message)
                                .thenRun(count::incrementAndGet);

                        futures.add(future);
                    }
                    return true;
                })
                .thenCompose(v ->
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                )
                .thenApply(v -> count);
    }

    /**
     * Goes down the table updating all emojis for all messages
     */
    public CompletableFuture<AtomicInteger> updateTable() {
        AtomicInteger count = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = pullMessages().stream()
                .map(id ->
                        updateMessage(id.toString())
                                .thenRun(count::incrementAndGet)
                )
                .toList();

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> count);
    }

    /**
     * Retrieves the top reacted-to messages on the database limited to the limti param
     */
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

    /**
     * Returns the top users limited to the param
     */
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


    /**
     * Pulls all message IDs from the database in long form.
     */
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

    /**
     * Attempts to retrieve a message from an ID, removing it from the database if it wasn't found
     */
    public CompletableFuture<Void> updateMessage(String id) {
        log().info("Attempting to retrieve message with ID {} for updating",id);
        return runtime()
                .getServer()
                .getLockChannel()
                .retrieveMessageById(id)
                .submit()
                .thenCompose(this::updateMessage)
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof ErrorResponseException err &&
                            err.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        deleteMessage(Long.parseLong(id));
                        return null;
                    }
                    throw new CompletionException(ex);
                });
    }

    /**
     * Updates the emojis on a message object, assuming it exists.
     */
    public CompletableFuture<Void> updateMessage(Message message) {
        log().info("Updating emojis on message with ID {}",message.getIdLong());
        List<CompletableFuture<Void>> futures = message.getReactions().stream()
                .filter(reaction -> reaction.getEmoji().equals(Emoji.fromUnicode("🐈")))
                .map(reaction ->
                        reaction.retrieveUsers()
                                .submit()
                                .thenAccept(userList ->
                                        updateReactions(
                                                message.getIdLong(),
                                                userList.stream()
                                                        .map(User::getIdLong)
                                                        .toList()
                                        )
                                )
                )
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Stores the ID to the database, defaulting to having no reactions.
     */
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

    /**
     * Deletes the requested row
     */
    public void deleteMessage(long messageID) {
        try (PreparedStatement stmt = conn.prepareStatement("""
            DELETE FROM message_reacts WHERE message_id = ?
        """)) {
            stmt.setLong(1,messageID);

            stmt.executeUpdate();
        } catch (SQLException ex) {
            log().error("Error logging message to cat table {}: ",messageID,ex);
        }
    }

    /**
     * Updates reactions in the database
     */
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

    /**
     * Packs a list of ids for database storage
     */
    public String packList(Collection<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Unpacks the list of users from the database row
     */
    public List<Long> extractList(String value) {
        if (value == null || value.isBlank() || value.contains("NO_USERS")) return List.of();

        return Stream.of(value.split(","))
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    public record MessageCatStat(long messageId, int count) {}

}