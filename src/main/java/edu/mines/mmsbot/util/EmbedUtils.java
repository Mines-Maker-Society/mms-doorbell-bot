package edu.mines.mmsbot.util;

import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;
import net.dv8tion.jda.api.EmbedBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;


public final class EmbedUtils implements MMSContext {

    private static final String[] splashes = getSplashes();


    private static String[] getSplashes() {
        try (InputStream is = EmbedUtils.class.getResourceAsStream("/splashes.txt")) {
            if (is == null) throw new IOException("Resource was null.");
            byte[] bytes = is.readAllBytes();

            return new String(bytes, StandardCharsets.UTF_8).split("\n");
        } catch (IOException ex) {
            MMSApp.getApp().getLogger().error("Failed to load splashes: ", ex);
            return new String[] {"Courtesy of 2026 Webmaster"};
        }
    }

    public static EmbedBuilder defaultEmbed() {
        int index = Math.abs(new Random().nextInt()) % splashes.length;
        return new EmbedBuilder()
                .setAuthor("Mines Maker Society","https://oreconnect.mines.edu/MMS/club_signup", "https://static-prod-us-east-1.campusgroups.com/upload/mines/2025/s3_image_upload_4018398_MMS_Fixed_Logo_V162_224183357.png")
                .setFooter(splashes[index])
                .setColor(0x96BEF0)
                .setTimestamp(Instant.now());
    }
}
