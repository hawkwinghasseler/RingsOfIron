package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The pieces this combination adds to the kit (variations/nasapunk-stations/stations.js): the
 * spec's text floor (section 17: body 13, labels and legends 12), labels and values, key caps,
 * names cut to fit, and the severity shapes. Copied from the context combination's helpers rather
 * than shared, as each combination keeps its own package.
 */
final class StDraw {
    static final float LB = 12f, B = 13f, V = 15f;
    /** The dark rim round a lens or a tile. */
    static final Color RIM = NpColors.BEZEL_SHADOW;
    /** A lens's lit highlight. */
    static final Color SHINE = new Color(1f, 1f, 1f, 0.14f);
    /** The board's legends while it is dim in Green. */
    static final Color LOW = NpColors.rgb(0x4A5058);
    static final Color BUTTON = NpColors.rgb(0x0E141A);
    private static final Pattern MARK = Pattern.compile("\\s+MK\\s*[0-9IVX]+$");

    private StDraw() {}

    /** A label: dim bold capitals at 12. */
    static float lab(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.4f, NpCanvas.START);
    }

    static float labEnd(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.4f, NpCanvas.END);
    }

    static float labW(NpCanvas cv, String t) {
        return cv.width(t, LB, true, 0.4f);
    }

    /** Body text, bold at 13. */
    static float body(NpCanvas cv, float x, float y, String t, Color c) {
        return cv.text(x, y, t, B, true, c, 0.3f, NpCanvas.START);
    }

    static float bodyEnd(NpCanvas cv, float x, float y, String t, Color c) {
        return cv.text(x, y, t, B, true, c, 0.3f, NpCanvas.END);
    }

    static float bodyW(NpCanvas cv, String t) {
        return cv.width(t, B, true, 0.3f);
    }

    /** A value then its unit dim at 12, 4 apart, from {@code x}; returns the width drawn. */
    static float val(NpCanvas cv, float x, float y, String v, String unit, float size, Color c) {
        float vw = cv.text(x, y, v, size, true, c, 0f, NpCanvas.START);
        if (unit == null || unit.isEmpty()) return vw;
        return vw + 4f + cv.text(x + vw + 4f, y, unit, LB, true, NpColors.DIM, 0.3f, NpCanvas.START);
    }

    /** A value and its unit ending at {@code x}; returns the pair's left edge. */
    static float valEnd(NpCanvas cv, float x, float y, String v, String unit, float size, Color c) {
        float w = valW(cv, v, unit, size);
        val(cv, x - w, y, v, unit, size, c);
        return x - w;
    }

    static float valW(NpCanvas cv, String v, String unit, float size) {
        float w = cv.width(v, size, true, 0f);
        return unit == null || unit.isEmpty() ? w : w + 4f + cv.width(unit, LB, true, 0.3f);
    }

    /** A label and its value after it, 5 apart; returns the width drawn. */
    static float pair(NpCanvas cv, float x, float y, String label, String v, Color c) {
        float lw = lab(cv, x, y, label);
        return lw + 5f + body(cv, x + lw + 5f, y, v, c);
    }

    static float pairW(NpCanvas cv, String label, String v) {
        return labW(cv, label) + 5f + bodyW(cv, v);
    }

    /** A keyboard key's cap, {@code h} tall from {@code y}: a hint of the key, not a button. */
    static float capW(NpCanvas cv, String k) {
        return cv.width(k, LB, true, 0f) + 10f;
    }

    static float capKey(NpCanvas cv, float x, float y, String k, float h) {
        float w = capW(cv, k);
        NpHardware.softKey(cv, x, y, w, h, false);
        cv.text(x + w / 2f, y + h / 2f + 4.3f, k, LB, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
        return w;
    }

    /** A cap and what it does, on a line whose baseline is {@code base}; returns the width drawn. */
    static float hint(NpCanvas cv, float x, float base, String k, String does, Color c) {
        float kw = capKey(cv, x, base - 13f, k, 17f);
        return kw + 5f + cv.text(x + kw + 5f, base, does, LB, true, c, 0.4f, NpCanvas.START);
    }

    static float hintW(NpCanvas cv, String k, String does) {
        return capW(cv, k) + 5f + cv.width(does, LB, true, 0.4f);
    }

    /** {@code s} cut with an ellipsis to fit {@code w}. */
    static String fit(NpCanvas cv, String s, float w, float size, boolean bold, float track) {
        if (s == null) return "";
        if (cv.width(s, size, bold, track) <= w) return s;
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n).trim() + "…";
            if (cv.width(t, size, bold, track) <= w) return t;
        }
        return "…";
    }

    /**
     * A name to fit {@code w}, losing its leading words before its letters (PIRATE RAIDER 6 to RAIDER
     * 6), and keeping a short last word, a hull number, when even that must be cut.
     */
    static String fitName(NpCanvas cv, String s, float w, float size, boolean bold, float track) {
        if (s == null) return "";
        String t = s.trim();
        if (cv.width(t, size, bold, track) <= w) return t;
        for (int sp = t.indexOf(' '); sp > 0; sp = t.indexOf(' ')) {
            String rest = t.substring(sp + 1).trim();
            if (rest.indexOf(' ') < 0 && rest.length() <= 3) break;
            t = rest;
            if (cv.width(t, size, bold, track) <= w) return t;
        }
        int last = t.lastIndexOf(' ');
        if (last > 0 && t.length() - last - 1 <= 3) {
            String tail = t.substring(last);
            return fit(cv, t.substring(0, last), w - cv.width(tail, size, bold, track), size, bold, track) + tail;
        }
        return fit(cv, t, w, size, bold, track);
    }

    /** {@code s} to fit {@code w}, losing its trailing words before its letters (HARPOON LAUNCHER to HARPOON). */
    static String fitHead(NpCanvas cv, String s, float w, float size, boolean bold, float track) {
        if (s == null) return "";
        String t = s.trim();
        while (cv.width(t, size, bold, track) > w) {
            int sp = t.lastIndexOf(' ');
            if (sp <= 0) return fit(cv, t, w, size, bold, track);
            t = t.substring(0, sp).trim();
        }
        return t;
    }

    static Color color(Level l) {
        return l == Level.WARNING ? NpColors.RED : l == Level.CAUTION ? NpColors.AMBER : NpColors.WHITE;
    }

    /**
     * The severity's shape on a text line with baseline {@code y}: a diamond for a warning, a
     * triangle for a caution, centred on {@code x}. B612 has neither glyph.
     */
    static void severity(NpCanvas cv, float x, float y, Level l, Color c) {
        if (l == Level.WARNING) NpSymbols.diamond(cv, x, y - 4.4f, 4.2f, c, 1.2f, true);
        else cv.fillPoly(new float[] {x, y - 9.5f, x + 5f, y, x - 5f, y}, c);
    }

    /** A page's header: its name left, its status right, and the rule under them. */
    static void head(NpCanvas cv, float gx, float gy, float gw, String name, String status, Color statusColor) {
        cv.text(gx + 6f, gy + 13f, name, LB, true, NpColors.WHITE, 0.8f, NpCanvas.START);
        if (status != null && !status.isEmpty()) {
            float room = gw - 18f - cv.width(name, LB, true, 0.8f);
            cv.text(gx + gw - 6f, gy + 13f, fit(cv, status, room, LB, true, 0.4f), LB, true, statusColor, 0.4f, NpCanvas.END);
        }
        cv.line(gx + 4f, gy + 18f, gx + gw - 4f, gy + 18f, 1f, NpColors.GRATICULE);
    }

    /**
     * A dead glass: a display whose console has no power reads NO POWER rather than a stale picture
     * (sections 5.1 and 9.1), the same on an MFD, a cluster and the gunner sight.
     */
    static void dead(NpCanvas cv, float gx, float gy, float gw, float gh) {
        cv.text(gx + gw / 2f, gy + gh / 2f + 5f, "NO POWER", 15f, true, NpColors.DIM, 1f, NpCanvas.MIDDLE);
    }

    /** A square push button on the glass with its legend (SENSORS' − and +). */
    static void button(NpCanvas cv, float x, float y, float w, float h, String t) {
        cv.fill(x, y, w, h, BUTTON);
        cv.stroke(x, y, w, h, 1f, NpColors.KEY_EDGE);
        cv.text(x + w / 2f, y + h / 2f + B * 0.36f, t, B, true, NpColors.WHITE, 0f, NpCanvas.MIDDLE);
    }

    /** A status box's width for {@code t} at 12, with 6 each side. */
    static float boxW(NpCanvas cv, String t) {
        return cv.width(t, LB, true, 0.5f) + 12f;
    }

    /** A status box at 12 on a line with baseline {@code base}; returns its width. */
    static float box(NpCanvas cv, float x, float base, String t, NpHardware.BoxState st) {
        float w = boxW(cv, t);
        NpHardware.box(cv, x, base - 13f, w, 17f, t, st, LB, false);
        return w;
    }

    /** A lens with a one-line legend, {@code h} tall: unlit dark, lit with dark ink. */
    static void lens(NpCanvas cv, float x, float y, float w, float h, String legend, Color lit) {
        NpHardware.lens(cv, x, y, w, h, legend, lit, false, LB);
    }

    /** A speed with its unit, "3.9 M/S", and a minus when opening. */
    static String speed(float v) {
        String[] s = NpFormat.speed(v);
        return (v < -0.05f ? NpFormat.MINUS : "") + s[0] + " " + s[1];
    }

    /** A distance with its unit, "1.2 KM". */
    static String range(double metres) {
        String[] r = NpFormat.range(metres);
        return r[0] + " " + r[1];
    }

    static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /**
     * A mount's place without the kind of weapon ("FWD GUN" is FWD, "PORT PD 2" PORT 2), as the
     * chips name guns by where they sit (section 13.2).
     */
    static String place(String name) {
        String[] w = upper(name).split(" ");
        StringBuilder b = new StringBuilder();
        for (String s : w) {
            if (s.equals("GUN") || s.equals("PD") || s.equals("MSL") || s.equals("BEAM")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(s);
        }
        String out = b.toString();
        return out.isEmpty() || out.matches("[0-9]+") ? upper(name) : out;
    }

    /** A weapon's name in capitals without its mark ("Coilgun Mk1" is COILGUN). */
    static String weaponName(String weapon) {
        return MARK.matcher(upper(weapon)).replaceAll("").trim();
    }

    /** A crew member's name as initials ("Cole Brandt" is CB), for half a chip. */
    static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String s : upper(name).trim().split("\\s+")) if (!s.isEmpty()) b.append(s.charAt(0));
        return b.toString();
    }
}
