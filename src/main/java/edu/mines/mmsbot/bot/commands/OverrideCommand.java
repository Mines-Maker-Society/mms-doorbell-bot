package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class OverrideCommand extends AbstractCommand implements MMSContext {

    public OverrideCommand() {
        super(Commands.slash("lock-override","Overrides the sensor and sets the status until the switch is triggered again.")
                .addOption(OptionType.BOOLEAN,"lock-state","Set to true to mark the Blaster Design Factory as closed/locked",true)
                .addOption(OptionType.BOOLEAN,"mute-ping","Optionally, disable the ping",false)
                .setContexts(InteractionContextType.GUILD)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild().getIdLong() != config().targetServer.serverID) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("This command can only be used in the Mines Maker Society Discord.")
                            .build())
                    .setEphemeral(true)
                    .queue();
        }

        boolean lockState = event.getOption("lock-state", true, OptionMapping::getAsBoolean);
        boolean mutePing = event.getOption("mute-ping",false, OptionMapping::getAsBoolean);

        Member member = event.getMember();
        if (member == null) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("An unknown error occurred while retrieving your membership status. Please contact the Webmaster.")
                            .build())
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (member.getRoles().stream().map(ISnowflake::getIdLong).toList().contains(config().targetServer.keyHolderRoleID)) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("This command can only be used by key holders (%s)."
                                    .formatted(MMSApp.getApp().getRuntime().getServer().getKeyHolderRole().getAsMention()))
                            .build())
                    .setEphemeral(true)
                    .queue();
        }

        if (lockState) {
            if (spaceStatus().isLocked()) {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setDescription("The Blaster Design Factory is already locked!")
                                .build())
                        .setEphemeral(true).queue();
                return;
            }
            spaceStatus().lock(member.getIdLong(), !mutePing);
        } else {
            if (!spaceStatus().isLocked()) {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setDescription("The Blaster Design Factory is already open!")
                                .build())
                        .setEphemeral(true).queue();
                return;
            }
            spaceStatus().open(member.getIdLong(), !mutePing);
        }

        event.replyEmbeds(EmbedUtils.defaultEmbed()
                        .setDescription("Marked the Blaster Design Factory as " + (lockState ? "locked" : "unlocked") + (mutePing ? " quietly." :  " and pinged members."))
                        .build())
                .setEphemeral(true).queue();
    }
}
