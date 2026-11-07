package com.ringsofiron.uihud.variants.nasapunk.screens;

import java.util.Locale;

/**
 * The game's notices name who says them before a colon ("Helm: burn complete"): the source tags the
 * HUD's comm loop and the log file them under, and the words after it.
 */
public final class Sources {
    /** The sources the game's notices name, in the log filter's order; LOG for one that names none. */
    public static final String[] ALL = {"Helm", "Gunnery", "Shields", "Engineering", "Medical", "Crew", "Boarding", "Station", "Sponsor", "Comms", "Log"};

    private Sources() {}

    /** The source notice {@code text} names, in capitals ("ENGINEERING"), or LOG. */
    public static String of(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon > 0) {
            String src = text.substring(0, colon).trim();
            for (String s : ALL) if (s.equalsIgnoreCase(src)) return s.toUpperCase(Locale.ROOT);
        }
        return "LOG";
    }

    /** Notice {@code text} without its source, in sentence case. */
    public static String body(String text) {
        if (text == null) return "";
        int colon = text.indexOf(':');
        if (colon > 0) {
            String src = text.substring(0, colon).trim();
            for (String s : ALL) {
                if (s.equalsIgnoreCase(src)) return Fmt.sentence(text.substring(colon + 1));
            }
        }
        return Fmt.sentence(text);
    }

    /** The short tag the comm loop prints: ENG for Engineering, else the source. */
    public static String tag(String source) {
        return "ENGINEERING".equals(source) ? "ENG" : source;
    }
}
