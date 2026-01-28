package edu.mines.mmsbot.data.util;

import edu.mines.mmsbot.MMSContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CatStatsUtils implements MMSContext {
    
    private final Connection conn;
    
    public CatStatsUtils(Connection conn) {
        this.conn = conn;
    }
    
    
    public void updateMessage(long messageId, int cats) {

    }

    public void updateUser(long userId, int cats) {
        
    }
}
