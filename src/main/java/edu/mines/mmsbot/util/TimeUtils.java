package edu.mines.mmsbot.util;

import java.text.SimpleDateFormat;
import java.time.LocalTime;
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

    public static String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();

        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        String period = hour < 12 ? "AM" : "PM";

        return String.format("%d:%02d %s", displayHour, minute, period);
    }

    public static String formatDuration(long milliseconds, boolean includeMillis) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Duration must not be negative.");
        }

        final long DAY = 86_400_000;
        final long HOUR = 3_600_000;
        final long MINUTE = 60_000;
        final long SECOND = 1_000;

        StringBuilder result = new StringBuilder();

        milliseconds = appendUnit(result, milliseconds, DAY, "day");
        milliseconds = appendUnit(result, milliseconds, HOUR, "hour");
        milliseconds = appendUnit(result, milliseconds, MINUTE, "minute");
        milliseconds = appendUnit(result, milliseconds, SECOND, "second");

        if (includeMillis && milliseconds > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append(milliseconds).append(milliseconds == 1 ? " millisecond" : " milliseconds");
        }

        return result.isEmpty() ? "0 milliseconds" : result.toString();
    }

    private static long appendUnit(StringBuilder result, long remaining, long unitMillis, String name) {
        long value = remaining / unitMillis;
        if (value > 0) {
            if (!result.isEmpty()) result.append(", ");
            result.append(value).append(' ').append(name).append(value == 1 ? "" : "s");
            remaining %= unitMillis;
        }
        return remaining;
    }
}
