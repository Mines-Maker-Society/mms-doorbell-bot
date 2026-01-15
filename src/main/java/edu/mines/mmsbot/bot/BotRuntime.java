package edu.mines.mmsbot.bot;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.bot.framework.SpaceDiscord;
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

    private void registerListeners(JDA jda, List<ListenerAdapter> listenerAdapters) {
        for (ListenerAdapter listenerAdapter : listenerAdapters) {
            jda.addEventListener(listenerAdapter);
        }
    }

    private void registerCommands(JDA jda, List<AbstractCommand> commandList) { // Me when I don't use reflection
        jda.retrieveCommands().submit().whenComplete((onlineData, error) -> {
            final List<SlashCommandData> updatedData = commandList.stream().map(AbstractCommand::getCommand).toList();
            if (error != null) {
                log().warn("Error occurred while registering command: ", error);
                return;
            }
            for (SlashCommandData expected : updatedData) {
                if (!onlineData.stream().map(Command::getName).toList().contains(expected.getName())) {
                    log().info("Command discrepancy found! Updating application commands.");
                    jda.updateCommands()
                            .addCommands(updatedData)
                            .queue();
                    break;
                }
            }
        });
    }

    public List<AbstractCommand> getCommandList() {
        return commandList;
    }

    public List<ListenerAdapter> getListenersList() {
        return listenersList;
    }

    public <T extends AbstractCommand> T getCommand(Class<T> clazz) {
        return commandList.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

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
