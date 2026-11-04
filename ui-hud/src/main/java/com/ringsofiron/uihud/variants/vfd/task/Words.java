package com.ringsofiron.uihud.variants.vfd.task;

import java.util.Locale;

/**
 * Text fitted to a field the way a reader can still take it in: cut at a word, never inside one, or
 * for a line that must be read whole, a marquee.
 */
final class Words {
    /** Seconds a line longer than its field holds at each end, and per cell as it steps (style-vfd 9.3). */
    static final float HOLD = 1.6f, STEP = 0.14f;

    private Words() {
    }

    /**
     * The first cell to show of a {@code length} character line in a {@code cells} wide field after
     * {@code shown} seconds: 0 for a line that fits; else it holds at its start, steps a cell at a
     * time until its end shows, holds there, and starts again (the marquee, style-vfd 9.3).
     */
    static int scroll(int length, int cells, float shown) {
        int over = length - cells;
        if (over <= 0) return 0;
        float cycle = HOLD + over * STEP + HOLD;
        float t = Math.max(0f, shown) % cycle;
        if (t < HOLD) return 0;
        return Math.min(over, (int) ((t - HOLD) / STEP));
    }

    /** What a {@code cells} wide field shows of {@code s} after {@code shown} seconds: all of it when it fits, else the marquee's window. */
    static String marquee(String s, int cells, float shown) {
        if (s == null || cells <= 0) return "";
        if (s.length() <= cells) return s;
        int from = scroll(s.length(), cells, shown);
        return s.substring(from, from + cells);
    }

    /**
     * {@code s} in capitals in at most {@code cells}: whole when it fits, else cut after the last whole
     * word that fits (and any dangling comma or colon dropped), else cut at the field's end when its
     * first word alone is too long.
     */
    static String cut(String s, int cells) {
        if (s == null || s.isEmpty() || cells <= 0) return "";
        String u = s.trim().toUpperCase(Locale.ROOT);
        if (u.length() <= cells) return u;
        int at = u.lastIndexOf(' ', cells);
        String c = at > 0 ? u.substring(0, at) : u.substring(0, cells);
        while (!c.isEmpty() && " ,:;(-".indexOf(c.charAt(c.length() - 1)) >= 0) c = c.substring(0, c.length() - 1);
        return c;
    }
}
