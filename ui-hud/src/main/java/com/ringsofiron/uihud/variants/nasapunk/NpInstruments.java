package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * The glass's instruments as kit.js draws them ({@code I}): the horizontal tape, the plan-view
 * scope, the timeline, and a value with its unit.
 */
public final class NpInstruments {
    private NpInstruments() {}

    // ---- values ---------------------------------------------------------------------------------

    /**
     * A value with its unit dim and one size step smaller, 4 units after it, the pair ending at
     * {@code x}; returns the value's left edge.
     */
    public static float value(NpCanvas cv, float x, float y, String v, String unit, float size, boolean bold, Color c) {
        float unitW = unit == null || unit.isEmpty() ? 0f : cv.width(unit, size - 4f, false, 0f) + 4f;
        float vw = cv.text(x - unitW, y, v, size, bold, c, 0f, NpCanvas.END);
        if (unitW > 0f) cv.text(x, y, unit, size - 4f, false, NpColors.DIM, 0f, NpCanvas.END);
        return x - unitW - vw;
    }

    /** A value then its unit, starting at {@code x}; returns the width drawn. */
    public static float valueAt(NpCanvas cv, float x, float y, String v, String unit, float size, boolean bold, Color c) {
        float vw = cv.text(x, y, v, size, bold, c, 0f, NpCanvas.START);
        if (unit == null || unit.isEmpty()) return vw;
        return vw + 4f + cv.text(x + vw + 4f, y, unit, size - 4f, false, NpColors.DIM, 0f, NpCanvas.START);
    }

    public static float valueWidth(NpCanvas cv, String v, String unit, float size, boolean bold) {
        float w = cv.width(v, size, bold, 0f);
        return unit == null || unit.isEmpty() ? w : w + 4f + cv.width(unit, size - 4f, false, 0f);
    }

    // ---- the tape -------------------------------------------------------------------------------

    /** What a horizontal tape shows besides its pointer. */
    public static final class Tape {
        /** Bands under the scale, as from, to (0..1) and a colour each. */
        public float[] bands = new float[0];
        public Color[] bandColors = new Color[0];
        /** A magenta bracket over the scale from {@code bracketA} to {@code bracketB}; NaN for none. */
        public float bracketA = Float.NaN, bracketB = Float.NaN;
        /** A notch through the scale (HOME), and its label below; NaN for none. */
        public float notch = Float.NaN;
        public Color notchColor;
        public String notchLabel;
        public Color pointer = NpColors.WHITE;

        public Tape band(float from, float to, Color c) {
            float[] b = new float[bands.length + 2];
            System.arraycopy(bands, 0, b, 0, bands.length);
            b[bands.length] = from;
            b[bands.length + 1] = to;
            Color[] cs = new Color[bandColors.length + 1];
            System.arraycopy(bandColors, 0, cs, 0, bandColors.length);
            cs[bandColors.length] = c;
            bands = b;
            bandColors = cs;
            return this;
        }
    }

    /** A horizontal tape: a scale with quarter ticks, its bands, and a pointer at {@code frac}. */
    public static void tape(NpCanvas cv, float x, float y, float w, float frac, Tape o) {
        cv.line(x, y, x + w, y, 1.5f, NpColors.WHITE);
        for (int k = 0; k <= 4; k++) cv.line(x + w * k / 4f, y, x + w * k / 4f, y + 5f, 1f, NpColors.WHITE);
        if (o != null) {
            for (int i = 0; i < o.bandColors.length; i++) {
                float a = o.bands[i * 2], b = o.bands[i * 2 + 1];
                cv.fill(x + w * a, y - 4f, w * (b - a), 3f, o.bandColors[i]);
            }
            if (!Float.isNaN(o.bracketA)) {
                float a = x + w * o.bracketA, b = x + w * o.bracketB;
                cv.poly(new float[] {a, y - 10f, a, y - 14f, b, y - 14f, b, y - 10f}, false, 1.5f, NpColors.MAGENTA);
            }
            if (!Float.isNaN(o.notch)) {
                float nx = x + w * MathUtils.clamp(o.notch, 0f, 1f);
                cv.line(nx, y - 9f, nx, y + 7f, 2f, o.notchColor != null ? o.notchColor : NpColors.WHITE);
                if (o.notchLabel != null) cv.text(nx, y + 17f, o.notchLabel, 9f, true, o.notchColor != null ? o.notchColor : NpColors.DIM, 0.4f, NpCanvas.MIDDLE);
            }
        }
        float px = x + w * MathUtils.clamp(frac, 0f, 1f);
        Color pc = o != null && o.pointer != null ? o.pointer : NpColors.WHITE;
        cv.fillPoly(new float[] {px, y - 1f, px - 5f, y - 9f, px + 5f, y - 9f}, pc);
    }

    // ---- the scope ------------------------------------------------------------------------------

