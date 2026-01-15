package edu.mines.mmsbot.bot.commands;

import edu.mines.mmsbot.bot.framework.AbstractCommand;
import edu.mines.mmsbot.util.EmbedUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class RoleCommand extends AbstractCommand {

    public RoleCommand() {
        super(Commands.slash("doorbell-role","Run this command to add or remove yourself from the doorbell ping role.").setContexts(InteractionContextType.GUILD));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (serverIncorrect(event)) return;

        Member member = event.getMember();
        if (member == null) {
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                            .setDescription("An unknown error occurred while retrieving your membership status. Please contact the Webmaster.")
                            .build())
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (member.getRoles().stream().map(Role::getIdLong).toList().contains(runtime().getServer().getUnlockPingRole().getIdLong())) {
            runtime().getServer().getGuild().removeRoleFromMember(member,runtime().getServer().getUnlockPingRole()).queue();
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                    .setDescription("You will no longer be notified whenever the Blaster Design Factory is locked or unlocked. Run this command to get notified again.")
                    .build()).setEphemeral(true).queue();
        } else {
            runtime().getServer().getGuild().addRoleToMember(member,runtime().getServer().getUnlockPingRole()).queue();
            event.replyEmbeds(EmbedUtils.defaultEmbed()
                    .setDescription("You will now be notified whenever the Blaster Design Factory is locked or unlocked. Run this command to stop getting these notifications.")
                    .build()).setEphemeral(true).queue();
        }
    }
}
