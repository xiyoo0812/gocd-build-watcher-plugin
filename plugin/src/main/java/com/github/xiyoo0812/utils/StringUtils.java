package com.github.xiyoo0812.utils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.thoughtworks.go.plugin.api.logging.Logger;
import java.util.ArrayList;

public final class StringUtils {
    private static final Logger LOGGER = Logger.getLoggerFor(StringUtils.class);

    public static boolean isBlank(String text) {
        return text == null || text.matches("\\s*");
    }

    public static String capitalize(String text) {
        return isBlank(text) ? text : text.substring(0, 1).toUpperCase() + text.substring(1);
    }

     public static String getEmailAddressesInString(String text) {
        ArrayList<String> emails = new ArrayList<>();

        Matcher matcher = Pattern.compile("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,15}").matcher(text);
        while (matcher.find()) {
           emails.add(matcher.group());
        }
        LOGGER.info("Found emails: " + emails);
        return emails.get(0);
    }

    public static HashMap<String, String> extractPipelineAndCounter(String text) {
        String[] parts = text.split("/");

        if (parts.length < 2) {
            return null;
        }

        HashMap<String, String> res = new HashMap<>();
        res.put("pipeline", parts[0]);
        res.put("counter", parts[1]);

        return res;
    }
}
