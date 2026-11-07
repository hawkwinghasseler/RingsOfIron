package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;

/**
 * Draws a page's parts on glass, as the mockups set a table: the part's title with a dim line at its
 * right and its own orders as lit buttons, the columns' heads dim over a rule, a row each with the
 * cyan cursor round the one it is on, the rows' orders as lit buttons at their right (barred ones
 * striped, with each reason once under the table), the lines and notes after it, and a scroll bar
 * when the rows run past the part's foot. Lays down the clicks' boxes as it draws.
 */
public final class Tables {
    /** How a combination sets its tables; every number is UI units. */
    public static final class Style {
        public float pad = 14f;
        public boolean title = true;
        public float titleH = 32f, titleSize = 15f;
        public float headH = 24f;
        public float rowH = 26f, subH = 15f, dividerH = 24f;
        public float lineH = 22f, noteH = 18f, gap = 14f;
        public float nameSize = 13f, cellSize = 13f;
        /** The rows' orders as buttons at their right; else the combination shows them elsewhere. */
        public boolean acts = true;
        public float actW = 78f, actH = 22f, actGap = 6f;
        /** The part's own orders as buttons at its title's right. */
        public boolean sectActs = true;
        /** The barred orders' reasons under the table, each once. */
        public boolean reasons = true;
        public boolean rules = true;
        /** The cyan cursor round the row it is on; a list of keys has no use for one. */
        public boolean cursor = true;
        /** Where a line's value starts after its label. */
        public float pairCol = 150f;
        /** The part's title centred over it with its dim line at its left, as a display unit heads its page; at its left when the line needs the room. */
        public boolean centre;
        /**
         * A narrow list leaves out the fixed columns at its right that its words won't fit, down to
         * two, for a frame that shows every cell of the cursor's row beside or under it.
         */
        public boolean drop;
        /**
         * Long words run onto further lines rather than being cut, for a narrow list: a part's notes,
         * and a last column that takes the rest, up to {@link #wrapMax} lines, which takes the room
         * the fixed columns have past their own words.
         */
        public boolean wrap;
        public int wrapMax = 3;
        /** How far apart a wrapped cell's lines are. */
        public float wrapH = 17f;
        /** Each row's dim line under it; a frame showing the cursor's row's terms under the list may leave it to them. */
        public boolean subs = true;
    }

    /** How wide words are drawn, in the font a cell uses. */
    interface Measure {
        float width(String words);
    }

    /** A part's dim line under its title, when it won't fit beside it. */
    private static final float SUB_H = 16f;

    /** A click on a scroll bar: the part, and which way. */
    public static final class Scroll {
        public final int sect;

        Scroll(int sect) {
            this.sect = sect;
        }
    }

    /** A filter's position with nothing at it: darker than a dim one. */
    private static final Color EMPTY = NpColors.GRATICULE.cpy().lerp(NpColors.DIM, 0.45f);
    private static final float[] COLX = new float[16], COLW = new float[16];
    private static final boolean[] GROWS = new boolean[16];
    private static final float[] WIDTH = new float[16];
    /**
     * The row buttons' places in the part being drawn, set by {@link #sect}: each place as wide as the
     * longest word any row has there, rows with fewer buttons taking the places at the right; and the
     * width they take together.
     */
    private static final float[] SLOT_X = new float[8], SLOT_W = new float[8];
    /** A wrapped cell's lines as drawn. */
    private static final Array<String> WRAPPED = new Array<>();
    /** Past this a part wants more than any box can give it, so a wrapping one stops measuring. */
    private static final float WANT_MOST = 4096f;
    private static int slots;
    private static float slotsW;

    private Tables() {}

    /**
     * What a click on a box {@link #sect} laid down does on page {@code p}: an order is given, a row
     * gets the cursor, a filter's position is set or stepped, a scroll bar pages its part. False
     * for a target it doesn't know.
     */
    public static boolean click(Page p, Object target, int arg) {
        if (target instanceof Act) {
            Act a = (Act) target;
            Row r = owner(p, a);
            if (r != null) p.moveTo(r);
            p.press(a);
            return true;
        }
        if (target instanceof Row) {
            Row r = (Row) target;
            boolean again = p.isCursor(r);
            p.moveTo(r);
            // a second click on the row gives its order, as Enter would
            if (again && arg == 1 && r.filter == null) p.enter();
            return true;
        }
        if (target instanceof Filter) {
            Filter f = (Filter) target;
            if (arg >= 0) f.at = Math.max(0, Math.min(f.options.size - 1, arg));
            else f.step(arg == -1 ? -1 : 1);
            if (p.boards != null) p.refresh(p.boards);
            return true;
        }
        if (target instanceof Scroll) {
            Scroll s = (Scroll) target;
            p.scroll(s.sect, arg * Math.max(1, p.shownIn(s.sect) - 1));
            return true;
        }
        return false;
    }

    private static Row owner(Page p, Act a) {
        for (int i = 0, n = p.rowCount(); i < n; i++) if (p.row(i).acts.contains(a, true)) return p.row(i);
        return null;
    }

    // ---- parts in a box -------------------------------------------------------------------------

    private static final float[] HEIGHTS = new float[32], SORTED = new float[32], COL_Y = new float[8];
    private static final float[] USED = new float[8], SHARE = new float[32];
    private static final int[] COL_OF = new int[32];

