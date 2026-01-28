package edu.mines.mmsbot.data.util;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.TimeUtils;

import java.sql.*;
import java.time.*;
import java.util.*;

public class OpStatsUtils implements MMSContext {

    private final Connection conn;
    
    public OpStatsUtils(Connection conn) {
        this.conn = conn;
    }

    public long logEvent(Event event) {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO events (timestamp, event_type, user_id) VALUES (?, ?, ?)")) {
            stmt.setLong(1, event.timestamp);
            stmt.setString(2, event.eventType.name());
            stmt.setLong(3, event.userID);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }

            throw new SQLException("Insert succeeded but no ID was returned.");
        } catch (SQLException e) {
            log().error("Error logging event: {}", e.getMessage(),e);
            return -1;
        }
    }

    public boolean claimEvent(long eventId, long userId) throws SQLException {
        log().info("Claiming event with ID {} for {}.", eventId,userId);
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE events SET user_id = ? WHERE id = ? AND user_id = -1")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, eventId);

            int rowsUpdated = stmt.executeUpdate();
            log().info("Query updated {} rows.", rowsUpdated);

            return rowsUpdated == 1;
        }
    }

    public Event getEvent(long eventId) {
        log().info("Fetching event with ID {}.", eventId);
        try (PreparedStatement stmt = conn.prepareStatement("SELECT timestamp, event_type, user_id FROM events WHERE id = ?")) {
            stmt.setLong(1, eventId);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");
                long userId = rs.getLong("user_id");

                return new Event(timestamp, EventType.valueOf(eventType), userId);
            }

            return null;
        } catch (SQLException e) {
            log().error("Error fetching event with id {}: {}.", eventId, e.getMessage(), e);
            return null;
        }
    }

    public Event getLastEvent(int offset) {
        log().info("Retrieving event with offset {}...", offset);

        try (PreparedStatement stmt = conn.prepareStatement("SELECT timestamp, event_type, user_id FROM events ORDER BY timestamp DESC LIMIT 1 OFFSET ?")) {

            stmt.setInt(1, offset);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                long stamp = rs.getLong("timestamp");
                log().info("Event was on {}", TimeUtils.formatDate(stamp));
                return new Event(
                        stamp,
                        EventType.valueOf(rs.getString("event_type")),
                        rs.getLong("user_id")
                );
            }

            return null;

        } catch (SQLException e) {
            log().error("Error getting event with offset {}: {}", offset, e.getMessage(),e);
            return null;
        }
    }

    public SessionStats[] getSessionStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT timestamp, event_type FROM events ORDER BY timestamp"
            );

            List<Long> openDurations = new ArrayList<>();
            List<Long> closedDurations = new ArrayList<>();

            Long lastOpenTime = null;
            Long lastCloseTime = null;

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                if ("OPEN".equals(eventType)) {
                    if (lastCloseTime != null) {
                        closedDurations.add(timestamp - lastCloseTime);
                    }
                    lastOpenTime = timestamp;
                } else if ("LOCK".equals(eventType) && lastOpenTime != null) {
                    openDurations.add(timestamp - lastOpenTime);
                    lastCloseTime = timestamp;
                    lastOpenTime = null;
                }
            }

            if (lastOpenTime != null) {
                openDurations.add(System.currentTimeMillis() - lastOpenTime);
            }

            return new SessionStats[] {
                    buildStats(openDurations),
                    buildStats(closedDurations)
            };

        } catch (SQLException e) {
            log().error("Error calculating session statistics: {}", e.getMessage(), e);
            return new SessionStats[] {
                    new SessionStats(0, 0, 0f, 0, 0, 0, 0),
                    new SessionStats(0, 0, 0f, 0, 0, 0, 0)
            };
        }
    }


    private SessionStats buildStats(List<Long> durations) {
        if (durations.isEmpty()) return new SessionStats(0, 0, 0f, 0, 0, 0, 0);

        durations.sort(Long::compareTo);

        long totalTime = durations.stream().mapToLong(Long::longValue).sum();
        int count = durations.size();

        long average = totalTime / count;

        long median = (count % 2 == 0)
                ? (durations.get(count / 2 - 1) + durations.get(count / 2)) / 2
                : durations.get(count / 2);

        double variance = durations.stream()
                .mapToDouble(d -> Math.pow(d - average, 2))
                .average()
                .orElse(0);

        float stdDev = (float) Math.sqrt(variance);

        long longest = durations.get(count - 1);
        long shortest = durations.getFirst();

        return new SessionStats(
                average,
                median,
                stdDev,
                longest,
                shortest,
                count,
                totalTime
        );
    }

    public TimeOfDayStats getTimeOfDayStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            List<OperatingHours> operatingHours = new ArrayList<>();
            List<LocalTime> openTimes = new ArrayList<>();
            List<LocalTime> closeTimes = new ArrayList<>();

            LocalTime lastOpen = null;

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                LocalTime time = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime();

                switch (eventType) {
                    case "OPEN" -> {
                        lastOpen = time;
                        openTimes.add(time);
                    }
                    case "LOCK" -> {
                        if (lastOpen != null) {
                            operatingHours.add(new OperatingHours(lastOpen, time));
                            closeTimes.add(time);
                            lastOpen = null;
                        }
                    }
                }
            }

            return new TimeOfDayStats(
                    getAverageTime(openTimes),
                    getAverageTime(closeTimes),
                    operatingHours
            );

        } catch (SQLException e) {
            log().error("Error calculating time of day statistics: {}", e.getMessage(), e);
            return new TimeOfDayStats(null, null, List.of());
        }
    }

    public DayOfWeekStats getDayOfWeekStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            Map<DayOfWeek, Integer> opensByDay = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, Long> durationByDay = new EnumMap<>(DayOfWeek.class);

            for (DayOfWeek day : DayOfWeek.values()) {
                opensByDay.put(day, 0);
                durationByDay.put(day, 0L);
            }

            Long lastOpenTime = null;
            DayOfWeek lastOpenDay = null;

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                LocalDate date = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                DayOfWeek day = date.getDayOfWeek();

                if (eventType.equals("OPEN")) {
                    opensByDay.put(day, opensByDay.get(day) + 1);
                    lastOpenTime = timestamp;
                    lastOpenDay = day;
                } else if (eventType.equals("LOCK") && lastOpenTime != null && lastOpenDay != null) {
                    long duration = timestamp - lastOpenTime;
                    durationByDay.put(lastOpenDay, durationByDay.get(lastOpenDay) + duration);
                    lastOpenTime = null;
                    lastOpenDay = null;
                }
            }

            DayOfWeek busiestDay = durationByDay.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            return new DayOfWeekStats(busiestDay, opensByDay, durationByDay);

        } catch (SQLException e) {
            log().error("Error calculating day of week statistics: {}", e.getMessage(),e);
            return new DayOfWeekStats(null, new EnumMap<>(DayOfWeek.class), new EnumMap<>(DayOfWeek.class));
        }
    }

    public UserStats getUserStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT user_id, event_type FROM events ORDER BY timestamp");

            Map<Long, Integer> opensByUser = new HashMap<>();
            Map<Long, Integer> locksByUser = new HashMap<>();

            while (rs.next()) {
                long userId = rs.getLong("user_id");
                if (userId == -1) continue;
                String eventType = rs.getString("event_type");

                if (eventType.equals("OPEN")) {
                    opensByUser.put(userId, opensByUser.getOrDefault(userId, 0) + 1);
                } else if (eventType.equals("LOCK")) {
                    locksByUser.put(userId, locksByUser.getOrDefault(userId, 0) + 1);
                }
            }

            return new UserStats(opensByUser, locksByUser);

        } catch (SQLException e) {
            log().error("Error calculating user statistics: {}", e.getMessage(),e);
            return new UserStats(new HashMap<>(), new HashMap<>());
        }
    }

    public StreakStats getStreakStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            Set<LocalDate> datesOpen = new HashSet<>();

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                LocalDate date = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (eventType.equals("OPEN")) datesOpen.add(date);
            }

            int currentStreak = 0;
            LocalDate checkDate = LocalDate.now();

            while (datesOpen.contains(checkDate)) {
                currentStreak++;
                checkDate = checkDate.minusDays(1);
            }

            List<LocalDate> sortedDates = datesOpen.stream()
                    .sorted()
                    .toList();

            int longestStreak = getLongestStreak(sortedDates);

            return new StreakStats(currentStreak, longestStreak, datesOpen.size());

        } catch (SQLException e) {
            log().error("Error calculating streak statistics: {}", e.getMessage(),e);
            return new StreakStats(0, 0, 0);
        }
    }

    private int getLongestStreak(List<LocalDate> sortedDates) {
        int longestStreak = 0;
        int tempStreak = 0;
        LocalDate previousDate = null;

        for (LocalDate date : sortedDates) {
            if (previousDate == null || date.equals(previousDate.plusDays(1))) {
                tempStreak++;
            } else {
                longestStreak = Math.max(longestStreak, tempStreak);
                tempStreak = 1;
            }
            previousDate = date;
        }
        longestStreak = Math.max(longestStreak, tempStreak);
        return longestStreak;
    }

    private LocalTime getAverageTime(List<LocalTime> times) {
        if (times.isEmpty()) return null;

        long totalSeconds = times.stream()
                .mapToLong(LocalTime::toSecondOfDay)
                .sum();

        long avgSeconds = totalSeconds / times.size();
        return LocalTime.ofSecondOfDay(avgSeconds);
    }

    public record SessionStats(
            long averageDuration,
            long medianDuration,
            float standardDeviation,
            long longestSession,
            long shortestSession,
            int totalSessions,
            long totalSessionTime
    ) {}

    public record TimeOfDayStats(
            LocalTime averageOpenTime,
            LocalTime averageCloseTime,
            List<OperatingHours> operatingHours
    ) {}

    public record OperatingHours(
            LocalTime open,
            LocalTime close
    ) {}


    public record DayOfWeekStats(
            DayOfWeek busiestDay,
            Map<DayOfWeek, Integer> opensByDay,
            Map<DayOfWeek, Long> durationByDay
    ) {}

    public record UserStats(
            Map<Long, Integer> opensByUser,
            Map<Long, Integer> locksByUser
    ) {}

    public record StreakStats(
            int currentStreak,
            int longestStreak,
            int totalDaysOpen
    ) {}

    public record Event(long timestamp, EventType eventType, long userID) {}

    public enum EventType {
        OPEN, LOCK, OVERRIDE_OPEN, OVERRIDE_LOCK, CLEAR_OVERRIDE,
    }
}
