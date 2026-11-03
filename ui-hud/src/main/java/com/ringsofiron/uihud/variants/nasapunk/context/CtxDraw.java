package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * The pieces this combination adds to the kit (variations/nasapunk-context/context.js): the text
 * sizes of the spec's 12.4 (labels 12, body 13, values 15), labels and values, key caps, the
 * severity shapes, and the controls C1 and X3 share (lit push buttons, guarded switches, the trim
 * selector, the barber-pole strip).
 */
final class CtxDraw {
    static final float LB = 12f, B = 13f, V = 15f;
    static final Color KORRY = NpColors.rgb(0x1F2226), KORRY_RIM = NpColors.rgb(0x121416), KORRY_INK = NpColors.rgb(0xD2D5CF);
    static final Color BARBER_DARK = NpColors.rgb(0x16181B), GUARD_DARK = NpColors.rgb(0x1A1A1A);
    static final Color COVER = new Color(200 / 255f, 220 / 255f, 235 / 255f, 1f), HINGE = NpColors.rgb(0x5A646E);
    static final Color SLIP = new Color(5 / 255f, 7 / 255f, 10 / 255f, 0.92f), SLIP_EDGE = NpColors.rgb(0x2A333D);
    static final Color BUTTON = NpColors.rgb(0x0E141A);

    private CtxDraw() {}

    /** A label: dim bold capitals at 12. */
    static float lab(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
    }