    /**
     * Parts {@code sects} of {@code p} one under another in the box, each as tall as it wants; when
     * they want more than the box has, the tallest give way first, down to what leaves the rest
     * theirs, and the rows past that scroll.
     */
    public static void stack(NpCanvas cv, Camera cam, Page p, IntArray sects, float x, float y, float w, float h, Style st, Hits hits) {
        int n = Math.min(sects.size, HEIGHTS.length);
        float total = 0f;
        for (int i = 0; i < n; i++) total += HEIGHTS[i] = want(cv, p, sects.get(i), w, st);
        if (total > h) cap(HEIGHTS, n, h);
        float yy = y;
        for (int i = 0; i < n; i++) {
            float sh = Math.min(HEIGHTS[i], y + h - yy);
            if (sh < 24f) break;
            sect(cv, cam, p, sects.get(i), x, yy, w, sh, st, hits);
            yy += sh;
        }
    }

    /** Caps {@code hs} at the level that makes the first {@code n} sum to {@code room}: the short keep theirs, the tall share what is left. */
    static void cap(float[] hs, int n, float room) {
        System.arraycopy(hs, 0, SORTED, 0, n);
        java.util.Arrays.sort(SORTED, 0, n);
        float used = 0f;
        for (int i = 0; i < n; i++) {
            float level = (room - used) / (n - i);
            if (SORTED[i] >= level) {
                for (int j = 0; j < n; j++) hs[j] = Math.min(hs[j], Math.max(level, 0f));
                return;
            }
            used += SORTED[i];
        }
    }

    /**
     * Parts {@code sects} of {@code p} flowed into {@code columns} columns across the box, each whole
     * in the first column with room for it and else in the emptiest; a column given more than it
     * holds shares its height out as {@link #stack} does, so every part shows, its rows past its foot
     * scrolling. The columns' tops keep the parts' order.
     */
    public static void flow(NpCanvas cv, Camera cam, Page p, IntArray sects, int columns, float x, float y, float w, float h, float gap, Style st, Hits hits) {
        int cols = Math.max(1, Math.min(columns, COL_Y.length)), n = Math.min(sects.size, HEIGHTS.length);
        float cw = (w - (cols - 1) * gap) / cols;
        for (int i = 0; i < n; i++) HEIGHTS[i] = want(cv, p, sects.get(i), cw, st);
        place(HEIGHTS, n, cols, h, COL_OF);
        for (int c = 0; c < cols; c++) COL_Y[c] = y;
        for (int i = 0; i < n; i++) {
            int at = COL_OF[i];
            float sh = Math.min(HEIGHTS[i], y + h - COL_Y[at]);
            if (sh < 24f) continue;
            sect(cv, cam, p, sects.get(i), x + at * (cw + gap), COL_Y[at], cw, sh, st, hits);
            COL_Y[at] += sh;
        }
    }

    /**
     * Places {@code n} parts wanting {@code hs} in {@code cols} columns {@code h} tall: each whole in
     * the first column with room for it, else in the emptiest; then the parts of a column given more
     * than {@code h} share it, the short keeping theirs and the tall what is left. Writes each part's
     * column to {@code colOf} and the height it gets over its want in {@code hs}.
     */
    static void place(float[] hs, int n, int cols, float h, int[] colOf) {
        for (int c = 0; c < cols; c++) USED[c] = 0f;
        for (int i = 0; i < n; i++) {
            int at = -1;
            for (int c = 0; c < cols && at < 0; c++) if (USED[c] + hs[i] <= h) at = c;
            if (at < 0) {
                at = 0;
                for (int c = 1; c < cols; c++) if (USED[c] < USED[at]) at = c;
            }
            colOf[i] = at;
            USED[at] += hs[i];
        }
        for (int c = 0; c < cols; c++) {
            if (USED[c] <= h) continue;
            int m = 0;
            for (int i = 0; i < n; i++) if (colOf[i] == c) SHARE[m++] = hs[i];
            cap(SHARE, m, h);
            m = 0;
            for (int i = 0; i < n; i++) if (colOf[i] == c) hs[i] = SHARE[m++];
        }
    }

    /**
     * The page's own orders as lit buttons {@code bw} wide along a strip at the box's foot, right to
     * left, with the page's status line (or the first barred order's reason) before them.
     */
    public static void pageActs(NpCanvas cv, Camera cam, Page p, float x, float y, float w, float h, float bw, Hits hits) {
        Ui.rule(cv, x + 6f, y, w - 12f);
        float bh = 22f, by = y + (h - bh) / 2f + 1f, right = x + w - 14f;
        float bx = right - p.acts.size * (bw + 6f) + 6f;
        for (int i = 0; i < p.acts.size; i++) {
            Act a = p.acts.get(i);
            float ax = bx + i * (bw + 6f);
            Ui.korry(cv, cam, ax, by, bw, bh, a.label, a.state, a.live, false);
            hits.add(ax - 2f, by - 2f, bw + 4f, bh + 4f, a, 0);
        }
        String line = p.status;
        for (int i = 0; i < p.acts.size && line.isEmpty(); i++) {
            Act a = p.acts.get(i);
            if (!a.live && !a.why.isEmpty()) line = a.label + ": " + a.why;
        }
        if (!line.isEmpty()) Ui.lab(cv, x + 14f, y + h / 2f + 5f, Ui.fit(cv, line, bx - x - 30f, Ui.LB, true, 0.6f));
    }

    /** Whether the page has orders or a status line of its own, for {@link #pageActs}. */
    public static boolean hasPageActs(Page p) {
        return p.acts.size > 0 || !p.status.isEmpty();
    }

    // ---- a part ---------------------------------------------------------------------------------

