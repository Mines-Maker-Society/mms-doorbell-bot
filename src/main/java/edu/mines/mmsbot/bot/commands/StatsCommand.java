package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.data.OperationStatistics.*;
import edu.mines.mmsbot.data.StatsReport;
import edu.mines.mmsbot.data.StatsReport.StatType;
import edu.mines.mmsbot.util.EmbedUtils;
import edu.mines.mmsbot.util.TimeUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class StatsCommand extends AbstractCommand {

    private StatsReport cachedReport;

    public StatsCommand() {
        super(Commands.slash("stats","Pull different statistics related to operation hours.").setContexts(InteractionContextType.GUILD));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        long startTime = System.currentTimeMillis();

        if (cachedReport == null || cachedReport.isExpired()) {
            log().info("Generating fresh statistics report (cache expired or missing)");
            cachedReport = generateFullReport();
        } else {
            log().info("Using cached statistics report (age: {}ms)", cachedReport.getAge());
        }

        EmbedBuilder summaryEmbed = cachedReport.getEmbed(StatType.SUMMARY);
        if (summaryEmbed != null) {
            summaryEmbed.setFooter("Retrieved in " + TimeUtils.formatDuration(System.currentTimeMillis() - startTime, true) +
                    " (" + TimeUtils.formatDuration(cachedReport.getAge(), false) + " old)");
            event.getHook().editOriginalEmbeds(summaryEmbed.build())
                    .submit().thenAccept(action->{
                        action.editMessageComponents(ActionRow.of(createStatsMenu())).queue();
                    });
        } else {
            throw new RuntimeException("Summary embed was null.");
        }
    }

    /**
     * Generates a complete statistics report with all embed types pre-computed.
     */
    private StatsReport generateFullReport() {
        Map<StatType, EmbedBuilder> embedMap = new EnumMap<>(StatType.class);

        for (StatType statType : StatType.values()) {
            embedMap.put(statType, generateStatistics(statType));
        }

        return new StatsReport(embedMap);
    }

    /**
     * Retrieves a cached embed for the given stat type, with updated footer.
     */
    public EmbedBuilder getCachedEmbed(StatType statType, long retrievalStartTime) {
        if (cachedReport == null || cachedReport.isExpired()) {
            log().info("Cache expired, regenerating report");
            cachedReport = generateFullReport();
        }

        EmbedBuilder embed = cachedReport.getEmbed(statType);
        if (embed != null) {
            long retrievalTime = System.currentTimeMillis() - retrievalStartTime;
            embed.setFooter("Retrieved in " + TimeUtils.formatDuration(retrievalTime, true) +
                    " (" + TimeUtils.formatDuration(cachedReport.getAge(), false) + " old)");
        }

        return embed;
    }

    public StringSelectMenu createStatsMenu() {
        StringSelectMenu.Builder menu = StringSelectMenu.create("stat-selector");
        for (StatType value : StatType.values()) {
            menu.addOption(value.title, value.name(), value.description);
        }
        return menu.build();
    }

    private EmbedBuilder generateStatistics(StatType statType) {
        return switch (statType) {
            case SESSION_AVERAGES -> {
                log().info("Calculating session averages...");
                SessionStats[] stats = stats().getSessionStatistics();
                SessionStats openStats = stats[0];
                SessionStats closedStats = stats[1];

                StringBuilder desc = new StringBuilder();
                desc.append("**__Open Session Stats__:** *(")
                        .append(TimeUtils.formatDuration(openStats.totalSessionTime(),false))
                        .append(" over ")
                        .append(openStats.totalSessions()).append(" sessions)*\n");
                desc.append("**Average Open Duration:** ").append(TimeUtils.formatDuration(openStats.averageDuration(), false)).append("\n");
                desc.append("**Median Open Duration:** ").append(TimeUtils.formatDuration(openStats.medianDuration(), false)).append("\n");
                desc.append("**Standard Deviation:** ").append(TimeUtils.formatDuration(Math.round(openStats.standardDeviation()), false)).append("\n");
                desc.append("**Longest Open Session:** ").append(TimeUtils.formatDuration(openStats.longestSession(), false)).append("\n");
                desc.append("**Shortest Open Session:** ").append(TimeUtils.formatDuration(openStats.shortestSession(), false)).append("\n");


                desc.append("\n").append("**__Closed Session Stats__:** *(")
                        .append(TimeUtils.formatDuration(closedStats.totalSessionTime(),false))
                        .append(" over ")
                        .append(closedStats.totalSessions()).append(" sessions)*\n");
                desc.append("**Average Closed Duration:** ").append(TimeUtils.formatDuration(closedStats.averageDuration(), false)).append("\n");
                desc.append("**Median Closed Duration:** ").append(TimeUtils.formatDuration(closedStats.medianDuration(), false)).append("\n");
                desc.append("**Standard Deviation:** ").append(TimeUtils.formatDuration(Math.round(closedStats.standardDeviation()), false)).append("\n");
                desc.append("**Longest Closed Session:** ").append(TimeUtils.formatDuration(closedStats.longestSession(), false)).append("\n");
                desc.append("**Shortest Closed Session:** ").append(TimeUtils.formatDuration(closedStats.shortestSession(), false)).append("\n");
                
                yield EmbedUtils.defaultEmbed()
                        .setTitle("⏱️ Session Duration Statistics")
                        .setDescription(desc.toString());
            }

            case TIME_OF_DAY -> {
                log().info("Calculating time of day patterns...");
                TimeOfDayStats timeStats = stats().getTimeOfDayStatistics();

                StringBuilder desc = new StringBuilder();

                if (timeStats.averageOpenTime() != null) {
                    desc.append("**Average Opening Time:** ").append(TimeUtils.formatTime(timeStats.averageOpenTime())).append("\n");
                    desc.append(createHourDistribution(timeStats.operatingHours().stream().map(OperatingHours::open).toList(), "Opens")).append("\n\n");
                }

                if (timeStats.averageCloseTime() != null) {
                    desc.append("**Average Closing Time:** ").append(TimeUtils.formatTime(timeStats.averageCloseTime())).append("\n");
                    desc.append(createHourDistribution(timeStats.operatingHours().stream().map(OperatingHours::close).toList(), "Closes"));
                }

                if (desc.isEmpty()) desc.append("Not enough data yet!");

                yield EmbedUtils.defaultEmbed()
                        .setTitle("🕐 Time of Day Patterns")
                        .setDescription(desc.toString());
            }

            case DAY_OF_WEEK -> {
                log().info("Calculating day of week patterns...");
                DayOfWeekStats dayStats = stats().getDayOfWeekStatistics();

                StringBuilder desc = new StringBuilder();

                if (dayStats.busiestDay() != null) {
                    desc.append("**Busiest Day:** ")
                            .append(dayStats.busiestDay().getDisplayName(TextStyle.FULL, Locale.US))
                            .append("\n\n");
                }

                desc.append("**Average Operating Hours by Day:**\n");

                long maxAvgDuration = DayOfWeek.values().length == 0 ? 1 :
                        Arrays.stream(DayOfWeek.values())
                                .mapToLong(day -> {
                                    int opens = dayStats.opensByDay().getOrDefault(day, 0);
                                    long total = dayStats.durationByDay().getOrDefault(day, 0L);
                                    return opens > 0 ? total / opens : 0;
                                })
                                .max()
                                .orElse(1);

                for (DayOfWeek day : DayOfWeek.values()) {
                    int opens = dayStats.opensByDay().getOrDefault(day, 0);
                    long totalDuration = dayStats.durationByDay().getOrDefault(day, 0L);
                    long avgDuration = opens > 0 ? totalDuration / opens : 0;

                    String dayName = day.getDisplayName(TextStyle.SHORT, Locale.US);
                    String bar = createBar(avgDuration, maxAvgDuration, 15);

                    desc.append("`").append("%-3s".formatted(dayName)).append("` ");
                    desc.append(bar);

                    desc.append(" average ").append(TimeUtils.formatDuration(avgDuration, false).replaceAll(", \\d+ seconds","")).append(" across ").append(opens).append(" opens").append("\n");
                }

                yield EmbedUtils.defaultEmbed()
                        .setTitle("📅 Day of Week Analysis")
                        .setDescription(desc.toString());
            }

            case USER_LEADERBOARD -> {
                log().info("Calculating user statistics...");
                UserStats userStats = stats().getUserStatistics();

                StringBuilder desc = new StringBuilder();
                desc.append("**Most Active Openers:**\n");

                List<Map.Entry<Long, Integer>> topOpeners = userStats.opensByUser().entrySet().stream()
                        .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                        .limit(5)
                        .toList();

                int rank = 1;
                for (Map.Entry<Long, Integer> entry : topOpeners) {
                    String medal = getMedal(rank);
                    String username = getUserName(entry.getKey());
                    desc.append(medal).append(" **").append(username).append("** - ").append(entry.getValue()).append(" opens\n");
                    rank++;
                }

                desc.append("\n**Most Active Lockers:**\n");

                List<Map.Entry<Long, Integer>> topLockers = userStats.locksByUser().entrySet().stream()
                        .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                        .limit(5)
                        .toList();

                rank = 1;
                for (Map.Entry<Long, Integer> entry : topLockers) {
                    String medal = getMedal(rank);
                    String username = getUserName(entry.getKey());
                    desc.append(medal).append(" **").append(username).append("** - ").append(entry.getValue()).append(" locks\n");
                    rank++;
                }

                yield EmbedUtils.defaultEmbed()
                        .setTitle("👥 User Leaderboard")
                        .setDescription(desc.toString());
            }

            case STREAK_STATS -> {
                log().info("Calculating streak statistics...");
                StreakStats streakStats = stats().getStreakStatistics();

                StringBuilder desc = new StringBuilder();
                desc.append("**Current Streak:** ").append(streakStats.currentStreak()).append(" day").append(streakStats.currentStreak() == 1 ? "" : "s").append("\n");
                desc.append("**Longest Streak:** ").append(streakStats.longestStreak()).append(" day").append(streakStats.longestStreak() == 1 ? "" : "s").append("\n");
                desc.append("**Total Days Open:** ").append(streakStats.totalDaysOpen()).append(" day").append(streakStats.totalDaysOpen() == 1 ? "" : "s").append("\n\n");

                if (streakStats.currentStreak() > 0) {
                    desc.append("Keep opening the space daily! 🌡️");
                } else {
                    desc.append("Streak broken - open the space before midnight to save the streak! 💤");
                }

                yield EmbedUtils.defaultEmbed()
                        .setTitle("🔥 Streak Statistics")
                        .setDescription(desc.toString());
            }

            case HOURLY_HEATMAP -> {
                log().info("Generating hourly heatmap...");
                TimeOfDayStats timeStats = stats().getTimeOfDayStatistics();

                Map<Integer, Integer> operatingHourCounts = new HashMap<>();

                for (OperatingHours oh : timeStats.operatingHours()) {
                    LocalTime open = oh.open();
                    LocalTime close = oh.close();

                    int startHour = open.getHour();
                    int endHour = close.getHour();

                    // Same-day operation
                    if (!close.isBefore(open)) {
                        for (int h = startHour; h < endHour; h++) {
                            operatingHourCounts.merge(h, 1, Integer::sum);
                        }
                    }

                    // Overnight operation
                    else {
                        for (int h = startHour; h < 24; h++) {
                            operatingHourCounts.merge(h, 1, Integer::sum);
                        }
                        for (int h = 0; h < endHour; h++) {
                            operatingHourCounts.merge(h, 1, Integer::sum);
                        }
                    }
                }

                StringBuilder desc = new StringBuilder();
                desc.append("```\n");
                desc.append(" Hour | Operating Activity\n");
                desc.append("------|").append("-".repeat(30)).append("\n");

                int maxCount = operatingHourCounts.values().stream()
                        .max(Integer::compareTo)
                        .orElse(1);

                for (int hour = 0; hour < 24; hour++) {
                    int count = operatingHourCounts.getOrDefault(hour, 0);
                    String hourStr = String.format("%02d:00", hour);
                    String bar = createBar(count, maxCount, 30);
                    desc.append(hourStr).append(" | ").append(bar).append("\n");
                }

                desc.append("```");

                yield EmbedUtils.defaultEmbed()
                        .setTitle("🌡️ Operating Hours Heatmap")
                        .setDescription(desc.toString());
            }

            case SUMMARY -> {
                log().info("Generating comprehensive report...");

                SessionStats[] sessionStats = stats().getSessionStatistics();
                TimeOfDayStats timeStats = stats().getTimeOfDayStatistics();
                DayOfWeekStats dayStats = stats().getDayOfWeekStatistics();
                StreakStats streakStats = stats().getStreakStatistics();

                StringBuilder desc = new StringBuilder();
                desc.append("**Total Operating Time:** ").append(TimeUtils.formatDuration(sessionStats[0].totalSessionTime(), false)).append("\n");
                desc.append("**Total Open Sessions:** ").append(sessionStats[0].totalSessions()).append("\n");
                desc.append("**Avg Open Session Length:** ").append(TimeUtils.formatDuration(sessionStats[0].averageDuration(), false)).append("\n");
                desc.append("**Longest Open Session:** ").append(TimeUtils.formatDuration(sessionStats[0].longestSession(), false)).append("\n");
                desc.append("**Current Streak:** ").append(streakStats.currentStreak()).append(" day").append(streakStats.currentStreak() == 1 ? "" : "s").append("\n\n");

                if (timeStats.averageOpenTime() != null) desc.append("**Typical Opening:** ").append(TimeUtils.formatTime(timeStats.averageOpenTime())).append("\n");
                if (timeStats.averageCloseTime() != null) desc.append("**Typical Closing:** ").append(TimeUtils.formatTime(timeStats.averageCloseTime())).append("\n");
                if (dayStats.busiestDay() != null) desc.append("**Busiest Day:** ").append(dayStats.busiestDay().getDisplayName(TextStyle.FULL, Locale.US)).append("\n");

                yield EmbedUtils.defaultEmbed()
                        .setTitle("📈 Statistics Summary")
                        .setDescription(desc.toString());
            }
        };
    }

    private String createHourDistribution(List<LocalTime> times, String label) {
        if (times.isEmpty()) return "";

        Map<Integer, Long> hourCounts = times.stream()
                .map(LocalTime::getHour)
                .collect(Collectors.groupingBy(h -> h, Collectors.counting()));

        long maxCount = hourCounts.values().stream().max(Long::compareTo).orElse(1L);

        StringBuilder result = new StringBuilder();
        result.append("*").append(label).append(" by hour:*\n");

        for (int block = 0; block < 24; block += 6) {
            result.append("`");
            for (int hour = block; hour < block + 6 && hour < 24; hour++) {
                long count = hourCounts.getOrDefault(hour, 0L);
                result.append(getBarChar(count, maxCount));
            }
            result.append("` ");
            result.append(String.format("%02d-%02d", block, Math.min(block + 5, 23)));
            result.append("\n");
        }

        return result.toString().trim();
    }

    private String getBarChar(long count, long max) {
        if (count == 0) return "░";
        double ratio = (double) count / max;
        if (ratio > 0.75) return "█";
        if (ratio > 0.5) return "▓";
        if (ratio > 0.25) return "▒";
        return "░";
    }

    private String createBar(long value, long max, int length) {
        if (max == 0) return "░".repeat(length);
        int filled = (int) Math.round(((double) value / max) * length);
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, length - filled));
    }

    private String getMedal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> rank + ".";
        };
    }

    private String getUserName(long userId) {
        try {
            Member member = runtime().getServer().getGuild().retrieveMemberById(userId).complete();
            return member.getEffectiveName();
        } catch (Exception e) {
            return "Unknown User (" + userId + ")";
        }
    }
}