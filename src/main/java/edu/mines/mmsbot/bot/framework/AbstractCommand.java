package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public abstract class AbstractCommand implements MMSContext {
    private final SlashCommandData command;

    public AbstractCommand(SlashCommandData command) {
        this.command = command;
    }

    public SlashCommandData getCommand() {
        return command;
    }

    public abstract void execute(SlashCommandInteractionEvent event);

    public boolean serverIncorrect(SlashCommandInteractionEvent event) {
        if (event.getGuild().getIdLong() != config().targetServer.serverID) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("This command can only be used in the Mines Maker Society Discord.")
                            .build())
                    .setEphemeral(true)
                    .queue();
            return true;
        }
        return false;
    }

    public boolean missingKeyholderRole(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("An unknown error occurred while retrieving your membership status. Please contact the Webmaster.")
                            .build())
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        if (!event.getMember().getRoles().stream().map(ISnowflake::getIdLong).toList().contains(config().targetServer.keyHolderRoleID)) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("This command can only be used by key holders (%s)."
                                    .formatted(runtime().getServer().getKeyHolderRole().getAsMention()))
                            .build())
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        return false;
    }
}