package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class ResumeCommand extends AbstractCommand {

    public ResumeCommand() {
        super(Commands.slash("resume", "Clears any override and resumes normal sensor operation immediately.")
                .setContexts(InteractionContextType.GUILD)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (serverIncorrect(event)) return;
        if (missingKeyholderRole(event)) return;

        Member member = event.getMember();

        boolean sensorState = app().getDoorMonitor().getLastStableState();
        String sensorStateText = sensorState ? "LOCKED 🔒" : "OPEN 🔓";

        if (spaceStatus().clearOverride(member.getIdLong(), sensorState)) {
            String previousOverride = spaceStatus().getState().toString();

            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setTitle("✅ Normal Operation Resumed")
                            .setDescription("""
                                    Override cleared: **%s**
                                    Current sensor state: **%s**
                                    The system is now following the door sensor."""
                                    .formatted(previousOverride,sensorStateText))
                            .build())
                    .setEphemeral(true)
                    .queue();

            log().info("User {} manually resumed normal operation. Previous override: {}, Sensor state: {}",
                    member.getIdLong(), previousOverride, sensorStateText);
        } else {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setTitle("Normal Operation In Progress")
                            .setDescription("""
                                    Current sensor state: **%s**
                                    The system is already following the door sensor."""
                                    .formatted(sensorStateText))
                            .build())
                    .setEphemeral(true)
                    .queue();
        }
    }
}