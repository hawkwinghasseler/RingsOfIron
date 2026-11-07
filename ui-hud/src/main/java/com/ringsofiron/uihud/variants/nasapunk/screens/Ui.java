package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * The pieces every Nasa-punk window draws with, as the three combinations' mockups draw them: the
 * text sizes (labels 12, body 13, values 15), labels and values with their units, key caps, lit push
 * buttons and the barber-pole strip of an order that can't be given, the cyan cursor, rules, and the
 * severity shapes B612 has no glyph for.
 */
public final class Ui {
    public static final float LB = 12f, B = 13f, V = 15f;
    public static final Color KORRY = NpColors.rgb(0x1F2226), KORRY_RIM = NpColors.rgb(0x121416), KORRY_INK = NpColors.rgb(0xD2D5CF);
    public static final Color BARBER_DARK = NpColors.rgb(0x16181B);
    public static final Color RULE = NpColors.rgb(0x1C232B);
    public static final Color SHADE = NpColors.rgb(0x0B1015);

    private Ui() {}

    /** A label: dim bold capitals at 12. */
    public static float lab(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
    }

    public static float labEnd(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
    }

    public static float labW(NpCanvas cv, String t) {
        return cv.width(t, LB, true, 0.6f);
    }

    /** A value then its unit dim at 12, 4 apart, from {@code x}; returns the width drawn. */
    public static float val(NpCanvas cv, float x, float y, String v, String unit, float size, Color c) {
        float vw = cv.text(x, y, v, size, true, c, 0f, NpCanvas.START);
        if (unit == null || unit.isEmpty()) return vw;
        return vw + 4f + cv.text(x + vw + 4f, y, unit, LB, true, NpColors.DIM, 0.3f, NpCanvas.START);
    }

    /** A value and its unit ending at {@code x}; returns the pair's left edge. */
    public static float valEnd(NpCanvas cv, float x, float y, String v, String unit, float size, Color c) {
        float w = valW(cv, v, unit, size);
        val(cv, x - w, y, v, unit, size, c);
        return x - w;
    }

    public static float valW(NpCanvas cv, String v, String unit, float size) {
        float w = cv.width(v, size, true, 0f);
        return unit == null || unit.isEmpty() ? w : w + 4f + cv.width(unit, LB, true, 0.3f);
    }

    /** A keyboard key's cap, 10 wider than its letters: a hint of the key, not a button. */
    public static float capW(NpCanvas cv, String k) {
        return cv.width(k, LB, true, 0f) + 10f;
    }

    public static float capKey(NpCanvas cv, float x, float y, String k, float h) {
        float w = capW(cv, k);
        NpHardware.softKey(cv, x, y, w, h, false);
        cv.text(x + w / 2f, y + h / 2f + 4.3f, k, LB, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
        return w;
    }

    /** A key cap then what it does, dim, on one line from {@code x}; returns the width drawn. */
    public static float keyHint(NpCanvas cv, float x, float y, String k, String does, float h) {
        float w = capKey(cv, x, y, k, h);
        return w + 8f + lab(cv, x + w + 8f, y + h / 2f + 4.3f, does);
    }

    public static float keyHintW(NpCanvas cv, String k, String does) {
        return capW(cv, k) + 8f + labW(cv, does);
    }

    /** {@code s} cut with an ellipsis to fit {@code w}. */
    public static String fit(NpCanvas cv, String s, float w, float size, boolean bold, float track) {
        if (s == null) return "";
        if (cv.width(s, size, bold, track) <= w) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (cv.width(s.substring(0, mid).trim() + "…", size, bold, track) <= w) lo = mid;
            else hi = mid - 1;
        }
        return lo == 0 ? "…" : s.substring(0, lo).trim() + "…";
    }

