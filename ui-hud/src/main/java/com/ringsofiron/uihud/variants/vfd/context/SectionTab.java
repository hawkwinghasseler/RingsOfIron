package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

/**
 * A list tab in sections on one pane, its soft keys under it: each row a line on the grid, a section's
 * head a legend with a ghost rule (which the focus passes over), a note dim, and under the list a few
 * lines more about the row in focus (an offer's terms, why it is locked). The tab builds the rows
 * ({@link #build}); the focus stays on the same row across a refresh, by its kind and its index.
 */
abstract class SectionTab extends ListTab {
    /** The list's width in the station's terminal, beside its rail; a tab across the screen has more ({@link #cells}). */
    static final int CELLS = 85;
    /** Rows that can't take the focus; a tab's own kinds of rows start at {@link #ITEM}. */
    static final int HEAD = 0, NOTE = 1, ITEM = 2;
    /** The list's top in the pane, and how many lines the detail under it may take. */
    static final float LIST = 12f;
    static final int DETAIL_LINES = 3;

    final Glass.Cols cols = new Glass.Cols();
    final Array<String> text = new Array<>();
    final IntArray kind = new IntArray(), ref = new IntArray();
    /** The detail of the row in focus, and how many of its lines there are. */
    final String[] detail = new String[DETAIL_LINES];
    int details;
    /** Whether the tab has a detail under its list at all (its room is kept). */
    boolean hasDetail;
    float paneH;
    /** The list's width in cells, from the pane's. */
    int cells = CELLS;

    SectionTab(String name, ClassicWindow window, String key) {
        super(name, window, key);
    }

    /** Adds the rows from the boards. */
    abstract void build(Boards b, HudData d);

    /** The detail lines for the row in focus, with {@link #tell}. None by default. */
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

    /** Puts {@code s} in the detail, wrapped to the pane, after what is there. */
    void tell(String s) {
        if (s == null || s.isEmpty() || details >= DETAIL_LINES) return;
        int n = Glass.wrap(s, cells - 1, wrapped, DETAIL_LINES - details);
        for (int i = 0; i < n; i++) detail[details++] = wrapped[i];
    }

    private final String[] wrapped = new String[DETAIL_LINES];

    @Override
    boolean selectable(int i) {
        return i >= 0 && i < kind.size && kind.get(i) >= ITEM;
    }

    @Override
    void refresh(Boards b, HudData d) {
        int was = pages.focus();
        int wasKind = was >= 0 && was < kind.size ? kind.get(was) : -1, wasRef = was >= 0 && was < ref.size ? ref.get(was) : -1;
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
        details = 0;
        if (pages.focus() >= 0) detailFor(pages.focus());
    }

    /** Room the detail takes under the list. */
    float detailRoom() {
        return hasDetail ? 14f + DETAIL_LINES * Glass.RH * 1.25f : 0f;
    }

    @Override
    float wantedHeight(float w) {
        return LIST + Math.max(1, text.size) * Glass.ROW + detailRoom() + 32f + Glass.KEYS;
    }

    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        paneH = h - Glass.KEYS;
        shown = Math.max(1, (int) Math.floor((paneH - LIST - detailRoom() - 32f) / Glass.ROW + 1e-3f));
        listX = x + 12f;
        listY = y + LIST;
        cells = Math.max(20, Glass.cells(w - 24f));
        listCells = cells;
        pages.set(text.size, shown);
        keys.place(x + 8f, y + paneH + Glass.KEY_GAP, w - 16f, y + paneH - 7f * Glass.P - 6f);
    }

    @Override
    void paint(Filament f, float time) {
        Glass.pane(f, x, y, w, paneH, null);
        keys.paint(f);
        float x0 = x + 12f, x1 = x + w - 12f;
        if (pages.pages() > 1) {
            float pw = Glass.pagerWidth(f, pages.page(), pages.pages());
            Glass.pager(f, x1 - pw, y + paneH - 7f * Glass.P - 30f - detailRoom(), pages.page(), pages.pages());
        }
        for (int i = pages.first(); i < pages.end(); i++) {
            float ry = rowY(i);
            int k = kind.get(i);
            if (k == HEAD) Glass.head(f, x0, ry, x1, text.get(i));
            else if (k == NOTE) Glass.text(f, x0 + Glass.CW, ry, Glass.cut(text.get(i), cells - 1), Drive.DIM);
            else Glass.row(f, x0, ry, text.get(i), cells, i == pages.focus(), Drive.NORM);
        }
        if (hasDetail) {
            float dy = y + paneH - 32f - detailRoom() + 10f;
            Glass.rule(f, x0, dy, x1 - x0);
            for (int i = 0; i < details; i++) Glass.text(f, x0 + Glass.CW, dy + 8f + i * Glass.RH * 1.25f, detail[i], i == 0 ? Drive.NORM : Drive.DIM);
        }
    }
}
