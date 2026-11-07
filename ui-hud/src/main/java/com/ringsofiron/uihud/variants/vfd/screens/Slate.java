package com.ringsofiron.uihud.variants.vfd.screens;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Ink;
import com.ringsofiron.uihud.variants.vfd.Kit;

/**
 * The captain's slate (style-vfd.md 18.4), as the mockups' {@code slate} draws it: a graphic LCD in
 * a rubber housing, the personal kit's screen. Its text is the terminals' 5x7 grid (9 units a cell,
 * {@link Glass#CW}) in dark dots on the grey-green, each with its faint shadow; the focus a dark block
 * with the dots left clear; a severity a square, outlined for a caution and solid for a warning, since
 * the LCD has no colour; rules printed on the glass; bars of dark segments; and the rubber keys under
 * the glass, each with its action printed on the housing over it ({@link SoftKeys#placeSlate}).
 */
public final class Slate {
    public static final float P = Glass.P, CW = Glass.CW, RH = Glass.RH, ROW = Glass.ROW;
    /** The glass in its housing: in from the sides, down from the top under the brand's silk, and the room under it, with keys and without. */
    public static final float SIDE = 14f, SILK = 26f, KEYS = 52f, FOOT = 16f;

    private Slate() {
    }

    /** The glass of a housing {@code x, y, w, h} with {@code top} over it for keys and its keys under it, into {@code out} (x, y, w, h). */
    public static void glass(float x, float y, float w, float h, float top, boolean keysUnder, float[] out) {
        out[0] = x + SIDE;
        out[1] = y + SILK + top;
        out[2] = w - 2f * SIDE;
        out[3] = h - SILK - top - (keysUnder ? KEYS : FOOT);
    }

    /** Text from {@code x, y} (the top of its cells). Returns its width. */
    public static float text(Filament f, float x, float y, CharSequence s) {
        return f.lcdDm(x, y, s, f.o().pitch(P));
    }

    /** Text at half the ink. */
    public static float dim(Filament f, float x, float y, CharSequence s) {
        return f.lcdDm(x, y, s, f.o().pitch(P).dim());
    }

    public static float text(Filament f, float x, float y, CharSequence s, boolean dim) {
        return dim ? dim(f, x, y, s) : text(f, x, y, s);
    }

    /** Text right-aligned so it ends at {@code right}. */
    public static float right(Filament f, float right, float y, CharSequence s, boolean dim) {
        float w = Glass.width(s.length());
        return text(f, right - w, y, s, dim);
    }

    /** Text dark-on-clear: a tab showing, a key's cap printed in the glass. */
    public static float inverse(Filament f, float x, float y, CharSequence s) {
        return f.lcdDm(x, y, s, f.o().pitch(P).cells(s.length()).inverse(0, s.length()));
    }

    /** Larger text at pitch {@code pitch}: a title, a name. */
    public static float big(Filament f, float x, float y, CharSequence s, float pitch) {
        return f.lcdDm(x, y, s, f.o().pitch(pitch));
    }

    /** How wide {@code n} cells of text at {@code pitch} set. */
    public static float bigWidth(int n, float pitch) {
        return n * 6f * pitch - pitch;
    }

    /**
     * A row of a list {@code cells} wide from {@code x}: the cursor's cell, then {@code s}. Focus puts
     * the cursor in and darkens the rest of the row, the dots left clear.
     */
    public static void row(Filament f, float x, float y, CharSequence s, int cells, boolean focus, boolean dim) {
        if (focus) {
            f.lcdDm(x, y, "▶", f.o().pitch(P).cells(1));
            f.lcdDm(x + CW, y, s, f.o().pitch(P).cells(cells - 1).inverse(0, cells - 1));
        } else {
            Filament.Opt o = f.o().pitch(P).cells(cells - 1);
            if (dim) o.dim();
            f.lcdDm(x + CW, y, s, o);
        }
    }

    /** The severity squares in two cells from {@code x} on the row whose cells start at {@code y}: a caution, then a warning. */
    public static void pips(Filament f, float x, float y, Kit.Sev sev) {
        float py = y + 3f * P;
        f.lcdPip(x + 2f * P, py, sev == Kit.Sev.CAUTION, false);
        f.lcdPip(x + CW + 2f * P, py, sev == Kit.Sev.WARNING, true);
    }

    /** A legend printed on the glass, its baseline at {@code y}: a column's head, a row's label. Returns its width. */
    public static float label(Filament f, float x, float y, String t) {
        return f.lcdText(x, y, t, f.o().size(12f).ink(Ink.LCD_LEGEND));
    }

