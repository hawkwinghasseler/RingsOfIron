package com.ringsofiron.uihud.variants.cassette.screens;

import java.util.Locale;

/** Numbers as the windows' tubes and paper write them: units, prices, credits, the day and time. */
final class Fmt {
    private Fmt() {}

    /** Whole units with thousands, or one decimal when it isn't whole: "2,350", "12.5". */
    static String units(double u) {
        double r = Math.rint(u);
        return Math.abs(u - r) < 0.05 ? String.format(Locale.ROOT, "%,.0f", r) : String.format(Locale.ROOT, "%.1f", u);
    }

    /** A price a unit: "35.7" under a hundred, "1,850" above. */
    static String price(double p) {
        return p >= 100 ? String.format(Locale.ROOT, "%,.0f", p) : String.format(Locale.ROOT, "%.1f", p);
    }

    /** "1,240 cr". */
    static String credits(double c) {
        return String.format(Locale.ROOT, "%,.0f cr", c);
    }

    /** "1,240". */
    static String thousands(double c) {
        return String.format(Locale.ROOT, "%,.0f", c);
    }

    /** "DAY 004  02:11" for sim seconds. */
    static String stamp(double seconds) {
        long s = Math.max(0L, (long) Math.floor(seconds));
        return String.format(Locale.ROOT, "DAY %03d  %02d:%02d", s / 86400, (s / 3600) % 24, (s / 60) % 60);
    }

    /** "004 02:11", the log's left column. */
    static String dayTime(double seconds) {
        long s = Math.max(0L, (long) Math.floor(seconds));
        return String.format(Locale.ROOT, "%03d %02d:%02d", s / 86400, (s / 3600) % 24, (s / 60) % 60);
    }

    static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** The first letter in capitals: "the nose" as "The nose". */
    static String capital(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
