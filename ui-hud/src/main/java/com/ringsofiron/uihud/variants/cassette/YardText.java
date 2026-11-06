package com.ringsofiron.uihud.variants.cassette;

import java.util.Locale;

/**
 * Text for the Yard Standard's instruments: flaps take capitals cut to their cells (a name longer than
 * its flaps is cut in its last cell, never wrapped), drums take figures of a fixed width with no
 * thousands separator, and times read as the tubes and drums show them.
 */
public final class YardText {
    private YardText() {}

    /** {@code s} in capitals, cut to {@code n} characters with an ellipsis in the last. */
    public static String fit(String s, int n) {
        if (s == null) return "";
        String u = s.toUpperCase(Locale.ROOT);
        if (u.length() <= n) return u;
        return n <= 1 ? u.substring(0, Math.max(0, n)) : u.substring(0, n - 1) + "…";
    }

    /** {@code s} cut to {@code n} characters with an ellipsis, case kept (typed paper, tubes). */
    public static String cut(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return n <= 1 ? s.substring(0, Math.max(0, n)) : s.substring(0, n - 1) + "…";
    }

    /** {@code v} on drums of {@code digits} figures and {@code decimals} after the point: 0041.0. */
    public static String drums(double v, int digits, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return blank(digits, decimals);
        double max = Math.pow(10, digits) - Math.pow(10, -decimals);
        double a = Math.min(Math.abs(v), max);
        String f = decimals > 0 ? String.format(Locale.ROOT, "%0" + (digits + 1 + decimals) + "." + decimals + "f", a)
                : String.format(Locale.ROOT, "%0" + digits + "d", Math.round(a));
        return f;
    }

    /** Drums with nothing on them: blank wheels in the same places. */
    public static String blank(int digits, int decimals) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < digits; i++) b.append(' ');
        if (decimals > 0) {
            b.append('.');
            for (int i = 0; i < decimals; i++) b.append(' ');
        }
        return b.toString();
    }

    /** A whole number on {@code digits} drums, leading wheels blank rather than zero: " 410". */
    public static String count(double v, int digits) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return blank(digits, 0);
        long n = Math.min(Math.round(Math.abs(v)), (long) Math.pow(10, digits) - 1);
        return String.format(Locale.ROOT, "%" + digits + "d", n);
    }

    /** Seconds as m:ss under an hour, h:mm:ss under a day ("1:52", "1:20:00"). */
    public static String clock(double seconds) {
        if (Double.isNaN(seconds)) return " :  ";
        long s = Math.max(0L, (long) Math.ceil(seconds));
        if (s < 3600) return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    /** Seconds as "14 S", "2:05" or "1H 20M" for a tube line. */
    public static String span(double seconds) {
        if (Double.isNaN(seconds)) return "";
        long s = Math.max(0L, (long) Math.ceil(seconds));
        if (s < 100) return s + " S";
        if (s < 3600) return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        if (s < 86400) return String.format(Locale.ROOT, "%dH %02dM", s / 3600, (s / 60) % 60);
        return String.format(Locale.ROOT, "%dD %02dH", s / 86400, (s / 3600) % 24);
    }

    /** A distance on drums in km with one decimal under 100 km ("3.2"), whole km above ("412"). */
    public static String km(double metres) {
        if (Double.isNaN(metres)) return "   . ";
        double k = metres / 1000.0;
        if (k < 99.95) return String.format(Locale.ROOT, "%4.1f", k);
        return String.format(Locale.ROOT, "%5d", Math.min(99999L, Math.round(k)));
    }

    /** Thousands with a separator, for tube and paper text (never drums). */
    public static String thousands(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }
}