    /** {@code s} broken into lines no wider than {@code w}, at spaces; at most {@code max} lines, the last cut to fit. */
    public static int wrap(NpCanvas cv, String s, float w, float size, boolean bold, float track, int max, String[] out) {
        if (s == null || s.isEmpty() || max <= 0) return 0;
        int n = 0;
        String rest = s.trim();
        while (!rest.isEmpty() && n < max) {
            if (cv.width(rest, size, bold, track) <= w || n == max - 1) {
                out[n++] = fit(cv, rest, w, size, bold, track);
                break;
            }
            int cut = -1;
            for (int i = rest.indexOf(' '); i > 0; i = rest.indexOf(' ', i + 1)) {
                if (cv.width(rest.substring(0, i), size, bold, track) > w) break;
                cut = i;
            }
            if (cut <= 0) {
                out[n++] = fit(cv, rest, w, size, bold, track);
                break;
            }
            out[n++] = rest.substring(0, cut);
            rest = rest.substring(cut + 1).trim();
        }
        return n;
    }

    /** The colour a cell's tone draws in. */
    public static Color color(Cell.Tone t) {
        switch (t) {
            case LABEL: return NpColors.DIM;
            case CYAN: return NpColors.CYAN;
            case GREEN: return NpColors.GREEN;
            case AMBER: return NpColors.AMBER;
            case RED: return NpColors.RED;
            case MAGENTA: return NpColors.MAGENTA;
            default: return NpColors.WHITE;
        }
    }

    /** Whether a tone is set bold: names and values are, body text and labels are not. */
    public static boolean bold(Cell.Tone t) {
        return t != Cell.Tone.BODY;
    }

    // ---- buttons ---------------------------------------------------------------------------------

    /**
     * A lit push button, as C1's orders are: dark with its legend, a green bar when what it orders is
     * running, amber when armed, and the barber strip with a dim legend when it can't be given.
     * {@code picked} rings it cyan, for the key that gives it.
     */
    public static void korry(NpCanvas cv, Camera cam, float x, float y, float w, float h, String label, Act.State st, boolean live, boolean picked) {
        Color lit = st == Act.State.ON ? NpColors.GREEN : st == Act.State.ARMED ? NpColors.AMBER : null;
        boolean na = !live && lit == null;
        Color ink = na ? NpColors.DIM : lit != null ? lit : KORRY_INK;
        cv.fillRound(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2.5f, KORRY_RIM, 1f);
        cv.fillRound(x, y, w, h, 1.5f, KORRY, 1f);
        cv.fillRound(x, y, w, h * 0.45f, 1.5f, NpColors.WHITE, 0.04f);
        float size = LB;
        String t = fit(cv, label, w - 8f, size, true, 0.4f);
        cv.text(x + w / 2f, y + h / 2f + (lit != null || na ? 1.5f : 4.3f), t, size, true, ink, 0.4f, NpCanvas.MIDDLE);
        if (lit != null) cv.fill(x + 5f, y + h - 5f, w - 10f, 2.5f, lit);
        if (na) barber(cv, cam, x + 5f, y + h - 6f, w - 10f);
        if (picked) cv.stroke(x - 3f, y - 3f, w + 6f, h + 6f, 1.5f, NpColors.CYAN);
    }

    /** The barber-pole strip, 4 tall: an order that can't be given now. */
    public static void barber(NpCanvas cv, Camera cam, float x, float y, float w) {
        stripes(cv, cam, x, y, w, 4f, 6f, NpColors.STENCIL, BARBER_DARK);
    }

    /** A short barber strip as a key to the reasons under a table, 12 wide. */
    public static void barberKey(NpCanvas cv, Camera cam, float x, float baseline) {
        barber(cv, cam, x, baseline - 7f, 12f);
    }

    /** Stripes at 45 degrees over a rectangle, half {@code a} and half {@code b}, {@code period} apart across them. */
    public static void stripes(NpCanvas cv, Camera cam, float x, float y, float w, float h, float period, Color a, Color b) {
        cv.fill(x, y, w, h, b);
        boolean clipped = cam != null && cv.clip(cam, x, y, w, h);
        float step = period * 1.4142f, band = step / 2f;
        for (float px = x - h; px < x + w; px += step) {
            float[] q = {px, y + h, px + band, y + h, px + band + h, y, px + h, y};
            if (!clipped) for (int i = 0; i < q.length; i += 2) q[i] = Math.max(x, Math.min(x + w, q[i]));
            cv.fillPoly(q, a);
        }
        if (clipped) cv.unclip();
    }

