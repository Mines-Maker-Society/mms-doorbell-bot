package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.BotRuntime;
import edu.mines.mmsbot.data.util.OpStatsUtils;
import edu.mines.mmsbot.util.EmbedUtils;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class SpaceStatus implements MMSContext {

    private final BotRuntime runtime;
    private SpaceState state;
    
    public enum SpaceState {
        OPEN(false, false),
        LOCKED(true, false),
        OPEN_OVERRIDDEN(false, true),
        LOCKED_OVERRIDDEN(true, true);

        private final boolean locked;
        private final boolean overridden;

        SpaceState(boolean locked, boolean overridden) {
            this.locked = locked;
            this.overridden = overridden;
        }

        public boolean isLocked() {
            return locked;
        }

        public boolean isOverridden() {
            return overridden;
        }

        public SpaceState clearOverride() {
            return locked ? LOCKED : OPEN;
        }

        public SpaceState override() {
            return locked ? LOCKED_OVERRIDDEN : OPEN_OVERRIDDEN;
        }
        
        public static SpaceState fromSensor(boolean locked) {
            return locked ? LOCKED : OPEN;
        }
    }

    public SpaceStatus(BotRuntime runtime) {
        this.runtime = runtime;

        OpStatsUtils.Event lastEvent = stats().getOpStats().getLastEvent(0);

        boolean locked = lastEvent == null
                || lastEvent.eventType().equals(OpStatsUtils.EventType.LOCK);

        state = SpaceState.fromSensor(locked);

        log().info("Initialized space state to {}", state);

        if (lastEvent == null) {
            updateStatus(System.currentTimeMillis());
        } else {
            updateStatus(lastEvent.timestamp());
        }
    }

    /**
     * Called by both the override clear command and GPIO monitor and handles logic for overrides and such.
     */
    public void handleSensorStateChange(boolean sensorShowsLocked, long timestamp, long userID) {
        log().info(
                "Sensor indicated state change. Stored State: {} Sensor State: {}",
                state, sensorShowsLocked
        );

        if (state.isOverridden()) {
            log().info("Override present ({}). Disengaging override.", state);
            state = state.clearOverride();
            return;
        }

        if (state.isLocked() == sensorShowsLocked) {
            log().info("State unchanged, presuming previous override. Ignoring.");
            return;
        }

        state = SpaceState.fromSensor(sensorShowsLocked);
        log().info("Updated stored state to {}, notifying members.", state);

        if (sensorShowsLocked) {
            long eventID = stats().getOpStats().logEvent(new OpStatsUtils.Event(timestamp, OpStatsUtils.EventType.LOCK, userID));
            sendNotification(userID, false, timestamp,eventID,true);
        } else {
            long eventID = stats().getOpStats().logEvent(new OpStatsUtils.Event(timestamp, OpStatsUtils.EventType.OPEN, userID));
            sendNotification(userID, true, timestamp, eventID,false);
        }
    }

    /**
     * Called by the open command which manually marks the space as open.
     */
    public boolean manualOpen(long userID, boolean pingMembers) {
        if (!state.isLocked()) {
            log().info("Space already open. Ignoring manual open request from {}", userID);
            return false;
        }

        if (state.isOverridden()) log().info("Space open was overridden ({}). Clearing override.", state);

        state = SpaceState.OPEN;
        log().info("Member {} has manually opened the space.", userID);

        long ts = System.currentTimeMillis();
        long eventID = stats().getOpStats().logEvent(new OpStatsUtils.Event(ts, OpStatsUtils.EventType.OPEN, userID));

        sendNotification(userID, pingMembers, ts, eventID,false);
        return true;
    }

    /**
     * Called by the lock command which manually marks the space as locked.
     */
    public boolean manualLock(long userID, boolean pingMembers) {
        if (state.isLocked()) {
            log().info("Space already locked. Ignoring manual lock request from {}", userID);
            return false;
        }

        if (state.isOverridden()) log().info("Space lock was overridden ({}). Clearing override.", state);


        state = SpaceState.LOCKED;
        log().info("Member {} has manually locked the space.", userID);

        long ts = System.currentTimeMillis();
        long eventID = stats().getOpStats().logEvent(new OpStatsUtils.Event(ts, OpStatsUtils.EventType.LOCK, userID));

        sendNotification(userID, pingMembers, ts, eventID,true);
        return true;
    }

    /**
     * Called by the open command if the space is already open, this results in the override activating. A user must double-execute the command to go from locked to open, then overridden.
     */
    public boolean overrideOpen(long userID) {
        if (state != SpaceState.OPEN) throw new IllegalStateException("Cannot enable open override while space is not open.");

        log().info("Enabling OPEN override requested by member {}.", userID);
        state = SpaceState.OPEN_OVERRIDDEN;

        stats().getOpStats().logEvent(new OpStatsUtils.Event(System.currentTimeMillis(), OpStatsUtils.EventType.OVERRIDE_OPEN, userID));
        return true;
    }

    /**
     * Called by the lock command if the space is already locked, this results in the override activating. A user must double-execute the command to go from open to locked, then overridden.
     */
    public boolean overrideLock(long userID) {
        if (state != SpaceState.LOCKED) throw new IllegalStateException("Cannot enable lock override while space is not locked.");

        log().info("Enabling LOCK override requested by member {}.", userID);
        state = SpaceState.LOCKED_OVERRIDDEN;

        stats().getOpStats().logEvent(new OpStatsUtils.Event(System.currentTimeMillis(), OpStatsUtils.EventType.OVERRIDE_LOCK, userID));
        return true;
    }

    /**
     * Called by the resume command, disengaging the override and immediately notifying members.
     */
    public boolean clearOverride(long userID, boolean sensorLocked) {
        if (!state.isOverridden()) {
            log().info("Manual override clear called by {} when no override is active. Ignoring.", userID);
            return false;
        }

        log().info("Manually clearing override. Stored State: {} Sensor State: {}", state, sensorLocked);

        state = SpaceState.fromSensor(sensorLocked);

        stats().getOpStats().logEvent(new OpStatsUtils.Event(
                System.currentTimeMillis(),
                OpStatsUtils.EventType.CLEAR_OVERRIDE,
                userID));
        return true;
    }

    /**
     * Internal lock/open notification for discord.
     * @param isLock true = lock, false = open
     */
    private void sendNotification(long userID, boolean ping, long timestamp, long eventID, boolean isLock) {
        TextChannel channel = runtime().getServer().getLockChannel();

        MessageCreateAction action = channel
                .sendMessageEmbeds(isLock
                        ? generateLockEmbed(userID, timestamp)
                        : generateOpenEmbed(userID, timestamp))
                .setContent(ping ? runtime().getServer().getUnlockPingRole().getAsMention() : "");

        if (userID == -1) action.addComponents(ActionRow.of(createClaimButton(eventID, isLock)));

        action.queue(sentMessage -> {
            cleanClaimButtons(channel, sentMessage);
            stats().getCatStats().storeMessage(sentMessage.getIdLong());
        });

        updateStatus(timestamp);
    }

    /**
     * Cleans up claim buttons in the channel, ignoring the intended new message.
     * This is here to discourage someone from going in and claiming ones they didn't actually lock/open for.
     */
    private void cleanClaimButtons(TextChannel channel, Message sentMessage) {
        log().info("Removing previous claim buttons...");

        channel.getHistoryBefore(sentMessage, 10).queue(messages -> {
            messages.getRetrievedHistory().stream()
                    .filter(m ->
                            m.getAuthor().getIdLong() ==
                                    runtime().getJda().getSelfUser().getIdLong()
                    )
                    .filter(m -> !m.getComponents().isEmpty())
                    .findFirst()
                    .ifPresent(m -> m.editMessageComponents().queue());
        });
    }

    /**
     * Generates an embed for a space lock event.
     */
    public MessageEmbed generateLockEmbed(long userID, long timestamp) {
        String description = (userID == -1
                ? "🔒 The Blaster Design Factory is no longer open."
                : "🔒 The Blaster Design Factory has been locked by " + runtime().getJda().getUserById(userID).getAsMention() + ".");

        OpStatsUtils.Event lastEvent = stats().getOpStats().getLastEvent(1);

        log().info("Generating a lock embed for an event that occurred on {}",TimeUtils.formatDate(timestamp));

        String duration = lastEvent == null
                ? "N/A This is the first time!"
                : TimeUtils.formatDuration(timestamp - lastEvent.timestamp(), false);

        return EmbedUtils.defaultEmbed()
                .setDescription(description + " It was open for " + duration)
                .addField(
                        "Want to receive status updates?",
                        "Run the command </doorbell-role:1448522122120134720> to get pinged whenever the Blaster Design Factory is opened.",
                        false
                )
                .setTimestamp(Instant.ofEpochMilli(timestamp))
                .build();
    }

    /**
     * Generates an embed for a space open event.
     */
    public MessageEmbed generateOpenEmbed(long userID, long timestamp) {
        String description = (userID == -1
                ? "🔓 The Blaster Design Factory is now opened."
                : "🔓 The Blaster Design Factory has been opened by " + runtime().getJda().getUserById(userID).getAsMention() + ".");

        OpStatsUtils.Event lastEvent = stats().getOpStats().getLastEvent(1);

        log().info("Generating an open embed for an event that occurred on {}",TimeUtils.formatDate(timestamp));

        String duration = lastEvent == null
                ? "N/A This is the first time!"
                : TimeUtils.formatDuration(timestamp - lastEvent.timestamp(), false);

        return EmbedUtils.defaultEmbed()
                .setDescription(description + " It was closed for " + duration)
                .addField(
                        "Want to receive status updates?",
                        "Run the command </doorbell-role:1448522122120134720> to get pinged whenever the Blaster Design Factory is opened.",
                        false
                )
                .setTimestamp(Instant.ofEpochMilli(timestamp))
                .build();
    }

    /**
     * Internal method for updating bot profile status
     */
    private void updateStatus(long updateTime) {
        log().info("Updating bot status...");

        Date date = new Date(updateTime);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE HH:mm");
        String formattedTime = sdf.format(date);

        String statusText = state.isLocked()
                ? "🔒 The Blaster Design Factory is locked"
                : "🔓 The Blaster Design Factory is open";

        runtime().getJda().getPresence().setActivity(Activity.playing(statusText).withState("Last update: " + formattedTime));
    }

    /**
     * Creates a button which identifies itself to an event and allows users to claim locks or unlocks
     */
    private Button createClaimButton(long eventID, boolean isLockEvent) {
        return Button.of(ButtonStyle.SUCCESS, (isLockEvent ? "lock" : "open") + "-event_" + eventID,"Claim Event", Emoji.fromUnicode("U+1F64B"));
    }

    public boolean isSpaceLocked() {
        return state.isLocked();
    }

    public SpaceState getState() {
        return state;
    }

}