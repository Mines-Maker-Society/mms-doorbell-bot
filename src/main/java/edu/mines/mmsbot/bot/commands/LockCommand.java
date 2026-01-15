package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class LockCommand extends AbstractCommand {

    public LockCommand() {
        super(Commands.slash("lock", "Overrides the sensor or locks the space until the switch is triggered again.")
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

        // Execute lock override
        if (spaceStatus().manualLock(member.getIdLong(), pingMembers)) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setTitle("🔒 Manual Lock")
                            .setDescription("""
                                    The Blaster Design Factory has been manually locked.
                                    Members will be notified the next time the door sensor changes state.""")
                            .build())
                    .setEphemeral(true)
                    .queue();
        } else {
            if (spaceStatus().overrideLock(member.getIdLong())) {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setTitle("🔒 Lock Override Activated")
                                .setDescription("""
                                        The Blaster Design Factory is already locked.
                                        Normal sensor operation will resume after the door sensor toggles back to this state.""")
                                .build())
                        .setEphemeral(true)
                        .queue();
            } else {
                event.replyEmbeds(EmbedUtils.defaultEmbed()
                                .setTitle("🔒 Lock Override Already Active")
                                .setDescription("Normal sensor operation will resume after the door sensor toggles back to this state.")
                                .build())
                        .setEphemeral(true)
                        .queue();
            }
        }

    }
}