    /** How tall part {@code s} draws at width {@code w} with every row showing. */
    public static float want(NpCanvas cv, Page p, int s, float w, Style st) {
        Sect sect = p.sects.get(s);
        float h = st.title && !sect.title.isEmpty() ? st.titleH + (subBelow(cv, sect, st.pad, titleRight(cv, sect, 0f, w, st), st) ? SUB_H : 0f) : 0f;
        if (heads(sect) && sect.rows.size > 0 && !anyFilter(sect)) h += st.headH;
        // wrapped rows take the height their words need at the columns' widths
        int nCols = st.wrap || st.drop ? layout(cv, sect, 0f, w, st) : 0;
        for (int i = 0; i < sect.rows.size; i++) {
            h += rowH(cv, sect.rows.get(i), sect, nCols, st);
            // a long log is taller than any screen well before its last line: no need to wrap it all
            if (st.wrap && h > WANT_MOST) break;
        }
        if (sect.rows.size == 0 && !sect.empty.isEmpty()) h += st.rowH;
        h += linesH(cv, sect.lines, w, st);
        h += notesH(cv, sect, w - 2f * st.pad, st);
        if (st.reasons) h += reasons(sect) * st.noteH;
        return h + 6f;
    }

    /** Draws part {@code s} of {@code p} in the box ({@code x}, {@code y}, {@code w}, {@code h}); returns the height it took. */
    public static float sect(NpCanvas cv, Camera cam, Page p, int s, float x, float y, float w, float h, Style st, Hits hits) {
        Sect sect = p.sects.get(s);
        p.area(s, x, y, w, h);
        float y0 = y, inner = w - 2f * st.pad, lx = x + st.pad;
        if (st.title && !sect.title.isEmpty()) {
            float right = titleRight(cv, sect, x, w, st);
            if (st.sectActs && sect.acts.size > 0) {
                float bx = right + 12f;
                float ax = bx, ay = y + (st.titleH - st.actH) / 2f - 1f;
                for (int i = 0; i < sect.acts.size; i++) {
                    Act a = sect.acts.get(i);
                    float bw = actW(cv, a.label, st);
                    Ui.korry(cv, cam, ax, ay, bw, st.actH, a.label, a.state, a.live, false);
                    hits.add(ax - 2f, ay - 2f, bw + 4f, st.actH + 4f, a, 0);
                    ax += bw + st.actGap;
                }
            }
            // centred, unless the sub needs more than the room left of the title: then the title goes
            // left, and the sub under it if it won't fit beside it either
            float mid = (lx + right) / 2f, tw = cv.width(sect.title, st.titleSize, true, 0.6f);
            boolean below = subBelow(cv, sect, lx, right, st);
            boolean centre = !below && st.centre && (sect.sub.isEmpty() || cv.width(sect.sub, Ui.LB, true, 0.6f) <= mid - tw / 2f - 20f - lx);
            if (centre) {
                cv.text(mid, y + 21f, sect.title, st.titleSize, true, NpColors.WHITE, 0.6f, NpCanvas.MIDDLE);
                if (!sect.sub.isEmpty()) {
                    String sub = Ui.fit(cv, sect.sub, mid - tw / 2f - 20f - lx, Ui.LB, true, 0.6f);
                    cv.text(lx, y + 21f, sub, Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                }
            } else {
                cv.text(lx, y + 21f, sect.title, st.titleSize, true, NpColors.WHITE, 0.6f, NpCanvas.START);
                if (below) {
                    cv.text(lx, y + st.titleH + 9f, Ui.fit(cv, sect.sub, inner, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                } else if (!sect.sub.isEmpty()) {
                    String sub = Ui.fit(cv, sect.sub, right - (lx + tw + 24f), Ui.LB, true, 0.6f);
                    cv.text(right, y + 21f, sub, Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
                }
            }
            y += st.titleH + (below ? SUB_H : 0f);
        }
        // what goes under the rows, so the rows know their room
        float tail = linesH(cv, sect.lines, w, st) + notesH(cv, sect, inner, st);
        int nReasons = st.reasons ? reasons(sect) : 0;
        tail += nReasons * st.noteH;

        boolean filters = anyFilter(sect);
        float gutter = anyMark(sect) ? 18f : 0f;
        int nCols = layout(cv, sect, x, w, st);
        if (heads(sect) && sect.rows.size > 0 && !filters) {
            for (int c = 0; c < nCols; c++) {
                Col col = sect.cols.get(c);
                if (col.head.isEmpty()) continue;
                String head = Ui.fit(cv, col.head, COLW[c], Ui.LB, true, 0.6f);
                if (col.end) Ui.labEnd(cv, COLX[c] + COLW[c], y + 16f, head);
                else Ui.lab(cv, COLX[c], y + 16f, head);
            }
            if (st.rules) Ui.rule(cv, lx, y + st.headH - 2f, inner);
            y += st.headH;
        }

        float room = y0 + h - tail - y;
        int first = Math.min(p.first(s), Math.max(0, sect.rows.size - 1));
        // fit as many rows as the room holds, from the first shown
        float used = 0f;
        int n = 0;
        for (int i = first; i < sect.rows.size; i++) {
            float rh = rowH(cv, sect.rows.get(i), sect, nCols, st);
            if (used + rh > room + 0.5f && n > 0) break;
            used += rh;
            n++;
        }
        p.shown(s, n);
        first = p.first(s);
        if (sect.rows.size == 0 && !sect.empty.isEmpty()) {
            Ui.lab(cv, lx, y + 17f, sect.empty);
            y += st.rowH;
        }
        float ry = y;
        for (int i = first; i < first + n && i < sect.rows.size; i++) {
            Row r = sect.rows.get(i);
            ry += row(cv, cam, p, s, r, x, ry, w, lx + gutter, nCols, gutter, st, hits);
        }
        if (n < sect.rows.size) scrollBar(cv, x + w - 7f, y, ry - y, first, n, sect.rows.size, s, hits);
        y = Math.max(ry, y);
        if (sect.rows.size > 0 && n > 0 && !filters) y += 4f;
        y += lines(cv, sect.lines, x, y, w, st);
        for (int i = 0; i < sect.notes.size; i++) {
            String note = sect.notes.get(i);
            if (!st.wrap) {
                WRAPPED.clear();
                WRAPPED.add(note);
            } else {
                breaks(note, inner, st.wrapMax, t -> Ui.labW(cv, t), WRAPPED);
            }
            for (int j = 0; j < WRAPPED.size; j++) {
                Ui.lab(cv, lx, y + 13f, Ui.fit(cv, WRAPPED.get(j), inner, Ui.LB, true, 0.6f));
                y += st.noteH;
            }
        }
        if (nReasons > 0) y += drawReasons(cv, cam, sect, lx, y, inner, st);
        return y - y0;
    }

    /** One row at ({@code x}, {@code y}); returns its height. */
    private static float row(NpCanvas cv, Camera cam, Page p, int s, Row r, float x, float y, float w, float cx, int nCols, float gutter, Style st, Hits hits) {
        float lx = x + st.pad, inner = w - 2f * st.pad;
        if (!r.divider.isEmpty()) {
            float dw = cv.text(x + st.pad, y + 16f, r.divider, Ui.LB, true, NpColors.WHITE, 0.6f, NpCanvas.START);
            Ui.rule(cv, x + st.pad + dw + 12f, y + 11f, inner - dw - 12f);
            y += st.dividerH;
        }
        Sect sect = sect(p, s);
        float extra = wrapExtra(cv, r, sect, nCols, st);
        boolean sub = st.subs && !r.sub.isEmpty();
        float rh = st.rowH + extra + (sub ? st.subH : 0f);
        boolean cur = p.isCursor(r);
        hits.add(x + 2f, y, w - 4f, rh, r, 1);
        float base = y + st.rowH / 2f + 4.6f;
        if (r.filter != null) {
            filter(cv, r.filter, lx, y, inner, st, hits);
        } else {
            if (r.mark != Row.NONE) Ui.mark(cv, lx + 6f, base, r.mark, r.hollow);
            for (int c = 0; c < nCols && c < r.cells.size; c++) {
                if (extra > 0f && c == nCols - 1) wrapped(cv, r, r.cells.get(c), COLX[c], COLW[c], base, st);
                else cell(cv, r, r.cells.get(c), sect.cols.get(c), COLX[c], COLW[c], base, st);
            }
            if (!Float.isNaN(r.bar) && nCols > 0) {
                float bx = COLX[nCols - 1] + COLW[nCols - 1] + st.gap, bw = Math.min(140f, x + w - st.pad - bx - (st.acts ? slotsW : 0f));
                if (bw > 30f) Ui.bar(cv, bx, base - 6f, bw, r.bar, r.bar < 0.25f ? NpColors.AMBER : NpColors.WHITE);
            }
            if (sub) Ui.lab(cv, cx, y + st.rowH + extra + st.subH - 6f, Ui.fit(cv, r.sub, inner - gutter - 10f, Ui.LB, true, 0.6f));
            if (st.acts && r.acts.size > 0) {
                Act enter = r.acts.size > 1 && cur ? p.picked(r) : null;
                float by = y + (st.rowH - st.actH) / 2f;
                for (int j = 0, at = slots - r.acts.size; j < r.acts.size; j++) {
                    Act a = r.acts.get(j);
                    int slot = Math.max(0, Math.min(slots - 1, at + j));
                    float ax = SLOT_X[slot], aw = SLOT_W[slot];
                    Ui.korry(cv, cam, ax, by, aw, st.actH, a.label, a.state, a.live, a == enter);
                    hits.add(ax - 2f, by - 2f, aw + 4f, st.actH + 4f, a, 0);
                }
            }
        }
        if (cur && st.cursor) Ui.cursor(cv, x + 3f, y + 1f, w - 6f, rh - 2f);
        else if (st.rules && r.filter == null) Ui.rule(cv, lx, y + rh - 1f, inner);
        return rh + (r.divider.isEmpty() ? 0f : st.dividerH);
    }

    private static Sect sect(Page p, int s) {
        return p.sects.get(s);
    }

    private static void cell(NpCanvas cv, Row r, Cell c, Col col, float x, float w, float base, Style st) {
        if (c.text.isEmpty()) return;
        if (c.tone == Cell.Tone.KEY) {
            float kw = Ui.capW(cv, c.text);
            String k = kw > w ? Ui.fit(cv, c.text, w - 10f, Ui.LB, true, 0f) : c.text;
            Ui.capKey(cv, col.end ? x + w - Ui.capW(cv, k) : x, base - 13f, k, 18f);
            return;
        }
        boolean name = c.tone == Cell.Tone.NAME;
        float size = name ? st.nameSize : st.cellSize;
        boolean bold = Ui.bold(c.tone) || r.notable;
        Color col0 = r.dim ? NpColors.DIM : Ui.color(c.tone);
        float track = bold ? 0.4f : 0.2f;
        float uw = c.unit.isEmpty() ? 0f : cv.width(c.unit, Ui.LB, true, 0.3f) + 4f;
        String t = Ui.fit(cv, c.text, w - uw, size, bold, track);
        float tw = cv.width(t, size, bold, track);
        float tx = col.end ? x + w - uw - tw : x;
        cv.text(tx, base, t, size, bold, col0, track, NpCanvas.START);
        if (uw > 0f) cv.text(tx + tw + 4f, base, c.unit, Ui.LB, true, NpColors.DIM, 0.3f, NpCanvas.START);
    }

    /** A filter's row: its name, the step keys, and its positions with their counts. */
    private static void filter(NpCanvas cv, Filter f, float x, float y, float w, Style st, Hits hits) {
        float base = y + st.rowH / 2f + 4.6f;
        Ui.lab(cv, x, base, f.label);
        float kx = x + 112f, ky = y + (st.rowH - 20f) / 2f;
        Ui.stepKey(cv, kx, ky, 22f, 20f, false);
        hits.add(kx, ky, 22f, 20f, f, -1);
        Ui.stepKey(cv, kx + 26f, ky, 22f, 20f, true);
        hits.add(kx + 26f, ky, 22f, 20f, f, -2);
        float px = kx + 70f;
        // every position at 13 and 26 apart where they fit, else smaller and closer
        float size = Ui.B, gap = 26f, all = 0f;
        for (int i = 0; i < f.options.size; i++) all += cv.width(f.options.get(i) + "  " + f.counts.get(i), size, true, 0.5f) + gap;
        if (px + all - gap > x + w) {
            size = Ui.LB;
            gap = 16f;
        }
        for (int i = 0; i < f.options.size; i++) {
            String t = f.options.get(i) + "  " + f.counts.get(i);
            float tw = cv.width(t, size, true, 0.5f);
            if (px + tw > x + w) break;
            boolean set = i == f.at, none = f.counts.get(i) == 0;
            Color c = set ? NpColors.WHITE : none ? EMPTY : NpColors.DIM;
            cv.text(px, base, t, size, true, c, 0.5f, NpCanvas.START);
            if (set) cv.fill(px, base + 4f, tw, 1.5f, NpColors.WHITE);
            hits.add(px - 4f, y + 2f, tw + 8f, st.rowH - 4f, f, i);
            px += tw + gap;
        }
    }

    private static void scrollBar(NpCanvas cv, float x, float y, float h, int first, int shown, int total, int s, Hits hits) {
        if (h <= 8f || total <= 0) return;
        cv.fill(x, y, 3f, h, NpColors.TRACK);
        float th = Math.max(12f, h * shown / total), ty = y + (h - th) * (total == shown ? 0f : (float) first / (total - shown));
        cv.fill(x, ty, 3f, th, NpColors.DIM);
        Scroll up = new Scroll(s);
        hits.add(x - 6f, y, 15f, ty - y, up, -1);
        hits.add(x - 6f, ty + th, 15f, y + h - ty - th, up, 1);
    }

    // ---- lines ----------------------------------------------------------------------------------

    /** How tall {@code lines} draw at width {@code w}. */
    public static float linesH(NpCanvas cv, Array<Line> lines, float w, Style st) {
        float h = 0f;
        for (int i = 0; i < lines.size; i++) h += lineH(lines.get(i), st);
        return h;
    }

    private static float lineH(Line l, Style st) {
        return (l.kind == Line.Kind.HEAD ? st.lineH + 4f : st.lineH) + (Float.isNaN(l.bar) ? 0f : 8f);
    }

    /** Draws {@code lines} from ({@code x}, {@code y}) across {@code w}; returns the height they took. */
    public static float lines(NpCanvas cv, Array<Line> lines, float x, float y, float w, Style st) {
        float y0 = y, lx = x + st.pad, inner = w - 2f * st.pad;
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            float base = y + 15f;
            float noteW = l.note.isEmpty() ? 0f : Ui.labW(cv, l.note) + 16f;
            switch (l.kind) {
                case HEAD:
                    base += 4f;
                    Ui.lab(cv, lx, base, l.label);
                    Ui.rule(cv, lx + Ui.labW(cv, l.label) + 12f, base - 4f, inner - Ui.labW(cv, l.label) - 12f);
                    break;
                case TEXT: {
                    boolean bold = l.tone != Cell.Tone.BODY;
                    String t = Ui.fit(cv, l.value, inner - noteW, Ui.B, bold, 0.3f);
                    cv.text(lx, base, t, Ui.B, bold, l.tone == Cell.Tone.LABEL ? NpColors.DIM : Ui.color(l.tone), 0.3f, NpCanvas.START);
                    break;
                }
                default: {
                    String lab = Ui.fit(cv, l.label, st.pairCol - 10f, Ui.LB, true, 0.6f);
                    Ui.lab(cv, lx, base, lab);
                    float vx = lx + Math.max(st.pairCol, Ui.labW(cv, lab) + 12f);
                    String v = Ui.fit(cv, l.value, lx + inner - noteW - vx, Ui.B, true, 0.3f);
                    float vw = cv.text(vx, base, v, Ui.B, true, Ui.color(l.tone), 0.3f, NpCanvas.START);
                    if (!l.unit.isEmpty()) cv.text(vx + vw + 4f, base, l.unit, Ui.LB, true, NpColors.DIM, 0.3f, NpCanvas.START);
                    break;
                }
            }
            if (noteW > 0f) Ui.labEnd(cv, lx + inner, base, l.note);
            if (!Float.isNaN(l.bar)) Ui.bar(cv, lx + (l.kind == Line.Kind.PAIR ? st.pairCol : 0f), base + 6f,
                    Math.min(300f, inner - (l.kind == Line.Kind.PAIR ? st.pairCol : 0f) - noteW), l.bar, NpColors.CYAN);
            y += lineH(l, st);
        }
        return y - y0;
    }

    // ---- reasons --------------------------------------------------------------------------------

    private static final Array<String> WHYS = new Array<>();
    private static final Array<String> WHO = new Array<>();

    /** Counts the distinct reasons a part's barred orders give, filling {@link #WHYS} and the orders each bars. */
    private static int reasons(Sect sect) {
        WHYS.clear();
        WHO.clear();
        for (int i = 0; i < sect.rows.size; i++) {
            Array<Act> acts = sect.rows.get(i).acts;
            for (int j = 0; j < acts.size; j++) {
                Act a = acts.get(j);
                if (a.live || a.why.isEmpty() || a.state == Act.State.ON) continue;
                int at = WHYS.indexOf(a.why, false);
                if (at < 0) {
                    WHYS.add(a.why);
                    WHO.add(a.label);
                } else if (!(", " + WHO.get(at) + ",").contains(", " + a.label + ",")) {
                    WHO.set(at, WHO.get(at) + ", " + a.label);
                }
            }
        }
        for (int j = 0; j < sect.acts.size; j++) {
            Act a = sect.acts.get(j);
            if (a.live || a.why.isEmpty() || a.state == Act.State.ON) continue;
            int at = WHYS.indexOf(a.why, false);
            if (at < 0) {
                WHYS.add(a.why);
                WHO.add(a.label);
            } else if (!(", " + WHO.get(at) + ",").contains(", " + a.label + ",")) {
                WHO.set(at, WHO.get(at) + ", " + a.label);
            }
        }
        return Math.min(WHYS.size, 4);
    }

    private static float drawReasons(NpCanvas cv, Camera cam, Sect sect, float x, float y, float w, Style st) {
        int n = reasons(sect);
        for (int i = 0; i < n; i++) {
            float base = y + 13f;
            Ui.barberKey(cv, cam, x, base);
            Ui.lab(cv, x + 18f, base, Ui.fit(cv, WHO.get(i) + ": " + WHYS.get(i), w - 18f, Ui.LB, true, 0.6f));
            y += st.noteH;
        }
        return n * st.noteH;
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** The height of a part's notes across {@code inner}: a line each, or as many as each runs onto when the style wraps. */
    private static float notesH(NpCanvas cv, Sect sect, float inner, Style st) {
        if (!st.wrap) return sect.notes.size * st.noteH;
        int lines = 0;
        for (int i = 0; i < sect.notes.size; i++) lines += Math.max(1, breaks(sect.notes.get(i), inner, st.wrapMax, t -> Ui.labW(cv, t), null));
        return lines * st.noteH;
    }

    /** A row's height: its line, its wrapped cell's further lines, its dim line and the divider over it. */
    private static float rowH(NpCanvas cv, Row r, Sect s, int nCols, Style st) {
        return st.rowH + wrapExtra(cv, r, s, nCols, st) + (st.subs && !r.sub.isEmpty() ? st.subH : 0f)
                + (r.divider.isEmpty() ? 0f : st.dividerH);
    }

    /**
     * Sets out part {@code sect}'s columns in a box at {@code x} {@code w} wide, as its rows draw them:
     * after its mark's gutter, before its rows' buttons. Returns how many show.
     */
    private static int layout(NpCanvas cv, Sect sect, float x, float w, Style st) {
        float inner = w - 2f * st.pad, lx = x + st.pad;
        float actsW = st.acts ? slots(cv, sect, x + w - st.pad, st) : 0f;
        float gutter = anyMark(sect) ? 18f : 0f;
        return columns(cv, sect, lx + gutter, inner - gutter - actsW - (actsW > 0f ? 8f : 0f), st);
    }

    /** The height a row's wrapped last cell adds past its first line, at the columns last set out; 0 if it fits or doesn't wrap. */
    private static float wrapExtra(NpCanvas cv, Row r, Sect s, int nCols, Style st) {
        if (!st.wrap || nCols == 0 || r.filter != null || nCols > r.cells.size) return 0f;
        int c = nCols - 1;
        Cell cell = r.cells.get(c);
        if (s.cols.get(c).width > 0f || cell.text.isEmpty() || cell.tone == Cell.Tone.KEY || !cell.unit.isEmpty()) return 0f;
        boolean bold = Ui.bold(cell.tone) || r.notable;
        float size = cell.tone == Cell.Tone.NAME ? st.nameSize : st.cellSize, track = bold ? 0.4f : 0.2f;
        if (cv.width(cell.text, size, bold, track) <= COLW[c]) return 0f;
        int lines = breaks(cell.text, COLW[c], st.wrapMax, t -> cv.width(t, size, bold, track), null);
        return Math.max(0, lines - 1) * st.wrapH;
    }

    /** A cell's words over as many lines as {@link #breaks} gives them, the last cut if they run on past it. */
    private static void wrapped(NpCanvas cv, Row r, Cell c, float x, float w, float base, Style st) {
        boolean bold = Ui.bold(c.tone) || r.notable;
        float size = c.tone == Cell.Tone.NAME ? st.nameSize : st.cellSize, track = bold ? 0.4f : 0.2f;
        Color ink = r.dim ? NpColors.DIM : Ui.color(c.tone);
        breaks(c.text, w, st.wrapMax, t -> cv.width(t, size, bold, track), WRAPPED);
        for (int i = 0; i < WRAPPED.size; i++)
            cv.text(x, base + i * st.wrapH, Ui.fit(cv, WRAPPED.get(i), w, size, bold, track), size, bold, ink, track, NpCanvas.START);
    }

    /**
     * Breaks {@code words} at their spaces into lines no wider than {@code w}, at most {@code max} of
     * them, into {@code out} if it is given: the last line takes all that is left, to be cut where it
     * is drawn, and a word wider than a line stands on its own. Returns how many lines.
     */
    static int breaks(String words, float w, int max, Measure m, Array<String> out) {
        if (out != null) out.clear();
        int n = words.length(), start = 0, lines = 0;
        while (start < n && words.charAt(start) == ' ') start++;
        while (start < n && lines < max) {
            if (lines == max - 1 || m.width(words.substring(start)) <= w) {
                if (out != null) out.add(words.substring(start));
                return lines + 1;
            }
            // the most words from start that fit, or the first alone if even it won't
            int end = -1;
            for (int sp = words.indexOf(' ', start); sp >= 0; sp = words.indexOf(' ', sp + 1)) {
                if (m.width(words.substring(start, sp)) > w) break;
                end = sp;
            }
            if (end < 0) {
                end = words.indexOf(' ', start);
                if (end < 0) end = n;
            }
            if (out != null) out.add(words.substring(start, end));
            lines++;
            start = end;
            while (start < n && words.charAt(start) == ' ') start++;
        }
        return lines;
    }

    /** A button's width for {@code label}: the style's, or wider to fit the word with the button's margin, up to 150. */
    private static float actW(NpCanvas cv, String label, Style st) {
        return Math.min(150f, Math.max(st.actW, cv.width(label, Ui.LB, true, 0.4f) + 10f));
    }

    /** Where part {@code sect}'s title has room to, in a box at {@code x} {@code w} wide: its orders' buttons, if it shows them, take the right. */
    private static float titleRight(NpCanvas cv, Sect sect, float x, float w, Style st) {
        float right = x + w - st.pad;
        if (!st.sectActs || sect.acts.size == 0) return right;
        float bx = right + st.actGap;
        for (int i = 0; i < sect.acts.size; i++) bx -= actW(cv, sect.acts.get(i).label, st) + st.actGap;
        return bx - 12f;
    }

    /** Whether part {@code sect}'s line goes under its title, fitting beside it neither centred nor at the left, between {@code lx} and {@code right}. */
    private static boolean subBelow(NpCanvas cv, Sect sect, float lx, float right, Style st) {
        if (sect.sub.isEmpty()) return false;
        float tw = cv.width(sect.title, st.titleSize, true, 0.6f), sw = cv.width(sect.sub, Ui.LB, true, 0.6f);
        if (st.centre && sw <= (lx + right) / 2f - tw / 2f - 20f - lx) return false;
        return sw > right - (lx + tw + 24f);
    }

    /**
     * Sets out the part's row buttons' places against {@code right} ({@link #SLOT_X}): as many as the
     * row with the most has, each as wide as the longest word a row has there, rows with fewer taking
     * the places at the right. Returns the width they take with their gaps.
     */
    private static float slots(NpCanvas cv, Sect s, float right, Style st) {
        int m = Math.min(maxActs(s), SLOT_W.length);
        for (int i = 0; i < m; i++) SLOT_W[i] = st.actW;
        for (int i = 0; i < s.rows.size; i++) {
            Row r = s.rows.get(i);
            for (int j = 0, at = m - r.acts.size; j < r.acts.size; j++) {
                if (at + j < 0) continue;
                SLOT_W[at + j] = Math.max(SLOT_W[at + j], actW(cv, r.acts.get(j).label, st));
            }
        }
        float x = right, total = 0f;
        for (int i = m - 1; i >= 0; i--) {
            x -= SLOT_W[i];
            SLOT_X[i] = x;
            x -= st.actGap;
            total += SLOT_W[i] + st.actGap;
        }
        slots = m;
        slotsW = total;
        return total;
    }

    private static int maxActs(Sect s) {
        int n = 0;
        for (int i = 0; i < s.rows.size; i++) n = Math.max(n, s.rows.get(i).acts.size);
        return n;
    }

    private static boolean anyMark(Sect s) {
        for (int i = 0; i < s.rows.size; i++) if (s.rows.get(i).mark != Row.NONE) return true;
        return false;
    }

    /** Whether any of the part's columns has a head to print. */
    private static boolean heads(Sect s) {
        for (int i = 0; i < s.cols.size; i++) if (!s.cols.get(i).head.isEmpty()) return true;
        return false;
    }

    private static boolean anyFilter(Sect s) {
        for (int i = 0; i < s.rows.size; i++) if (s.rows.get(i).filter != null) return true;
        return false;
    }

    /** Places the columns from {@code x} across {@code w}: fixed widths, the rest to a 0-width one, all squeezed to fit. */
    /**
     * Lays the part's columns out from {@code x} across {@code w}: each at its own width, a 0-width one
     * taking what is left (60 at least), and a column of set width too narrow for its words (its head
     * and its widest cell) widened to them. Too wide for the room then, the columns first give up what
     * their words don't use, then all shrink alike, to 55 per cent at most.
     */
    private static int columns(NpCanvas cv, Sect s, float x, float w, Style st) {
        int n = Math.min(s.cols.size, COLX.length);
        if (n == 0) return 0;
        if (s.need == null) s.need = needs(cv, s, n, st);
        if (st.drop) {
            for (int c = 0; c < n; c++) WIDTH[c] = s.cols.get(c).width;
            n = kept(WIDTH, s.need, n, w, st.gap);
        }
        float fixed = 0f, gap = st.gap;
        int rest = 0;
        for (int c = 0; c < n; c++) {
            float cw = s.cols.get(c).width;
            if (cw <= 0f) rest++;
            else fixed += cw;
            GROWS[c] = cw > 0f;
        }
        float gaps = gap * (n - 1);
        float restW = rest == 0 ? 0f : Math.max(60f, (w - fixed - gaps) / rest);
        for (int c = 0; c < n; c++) {
            float cw = s.cols.get(c).width;
            COLW[c] = cw <= 0f ? restW : cw;
        }
        fit(COLW, s.need, GROWS, n, w - gaps);
        if (st.wrap) give(COLW, s.need, GROWS, n);
        float cx = x;
        for (int c = 0; c < n; c++) {
            COLX[c] = cx;
            cx += COLW[c] + gap;
        }
        // the last 0-width column takes to the end
        if (rest > 0 && s.cols.get(n - 1).width <= 0f) COLW[n - 1] = Math.max(COLW[n - 1], x + w - COLX[n - 1]);
        s.whole = whole(COLW, s.need, n);
        return n;
    }

    /**
     * How many of the first {@code n} columns, {@code width} wide (0 for one that takes the rest) and
     * needing {@code need} for their words, show in {@code w}: all, less the fixed ones at the right
     * whose words won't fit, down to two. One that takes the rest counts at 60 at most, and stops the
     * dropping when it is the last.
     */
    /**
     * Gives a last column that takes the rest ({@code grows} false) the room the fixed columns before
     * it have past their words, as much as it is short of its own, each giving in proportion to what
     * it has spare: for a list that runs that column onto further lines, so it takes as few as it can.
     */
    static void give(float[] w, float[] need, boolean[] grows, int n) {
        if (n < 2 || grows[n - 1]) return;
        float shortBy = need[n - 1] - w[n - 1], slack = 0f;
        if (shortBy <= 0f) return;
        for (int c = 0; c < n - 1; c++) if (grows[c]) slack += Math.max(0f, w[c] - need[c]);
        float take = Math.min(shortBy, slack);
        if (take <= 0f) return;
        for (int c = 0; c < n - 1; c++) if (grows[c]) w[c] -= Math.max(0f, w[c] - need[c]) * take / slack;
        w[n - 1] += take;
    }

    /** Which of the first {@code n} columns, {@code w} wide, hold their {@code need} whole: a bit each, the first lowest. */
    static long whole(float[] w, float[] need, int n) {
        long bits = 0L;
        for (int c = 0; c < n && c < 64; c++) if (w[c] + 0.5f >= need[c]) bits |= 1L << c;
        return bits;
    }

    static int kept(float[] width, float[] need, int n, float w, float gap) {
        while (n > 2 && width[n - 1] > 0f) {
            float sum = gap * (n - 1);
            for (int c = 0; c < n; c++) sum += width[c] > 0f ? need[c] : Math.min(60f, need[c]);
            if (sum <= w) break;
            n--;
        }
        return n;
    }

    /**
     * Fits the first {@code n} widths to {@code room}: each that {@code grows} widened to its
     * {@code need} first, then whatever is too wide taken back ({@link #squeeze}).
     */
    static void fit(float[] w, float[] need, boolean[] grows, int n, float room) {
        float sum = 0f;
        for (int c = 0; c < n; c++) {
            if (grows[c]) w[c] = Math.max(w[c], need[c]);
            sum += w[c];
        }
        if (sum > room) squeeze(w, need, n, sum - room);
    }

    /**
     * Takes {@code over} from the first {@code n} widths: first what each has beyond its {@code need},
     * each giving in proportion to what it has spare, then from all alike, down to 55 per cent at most.
     */
    static void squeeze(float[] w, float[] need, int n, float over) {
        float slack = 0f;
        for (int c = 0; c < n; c++) slack += Math.max(0f, w[c] - need[c]);
        float take = Math.min(over, slack);
        if (take > 0f) for (int c = 0; c < n; c++) w[c] -= Math.max(0f, w[c] - need[c]) * take / slack;
        over -= take;
        if (over <= 0f) return;
        float sum = 0f;
        for (int c = 0; c < n; c++) sum += w[c];
        float k = sum > 0f ? Math.max(0.55f, (sum - over) / sum) : 1f;
        for (int c = 0; c < n; c++) w[c] *= k;
    }

    /** The room each of the part's first {@code n} columns' words take: its head, and its widest cell with the cell's unit. */
    private static float[] needs(NpCanvas cv, Sect s, int n, Style st) {
        float[] need = new float[n];
        for (int c = 0; c < n; c++) {
            String head = s.cols.get(c).head;
            need[c] = head.isEmpty() ? 0f : cv.width(head, Ui.LB, true, 0.6f);
        }
        for (int i = 0; i < s.rows.size; i++) {
            Row r = s.rows.get(i);
            if (r.filter != null) continue;
            for (int c = 0; c < n && c < r.cells.size; c++) need[c] = Math.max(need[c], cellW(cv, r, r.cells.get(c), st));
        }
        for (int c = 0; c < n; c++) need[c] += 2f;
        return need;
    }

    /** How wide a cell's words are drawn, as {@link #cell} draws them. */
    private static float cellW(NpCanvas cv, Row r, Cell c, Style st) {
        if (c.text.isEmpty()) return 0f;
        if (c.tone == Cell.Tone.KEY) return Ui.capW(cv, c.text);
        boolean bold = Ui.bold(c.tone) || r.notable;
        float size = c.tone == Cell.Tone.NAME ? st.nameSize : st.cellSize, track = bold ? 0.4f : 0.2f;
        float uw = c.unit.isEmpty() ? 0f : cv.width(c.unit, Ui.LB, true, 0.3f) + 4f;
        return cv.width(c.text, size, bold, track) + uw;
    }
}
