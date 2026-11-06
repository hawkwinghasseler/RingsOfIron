package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * Where a press lands on a screen painted in immediate mode: each pressable place is recorded as it
 * is painted, with whom it tells and a code saying what it is, so a press between frames finds what
 * the last frame drew. Boxes are in the stage's design units, y down, as {@code YardCanvas} gives a press.
 */
final class Hits {
    /** Who a box tells when it is pressed. */
    interface Target {
        void hit(int code);
    }

    private final FloatArray boxes = new FloatArray(128);
    private final IntArray codes = new IntArray(32);
    private final Array<Target> targets = new Array<>(false, 32);

    void clear() {
        boxes.clear();
        codes.clear();
        targets.clear();
    }

    /** A box drawn at ({@code x}, {@code y}) where {@code p} is drawing now. */
    void add(YardPaint p, float x, float y, float w, float h, Target target, int code) {
        boxes.add(x + p.originX());
        boxes.add(y + p.originY());
        boxes.add(w);
        boxes.add(h);
        codes.add(code);
        targets.add(target);
    }

    /** The newest box under the point: it was drawn last, on top. -1 for none. */
    private int at(float px, float py) {
        for (int i = codes.size - 1; i >= 0; i--) {
            float x = boxes.get(4 * i), y = boxes.get(4 * i + 1);
            if (px >= x && px <= x + boxes.get(4 * i + 2) && py >= y && py <= y + boxes.get(4 * i + 3)) return i;
        }
        return -1;
    }

    boolean any(float px, float py) {
        return at(px, py) >= 0;
    }

    /** Tells the box under the point; true when there was one. */
    boolean press(float px, float py) {
        int i = at(px, py);
        if (i < 0) return false;
        targets.get(i).hit(codes.get(i));
        return true;
    }
}
