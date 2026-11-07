package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntIntMap;

/**
 * The lit dots of a graphic dot panel for one frame (style-vfd.md, section 18.3): every piece drawn on
 * it lights the panel's own dots, the brightest call on a dot wins, and a label's ground keeps the dots
 * under it dark while their ghosts still show. Dots are counted from the stage's top left,
 * {@link #pitch} units apart, y down, so every frame lands on the same grid as the panel's ghosts.
 * Drawing the dots is the painter's; this only works out which are lit, and how hard.
 */
final class Plot {
    /** How hard a dot is lit: L1, L2 and L3 (style-vfd.md, section 4). */
    static final int DIM = 1, NORM = 2, FULL = 3;
    /** Indices past this are off any stage. */
    private static final int MAX = 1 << 15;

    final float pitch;
    private final IntIntMap lit = new IntIntMap(1024);
    /** Label grounds: x, y, w, h each, y down. */
    private final FloatArray grounds = new FloatArray();
    /** Where the last chain ended, its dot and how many dots it had lit along, so a path in pieces keeps its rhythm. */
    private int lastI = -1, lastJ = -1, count;

    Plot(float pitch) {
        this.pitch = pitch;
    }

    /** Starts a frame: nothing lit, no grounds. */
    void clear() {
        lit.clear();
        grounds.clear();
        lastI = lastJ = -1;
        count = 0;
    }

    /** The column of the dot nearest {@code x}, and the row nearest {@code y}. */
    int col(float x) {
        return (int) Math.floor(x / pitch);
    }

    int row(float y) {
        return (int) Math.floor(y / pitch);
    }

    /** The centre of column {@code i}, and of row {@code j}. */
    float x(int i) {
        return i * pitch + pitch / 2f;
    }

    float y(int j) {
        return j * pitch + pitch / 2f;
    }

    /** Lights dot {@code i, j} at {@code level}, unless it is lit harder already. */
    void on(int i, int j, int level) {
        if (i < 0 || j < 0 || i >= MAX || j >= MAX) return;
        int key = key(i, j);
        if (lit.get(key, 0) < level) lit.put(key, level);
    }

    /** How hard dot {@code i, j} is lit, 0 for not. */
    int level(int i, int j) {
        if (i < 0 || j < 0 || i >= MAX || j >= MAX) return 0;
        return lit.get(key(i, j), 0);
    }

    int size() {
        return lit.size;
    }

    /**
     * A chain of dots along a line: the dots it passes, every {@code every}th of them lit. A chain that
     * starts on the dot the last one ended on carries its count on, so a path given in pieces is dotted
     * evenly along its length; {@code fresh} starts the count again.
     */
    void chain(float x0, float y0, float x1, float y1, int level, int every, boolean fresh) {
        float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        int n = Math.max(1, (int) Math.ceil(len / (pitch * 0.5f)));
        int i0 = col(x0), j0 = row(y0);
        boolean carry = !fresh && Math.abs(i0 - lastI) <= 1 && Math.abs(j0 - lastJ) <= 1;
        if (!carry) {
            count = 0;
            lastI = lastJ = Integer.MIN_VALUE;
        }
        for (int q = 0; q <= n; q++) {
            float t = q / (float) n;
            int i = col(x0 + (x1 - x0) * t), j = row(y0 + (y1 - y0) * t);
            if (i == lastI && j == lastJ) continue;
            if (count % Math.max(1, every) == 0) on(i, j, level);
            count++;
            lastI = i;
            lastJ = j;
        }
    }

    /** A circle of dots round {@code cx, cy}, every {@code every}th lit. */
    void circle(float cx, float cy, float r, int level, int every) {
        if (r <= 0f) return;
        int n = Math.max(24, (int) Math.ceil(2.0 * Math.PI * r / (pitch * 0.5f)));
        float px = cx + r, py = cy;
        for (int q = 1; q <= n; q++) {
            double a = 2.0 * Math.PI * q / n;
            float x = cx + (float) (Math.cos(a) * r), y = cy + (float) (Math.sin(a) * r);
            chain(px, py, x, y, level, every, q == 1);
            px = x;
            py = y;
        }
    }

    /**
     * A body: a disc with a solid limb and an ordered dither inside. Drawn large (past 60 units) it is
     * the ground under the orbits rather than a mark, so a sparser dither with its limb at L1 lets the
     * orbits stand out over it. Only the dots between {@code left} and {@code right}, {@code top} and
     * {@code bottom} are worked out, since zoomed in a body is far bigger than the panel.
     */
    void disc(double cx, double cy, double r, float left, float top, float right, float bottom) {
        if (!(r > 0)) return;
        boolean big = r > 60;
        int i0 = Math.max(col(left), (int) Math.floor((cx - r) / pitch) - 1), i1 = Math.min(col(right), (int) Math.ceil((cx + r) / pitch) + 1);
        int j0 = Math.max(row(top), (int) Math.floor((cy - r) / pitch) - 1), j1 = Math.min(row(bottom), (int) Math.ceil((cy + r) / pitch) + 1);
        double limb = pitch * 1.05;
        for (int j = j0; j <= j1; j++) {
            double dy = y(j) - cy;
            for (int i = i0; i <= i1; i++) {
                double dx = x(i) - cx, d = Math.sqrt(dx * dx + dy * dy);
                if (d > r) continue;
                if (d > r - limb) on(i, j, big ? DIM : NORM);
                else if (big ? (i % 2 == 0 && j % 2 == 0) : ((i + j) % 2 == 0)) on(i, j, DIM);
            }
        }
    }

    /** A bitmap of '#' and '.' rows centred on the dot nearest {@code x, y}. */
    void sprite(String[] rows, float x, float y, int level) {
        int ci = col(x), cj = row(y), hh = rows.length / 2;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int hw = row.length() / 2;
            for (int c = 0; c < row.length(); c++) if (row.charAt(c) == '#') on(ci - hw + c, cj - hh + r, level);
        }
    }

    /** Keeps a box dark, a label's ground: no dot lit in it however it was lit, before or after. */
    void ground(float x, float y, float w, float h) {
        grounds.add(x);
        grounds.add(y);
        grounds.add(w);
        grounds.add(h);
    }

    /** Whether the dot centred at {@code x, y} lies on a label's ground. */
    boolean grounded(float x, float y) {
        float[] g = grounds.items;
        for (int k = 0; k + 3 < grounds.size; k += 4) {
            if (x > g[k] && x < g[k] + g[k + 2] && y > g[k + 1] && y < g[k + 1] + g[k + 3]) return true;
        }
        return false;
    }

    /** What is lit: each entry's key gives its dot ({@link #colOf}, {@link #rowOf}), its value the level. */
    IntIntMap.Entries entries() {
        return lit.entries();
    }

    static int colOf(int key) {
        return key & (MAX - 1);
    }

    static int rowOf(int key) {
        return key >>> 15;
    }

    private static int key(int i, int j) {
        return (j << 15) | i;
    }
}
