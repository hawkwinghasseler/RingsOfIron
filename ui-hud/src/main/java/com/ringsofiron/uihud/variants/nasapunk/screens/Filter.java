package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * A filter over a list (the log's severity and source): a row of positions, each with its count, one
 * of them set. Left and right step it once the cursor is on it, or a click sets a position.
 */
public final class Filter {
    public final String label;
    public final Array<String> options = new Array<>();
    public final IntArray counts = new IntArray();
    public int at;

    public Filter(String label) {
        this.label = label;
    }

    public void clear() {
        options.clear();
        counts.clear();
    }

    public void add(String option, int count) {
        options.add(option);
        counts.add(count);
    }

    /** The set position's words, or "" with none. */
    public String chosen() {
        return at >= 0 && at < options.size ? options.get(at) : "";
    }

    /** Steps the set position by {@code d}, stopping at either end. */
    public void step(int d) {
        at = Math.max(0, Math.min(options.size - 1, at + d));
    }

    /** Sets the position named {@code option}, if there is one. */
    public void set(String option) {
        int i = options.indexOf(option, false);
        if (i >= 0) at = i;
    }
}
