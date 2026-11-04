package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * The parts a slab's rows are built of (the mockups' run of parts): labels and units as legends,
 * numbers in seven segments standing on the row's baseline, words in dot matrix, and times to go in
 * the spec's forms (5.4). Each draws at {@code x} on baseline {@code b} and returns the x after it.
 */
final class Rows {
    private Rows() {
    }

    /** A label or unit: a dim legend. */
    static float lab(Filament f, float x, float b, String t) {
        return x + f.legend(x, b, t, f.o().dim()) + 4f;
    }

    /** A state word that is lit or its ghost. */
    static float state(Filament f, float x, float b, String t, boolean lit, Phosphor color) {
        return x + f.legend(x, b, t, f.o().lit(lit).norm().color(color)) + 6f;
    }

    /** Digits, 12 tall, right-aligned in {@code cells}. */
    static float num(Filament f, float x, float b, String v, int cells) {
        return num(f, x, b, v, cells, Phosphor.ZINC, Drive.NORM);
    }

    static float num(Filament f, float x, float b, String v, int cells, Phosphor color, float level) {
        return x + f.seg7(x, b - 12f, 12f, v, f.o().cells(cells).right().color(color).level(level)) + 6f;
    }

    /** Words in dot matrix at pitch 1.5, in {@code cells}. */
    static float txt(Filament f, float x, float b, String v, int cells) {
        return txt(f, x, b, v, cells, Phosphor.ZINC, Drive.NORM);
    }

    static float txt(Filament f, float x, float b, String v, int cells, Phosphor color, float level) {
        return x + f.dm(x, Geo.dmTop(b), v, f.o().pitch(Geo.P).cells(cells).color(color).level(level)) + 6f;
    }

    /**
     * A time to go: "T-42 S" under a minute, "T-12:05" under an hour, "T-3 H 05 M" under a day,
     * "T-2.4 D" beyond (5.4). Nothing for NaN.
     */
    static float time(Filament f, float x, float b, double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return x;
        float cx = lab(f, x, b, "T-") - 2f;
        long s = (long) Math.ceil(seconds - 1e-6);
        if (s < 60) {
            cx = num(f, cx, b, Long.toString(s), 2) - 2f;
            return lab(f, cx, b, "S");
        }
        if (s < 3600) return num(f, cx, b, Fmt.mmss(s), 4);
        if (s < 86_400) {
            cx = num(f, cx, b, Long.toString(s / 3600), 2) - 2f;
            cx = lab(f, cx, b, "H");
            cx = num(f, cx, b, String.format(java.util.Locale.ROOT, "%02d", s % 3600 / 60), 2) - 2f;
            return lab(f, cx, b, "M");
        }
        cx = num(f, cx, b, Fmt.fit(s / 86_400.0, 3, 1), 3) - 2f;
        return lab(f, cx, b, "D");
    }

    /** A time to go as text, for a dot-matrix field: "42 S", "12:05", "3 H 05 M", "2.4 D". */
    static String timeText(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return "";
        long s = (long) Math.ceil(seconds - 1e-6);
        if (s < 60) return s + " S";
        if (s < 3600) return Fmt.mmss(s);
        if (s < 86_400) return String.format(Locale.ROOT, "%d H %02d M", s / 3600, s % 3600 / 60);
        return Fmt.fit(s / 86_400.0, 3, 1) + " D";
    }

    /** A distance with its unit, in {@code cells}: metres under a kilometre, kilometres above. */
    static float range(Filament f, float x, float b, double meters, int cells) {
        float cx = num(f, x, b, Fmt.range(meters, cells), cells) - 2f;
        return lab(f, cx, b, Fmt.km(meters) ? "KM" : "M");
    }

    /**
     * Shortens {@code sb} to {@code cells}: at a clause's end when one falls in the last 40%, else at
     * the last whole word; an aside cut short goes whole, and no stop or bracket is left trailing.
     */
    static void fit(StringBuilder sb, int cells) {
        if (sb.length() <= cells) return;
        int cut = cells;
        while (cut > 0 && sb.charAt(cut) != ' ') cut--;
        for (int i = cut - 1; i >= cells * 0.6f; i--) {
            char ch = sb.charAt(i);
            if (ch == ':' || ch == ';') {
                cut = i;
                break;
            }
        }
        sb.setLength(cut > 0 ? cut : cells);
        int aside = sb.lastIndexOf("(");
        if (aside > 0 && sb.indexOf(")", aside) < 0) sb.setLength(aside);
        while (sb.length() > 0 && " :;,(".indexOf(sb.charAt(sb.length() - 1)) >= 0) sb.setLength(sb.length() - 1);
    }

    /** {@code s} in capitals, shortened to {@code cells} as {@link #fit} does. */
    static String words(String s, int cells) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.trim().toUpperCase(Locale.ROOT));
        fit(sb, cells);
        return sb.toString();
    }
}