    /** The cyan cursor box round a row. */
    public static void cursor(NpCanvas cv, float x, float y, float w, float h) {
        cv.stroke(x, y, w, h, 1.5f, NpColors.CYAN);
    }

    /** A hairline rule across a glass. */
    public static void rule(NpCanvas cv, float x, float y, float w) {
        cv.fill(x, y, w, 1f, RULE);
    }

    /** A part's head between dashed rules ("— ROUTE —"), as the terms panels have them. */
    public static void dashedHead(NpCanvas cv, float x, float y, float w, String title) {
        float tw = labW(cv, title), cx = x + w / 2f;
        cv.dashed(x, y - 4f, cx - tw / 2f - 10f, y - 4f, 1f, NpColors.DIM, 6f, 4f);
        cv.dashed(cx + tw / 2f + 10f, y - 4f, x + w, y - 4f, 1f, NpColors.DIM, 6f, 4f);
        cv.text(cx, y, title, LB, true, NpColors.DIM, 0.6f, NpCanvas.MIDDLE);
    }

    /** A bar across {@code w}, 4 tall: its share lit in {@code c} over the track. */
    public static void bar(NpCanvas cv, float x, float y, float w, float share, Color c) {
        cv.fill(x, y, w, 4f, NpColors.TRACK);
        float f = Math.max(0f, Math.min(1f, share));
        if (f > 0f) cv.fill(x, y, w * f, 4f, c);
    }

    /**
     * A severity's shape on a text line with its baseline at {@code y}, centred on {@code x}: a diamond
     * for a warning, a triangle for a caution, a dot for a notable message; hollow once it cleared.
     */
    public static void mark(NpCanvas cv, float x, float y, int mark, boolean hollow) {
        switch (mark) {
            case Row.WARNING:
                NpSymbols.diamond(cv, x, y - 4.7f, 5f, NpColors.RED, 1.4f, !hollow);
                break;
            case Row.CAUTION:
                if (hollow) cv.poly(new float[] {x, y - 10f, x + 5.6f, y, x - 5.6f, y}, true, 1.4f, NpColors.AMBER);
                else cv.fillPoly(new float[] {x, y - 10.5f, x + 5.8f, y, x - 5.8f, y}, NpColors.AMBER);
                break;
            case Row.NOTABLE:
                cv.disc(x, y - 4.5f, 5f, NpColors.WHITE);
                break;
            default:
                break;
        }
    }

    /** A small step key (◀ ▶) on the glass, 22 across: a filter's or a list's. */
    public static void stepKey(NpCanvas cv, float x, float y, float w, float h, boolean right) {
        cv.fill(x, y, w, h, NpColors.KEY_FACE);
        cv.stroke(x, y, w, h, 1f, NpColors.KEY_EDGE);
        float cx = x + w / 2f, cy = y + h / 2f, s = 4.5f;
        if (right) cv.fillPoly(new float[] {cx - s * 0.7f, cy - s, cx + s * 0.8f, cy, cx - s * 0.7f, cy + s}, NpColors.STENCIL);
        else cv.fillPoly(new float[] {cx + s * 0.7f, cy - s, cx - s * 0.8f, cy, cx + s * 0.7f, cy + s}, NpColors.STENCIL);
    }

    /** An up or down arrow key (▲ ▼) on the glass, for the scroll keys' hint. */
    public static void arrowKey(NpCanvas cv, float x, float y, float w, float h, boolean up) {
        cv.fill(x, y, w, h, NpColors.KEY_FACE);
        cv.stroke(x, y, w, h, 1f, NpColors.KEY_EDGE);
        float cx = x + w / 2f, cy = y + h / 2f, s = 4.5f;
        if (up) cv.fillPoly(new float[] {cx - s, cy + s * 0.7f, cx, cy - s * 0.8f, cx + s, cy + s * 0.7f}, NpColors.STENCIL);
        else cv.fillPoly(new float[] {cx - s, cy - s * 0.7f, cx, cy + s * 0.8f, cx + s, cy - s * 0.7f}, NpColors.STENCIL);
    }
}
