package com.ringsofiron.uihud.variants.vfd.task;

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
 * A list tab in sections: each row a line on the grid, a section's head a legend with a ghost rule
 * (which the focus passes over), a note dim, and beside the list what there is to say about the row in
 * focus (an offer's terms, why it is locked, a member's record), the first line bright and the rest
 * dim. The tab builds the rows ({@link #build}); the focus stays on the same row across a refresh, by
 * its kind and its index.
 */
abstract class SectionTab extends ListTab {
    /** Rows that can't take the focus; a tab's own kinds of rows start at {@link #ITEM}. */
    static final int HEAD = 0, NOTE = 1, ITEM = 2;
    /** The most things said about the row in focus. */
    static final int TOLD = 10;
    static final float LINE = Glass.RH * 1.25f;

    final Glass.Cols cols = new Glass.Cols();
    final Array<String> text = new Array<>();
    final IntArray kind = new IntArray(), ref = new IntArray();
    /** What is said about the row in focus, unwrapped, and how much; the lines it wraps to as painted. */
    final String[] told = new String[TOLD];
    int tells;
    private final String[] lines = new String[24];
    /** The list's width in cells. */
    int cells = 30;

    SectionTab(String name, ClassicWindow window, String cap) {
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
    void place(Place p) {
        super.place(p);
        listX = p.lx;
        listY = p.ly;
        cells = Math.max(16, Glass.cells(p.lw()));
        listCells = cells;
        int fit = fit(p.lb);
        // A list longer than the room gives its last row to the pager
        shown = text.size > fit ? Math.max(1, fit - 1) : fit;
        pages.set(text.size, shown);
    }

    @Override
    void paint(Filament f, float time) {
        boolean lcd = at.lcd;
        float x0 = listX, x1 = at.lr;
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
                } else {
                    Slate.row(f, x0, ry, t, cells, i == pages.focus(), false);
                }
            } else {
                if (k == HEAD) Glass.head(f, x0, ry, x1, Glass.cut(t, cells));
                else if (k == NOTE) Glass.text(f, x0 + Glass.CW, ry, Glass.cut(t, cells - 1), Drive.DIM);
                else Glass.row(f, x0, ry, t, cells, i == pages.focus(), Drive.NORM);
            }
        }
        if (text.size == 0) empty(f, x0, listY);
        if (pages.pages() > 1) {
            float py = listY + shown * rowStep + 3f;
            if (lcd) Slate.pager(f, x1 - Slate.pagerWidth(pages.page(), pages.pages()), py, pages.page(), pages.pages());
            else Glass.pager(f, x1 - Glass.pagerWidth(f, pages.page(), pages.pages()), py, pages.page(), pages.pages());
        }
        paintDetail(f, at.dx, at.dy);
    }

    /** What the list says with no rows. */
    void empty(Filament f, float x, float y) {
    }

    /** The lines about the row in focus from {@code y}, the first bright and the rest dim, as many as the room takes; returns the y after them. */
    float paintDetail(Filament f, float x, float y) {
        int cellsHere = Glass.cells(at.dr - x);
        for (int i = 0; i < tells && y + Glass.RH <= at.db; i++) {
            int n = Glass.wrap(told[i], cellsHere, lines, lines.length);
            for (int j = 0; j < n && y + Glass.RH <= at.db; j++) {
                if (at.lcd) Slate.text(f, x, y, lines[j], i > 0);
                else Glass.text(f, x, y, lines[j], i == 0 ? Drive.NORM : Drive.DIM);
                y += LINE;
            }
            y += 4f;
        }
        return y;
    }
}
