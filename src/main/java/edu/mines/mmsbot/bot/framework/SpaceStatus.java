package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.data.OperationStatistics;
import edu.mines.mmsbot.util.EmbedUtils;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.entities.Activity;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SpaceStatus implements MMSContext {
    private boolean locked;

    public SpaceStatus() {
        OperationStatistics.Event lastEvent = stats().getLastEvent();
        this.locked = lastEvent == null || lastEvent.eventType().equals(OperationStatistics.EventType.LOCK);
        if (lastEvent == null) updateStatus(System.currentTimeMillis());
        else updateStatus(lastEvent.timestamp());
    }

    public boolean isLocked() {
        return locked;
    }

    public void lock(long userID, boolean ping) {
        if (locked) return;
        locked = true;
        runtime().getServer().getLockChannel().sendMessageEmbeds(
                EmbedUtils.defaultEmbed()
                        .setDescription((userID == -1 ? "\uD83D\uDD12 the Blaster Design Factory is no longer open." : "\uD83D\uDD12 The Blaster Design Factory has been locked by " + runtime().getJda().getUserById(userID).getAsMention() + ".") +  " It was open for " + (stats().getLastEvent() == null ? "This is the first time!" : TimeUtils.formatDuration(System.currentTimeMillis() - stats().getLastEvent().timestamp(),false)))
                        .addField(
                                "Want to receive status updates?",
                                "Run the command </doorbell-role:1448522122120134720> to get pinged whenever the Blaster Design Factory is opened.",
                                false
                        )
                        .build()
        ).setContent(ping ? runtime().getServer().getUnlockPingRole().getAsMention() : "").queue();

        stats().logEvent(new OperationStatistics.Event(System.currentTimeMillis(), OperationStatistics.EventType.LOCK,userID));
        updateStatus(System.currentTimeMillis());
    }

    public void open(long userID, boolean ping) {
        if (!locked) return;
        locked = false;
        runtime().getServer().getLockChannel().sendMessageEmbeds(
                EmbedUtils.defaultEmbed()
                        .setDescription((userID == -1 ? "\uD83D\uDD13 The Blaster Design Factory is now opened. " : "\uD83D\uDD13 the Blaster Design Factory has been opened by " + runtime().getJda().getUserById(userID).getAsMention() + ".") + " It was closed for " + (stats().getLastEvent() == null ? "N/A This is the first time!" : TimeUtils.formatDuration(System.currentTimeMillis() - stats().getLastEvent().timestamp(),false)))
                        .addField(
                                "Want to receive status updates?",
                                "Run the command </doorbell-role:1448522122120134720> to get pinged whenever the Blaster Design Factory is opened.",
                                false
                        )
                        .build()
        ).setContent(ping ? runtime().getServer().getUnlockPingRole().getAsMention() : "").queue();

        stats().logEvent(new OperationStatistics.Event(System.currentTimeMillis(), OperationStatistics.EventType.OPEN,userID));
        updateStatus(System.currentTimeMillis());
    }

    public void updateStatus(long updateTime) {
        log().info("Updating bot status...");

        Date date = new Date(updateTime);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE HH:mm");
        String formattedTime = sdf.format(date);

        runtime().getJda().getPresence().setActivity(Activity.playing(locked ? "\uD83D\uDD12 The Blaster Design Factory is locked" : "\uD83D\uDD13 The Blaster Design Factory is open")
                .withState("Last update: " + formattedTime));
    }
}
