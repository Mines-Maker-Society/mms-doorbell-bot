package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class CommandHandler extends ListenerAdapter implements MMSContext {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        final String name = event.getName();
        final List<AbstractCommand> commandList = runtime().getCommandList();
        for (AbstractCommand abstractCommand : commandList) {
            if (!abstractCommand.getCommand().getName().equals(name)) continue;
            abstractCommand.execute(event);
        }
    }
}
