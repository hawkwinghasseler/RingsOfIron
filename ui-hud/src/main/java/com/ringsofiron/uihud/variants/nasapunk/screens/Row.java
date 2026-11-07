package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;

import java.util.function.IntConsumer;

/**
 * A row of a page's table: its cells in its columns' order, a dim line under it, its own orders,
 * what left and right step through (a refit's weapon), and the lines a terms panel shows for it.
 */
public final class Row {
    /** What the row is about, kept across refreshes so the cursor stays on it. */
    public String id = "";
    public final Array<Cell> cells = new Array<>();
    /** A dim line under the row's cells, or "". */
    public String sub = "";
    public final Array<Act> acts = new Array<>();
    /** Left (−1) and right (+1) step the row's choice; null when it has none. */
    public IntConsumer step;
    /** The row can't be taken now (a locked offer): set dim. */
    public boolean dim;
    /** A share from 0 to 1 drawn as a bar under the row, or NaN for none. */
    public float bar = Float.NaN;
    /** What a terms panel shows for the row, top down. */
    public final Array<Line> detail = new Array<>();
    /** A severity's shape before its first cell, as P2 draws them: none, a notable dot, a caution's triangle, a warning's diamond. */
    public int mark = NONE;
    /** The mark drawn hollow: the condition cleared, the missile's flight over. */
    public boolean hollow;
    /** The row is a filter's control (the log's): drawn as its positions, stepped by left and right. */
    public Filter filter;
    /** A divider drawn over the row, opening a run of rows ("DAY 3"), or "". */
    public String divider = "";
    /** The row's words are news worth the captain's notice: set bold. */
    public boolean notable;

    public static final int NONE = 0, NOTABLE = 1, CAUTION = 2, WARNING = 3;

    public Row(String id) {
        this.id = id == null ? "" : id;
    }

    public Row add(String text, String unit, Cell.Tone tone) {
        cells.add(new Cell().set(text, unit, tone));
        return this;
    }

    public Row add(String text, Cell.Tone tone) {
        return add(text, "", tone);
    }

    public Act act(String label, boolean live, String why, Runnable run) {
        Act a = new Act().set(label, live, why, run);
        a.id = id + "/" + a.label;
        acts.add(a);
        return a;
    }

    /** Cell {@code i}, or an empty one past the row's end. */
    public Cell cell(int i) {
        return i >= 0 && i < cells.size ? cells.get(i) : EMPTY;
    }

    /** What Enter gives on the row: its first order that can be given, else null. */
    public Act enter() {
        for (int i = 0; i < acts.size; i++) if (acts.get(i).live) return acts.get(i);
        return null;
    }

    private static final Cell EMPTY = new Cell();
}