    static float labEnd(NpCanvas cv, float x, float y, String t) {
        return cv.text(x, y, t, LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
    }

    /** Body text, at 13. */
    static float body(NpCanvas cv, float x, float y, String t, boolean bold, Color c) {
        return cv.text(x, y, t, B, bold, c, 0.3f, NpCanvas.START);
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

    /** A keyboard key's cap: a hint of the key, not a button. */
    static float capW(NpCanvas cv, String k) {
        return cv.width(k, LB, true, 0f) + 10f;
    }

    static float capKey(NpCanvas cv, float x, float y, String k, float h) {
        float w = capW(cv, k);
        NpHardware.softKey(cv, x, y, w, h, false);
        cv.text(x + w / 2f, y + h / 2f + 4.3f, k, LB, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
        return w;
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
     * The severity's shape on a text line ending at baseline {@code y}: a diamond for a warning, a
     * triangle for a caution, centred on {@code x}. B612 has neither glyph.
     */
    static void severity(NpCanvas cv, float x, float y, Level l, Color c) {
        if (l == Level.WARNING) NpSymbols.diamond(cv, x, y - 4.7f, 4.6f, c, 1.2f, true);
        else cv.fillPoly(new float[] {x, y - 10.5f, x + 5.6f, y, x - 5.6f, y}, c);
    }

    /** A lens lit by a level, its legend led by the level's shape; unlit with no level. */
    static void sevLens(NpCanvas cv, float x, float y, float w, float h, String name, Level l) {
        Color lit = l == null || l == Level.ADVISORY ? null : color(l);
        if (lit == null) {
            NpHardware.lens(cv, x, y, w, h, name, null, false, LB);
            return;
        }
        NpHardware.lens(cv, x, y, w, h, "", lit, false, LB);
        float tw = cv.width(name, LB, true, 0.2f), sx = x + w / 2f - (tw + 14f) / 2f, base = y + h / 2f + LB * 0.36f;
        if (l == Level.WARNING) NpSymbols.diamond(cv, sx + 4f, base - 4.3f, 3.6f, NpColors.INK, 1f, true);
        else cv.fillPoly(new float[] {sx + 4f, base - 8.6f, sx + 8.6f, base, sx - 0.6f, base}, NpColors.INK);
        cv.text(sx + 14f, base, name, LB, true, NpColors.INK, 0.2f, NpCanvas.START);
    }

    // ---- C1's and X3's controls -----------------------------------------------------------------

    /** How a push button stands: unlit, running (green), can't be given now (strip), armed (amber). */
    enum Btn { IDLE, ON, NA, ARMED }

    /**
     * A lit push button with a two-line legend: the order, then its state. {@code name}: both lines
     * are the order's name (a helm order, ABANDON SHIP), so running lights the whole legend.
     */
    static void korry(NpCanvas cv, Camera cam, float x, float y, float w, float h, String l1, String l2, Btn st, boolean name) {
        boolean na = st == Btn.NA;
        Color lit = st == Btn.ON ? NpColors.GREEN : st == Btn.ARMED ? NpColors.AMBER : null;
        Color ink = na ? NpColors.DIM : name && lit != null ? lit : KORRY_INK;
        cv.fillRound(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2.5f, KORRY_RIM, 1f);
        cv.fillRound(x, y, w, h, 1.5f, KORRY, 1f);
        cv.fillRound(x, y, w, h * 0.45f, 1.5f, NpColors.WHITE, 0.04f);
        boolean two = l2 != null && !l2.isEmpty();
        cv.text(x + w / 2f, two ? y + h / 2f - 3f : y + h / 2f + 4.3f, l1, LB, true, ink, 0.4f, NpCanvas.MIDDLE);
        if (two) cv.text(x + w / 2f, y + h / 2f + 11f, l2, LB, true, name ? ink : lit != null ? lit : NpColors.DIM, 0.4f, NpCanvas.MIDDLE);
        if (lit != null) cv.fill(x + 5f, y + h - 5f, w - 10f, 2.5f, lit);
        if (na) barber(cv, cam, x + 5f, y + h - 6f, w - 10f);
    }

    /** A guarded switch: the button under a hinged clear cover with a striped rim; open, the cover stands on its hinge. */
    static void guarded(NpCanvas cv, Camera cam, float x, float y, float w, float h, String l1, String l2, Btn st, boolean name, boolean open) {
        stripes(cv, cam, x - 5f, y - 5f, w + 10f, h + 10f, 8f, NpColors.ORANGE, GUARD_DARK);
        korry(cv, cam, x, y, w, h, l1, l2, st, name);
        if (!open) {
            cv.fillRound(x - 1f, y - 1f, w + 2f, h + 2f, 2f, COVER, 0.08f);
        } else {
            cv.fillRound(x - 3f, y - 11f, w + 6f, 5f, 1f, COVER, 0.22f);
            cv.line(x - 3f, y - 6f, x + w + 3f, y - 6f, 1f, HINGE);
        }
    }

    /** The barber-pole strip, 4 tall: an order that can't be given now. */
    static void barber(NpCanvas cv, Camera cam, float x, float y, float w) {
        stripes(cv, cam, x, y, w, 4f, 6f, NpColors.STENCIL, BARBER_DARK);
    }

    /** Stripes at 45 degrees over a rectangle, half {@code a} and half {@code b}, {@code period} apart across them. */
    static void stripes(NpCanvas cv, Camera cam, float x, float y, float w, float h, float period, Color a, Color b) {
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

    /**
     * The shield trim as a five-way selector, 100 x 40: TRIM, and the hull in miniature with a mark
     * on each side it can lean to and a dot for even; the side chosen is lit cyan.
     */
    static void trimSelector(NpCanvas cv, Camera cam, float x, float y, String cur, boolean na) {
        float w = 100f, h = 40f;
        Color ink = na ? NpColors.DIM : KORRY_INK;
        cv.fillRound(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2.5f, KORRY_RIM, 1f);
        cv.fillRound(x, y, w, h, 1.5f, KORRY, 1f);
        cv.fillRound(x, y, w, h * 0.45f, 1.5f, NpColors.WHITE, 0.04f);
        cv.text(x + 8f, y + 24f, "TRIM", LB, true, ink, 0.4f, NpCanvas.START);
        float cx = x + 70f, cy = y + 19f;
        cv.poly(new float[] {cx - 20f, cy - 5f, cx + 10f, cy - 5f, cx + 19f, cy, cx + 10f, cy + 5f, cx - 20f, cy + 5f}, true, 1.2f, ink);
        side(cv, cx - 12f, cy - 9f, cx + 6f, cy - 9f, "port", cur, na, ink);
        side(cv, cx - 12f, cy + 9f, cx + 6f, cy + 9f, "starboard", cur, na, ink);
        boolean bow = !na && "bow".equals(cur), stern = !na && "stern".equals(cur);
        cv.poly(new float[] {cx + 24f, cy - 5f, cx + 26f, cy, cx + 24f, cy + 5f}, false, bow ? 2.5f : 1.5f, bow ? NpColors.CYAN : ink);
        cv.line(cx - 25f, cy - 5f, cx - 25f, cy + 5f, stern ? 2.5f : 1.5f, stern ? NpColors.CYAN : ink);
        cv.disc(cx - 4f, cy, 4.4f, !na && "even".equals(cur) ? NpColors.CYAN : ink);
        if (na) barber(cv, cam, x + 5f, y + h - 6f, w - 10f);
    }

    private static void side(NpCanvas cv, float x1, float y1, float x2, float y2, String which, String cur, boolean na, Color ink) {
        boolean on = !na && which.equals(cur);
        cv.line(x1, y1, x2, y2, on ? 2.5f : 1.5f, on ? NpColors.CYAN : ink);
    }

    /** A square push button on the glass with its legend (H5's − and +), 24 across. */
    static void button(NpCanvas cv, float x, float y, float w, float h, String t, float size) {
        cv.fill(x, y, w, h, BUTTON);
        cv.stroke(x, y, w, h, 1f, NpColors.KEY_EDGE);
        cv.text(x + w / 2f, y + h / 2f + size * 0.36f, t, size, true, NpColors.WHITE, 0f, NpCanvas.MIDDLE);
    }

    /** A button with a key cap and a word (X2's), 26 tall. */
    static void keyButton(NpCanvas cv, float x, float y, float w, String key, String t) {
        cv.fillRound(x, y, w, 26f, 1.5f, NpColors.KEY_FACE, 1f);
        cv.stroke(x, y, w, 26f, 1f, NpColors.KEY_EDGE);
        float kw = capKey(cv, x + 5f, y + 4f, key, 18f);
        cv.text(x + 5f + kw + 8f, y + 17.5f, t, B, true, NpColors.WHITE, 0.5f, NpCanvas.START);
    }
}
