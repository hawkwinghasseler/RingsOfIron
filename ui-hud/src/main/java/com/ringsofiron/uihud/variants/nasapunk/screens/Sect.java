package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;

/**
 * A part of a page: its title and a dim line at its right, a table of rows under its columns' heads,
 * what shows when it has no rows, lines after the table, dim notes at its foot, and its own orders.
 */
public final class Sect {
    public String title = "", sub = "", empty = "";
    public final Array<Col> cols = new Array<>();
    public final Array<Row> rows = new Array<>();
    public final Array<Line> lines = new Array<>();
    public final Array<String> notes = new Array<>();
    /** The part's own orders (FILL TANKS by the tanks), in their order. */
    public final Array<Act> acts = new Array<>();
    /** The room each column's words take, measured by {@link Tables} the first time it has to squeeze them. */
    float[] need;
    /** Which of its columns the table last drew with their words whole, a bit each: none left out by a narrow list, none cut. */
    long whole;

    public Sect(String title) {
        this.title = title == null ? "" : title;
    }

    public Sect sub(String sub) {
        this.sub = sub == null ? "" : sub;
        return this;
    }

    public Sect empty(String empty) {
        this.empty = empty == null ? "" : empty;
        return this;
    }

    public Sect col(Col c) {
        cols.add(c);
        return this;
    }

    public Row row(String id) {
        Row r = new Row(id);
        rows.add(r);
        return r;
    }

    public Act act(String label, boolean live, String why, Runnable run) {
        Act a = new Act().set(label, live, why, run);
        a.id = title + "/" + a.label;
        acts.add(a);
        return a;
    }

    public Line line(Line l) {
        lines.add(l);
        return l;
    }

    /**
     * Whether the table last drew column {@code c} with every row's words whole, so a panel of the
     * cursor's row's terms needn't say them again. False until it has been drawn.
     */
    public boolean drawnWhole(int c) {
        return c >= 0 && c < 64 && (whole >>> c & 1L) != 0L;
    }

    public Sect note(String note) {
        if (note != null && !note.isEmpty() && !notes.contains(note, false)) notes.add(note);
        return this;
    }
}