    /** A contact on a scope: where it is as a fraction of the range (x right, y down), and what. */
    public static final class Blip {
        public NpSymbols.Kind kind = NpSymbols.Kind.NEUTRAL;
        public float x, y, heading;
        public boolean locked, nav;
        public String label;
    }

    /**
     * A plan-view scope on centre ({@code cx}, {@code cy}) and radius {@code r}: range rings, a
     * cross, one's own ship as a chevron on its heading, and each blip; a blip past the rim is a
     * tick on the rim at its bearing.
     */
    public static void scope(NpCanvas cv, float cx, float cy, float r, float heading, Iterable<Blip> blips, String range) {
        cv.ring(cx, cy, r * 0.5f, 1f, NpColors.GRATICULE);
        cv.ring(cx, cy, r, 1f, NpColors.GRATICULE);
        cv.line(cx - r, cy, cx + r, cy, 1f, NpColors.GRATICULE);
        cv.line(cx, cy - r, cx, cy + r, 1f, NpColors.GRATICULE);
        float hr = heading * MathUtils.degreesToRadians;
        cv.line(cx, cy, cx + r * 0.42f * MathUtils.sin(hr), cy - r * 0.42f * MathUtils.cos(hr), 1f, NpColors.CYAN);
        NpSymbols.chevron(cv, cx, cy, heading, NpColors.CYAN, 1.5f, 4.5f);
        if (blips != null) for (Blip b : blips) blip(cv, cx, cy, r, b);
        if (range != null) cv.text(cx + r - 2f, cy + r - 2f, range, 9.5f, false, NpColors.DIM, 0f, NpCanvas.END);
    }

    private static void blip(NpCanvas cv, float cx, float cy, float r, Blip k) {
        Color col = NpSymbols.colorOf(k.kind);
        float d = (float) Math.sqrt(k.x * k.x + k.y * k.y);
        if (d > 1f) {
            float ux = k.x / d, uy = k.y / d;
            cv.line(cx + (r - 2f) * ux, cy + (r - 2f) * uy, cx + (r + 6f) * ux, cy + (r + 6f) * uy, 2.5f, col);
            return;
        }
        float x = cx + k.x * r, y = cy + k.y * r;
        switch (k.kind) {
            case MISSILE: case OWN_MISSILE: NpSymbols.chevron(cv, x, y, k.heading, col, 1.5f, 3.5f); break;
            case STATION: NpSymbols.station(cv, x, y, 3.5f, col, 1.2f); break;
            case DERELICT: cv.strokeDashed(x - 3f, y - 3f, 6f, 6f, 1.2f, col, 2f, 1.5f); break;
            case UNKNOWN: NpSymbols.quatrefoil(cv, x, y, 5f, col, 1.3f); break;
            default: NpSymbols.shape(cv, k.kind, x, y, 3.5f, col, 1.3f, k.locked, 0f); break;
        }
        if (k.locked) NpSymbols.brackets(cv, x, y, 8f, 3f, col, 1f);
        if (k.nav) cv.ring(x, y, 8f, 1.2f, NpColors.MAGENTA);
        if (k.label != null) cv.text(x + 8f, y - 5f, k.label, 9f, true, col, 0.4f, NpCanvas.START);
    }

    // ---- the timeline ---------------------------------------------------------------------------

    /** An event on a timeline: when (0..1 of its span), its words, and its mark. */
    public static final class Event {
        public float t;
        public String label, sub;
        public Color color = NpColors.MAGENTA;
        public boolean tick, filled;
    }

    /** A timeline: now at {@code x}, the future to the right, eighth ticks, and its events. */
    public static void timeline(NpCanvas cv, float x, float y, float w, Iterable<Event> events, String now, String end) {
        cv.line(x, y, x + w, y, 1f, NpColors.DIM);
        for (int k = 1; k < 8; k++) cv.line(x + w * k / 8f, y - 2f, x + w * k / 8f, y + 2f, 1f, NpColors.DIM);
        cv.line(x, y - 8f, x, y + 8f, 2.5f, NpColors.WHITE);
        cv.text(x + 3f, y + 18f, now == null ? "NOW" : now, 9f, true, NpColors.DIM, 0.4f, NpCanvas.START);
        if (events != null) for (Event e : events) {
            float ex = x + w * MathUtils.clamp(e.t, 0f, 1f);
            if (e.tick) cv.line(ex, y - 6f, ex, y + 6f, 2f, e.color);
            else NpSymbols.burnMark(cv, ex, y, 6f, e.color, 1.5f, e.filled);
            if (e.label != null) cv.text(ex, y - 10f, e.label, 10f, true, e.color, 0.3f, NpCanvas.MIDDLE);
            if (e.sub != null) cv.text(ex, y + 19f, e.sub, 9.5f, false, NpColors.DIM, 0f, NpCanvas.MIDDLE);
        }
        if (end != null) cv.text(x + w, y + 18f, end, 9f, false, NpColors.DIM, 0f, NpCanvas.END);
    }
}