    /** A heading printed on the glass, larger and in full ink. */
    public static float heading(Filament f, float x, float y, String t) {
        return f.lcdText(x, y, t, f.o().size(15f));
    }

    /** A rule printed on the glass. */
    public static void rule(Filament f, float x, float y, float w) {
        f.lcdRect(x, y, w, 0.8f, 0.35f);
    }

    public static void vrule(Filament f, float x, float y, float h) {
        f.lcdRect(x, y, 0.8f, h, 0.35f);
    }

    /** A bar of {@code n} dark segments, {@code frac} of them set; in a focused row's dark block, the set ones left clear. */
    public static void bar(Filament f, float x, float y, float w, float h, int n, float frac, boolean inverse) {
        float gap = 1.2f, sw = (w - gap * (n - 1)) / n;
        int lit = Math.round(Math.max(0f, Math.min(1f, frac)) * n);
        for (int i = 0; i < n; i++) segment(f, x + i * (sw + gap), y, sw, h, i < lit, inverse);
    }

    /**
     * A bar for a figure either side of nothing (a standing, -100 to 100): {@code n} segments a side
     * of a tick in the middle, set outward from it to {@code frac} of a side, left for below nothing.
     */
    public static void signedBar(Filament f, float x, float y, float w, float h, int n, float frac) {
        float gap = 1.2f, mid = x + w / 2f, sw = (w / 2f - 1.5f - gap * (n - 1)) / n;
        int lit = Math.round(Math.min(1f, Math.abs(frac)) * n);
        f.lcdRect(mid - 0.4f, y - 1.5f, 0.8f, h + 3f, 0.6f);
        for (int i = 0; i < n; i++) {
            float off = 1.5f + i * (sw + gap);
            segment(f, mid + off, y, sw, h, frac > 0f && i < lit, false);
            segment(f, mid - off - sw, y, sw, h, frac < 0f && i < lit, false);
        }
    }

    private static void segment(Filament f, float x, float y, float w, float h, boolean set, boolean inverse) {
        if (inverse) {
            // The dots left clear in the row's dark block: the set ones bright, the rest a faint print
            f.lcdClear(x, y, w, h, set ? 1f : 0.18f);
            return;
        }
        if (set) f.lcdRect(x, y, w, h, 1f);
        else f.lcdRect(x, y, w, h, 0.08f);
    }

    /** "PG 1/3" with its arrows set where there is more, from {@code x}; returns its width. */
    public static float pager(Filament f, float x, float y, int page, int of) {
        String s = "PG " + page + "/" + of;
        float cx = x + dim(f, x, y, s) + CW;
        cx += f.lcdDm(cx, y, "▲", page > 1 ? f.o().pitch(P) : f.o().pitch(P).dim()) + CW;
        cx += f.lcdDm(cx, y, "▼", page < of ? f.o().pitch(P) : f.o().pitch(P).dim());
        return cx - x;
    }

    /** How wide {@link #pager} sets. */
    public static float pagerWidth(int page, int of) {
        return Glass.width(("PG " + page + "/" + of).length()) + CW + Glass.width(1) + CW + Glass.width(1);
    }

    /** A choice: its legend, then its value in {@code cells} between ◀ and ▶, darkened while it has the focus. Returns the x after it. */
    public static float choice(Filament f, float x, float y, String label, String value, int cells, boolean focus) {
        label(f, x, y + 9f, label);
        float vx = x + 74f;
        dim(f, vx, y, "◀");
        Filament.Opt o = f.o().pitch(P).cells(cells);
        if (focus) o.inverse(0, cells);
        f.lcdDm(vx + 2f * CW, y, value, o);
        dim(f, vx + (cells + 3) * CW, y, "▶");
        return vx + (cells + 4) * CW;
    }

    /** Keys printed in the glass, each cap dark with its letters clear, and their word: "L OR ESC CLOSE". Returns the x after it. */
    public static float capWords(Filament f, float x, float y, String[] words, boolean[] caps) {
        float cx = x;
        for (int i = 0; i < words.length; i++) {
            if (words[i] == null || words[i].isEmpty()) continue;
            if (caps[i]) inverse(f, cx, y, words[i]);
            else text(f, cx, y, words[i]);
            cx += (words[i].length() + 1) * CW;
        }
        return cx;
    }

    /** How wide {@link #capWords} sets. */
    public static float capWordsWidth(String[] words) {
        int n = 0;
        for (String w : words) if (w != null && !w.isEmpty()) n += w.length() + 1;
        return Math.max(0, n * CW - CW - P);
    }
}
