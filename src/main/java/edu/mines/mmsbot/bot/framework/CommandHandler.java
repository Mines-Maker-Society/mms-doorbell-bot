package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class CommandHandler extends ListenerAdapter implements MMSContext {
    @Override // Use the AbstractCommand class if you want to create new commands. This will route the event to it properly.
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        final String name = event.getName();
        final List<AbstractCommand> commandList = runtime().getCommandList();
        for (AbstractCommand abstractCommand : commandList) {
            if (!abstractCommand.getCommand().getName().equals(name)) continue;
            try {
                abstractCommand.execute(event);
            } catch (Exception ex) {
                log().error("An error occurred while executing a command.",ex);
                MessageEmbed reply = EmbedUtils.defaultEmbed()
                        .setTitle("An error occurred while processing this command.")
                        .setDescription("Please contact the Webmaster.")
                        .addField("Reason",ex.getMessage(),false)
                        .build();
                
                if (event.isAcknowledged()) {
                    event.getHook().editOriginalEmbeds(reply).queue();
                } else {
                    event.replyEmbeds(reply).queue();
                }
            }
        }
    }
}
