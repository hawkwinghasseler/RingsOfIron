package com.ringsofiron.uihud.variants.nasapunk;

/**
 * Numbers as the style guide writes them: thin-space thousands, a true minus, countdowns in M:SS
 * (H:MM:SS past an hour), and the units the word list uses. Values and units are returned apart,
 * since the glass draws a unit dim and smaller beside its value.
 */
public final class NpFormat {
    public static final String MINUS = "−";

    private NpFormat() {}

    /** A whole number with thin spaces between its thousands. */
    public static String thousands(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder(s.length() + 4);
        if (n < 0) b.append(MINUS);
        int lead = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) b.append(NpCanvas.THIN);
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** One decimal, or none from 100 up, with thousands spaced. */
    public static String tenths(double v) {
        if (Math.abs(v) >= 99.95) return thousands(Math.round(v));
        String s = String.format(java.util.Locale.ROOT, "%.1f", Math.abs(v));
        return (v < -0.05 ? MINUS : "") + s;
    }

    /** M:SS, or H:MM:SS past an hour; never negative. */
    public static String clock(double seconds) {
        long t = Math.max(0L, (long) Math.ceil(seconds));
        long h = t / 3600, m = (t / 60) % 60, s = t % 60;
        if (h > 0) return h + ":" + two(m) + ":" + two(s);
        return m + ":" + two(s);
    }

    /** A countdown: T−M:SS. */
    public static String tMinus(double seconds) {
        return "T" + MINUS + clock(seconds);
    }

    /**
     * Time left to a deadline, to the two units that matter: 2D 21H, 5H 12M, then M:SS under an
     * hour; PAST once it has gone, "" with none.
     */
    public static String timeLeft(double seconds) {
        if (Double.isNaN(seconds)) return "";
        if (seconds < 0) return "PAST";
        long t = (long) seconds;
        if (t >= 86400L) return t / 86400L + "D " + t % 86400L / 3600L + "H";
        if (t >= 3600L) return t / 3600L + "H " + t % 3600L / 60L + "M";
        return clock(seconds);
    }

    /** The time of day, HH:MM:SS. */
    public static String timeOfDay(double secondsOfDay) {
        long t = Math.max(0L, (long) Math.floor(secondsOfDay)) % 86400L;
        return two(t / 3600) + ":" + two((t / 60) % 60) + ":" + two(t % 60);
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : Long.toString(v);
    }

    /** A distance in metres as value and unit: M under a kilometre, then KM, one decimal under 100. */
    public static String[] range(double metres) {
        double m = Math.abs(metres);
        if (m < 1000.0) return new String[] {Long.toString(Math.round(m)), "M"};
        double km = m / 1000.0;
        return new String[] {tenths(km), "KM"};
    }

    /** A speed in m/s as value and unit: M/S under 10 km/s, KM/S above. */
    public static String[] speed(double mps) {
        double v = Math.abs(mps);
        if (v < 10000.0) return new String[] {v < 100.0 ? tenths(v) : thousands(Math.round(v)), "M/S"};
        return new String[] {String.format(java.util.Locale.ROOT, "%.2f", v / 1000.0), "KM/S"};
    }

    /** A whole number of m/s with thousands spaced. */
    public static String mps(double mps) {
        return thousands(Math.round(mps));
    }

    /** A percentage, whole. */
    public static String percent(double frac) {
        return Math.round(Math.max(0.0, frac) * 100.0) + "%";
    }
}
