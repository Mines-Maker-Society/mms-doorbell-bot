package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class OpenCommand extends AbstractCommand {

    public OpenCommand() {
        super(Commands.slash("open", "Overrides the sensor or opens the space until the switch is triggered again.")
                .addOption(OptionType.BOOLEAN, "ping-members", "Optionally, ping the members", false)
                .setContexts(InteractionContextType.GUILD)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (serverIncorrect(event)) return;
        if (missingKeyholderRole(event)) return;

        Member member = event.getMember();
        boolean pingMembers = event.getOption("ping-members", false, OptionMapping::getAsBoolean);

        // Execute unlock override
        if (spaceStatus().manualOpen(member.getIdLong(), pingMembers)) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setTitle("🔓 Manual Open")
                            .setDescription("""
                                    The Blaster Design Factory has been manually opened.
                                    Members will be notified the next time the door sensor changes state.""")
                            .build())
                    .setEphemeral(true)
                    .queue();
        } else {
            if (spaceStatus().overrideOpen(member.getIdLong())) {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setTitle("🔓 Open Override Activated")
                                .setDescription("""
                                        The Blaster Design Factory is already locked.
                                        Normal sensor operation will resume after the door sensor toggles back to this state.""")
                                .build())
                        .setEphemeral(true)
                        .queue();
            } else {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setTitle("🔓 Open Override Already Active")
                                .setDescription("Normal sensor operation will resume after the door sensor toggles back to this state.")
                                .build())
                        .setEphemeral(true)
                        .queue();
            }
        }
    }
}