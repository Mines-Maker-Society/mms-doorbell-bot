package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class PingCommand extends AbstractCommand {

    public PingCommand() {
        super(Commands.slash("ping", "Get this application's latency to discord."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        event.reply("Ping!").setEphemeral(true)
                .flatMap(v ->
                        event.getHook().editOriginalFormat("Ponged after %d ms", System.currentTimeMillis() - time)
                ).queue();
    }
}
