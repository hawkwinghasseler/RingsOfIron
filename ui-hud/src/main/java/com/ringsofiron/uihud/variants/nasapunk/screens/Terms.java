package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;

/**
 * The terms of a row, as a panel beside or under its list shows them for the row under the cursor:
 * the row's own lines, or for a row that has none, each of its cells after the first under its
 * column's head. Keeps the lines it makes, so a frame can ask every time it draws.
 */
public final class Terms {
    private final Array<Line> made = new Array<>();
    private final Array<Line> pool = new Array<>();

    /** The terms of {@code r}, a row of part {@code s}. */
    public Array<Line> of(Row r, Sect s) {
        if (r.detail.size > 0) return r.detail;
        pool.addAll(made);
        made.clear();
        if (s == null) return made;
        for (int c = 1; c < r.cells.size && c < s.cols.size; c++) {
            Cell cell = r.cells.get(c);
            String head = s.cols.get(c).head;
            if (cell.text.isEmpty() || head.isEmpty()) continue;
            Line l = pool.size > 0 ? pool.pop() : new Line();
            l.kind = Line.Kind.PAIR;
            l.label = head;
            l.value = cell.text;
            l.unit = cell.unit;
            l.note = "";
            l.bar = Float.NaN;
            // a name or body text reads as the terms' value
            l.tone = cell.tone == Cell.Tone.NAME || cell.tone == Cell.Tone.BODY || cell.tone == Cell.Tone.LABEL ? Cell.Tone.VALUE : cell.tone;
            made.add(l);
        }
        if (!r.sub.isEmpty()) {
            Line l = pool.size > 0 ? pool.pop() : new Line();
            l.kind = Line.Kind.TEXT;
            l.label = "";
            l.value = r.sub;
            l.unit = "";
            l.note = "";
            l.bar = Float.NaN;
            l.tone = Cell.Tone.LABEL;
            made.add(l);
        }
        return made;
    }
}
