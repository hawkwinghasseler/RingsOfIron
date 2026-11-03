package com.ringsofiron.uihud.variants.vfd;

import java.util.Locale;

/**
 * Numbers and words as FILAMENT's fixed fields show them: a value cut to a field's cells rather than
 * a field grown to the value, times as m:ss, ranges in metres under a kilometre and kilometres above
 * (the lexicon's rule), and names in capitals cut to their cells.
 */
public final class Fmt {
    private Fmt() {
    }

    /**
     * {@code v} in at most {@code cells} seven-segment cells, with up to {@code maxDecimals} decimals
     * where they fit ('.' joins the cell before, so "7.67" takes three); "" for NaN, and nines when the
     * whole part will not fit.
     */
    public static String fit(double v, int cells, int maxDecimals) {
        if (Double.isNaN(v)) return "";
        if (Double.isInfinite(v)) return v > 0 ? "9".repeat(Math.max(1, cells)) : "-" + "9".repeat(Math.max(1, cells - 1));
        boolean neg = v < 0;
        double a = Math.abs(v);
        int room = neg ? cells - 1 : cells;
        if (room <= 0) return "";
        for (int d = Math.max(0, maxDecimals); d >= 0; d--) {
            String s = String.format(Locale.ROOT, "%." + d + "f", a);
            int digits = s.length() - (d > 0 ? 1 : 0);
            if (digits <= room) {
                if (neg && isZero(s)) neg = false;
                return neg ? "-" + s : s;
            }
        }
        return (neg ? "-" : "") + "9".repeat(room);
    }

    private static boolean isZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '0' && c != '.') return false;
        }
        return true;
    }

    /** A range in {@code cells}: metres under a kilometre, kilometres above. "" for NaN. */
    public static String range(double meters, int cells) {
        if (Double.isNaN(meters)) return "";
        if (meters < 1000) return fit(Math.round(meters), cells, 0);
        return fit(meters / 1000.0, cells, 1);
    }

    /** Whether {@link #range} gave kilometres. */
    public static boolean km(double meters) {
        return !Double.isNaN(meters) && meters >= 1000;
    }

    /** A range with its unit, for dot matrix: "140 M", "2.8 KM", "1,204 KM". */
    public static String rangeText(double meters) {
        if (Double.isNaN(meters)) return "";
        if (meters < 1000) return Math.round(meters) + " M";
        if (meters < 100_000) return String.format(Locale.ROOT, "%.1f KM", meters / 1000.0);
        return String.format(Locale.ROOT, "%,d KM", Math.round(meters / 1000.0));
    }

    /** Seconds as m:ss, or h:mm:ss from an hour, or d:hh from a day; "" for NaN or less than nothing. */
    public static String mmss(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return "";
        long s = (long) Math.ceil(seconds - 1e-6);
        if (s >= 86_400) return String.format(Locale.ROOT, "%dD%02d", s / 86_400, s % 86_400 / 3600);
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Whole seconds, "" for NaN. */
    public static String secs(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return "";
        return Long.toString((long) Math.ceil(seconds - 1e-6));
    }

    /** The career's day, from 1. */
    public static int day(double simSeconds) {
        return (int) Math.floor(Math.max(0, simSeconds) / 86_400.0) + 1;
    }

    /** The time of day, hh:mm:ss. */
    public static String clock(double simSeconds) {
        long s = (long) Math.floor(Math.max(0, simSeconds)) % 86_400;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60);
    }

    /** {@code s} in capitals, cut to {@code cells}; "" for null. */
    public static String cut(String s, int cells) {
        if (s == null || s.isEmpty()) return "";
        String u = s.toUpperCase(Locale.ROOT);
        return u.length() <= cells ? u : u.substring(0, Math.max(0, cells));
    }

    /** Seven-segment cells {@code text} takes: '.' and ':' join the cell before. */
    public static int segCells(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '.' || c == ':') && n > 0) continue;
            n++;
        }
        return n;
    }
}
