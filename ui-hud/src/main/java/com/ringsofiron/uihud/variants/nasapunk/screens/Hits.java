package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

/**
 * Where a screen's clicks go, laid down as it draws: boxes in its local units, y down, each with what
 * a click on it reaches (an order, a row, a tab) and a number for it. The last box laid down over a
 * point wins, as the last drawn is on top. A box with no target is a plate: it takes the click so it
 * reaches nothing under the screen, and does nothing.
 */
public final class Hits {
    private final FloatArray boxes = new FloatArray();
    private final Array<Object> targets = new Array<>();
    private final IntArray args = new IntArray();

    public void clear() {
        boxes.clear();
        targets.clear();
        args.clear();
    }

    public void add(float x, float y, float w, float h, Object target, int arg) {
        if (w <= 0f || h <= 0f) return;
        boxes.add(x);
        boxes.add(y);
        boxes.add(w);
        boxes.add(h);
        targets.add(target);
        args.add(arg);
    }

    /** A plate over ({@code x}, {@code y}, {@code w}, {@code h}): clicks there stop at the screen. */
    public void plate(float x, float y, float w, float h) {
        add(x, y, w, h, null, 0);
    }

    /** The box over ({@code x}, {@code y}) laid down last, or −1 for none. */
    public int at(float x, float y) {
        for (int i = targets.size - 1; i >= 0; i--) {
            float bx = boxes.get(i * 4), by = boxes.get(i * 4 + 1), bw = boxes.get(i * 4 + 2), bh = boxes.get(i * 4 + 3);
            if (x >= bx && x < bx + bw && y >= by && y < by + bh) return i;
        }
        return -1;
    }

    public Object target(int i) {
        return targets.get(i);
    }

    public int arg(int i) {
        return args.get(i);
    }

    public int size() {
        return targets.size;
    }
}
