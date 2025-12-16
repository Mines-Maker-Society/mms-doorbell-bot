package edu.mines.mmsbot.data;

import edu.mines.mmsbot.MMSContext;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

        Runtime.getRuntime().addShutdownHook(new Thread(this::closeDatabase));
    }

    private void enableWAL() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // WAL should provide data integrity if some idiot unplugs the Pi
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA wal_autocheckpoint = 1000;");
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = conn.createStatement();

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

    public void closeDatabase() {
        if (conn != null) {
            try {
                conn.close();
                log().info("Database closed successfully");
            } catch (SQLException e) {
                log().error("Error closing database: ", e.getMessage());
            }
        }
    }

    public void logEvent(Event event) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO events (timestamp, event_type, user_id) VALUES (?, ?, ?)"
            );
            stmt.setLong(1, event.timestamp);
            stmt.setString(2, event.eventType.name());
            stmt.setLong(3, event.userID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log().error("Error logging event: {}", e.getMessage());
        }
    }

    public long getTotalOperatingTime() {
        try {
            Statement stmt = conn.createStatement();
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

            if (lastOpenTime != null) {
                totalTime += (System.currentTimeMillis() - lastOpenTime);
            }

            return totalTime;

        } catch (SQLException e) {
            log().error("Error calculating total operating time: {}", e.getMessage());
            return 0;
        }
    }

    public List<Long> getAllEventTimes(EventType type) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            List<Long> timestamps = new ArrayList<>();

            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String eventType = rs.getString("event_type");

                if (eventType.equals(type.name())) {
                    timestamps.add(timestamp);
                }
            }

            return timestamps;

        } catch (SQLException e) {
            log().error("Error retrieving all event times: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public long getAverageStateDuration(EventType state) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            List<Long> durations = new ArrayList<>();
            Long stateStart = null;

            while (rs.next()) {
                long ts = rs.getLong("timestamp");
                EventType type = EventType.valueOf(rs.getString("event_type"));

                if (type == state) {
                    if (stateStart == null) stateStart = ts;
                } else {
                    if (stateStart != null) {
                        durations.add(ts - stateStart);
                        stateStart = null;
                    }
                }
            }

            if (stateStart != null) {
                durations.add(System.currentTimeMillis() - stateStart);
            }

            if (durations.isEmpty()) return 0;

            long sum = 0;
            for (long d : durations) sum += d;
            return sum / durations.size();

        } catch (SQLException e) {
            log().error("Error calculating average state duration: {}", e.getMessage());
            return 0;
        }
    }

    public record StatePeriod(long startTimestamp, long duration) {}

    public StatePeriod getLongestStatePeriod(EventType state) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT timestamp, event_type FROM events ORDER BY timestamp");

            long longestDuration = 0;
            long longestStart = 0;
            Long currentStart = null;

            while (rs.next()) {
                long ts = rs.getLong("timestamp");
                EventType type = EventType.valueOf(rs.getString("event_type"));

                if (type == state) {
                    if (currentStart == null) currentStart = ts;
                } else {
                    if (currentStart != null) {
                        long duration = ts - currentStart;
                        if (duration > longestDuration) {
                            longestDuration = duration;
                            longestStart = currentStart;
                        }
                        currentStart = null;
                    }
                }
            }

            if (currentStart != null) {
                long duration = System.currentTimeMillis() - currentStart;
                if (duration > longestDuration) {
                    longestDuration = duration;
                    longestStart = currentStart;
                }
            }

            return new StatePeriod(longestStart, longestDuration);

        } catch (SQLException e) {
            log().error("Error calculating longest state period: {}", e.getMessage());
            return new StatePeriod(0, 0);
        }
    }

    public Event getLastEvent() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("""
                SELECT timestamp, event_type, user_id
                FROM events
                ORDER BY timestamp DESC
                LIMIT 1
            """);

            if (rs.next()) {
                return new Event(
                        rs.getLong("timestamp"),
                        EventType.valueOf(rs.getString("event_type")),
                        rs.getLong("user_id")
                );
            }

            return null;

        } catch (SQLException e) {
            log().error("Error getting last event: {}", e.getMessage());
            return null;
        }
    }

    public record Event(long timestamp, EventType eventType, long userID) {}

    public enum EventType {
        OPEN, LOCK
    }
}
