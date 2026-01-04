package edu.mines.mmsbot.data;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.TimeUtils;

import java.io.File;
import java.sql.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class OperationStatistics implements MMSContext {

    private Connection conn;

    public void startDatabase(String statisticsFile) throws Exception {
        File dbFile = new File(statisticsFile);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        // SQLite creates the file if it doesn't exist
        String url = "jdbc:sqlite:" + statisticsFile;
        conn = DriverManager.getConnection(url);

        createTables();
        enableWAL();
    }

    private void enableWAL() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // WAL should provide data integrity if power is lost to the Pi
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA wal_autocheckpoint = 1000;");
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                user_id INTEGER NOT NULL
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp)");
        }
    }

    public void closeDatabase() {
        if (conn != null) {
            try {
                conn.close();
                log().info("Database closed successfully");
            } catch (SQLException e) {
                log().error("Error closing database: {}", e.getMessage(),e);
            }
        }
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

                return new Event(timestamp,EventType.valueOf(eventType),userId);
            }

            return null;
        } catch (SQLException e) {
            log().error("Error fetching event with id {}: {}.",eventId,e.getMessage(),e);
            return null;
        }
    }

    public long getTotalOperatingTime() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            long totalTime = 0;
            Long lastOpenTime = null;

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                if (eventType.equals("OPEN")) {
                    lastOpenTime = timestamp;
                } else if (eventType.equals("LOCK") && lastOpenTime != null) {
                    totalTime += (timestamp - lastOpenTime);
                    lastOpenTime = null;
                }
            }

            if (lastOpenTime != null) totalTime += (System.currentTimeMillis() - lastOpenTime);

            return totalTime;

        } catch (SQLException e) {
            log().error("Error calculating total operating time: {}", e.getMessage(),e);
            return 0;
        }
    }

    public List<Long> getAllEventTimes(EventType type) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            List<Long> timestamps = new ArrayList<>();

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                if (eventType.equals(type.name())) timestamps.add(timestamp);
            }

            return timestamps;

        } catch (SQLException e) {
            log().error("Error retrieving all event times: {}", e.getMessage(),e);
            return new ArrayList<>();
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

    public SessionStats getSessionStatistics() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            List<Long> sessionDurations = new ArrayList<>();
            List<Long> closedDurations = new ArrayList<>();
            Long lastOpenTime = null;
            Long lastCloseTime = null;
            long longestSession = 0;
            long shortestSession = Long.MAX_VALUE;

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                if (eventType.equals("OPEN")) {
                    if (lastCloseTime != null) closedDurations.add(timestamp - lastCloseTime);

                    lastOpenTime = timestamp;
                } else if (eventType.equals("LOCK") && lastOpenTime != null) {
                    long duration = timestamp - lastOpenTime;
                    sessionDurations.add(duration);
                    longestSession = Math.max(longestSession, duration);
                    if (duration > 0) shortestSession = Math.min(shortestSession, duration);

                    lastCloseTime = timestamp;
                    lastOpenTime = null;
                }
            }

            if (lastOpenTime != null) {
                long currentDuration = System.currentTimeMillis() - lastOpenTime;
                sessionDurations.add(currentDuration);
                longestSession = Math.max(longestSession, currentDuration);
                if (currentDuration > 0) shortestSession = Math.min(shortestSession, currentDuration);
            }

            long avgOpen = sessionDurations.isEmpty()
                    ? 0
                    : (long) sessionDurations.stream().mapToLong(Long::longValue).average().orElse(0);
            long avgClosed = closedDurations.isEmpty()
                    ? 0
                    : (long) closedDurations.stream().mapToLong(Long::longValue).average().orElse(0);

            if (shortestSession == Long.MAX_VALUE) shortestSession = 0;

            return new SessionStats(
                    avgOpen,
                    avgClosed,
                    longestSession,
                    shortestSession,
                    sessionDurations.size()
            );

        } catch (SQLException e) {
            log().error("Error calculating session statistics: {}", e.getMessage(),e);
            return new SessionStats(0, 0, 0, 0, 0);
        }
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
            long averageOpenDuration,
            long averageClosedDuration,
            long longestSession,
            long shortestSession,
            int totalSessions
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

    public Connection getConn() {
        return conn;
    }
}