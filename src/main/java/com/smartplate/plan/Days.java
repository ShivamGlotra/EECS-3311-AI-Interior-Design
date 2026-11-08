package com.smartplate.plan;

import java.util.List;
import java.util.Locale;

/**
 * Day names used by meal plans. A plan of N days uses the first N days,
 * starting Monday.
 */
public final class Days {

    public static final List<String> WEEK = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
            "Sunday");

    private Days() {
    }

    /**
     * "tue", "TUESDAY", "Tuesday's" -> "Tuesday"; returns null if not a day name.
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replace("'s", "");
        if (t.length() < 3) {
            return null;
        }
        for (String day : WEEK) {
            String d = day.toLowerCase(Locale.ROOT);
            if (d.equals(t) || (t.length() >= 3 && d.startsWith(t) && t.length() <= d.length())) {
                return day;
            }
        }
        return null;
    }

    public static String findIn(String text) {
        if (text == null) {
            return null;
        }
        for (String word : text.split("[^A-Za-z'-]+")) {
            if (word.contains("-")) {
                for (String part : word.split("-")) {
                    String day = normalize(part);
                    if (day != null && day.equalsIgnoreCase(part.replace("'s", ""))) {
                        return day;
                    }
                }
            } else {
                String day = normalize(word);
                if (day != null) {
                    return day;
                }
            }
        }
        return null;
    }

    /** The first {@code n} days of the week. */
    public static List<String> firstDays(int n) {
        return WEEK.subList(0, Math.max(0, Math.min(n, WEEK.size())));
    }
}
