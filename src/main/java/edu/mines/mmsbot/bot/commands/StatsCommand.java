package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.data.OperationStatistics;
import edu.mines.mmsbot.util.EmbedUtils;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class StatsCommand extends AbstractCommand implements MMSContext {

    public StatsCommand() {
        super(Commands.slash("stats","Pull different statistics related to operation hours.").setContexts(InteractionContextType.GUILD));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long startTime = System.currentTimeMillis();
        event.replyEmbeds(generateStatistics(StatType.GRAND_TOTAL).setFooter("Retrieved in " + TimeUtils.formatDuration(System.currentTimeMillis() - startTime,false)).build()).addComponents(
                ActionRow.of(createStatsMenu())
        ).queue();
    }

    public StringSelectMenu createStatsMenu() {
        StringSelectMenu.Builder menu = StringSelectMenu.create("stat-selector");
        for (StatType value : StatType.values()) {
            menu.addOption(value.title,value.name(),value.description);
        }
        return menu.build();
    }

    public EmbedBuilder generateStatistics(StatType statType) {
        return switch (statType) {
            case GRAND_TOTAL -> {
                log().info("Calculating grand total...");

                yield EmbedUtils.defaultEmbed().setDescription("Total time the Blaster Design Factory has been open: " + TimeUtils.formatDuration(stats().getTotalOperatingTime(),false));
            }
            case AVERAGE_UNLOCK_TIME -> {
                log().info("Calculating average opening time...");
                List<Long> allTimes = stats().getAllEventTimes(OperationStatistics.EventType.OPEN);
                long avg = getAverageDayTime(allTimes);

                yield EmbedUtils.defaultEmbed().setDescription("On average, the Blaster Design Factory opens at " + TimeUtils.formatDayTime(avg));
            }
            case AVERAGE_CLOSING_TIME -> {
                log().info("Calculating average closing time...");
                List<Long> allTimes = stats().getAllEventTimes(OperationStatistics.EventType.LOCK);
                long avg = getAverageDayTime(allTimes);

                yield EmbedUtils.defaultEmbed().setDescription("On average, the Blaster Design Factory closes at " + TimeUtils.formatDayTime(avg));
            }
            case AVERAGE_OPEN_PERIOD -> {
                log().info("Calculating average length open...");
                long avg = stats().getAverageStateDuration(OperationStatistics.EventType.OPEN);

                yield EmbedUtils.defaultEmbed().setDescription("On average, the Blaster Design Factory stays open for " + TimeUtils.formatDuration(avg,true));
            }
            case AVERAGE_CLOSED_PERIOD -> {
                log().info("Calculating average length closed...");
                long avg = stats().getAverageStateDuration(OperationStatistics.EventType.LOCK);

                yield EmbedUtils.defaultEmbed().setDescription("On average, the Blaster Design Factory stays locked for " + TimeUtils.formatDuration(avg, true));
            }
            case LONGEST_OPEN_PERIOD -> {
                log().info("Calculating longest open period...");
                OperationStatistics.StatePeriod longest = stats().getLongestStatePeriod(OperationStatistics.EventType.OPEN);

                yield EmbedUtils.defaultEmbed()
                        .setDescription("The Blaster Design Factory was open for " + TimeUtils.formatDuration(longest.duration(),true) + " on " + TimeUtils.formatDate(longest.startTimestamp()));
            }
            case LONGEST_CLOSED_PERIOD -> {
                log().info("Calculating longest closed period...");
                OperationStatistics.StatePeriod longest = stats().getLongestStatePeriod(OperationStatistics.EventType.LOCK);

                yield EmbedUtils.defaultEmbed()
                        .setDescription("The Blaster Design Factory was closed for " + TimeUtils.formatDuration(longest.duration(),true) + " on " + TimeUtils.formatDate(longest.startTimestamp()));
            }
        };
    }

    private static long getAverageDayTime(List<Long> allTimes) {
        long millisPerDay = 24L * 60 * 60 * 1000;

        long sum = 0;
        for (long ts : allTimes) {
            Instant instant = Instant.ofEpochMilli(ts);
            LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.of("America/Denver"));

            long millisSinceMidnight =
                    ldt.toLocalTime().toSecondOfDay() * 1000L +
                            ldt.toLocalTime().getNano() / 1_000_000;

            sum += millisSinceMidnight;
        }

        long avg = sum / allTimes.size();
        avg = ((avg % millisPerDay) + millisPerDay) % millisPerDay;
        return avg;
    }

    public enum StatType {
        GRAND_TOTAL("Grand Total","Total hours the Blaster Design Factory has been open for use."),
        AVERAGE_UNLOCK_TIME("Average Opening Time","The average time of day the Blaster Design Factory opens."),
        AVERAGE_CLOSING_TIME("Average Closing Time" ,"The average time of day the Blaster Design Factory closes. "),
        AVERAGE_OPEN_PERIOD("Average Open Period","The average time the Blaster Design Factory remains open."),
        AVERAGE_CLOSED_PERIOD("Average Locked Period","The average time the Blaster Design Factory remains locked."),
        LONGEST_OPEN_PERIOD("Longest Duration Open","The longest continuous operation of the Blaster Design Factory."),
        LONGEST_CLOSED_PERIOD("Longest Duration Closed","The longest continuous closure of the Blaster Design Factory.");

        StatType(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public final String title;
        public final String description;
    }
}
