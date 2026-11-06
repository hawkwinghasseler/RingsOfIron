package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * An amber ledger tube on its character grid (style guide 9.5): IBM Plex Mono at 15 units, a column 9
 * units and a row 18, 8 units of margin. Rows are counted from the top, columns from the left;
 * {@link #right} ends a value at its column, so numbers line up on their units. Set it where the
 * page lays it this frame, draw {@link #under}, the rows, then {@link #over}.
 */
final class Ledger {
    static final float COL = 9f, ROW = 18f, PAD = 8f, SIZE = 15f;
    static final Color AMBER = Yard.P_AMBER, DIM = Yard.P_DIM;

    float x, y, w, h;
    int cols, rows;

    Ledger set(float x, float y, int cols, int rows) {
        this.x = x;
        this.y = y;
        this.cols = cols;
        this.rows = rows;
        w = width(cols);
        h = height(rows);
        return this;
    }

    /** Columns a tube {@code w} wide holds. */
    static int colsIn(float w) {
        return Math.max(1, (int) Math.floor((w - 2f * PAD) / COL));
    }

    /** Rows a tube {@code h} tall holds. */
    static int rowsIn(float h) {
        return Math.max(1, (int) Math.floor((h - 2f * PAD + 2f) / ROW));
    }

    static float width(int cols) {
        return cols * COL + 2f * PAD;
    }

    static float height(int rows) {
        return rows * ROW + 2f * PAD - 2f;
    }

    /** The left of column {@code c}. */
    float cx(float c) {
        return x + PAD + c * COL;
    }

    /** The baseline of row {@code r}. */
    float base(int r) {
        return y + PAD + 13.5f + r * ROW;
    }

    float rowTop(int r) {
        return base(r) - 13.5f;
    }

    void under(YardPaint p, boolean dead) {
        p.tube(x, y, w, h, 4f, 5f, dead);
    }

    void over(YardPaint p) {
        p.tubeOver(x, y, w, h);
    }

    void at(YardPaint p, float c, int r, CharSequence s, Color col) {
        p.tube(s, cx(c), base(r), SIZE, col, false);
    }

    /** Ends at the left of column {@code c}. */
    void right(YardPaint p, float c, int r, CharSequence s, Color col) {
        if (s == null || s.length() == 0) return;
        p.tube(s, cx(c) - p.width(YardFonts.Face.TUBE, SIZE, s), base(r), SIZE, col, false);
    }

    /** Text on a row in reverse video: dark on the lit bar, no bloom. */
    void rev(YardPaint p, float c, int r, CharSequence s) {
        p.text(YardFonts.Face.TUBE, SIZE, s, cx(c), base(r), Yard.CRT_BLACK);
    }

    void revRight(YardPaint p, float c, int r, CharSequence s) {
        if (s == null || s.length() == 0) return;
        p.text(YardFonts.Face.TUBE, SIZE, s, cx(c) - p.width(YardFonts.Face.TUBE, SIZE, s), base(r), Yard.CRT_BLACK);
    }

    /** A row, or the part of it from column {@code c0} to {@code c1}, in reverse video. */
    void bar(YardPaint p, int r, float c0, float c1) {
        float bx = cx(c0) - 3f, bw = (c1 - c0) * COL + 6f;
        p.glowRect(bx, rowTop(r), bw, ROW, 6f, AMBER, 0.3f);
        p.rect(bx, rowTop(r), bw, ROW, AMBER);
    }

    void bar(YardPaint p, int r) {
        bar(p, r, 0, cols);
    }

    /** The rule under row {@code r}. */
    void rule(YardPaint p, int r) {
        p.rect(x + 4f, base(r) + 4.1f, w - 8f, 0.8f, DIM, 0.75f);
    }

    /** The caution mark at a row's left: a drawn triangle, never a glyph. */
    void caution(YardPaint p, float c, int r, Color col) {
        p.triangle(cx(c) + 5f, base(r) - 5f, 5.5f, col);
    }

    /** The warning mark: a drawn diamond. */
    void warning(YardPaint p, float c, int r, Color col) {
        p.diamond(cx(c) + 5f, base(r) - 5f, 5f, col);
    }

    /**
     * The position meter in the tube's right bezel, beside rows {@code r0} to {@code r1}: the band's
     * height is the share of the list in view ({@code shown} of {@code total} from {@code first}), its
     * place the position. Nothing while the whole list is in view.
     */
    void meter(YardPaint p, int r0, int r1, int first, int shown, int total) {
        if (total <= shown || shown <= 0) return;
        float top = rowTop(r0) + 2f, mh = rowTop(r1) + ROW - 2f - top, mx = x + w + 1.5f;
        float share = shown / (float) total, bh = Math.max(12f, share * mh);
        float pos = first / (float) Math.max(1, total - shown), by = top + Math.min(1f, pos) * (mh - bh);
        p.rrect(mx - 3f, top - 3f, 12f, mh + 6f, 2.5f, Yard.hex("0B0908"));
        p.rect(mx, top, 6f, mh, Yard.hex("2E2925"));
        p.rrect(mx, by, 6f, bh, 1f, Yard.hex("E9E2CF"));
    }

    /** {@code s} cut to {@code n} characters, its end an ellipsis when it is longer. */
    static String cut(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        if (n <= 1) return "…";
        return s.substring(0, n - 1) + "…";
    }
}
