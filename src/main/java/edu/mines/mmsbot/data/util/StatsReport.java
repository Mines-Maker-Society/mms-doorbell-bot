package edu.mines.mmsbot.data.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public class StatsReport {

    private final Map<StatType, EmbedBuilder> embedCache;
    private final Instant generatedAt;
    private static final long CACHE_DURATION_MS = 15 * 60 * 1000;

    public StatsReport(Map<StatType, EmbedBuilder> embedCache) {
        this.embedCache = new EnumMap<>(embedCache);
        this.generatedAt = Instant.now();
    }

    public EmbedBuilder getEmbed(StatType statType) {
        EmbedBuilder cached = embedCache.get(statType);
        if (cached == null) return null;

        return new EmbedBuilder(cached);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(generatedAt.plusMillis(CACHE_DURATION_MS));
    }

    public long getAge() {
        return Instant.now().toEpochMilli() - generatedAt.toEpochMilli();
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public enum StatType {
        SESSION_AVERAGES("Session Averages", "Average duration of open and closed periods"),
        TIME_OF_DAY("Time of Day", "When the space typically opens and closes"),
        DAY_OF_WEEK("Day of Week", "Which days of the week are most active"),
        USER_LEADERBOARD("User Leaderboard", "Most active users for opening and locking"),
        STREAK_STATS("Streak Statistics", "Current and longest consecutive day streaks"),
        HOURLY_HEATMAP("Hourly Heatmap", "Visual representation of hourly activity"),
        SUMMARY("Statistics Summary", "Overview of all key statistics");

        public final String title;
        public final String description;

        StatType(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }
}