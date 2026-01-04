package edu.mines.mmsbot.bot.commands;

import com.opencsv.CSVWriter;
import edu.mines.mmsbot.bot.framework.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DataCommand extends AbstractCommand {
    public DataCommand() {
        super(Commands.slash("data","Export all data collected by the doorbell.")
                .addOptions(
                        new OptionData(OptionType.STRING,"format","The file format of the database.",true)
                                .addChoice("SQLite Database","sqlite")
                                .addChoice("CSV Spreadsheet","csv")
                )
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String format = event.getOption("format").getAsString();

        event.deferReply().queue();

        try {
            if (format.equals("csv")) {
                exportCSV(event);
            } else if (format.equals("sqlite")) {
                exportSQLiteDatabase(event);
            }
        } catch (Exception e) {
            log().error("Error exporting data: {}", e.getMessage(), e);
            event.getHook().editOriginal("Failed to export data: " + e.getMessage()).queue();
        }
    }

    private void exportCSV(SlashCommandInteractionEvent event) throws Exception {
        Connection conn = stats().getConn();
        File csvFile = File.createTempFile("doorbell_events_", ".csv");

        try (CSVWriter writer = new CSVWriter(new FileWriter(csvFile))) {
            String[] header = {"ID", "Timestamp", "Event Type", "User ID"};
            writer.writeNext(header);

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, timestamp, event_type, user_id FROM events ORDER BY timestamp");

            while (rs.next()) {
                String[] row = {
                        String.valueOf(rs.getInt("id")),
                        String.valueOf(rs.getLong("timestamp")),
                        rs.getString("event_type"),
                        String.valueOf(rs.getLong("user_id"))
                };
                writer.writeNext(row);
            }

            rs.close();
            stmt.close();
        }

        event.getHook().editOriginal("CSV export completed:")
                .setFiles(FileUpload.fromData(csvFile, "doorbell_events.csv"))
                .queue(success -> csvFile.delete(), failure -> csvFile.delete());
    }

    private void exportSQLiteDatabase(SlashCommandInteractionEvent event) throws Exception {
        String dbPath = config().statisticsFile;
        File dbFile = new File(dbPath);

        if (!dbFile.exists()) {
            event.getHook().editOriginal("Database file not found at: " + dbPath).queue();
            return;
        }

        File backupFile = File.createTempFile("doorbell_backup_", ".sqlite");

        try {
            Connection conn = stats().getConn();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM INTO '" + backupFile.getAbsolutePath().replace("\\", "\\\\") + "'");
                log().info("Database backup created successfully");
            }

            event.getHook().editOriginal("SQLite database export completed:")
                    .setFiles(FileUpload.fromData(backupFile, "doorbell_stats.sqlite"))
                    .queue(success -> backupFile.delete(), failure -> backupFile.delete());

        } catch (Exception e) {
            backupFile.delete();
            log().error("Error creating database backup: {}", e.getMessage());
            event.getHook().editOriginal("Failed to export database: " + e.getMessage()).queue();
        }
    }
}