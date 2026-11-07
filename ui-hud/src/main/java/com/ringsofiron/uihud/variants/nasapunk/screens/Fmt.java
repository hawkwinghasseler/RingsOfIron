package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The windows' words as the style guide writes them: instruments in capitals with thin-space
 * thousands and the unit list's signs (M³), the game's own sentences left in sentence case.
 */
public final class Fmt {
    private static final Pattern COMMA_THOUSANDS = Pattern.compile("(?<=\\d),(?=\\d{3}(?!\\d))");
    private static final Pattern CUBIC = Pattern.compile("(?i)(?<=\\d|\\b)m3\\b");

    private Fmt() {}

    /** The game's words in capitals for an instrument, its thousands spaced and its cubic metres M³. */
    public static String caps(String s) {
        if (s == null || s.isEmpty()) return "";
        return digits(CUBIC.matcher(s).replaceAll("m³")).toUpperCase(Locale.ROOT);
    }

    /** The game's comma thousands ("12,000 cr") with thin spaces, as the glass writes them. */
    public static String digits(String s) {
        if (s == null || s.isEmpty()) return "";
        return COMMA_THOUSANDS.matcher(s).replaceAll(String.valueOf(NpCanvas.THIN));
    }

    /** The game's sentence with its first letter a capital and its thousands spaced. */
    public static String sentence(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = digits(s.trim());
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Credits, whole, thousands spaced: the value; its unit is CR. */
    public static String credits(double cr) {
        return NpFormat.thousands(Math.round(cr));
    }

    /** Credits with their unit, for a line of text: "3 900 CR". */
    public static String cr(double cr) {
        return credits(cr) + " CR";
    }

    /** Units of cargo, whole when they are, else to a tenth. */
    public static String units(double u) {
        double r = Math.rint(u);
        return Math.abs(u - r) < 0.05 ? NpFormat.thousands((long) r) : NpFormat.tenths(u);
    }

    /** A price a unit: a tenth under 100, whole from there, as the market writes it. */
    public static String price(double p) {
        return p >= 99.95 ? NpFormat.thousands(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p);
    }

    /** A signed whole number with a true minus: "+5", "−2", "+0". */
    public static String signed(double v) {
        long r = Math.round(v);
        return (r < 0 ? NpFormat.MINUS : "+") + Math.abs(r);
    }

    /** "1 OF 3" style counts and other plain numbers. */
    public static String n(long v) {
        return NpFormat.thousands(v);
    }

    /** "the nose" to "NOSE": a hardpoint's place as a mount's name. */
    public static String mount(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.regionMatches(true, 0, "the ", 0, 4)) t = t.substring(4);
        return caps(t);
    }

    /** "Propellant 40%" style shares of 0 to 1 as a whole per cent. */
    public static String percent(float share) {
        return Math.round(Math.max(0f, share) * 100f) + "%";
    }
}
