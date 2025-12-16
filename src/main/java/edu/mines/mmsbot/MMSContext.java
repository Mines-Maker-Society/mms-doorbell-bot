package edu.mines.mmsbot;

import edu.mines.mmsbot.bot.BotRuntime;
import edu.mines.mmsbot.bot.framework.SpaceStatus;
import edu.mines.mmsbot.data.Config;
import edu.mines.mmsbot.data.OperationStatistics;
import org.slf4j.Logger;

public interface MMSContext {

    default MMSApp app() {
        return MMSApp.getApp();
    }

    default SpaceStatus spaceStatus() {
        return app().getSpaceStatus();
    }

    default BotRuntime runtime() {
        return app().getRuntime();
    }

    default OperationStatistics stats() {
        return app().getStatistics();
    }

    default Logger log() {
        return app().getLogger();
    }

    default Config config() {
        return app().getConfig();
    }
}
