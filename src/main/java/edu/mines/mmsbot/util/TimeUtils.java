package edu.mines.mmsbot.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeUtils {

    public static String formatDate(long milliseconds) {
        Date date = new Date(milliseconds);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy, HH:mm");
        return sdf.format(date);
    }

    public static String formatDayTime(long milliseconds) {
        Date date = new Date(milliseconds);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(date);
    }

    public static String formatDuration(long milliseconds, boolean millis) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Duration must not be negative.");
        }

        long days = milliseconds / (1000 * 60 * 60 * 24);
        long hours = (milliseconds % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (milliseconds % (1000 * 60)) / 1000;
        long remainingMillis = milliseconds % 1000;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append(days == 1 ? " day" : " days");
            if (hours > 0 || minutes > 0 || seconds > 0 || remainingMillis > 0) {
                result.append(", ");
            }
        }
        if (hours > 0) {
            result.append(hours).append(hours == 1 ? " hour" : " hours");
            if (minutes > 0 || seconds > 0 || remainingMillis > 0) {
                result.append(", ");
            }
        }
        if (minutes > 0) {
            result.append(minutes).append(minutes == 1 ? " minute" : " minutes");
            if (seconds > 0 || remainingMillis > 0) {
                result.append(", ");
            }
        }
        if (seconds > 0) {
            result.append(seconds).append(seconds == 1 ? " second" : " seconds");
            if (remainingMillis > 0) {
                result.append(", ");
            }
        }
        if (remainingMillis > 0 && millis) {
            result.append(remainingMillis).append(remainingMillis == 1 ? " millisecond" : " milliseconds");
        }

        if (result.length() == 0) {
            return "0 milliseconds";
        }

        return result.toString();
    }
}
