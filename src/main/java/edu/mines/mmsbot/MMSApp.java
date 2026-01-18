package edu.mines.mmsbot;

import edu.mines.mmsbot.bot.BotRuntime;
import edu.mines.mmsbot.bot.commands.*;
import edu.mines.mmsbot.bot.framework.CommandHandler;
import edu.mines.mmsbot.bot.framework.SpaceStatus;
import edu.mines.mmsbot.bot.listeners.ClaimListener;
import edu.mines.mmsbot.bot.listeners.LockChannelListener;
import edu.mines.mmsbot.bot.listeners.StatsListener;
import edu.mines.mmsbot.data.Config;
import edu.mines.mmsbot.data.JsonSerializable;
import edu.mines.mmsbot.data.OperationStatistics;
import edu.mines.mmsbot.pi.DoorMonitor;
import edu.mines.mmsbot.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class MMSApp {

    private static MMSApp app;
    private File configFile = new File("MMSBot/config.json");
    private final Logger logger = LoggerFactory.getLogger("MMS Bot Controller");
    private Config config;
    private OperationStatistics statistics;
    private BotRuntime runtime;
    private DoorMonitor doorMonitor;
    private SpaceStatus spaceStatus;
    
    /*
        ⠀⢸⠂⠀⠀⠀⠘⣧⠀⠀⣟⠛⠲⢤⡀⠀⠀⣰⠏⠀⠀⠀⠀⠀⢹⡀
        ⠀⡿⠀⠀⠀⠀⠀⠈⢷⡀⢻⡀⠀⠀⠙⢦⣰⠏⠀⠀⠀⠀⠀⠀⢸⠀
        ⠀⡇⠀⠀⠀⠀⠀⠀⢀⣻⠞⠛⠀⠀⠀⠀⠻⠀⠀⠀⠀⠀⠀⠀⢸⠀
        ⠀⡇⠀⠀⠀⠀⠀⠀⠛⠓⠒⠓⠓⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⠀ You like trying to update
        ⠀⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣸⠀ people's code dont you?
        ⠀⢿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣀⣀⣀⠀⠀⢀⡟⠀
        ⠀⠘⣇⠀⠘⣿⠋⢹⠛⣿⡇⠀⠀⠀⠀⣿⣿⡇⠀⢳⠉⠀⣠⡾⠁⠀
        ⣦⣤⣽⣆⢀⡇⠀⢸⡇⣾⡇⠀⠀⠀⠀⣿⣿⡷⠀⢸⡇⠐⠛⠛⣿⠀
        ⠹⣦⠀⠀⠸⡇⠀⠸⣿⡿⠁⢀⡀⠀⠀⠿⠿⠃⠀⢸⠇⠀⢀⡾⠁⠀
        ⠀⠈⡿⢠⢶⣡⡄⠀⠀⠀⠀⠉⠁⠀⠀⠀⠀⠀⣴⣧⠆⠀⢻⡄⠀⠀
        ⠀⢸⠃⠀⠘⠉⠀⠀⠀⠠⣄⡴⠲⠶⠴⠃⠀⠀⠀⠉⡀⠀⠀⢻⡄⠀
        ⠀⠘⠒⠒⠻⢦⣄⡀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣤⠞⠛⠒⠛⠋⠁⠀
        ⠀⠀⠀⠀⠀⠀⠸⣟⠓⠒⠂⠀⠀⠀⠀⠀⠈⢷⡀⠀⠀⠀⠀⠀⠀⠀
        ⠀⠀⠀⠀⠀⠀⠀⠙⣦⠀⠀⠀⠀⠀⠀⠀⠀⠈⢷⠀⠀⠀⠀⠀⠀⠀
        ⠀⠀⠀⠀⠀⠀⠀⣼⣃⡀⠀⠀⠀⠀⠀⠀⠀⠀⠘⣆⠀⠀⠀⠀⠀⠀
        ⠀⠀⠀⠀⠀⠀⠀⠉⣹⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⢻⠀⠀⠀⠀⠀⠀
        ⠀⠀⠀⠀⠀⠀⠀⠀⡿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⡆⠀⠀⠀⠀⠀
     */

    public static void main(String[] args) {
        app = new MMSApp();
        app.init(new Args(args));
    }

    private void init(Args args) {
        // Configuration should ALWAYS be loaded first.
        loadConfig(args);

        statistics = new OperationStatistics();
        loadStatistics(config); // This loads the database, it depends on the config being enabled.

        logger.info("Initializing bot runtime...");
        try { // Listeners and commands added here because im too lazy to do reflection
            runtime = new BotRuntime(
                    config,
                    List.of(new PingCommand(), new LockCommand(), new OpenCommand(), new StatsCommand(), new RoleCommand(), new DataCommand(), new ResumeCommand()),
                    List.of(new CommandHandler(), new StatsListener(), new ClaimListener(), new LockChannelListener())
            );
        } catch (InterruptedException ex) {
            logger.error("Failed to initialize bot: ", ex);
        }

        logger.info("Setting up Blaster Design Factory status...");
        spaceStatus = new SpaceStatus(runtime); // This depends on a bot being enabled, as it needs to set the status.

        logger.info("Starting door monitor...");
        try {
            doorMonitor = new DoorMonitor(config.virtual);
            doorMonitor.setupMonitor();
            doorMonitor.getMonitorThread().start();
        } catch (IllegalStateException e) {
            logger.error("Failed to initialize GPIO: ", e);
        }

        logger.info("Adding shutdown hooks...");
        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown));

        logger.info("Bot is now fully operational!"); // :3
    }

    // Not super crucial, but just provides consistency when testing
    private void onShutdown() {
        doorMonitor.stopMonitoring();
        runtime.getJda().shutdown();
        statistics.closeDatabase();
    }

    private void loadConfig(Args args) {
        logger.info("Initializing Config...");

        try {
            File specificFile = new File(args.getArg("config"));
            if (!specificFile.exists()) throw new RuntimeException("Configuration file not found at specified location: " + configFile.getAbsolutePath());
            configFile = specificFile;
        } catch (NullPointerException ex) {
            logger.warn("Config file not specified, using default configuration file: {}", configFile.getAbsolutePath());
        }

        try {
            config = JsonSerializable.load(configFile,Config.class, new Config());
        } catch (Exception ex) {
            logger.warn("Generating default configuration file: {}", configFile.getAbsolutePath());
        }

        logger.info("Saving configuration file...");
        config.save();
    }

    private void loadStatistics(Config config) {
        try {
            statistics.startDatabase(config.statisticsFile);
            logger.info("Database started.");
        } catch (Exception ex) {
            logger.error("Failed to load statistics database: ", ex);
            System.exit(1);
        }
    }

    public SpaceStatus getSpaceStatus() {
        return spaceStatus;
    }

    public BotRuntime getRuntime() {
        return runtime;
    }

    public OperationStatistics getStatistics() {
        return statistics;
    }

    public Config getConfig() {
        return config;
    }

    public DoorMonitor getDoorMonitor() {
        return doorMonitor;
    }

    public Logger getLogger() {
        return logger;
    }

    public File getActiveConfigFile() {
        return configFile;
    }

    public static MMSApp getApp() {
        return app;
    }
}