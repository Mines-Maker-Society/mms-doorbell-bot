package edu.mines.mmsbot.bot;

import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.bot.framework.SpaceDiscord;
import edu.mines.mmsbot.bot.framework.SpaceStatus;
import edu.mines.mmsbot.data.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotRuntime implements MMSContext {
    private final JDA jda;
    private final SpaceDiscord server;
    private final List<AbstractCommand> commandList;
    private final List<ListenerAdapter> listenersList;

    public BotRuntime(Config config, List<AbstractCommand> commands, List<ListenerAdapter> listeners) throws InterruptedException {
        jda = JDABuilder.create(config.token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS)) // The intents we actually need are left as an exercise to the reader.
                .setStatus(OnlineStatus.ONLINE)
                .build().awaitReady(); // Do not forget.

        commandList = commands;
        listenersList = listeners;
        server = new SpaceDiscord(jda);

        if (server.isVerified(true)) { // Did you .awaitReady()?
            log().info("Server verified successfully.");
        } else {
            log().warn("Server is not configured properly!");
        }

        registerListeners(jda,listeners);
        registerCommands(jda,commands);
        
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.getPresence().setActivity(Activity.customStatus("MMS Bot is Operational."));
    }

    /**
     * Registers all listeners defined in the main class.
     */
    private void registerListeners(JDA jda, List<ListenerAdapter> listenerAdapters) {
        for (ListenerAdapter listenerAdapter : listenerAdapters) {
            jda.addEventListener(listenerAdapter);
        }
    }

    /**
     * Registers all the commands defined in the main class.
     */
    private void registerCommands(JDA jda, List<AbstractCommand> commands) {
        jda.updateCommands()
                .addCommands(commands.stream().map(AbstractCommand::getCommand).toList())
                .queue();
    }

    public List<AbstractCommand> getCommandList() {
        return commandList;
    }

    public List<ListenerAdapter> getListenersList() {
        return listenersList;
    }

    /**
     * Returns the instance of the active command of the class in the parameter.
     */
    public <T extends AbstractCommand> T getCommand(Class<T> clazz) {
        return commandList.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the instance of the active listener of the class in the parameter.
     */
    public <T extends ListenerAdapter> T getListener(Class<T> clazz) {
        return listenersList.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public SpaceDiscord getServer() {
        return server;
    }

    public JDA getJda() {
        return jda;
    }
}
