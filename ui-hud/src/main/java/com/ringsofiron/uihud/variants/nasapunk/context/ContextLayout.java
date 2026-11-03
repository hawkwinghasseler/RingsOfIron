package com.ringsofiron.uihud.variants.nasapunk.context;

/**
 * The context spec's zones (functional-context.md, section 5.1) in a window {@code w} by {@code h}
 * UI units, y down from the top. At 1280 x 800 they are the spec's table. Wider, the side columns
 * stay at the edges and the centre grows; taller, the bottom row stays on the bottom edge and the
 * middle row grows. A widget lives in one zone and never moves a widget in another.
 */
final class ContextLayout {
    enum Zone { STRIP, LTOP, LMID, LBOT, TOPC, CENTRE, BOTC, RTOP, RMID, RBOT }

    /** The design's size, and the side columns' width and margin, units. */
    static final float W = 1280f, H = 800f, COL = 308f, EDGE = 12f;

    private ContextLayout() {}

    /** {@code z}'s rectangle as x, y (down), width, height. */
    static float[] zone(Zone z, float w, float h) {
        float right = w - EDGE - COL, cx = w / 2f, low = h - H;
        switch (z) {
            case STRIP: return new float[] {0f, 0f, w, 26f};
            case LTOP: return new float[] {EDGE, 38f, COL, 150f};
            case LMID: return new float[] {EDGE, 194f, COL, 358f + low};
            case LBOT: return new float[] {EDGE, 558f + low, COL, 230f};
            case TOPC: return new float[] {cx - 250f, 38f, 500f, 174f};
            case CENTRE: return new float[] {360f, 220f, w - 720f, 370f + low};
            case BOTC: return new float[] {cx - 310f, 598f + low, 620f, 190f};
            case RTOP: return new float[] {right, 38f, COL, 150f};
            case RMID: return new float[] {right, 194f, COL, 358f + low};
            default: return new float[] {right, 558f + low, COL, 230f};
        }
    }

    /** Whether two rectangles (x, y, w, h) share any area. */
    static boolean overlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }
}
