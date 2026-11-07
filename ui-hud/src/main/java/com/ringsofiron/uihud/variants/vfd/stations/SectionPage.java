package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

/**
 * A list page in sections: each row a line on the grid, a section's head a legend with a ghost rule
 * (which the focus passes over), a note dim, and a few lines more about the row in focus (an offer's
 * terms, why it is locked, a member's record). In a side terminal the list stands beside the page
 * names and the lines about the row take the glass's width under them; across the screen and on the
 * slate's LCD they follow the list. The page builds the rows ({@link #build}); the focus stays on the
 * same row across a refresh, by its kind and its index.
 */
abstract class SectionPage extends ListPage {
    /** Rows that can't take the focus; a page's own kinds of rows start at {@link #ITEM}. */
    static final int HEAD = 0, NOTE = 1, ITEM = 2;
    /** The most things said about the row in focus, and the lines they take when they follow the list. */
    static final int TOLD = 6, DETAIL_LINES = 3;
    static final float LINE = Glass.RH * 1.25f;

    final Glass.Cols cols = new Glass.Cols();
    final Array<String> text = new Array<>();
    final IntArray kind = new IntArray(), ref = new IntArray();
    /** What is said about the row in focus, unwrapped, and how much; the lines it wraps to as painted. */
    final String[] told = new String[TOLD];
    int tells;
    private final String[] lines = new String[16];
    /** Whether the page says more about the row in focus at all (its room is kept). */
    boolean hasDetail;
    /** Where the lines about the row go: their top and left, how wide in cells, how many fit. */
    float detailY, detailX;
    int detailCells = 40, detailLines;
    /** The list's width in cells. */
    int cells = 30;
    /** Whether the lines about the row stand under the page names, across the glass. */
    private boolean under;

    SectionPage(String name, ClassicWindow window, String cap) {
        super(name, window, cap);
    }

    /** Adds the rows from the boards. */
    abstract void build(Boards b, HudData d);

    /** What to say about the row in focus, with {@link #tell}. Nothing by default. */
    void detailFor(int focus) {
    }

    void add(int k, int r, String t) {
        kind.add(k);
        ref.add(r);
        text.add(t);
    }

    void head(String t) {
        add(HEAD, text.size, t);
    }

    void note(String t) {
        add(NOTE, text.size, t);
    }

    /** Says {@code s} about the row in focus, after what is said already; wrapped to the room as painted. */
    void tell(String s) {
        if (s == null || s.isEmpty() || tells >= TOLD) return;
        told[tells++] = s;
    }

    /** The kind of row {@code i}, or -1. */
    int kindAt(int i) {
        return i >= 0 && i < kind.size ? kind.get(i) : -1;
    }

    @Override
    boolean selectable(int i) {
        return i >= 0 && i < kind.size && kind.get(i) >= ITEM;
    }

    @Override
    void refresh(Boards b, HudData d) {
        int was = pages.focus();
        int wasKind = kindAt(was), wasRef = was >= 0 && was < ref.size ? ref.get(was) : -1;
        text.clear();
        kind.clear();
        ref.clear();
        build(b, d);
        pages.set(text.size, shown);
        for (int i = 0; i < kind.size; i++) {
            if (kind.get(i) == wasKind && ref.get(i) == wasRef) {
                pages.focus(i);
                break;
            }
        }
        focusMoved();
    }

    @Override
    void focusMoved() {
        super.focusMoved();
        tells = 0;
        if (pages.focus() >= 0) detailFor(pages.focus());
    }

    @Override
    void place(Page.Place p) {
        super.place(p);
        under = !p.lcd && !wide() && p.split > p.top + 4f * Glass.ROW;
        listX = p.left;
        listY = p.top;
        cells = Math.max(16, Glass.cells(p.x1 - p.left));
        listCells = cells;
        float end;
        if (under) {
            end = p.split - 12f;
            detailX = p.x0;
            detailY = p.split;
            detailCells = Glass.cells(p.x1 - p.x0);
            detailLines = Math.max(0, (int) Math.floor((p.bottom - detailY) / LINE + 1e-3f));
        } else {
            float room = hasDetail ? 14f + DETAIL_LINES * LINE : 0f;
            end = p.bottom - room;
            detailX = p.left;
            detailY = end + 14f;
            detailCells = cells;
            detailLines = hasDetail ? DETAIL_LINES : 0;
        }
        int fit = Math.max(1, (int) Math.floor((end - listY) / Glass.ROW + 1e-3f));
        // A list longer than the room gives its last row to the pager
        shown = text.size > fit ? Math.max(1, fit - 1) : fit;
        pages.set(text.size, shown);
    }

    @Override
    void paint(Filament f, float time) {
        boolean lcd = at.lcd;
        float x0 = listX, x1 = at.x1;
        for (int i = pages.first(); i < pages.end(); i++) {
            float ry = rowY(i);
            int k = kind.get(i);
            String t = text.get(i);
            if (lcd) {
                // A section's head is a legend printed on the glass, under a printed rule
                if (k == HEAD) {
                    if (i > pages.first()) Slate.rule(f, x0, ry - 3f, x1 - x0);
                    Slate.label(f, x0, ry + 10f, Glass.cut(t, cells));
                } else if (k == NOTE) {
                    Slate.text(f, x0 + Glass.CW, ry, Glass.cut(t, cells - 1), true);
                }
                else Slate.row(f, x0, ry, t, cells, i == pages.focus(), false);
            } else {
                if (k == HEAD) Glass.head(f, x0, ry, x1, Glass.cut(t, cells));
                else if (k == NOTE) Glass.text(f, x0 + Glass.CW, ry, Glass.cut(t, cells - 1), Drive.DIM);
                else Glass.row(f, x0, ry, t, cells, i == pages.focus(), Drive.NORM);
            }
        }
        if (text.size == 0) empty(f, x0, listY);
        if (pages.pages() > 1) {
            float py = listY + shown * Glass.ROW + 3f;
            if (lcd) Slate.pager(f, x1 - Slate.pagerWidth(pages.page(), pages.pages()), py, pages.page(), pages.pages());
            else Glass.pager(f, x1 - Glass.pagerWidth(f, pages.page(), pages.pages()), py, pages.page(), pages.pages());
        }
        if (under) Glass.rule(f, at.gx + 8f, at.split - 10f, at.gw - 16f);
        paintDetail(f);
    }

    /** What the list says with no rows. */
    void empty(Filament f, float x, float y) {
    }

    /** The lines about the row in focus, the first bright and the rest dim, as many as the room takes. */
    void paintDetail(Filament f) {
        if (tells == 0 || detailLines == 0) return;
        float y = detailY;
        if (!under) {
            if (at.lcd) Slate.rule(f, detailX, y - 8f, at.x1 - detailX);
            else Glass.rule(f, detailX, y - 8f, at.x1 - detailX);
        }
        int line = 0;
        for (int i = 0; i < tells && line < detailLines; i++) {
            int n = Glass.wrap(told[i], detailCells, lines, Math.min(lines.length, detailLines - line));
            for (int j = 0; j < n; j++, line++) {
                if (at.lcd) Slate.text(f, detailX, y, lines[j], i > 0);
                else Glass.text(f, detailX, y, lines[j], i == 0 ? Drive.NORM : Drive.DIM);
                y += LINE;
            }
        }
    }
}
