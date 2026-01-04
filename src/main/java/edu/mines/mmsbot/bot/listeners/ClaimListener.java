package edu.mines.mmsbot.bot.listeners;

import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public class ClaimListener extends ListenerAdapter implements MMSContext {

    @Override // I technically made it so these could never expire, but I don't want someone going in and claiming a bunch of unclaimed ones and gaming the statistics
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Message message = event.getMessage();
        Button button = event.getButton();
        User user = event.getUser();
        Member member = runtime().getServer().getGuild().getMember(user);

        if (member != null && !member.getRoles().stream().map(ISnowflake::getIdLong).toList().contains(config().targetServer.keyHolderRoleID)) {
            event.replyEmbeds(
                    EmbedUtils.defaultEmbed()
                            .setTitle("Unable to claim event")
                            .setDescription("Only key holders (%s) may claim events.".formatted(runtime().getServer().getKeyHolderRole().getAsMention()))
                            .build()
            ).queue();
            return;
        }

        String buttonID = button.getCustomId();

        boolean lock = buttonID.startsWith("lock-event_");
        String eventString = buttonID.replaceAll("(lock-event_|open-event_)","");
        long eventID = Long.parseLong(eventString);
        try {
            if (!stats().claimEvent(eventID,user.getIdLong())) {
                event.replyEmbeds(
                        EmbedUtils.defaultEmbed()
                                .setTitle("Event already claimed")
                                .setDescription("This event has been claimed, but the message was not updated. It will now be fixed.")
                                .build()
                ).setEphemeral(true).queue();

                updateMessage(message,lock,stats().getEvent(eventID).userID());

                return;
            }
        }  catch (SQLException e) {
            log().error("Error claiming event: {}", e.getMessage(),e);
            event.replyEmbeds(
                    EmbedUtils.defaultEmbed()
                            .setTitle("Error claiming event")
                            .setDescription("This event could not be claimed due to an SQL exception. Please contact the webmaster.")
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(
                EmbedUtils.defaultEmbed()
                        .setTitle("Claiming Event...")
                        .build()
        ).setEphemeral(true).queue();
        updateMessage(message,lock,user.getIdLong());
    }

    private void updateMessage(Message message, boolean lock, long claimerId) {
        long stamp = -1;

        for (MessageEmbed embed : message.getEmbeds()) {
            OffsetDateTime time = embed.getTimestamp();
            if (time == null) continue;
            stamp = Instant.from(time).toEpochMilli();
        }

        long stampUsed = stamp == -1
                ? Instant.from(message.getTimeCreated()).toEpochMilli()
                : stamp;
        MessageEmbed embed = lock
                ? spaceStatus().generateLockEmbed(claimerId,stampUsed)
                : spaceStatus().generateOpenEmbed(claimerId,stampUsed);

        message.editMessageEmbeds(
                embed
        ).and(
                message.editMessageComponents()
        ).queue();
    }
}
