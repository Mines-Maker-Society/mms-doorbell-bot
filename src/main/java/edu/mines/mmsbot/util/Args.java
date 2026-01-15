package edu.mines.mmsbot.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Args {
    private final Map<String, String> args = new HashMap<>();

    public Args(String[] flags) {
        StringBuilder combined = new StringBuilder();
        for (String flag : flags) {
            combined.append(flag).append(" ");
        }
        combined = new StringBuilder(combined.toString().trim());

        Pattern pattern = Pattern.compile("(--([A-Za-z0-9]+)=\"([^\"]+)\")");
        Matcher matcher = pattern.matcher(combined.toString());
        while (matcher.find()) {
            args.put(matcher.group(2),matcher.group(3));
        }
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public String getArg(String arg) throws NullPointerException {
        String value = getArgs().get(arg);
        if (value == null || value.isEmpty()) throw new NullPointerException("Argument \"" + arg + "\" not set!");
        return value;
    }
}
