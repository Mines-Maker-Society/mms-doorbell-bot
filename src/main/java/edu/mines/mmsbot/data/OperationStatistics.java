package edu.mines.mmsbot.data;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.data.util.CatStatsUtils;
import edu.mines.mmsbot.data.util.OpStatsUtils;

import java.io.File;
import java.sql.*;

public class OperationStatistics implements MMSContext {

    private Connection conn;
    private OpStatsUtils opStats;
    private CatStatsUtils catStats;

    public void startDatabase(String statisticsFile) throws Exception {
        File dbFile = new File(statisticsFile);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        // SQLite creates the file if it doesn't exist
        String url = "jdbc:sqlite:" + statisticsFile;
        conn = DriverManager.getConnection(url);
        this.opStats = new OpStatsUtils(conn);
        this.catStats = new CatStatsUtils(conn);

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

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS message_reactions (
                message_id INTEGER PRIMARY KEY,
                reactions INTEGER NOT NULL
            )
            """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS user_cats (
                user_id INTEGER PRIMARY KEY,
                cats INTEGER NOT NULL
            )
            """);
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

    public Connection getConn() {
        return conn;
    }

    public CatStatsUtils getCatStats() {
        return catStats;
    }

    public OpStatsUtils getOpStats() {
        return opStats;
    }
}