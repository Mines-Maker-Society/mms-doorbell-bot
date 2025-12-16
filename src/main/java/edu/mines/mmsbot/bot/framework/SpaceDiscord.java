package edu.mines.mmsbot.bot.framework;

import edu.mines.mmsbot.MMSContext;
import edu.mines.mmsbot.data.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class SpaceDiscord implements MMSContext {

    private final Config.TargetServer ts;
    private final JDA jda;
    private Guild guild;
    private TextChannel lockChannel;
    private Role keyHolderRole;
    private Role unlockPingRole;

    public SpaceDiscord(JDA jda) {
        this.ts = config().targetServer;
        this.jda = jda;
    }

    // This function is where you verify all the config discord ID variables
    public boolean isVerified(boolean coldStart) {
        if (coldStart) this.guild = jda.getGuildById(ts.serverID);
        if (guild == null) {
            log().warn("Server with ID {} not found!",ts.serverID);
            return false;
        }

        this.lockChannel = guild.getTextChannelById(ts.lockChannelID);
        if (lockChannel == null) {
            log().warn("Lock channel with ID {} not found!",ts.lockChannelID);
            return false;
        }

        this.keyHolderRole = guild.getRoleById(ts.keyHolderRoleID);
        if (keyHolderRole == null) {
            log().warn("Key holder role could not be found with ID {}!",ts.keyHolderRoleID);
            return false;
        }

        this.unlockPingRole = guild.getRoleById(ts.unlockPingRole);
        if (unlockPingRole == null) {
            log().warn("Unlock ping role could not be found with ID {}!",ts.unlockPingRole);
            return false;
        }

        return true;
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    public Guild getGuild() {
        return guild;
    }

    public Role getUnlockPingRole() {
        return unlockPingRole;
    }

    public Role getKeyHolderRole() {
        return keyHolderRole;
    }

    public TextChannel getLockChannel() {
        return lockChannel;
    }
}
