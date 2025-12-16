package edu.mines.mmsbot.bot.framework;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public abstract class AbstractCommand {
    private final SlashCommandData command;

    public AbstractCommand(SlashCommandData command) {
        this.command = command;
    }

    public SlashCommandData getCommand() {
        return command;
    }

    public abstract void execute(SlashCommandInteractionEvent event);
}