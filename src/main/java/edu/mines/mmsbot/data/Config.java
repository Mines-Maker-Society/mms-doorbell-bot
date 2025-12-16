package edu.mines.mmsbot.data;

import edu.mines.mmsbot.MMSApp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Config implements JsonSerializable<Config> {
    @Override
    public File getFile() {
        return MMSApp.getApp().getActiveConfigFile();
    }


    public String token = "Your Token Goes Here";
    public String statisticsFile = "MMSBot/stats.sqlite";
    public TargetServer targetServer = new TargetServer();
    public int debounceSeconds = 60;
    public List<Long> developerIDs = new ArrayList<>(List.of(643598233913786387L, 320991166591926272L));
    public class TargetServer {
        public long serverID = 0L;
        public long lockChannelID = 0L;
        public long keyHolderRoleID = 0L;
        public long unlockPingRole = 0L;
    }
}
