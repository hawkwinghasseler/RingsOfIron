package com.ringsofiron.uihud.variants.vfd.context;

/**
 * Where FILAMENT × Context puts things: the zones of functional-context 5.1 on its 1280 x 800 grid,
 * as the mockups lay them out (documentation/ui-overhaul/vfd/src/context.js). On a bigger stage the
 * side columns keep to the edges and the centre grows (5.1): each group is drawn on the grid from an
 * origin that anchors it, the left column at the left, the right column at the right, the centre at
 * the middle, and the bottom rack's groups on the stage's bottom.
 */
final class Geo {
    private Geo() {
    }

    /** The grid every zone is laid out on. */
    static final float W = 1280f, H = 800f;

    /** The side columns (5.1): left at 12, right at 960, 308 wide. */
    static final float LX = 12f, RX = 960f, CW = 308f;
    /** Column text is 5 x 7 at pitch 1.5: a column's 34 characters are 34 cells of 9 (5.6). */
    static final float P = 1.5f, CELL = 9f;
    /** A slab row: legends sit on its baseline, numbers and dot matrix stand on it. */
    static final float ROW = 20f;
    /** P2 and P4 rows are 24 apart (P2: six rows in 150). */
    static final float LIST_ROW = 24f;

    /** A slab row's baseline, from the row's top. */
    static float base(float rowTop) {
        return rowTop + 14.5f;
    }

    /** Dot matrix at pitch 1.5 standing on {@code baseline}. */
    static float dmTop(float baseline) {
        return baseline - 10.5f;
    }

    // Zones, outer edge first (5.1)
    static final float TOP_Y = 38f, MID_Y = 194f, MID_B = 552f, BOT_Y = 558f;
    /** Top centre: P3 and X3, 500 wide. */
    static final float TC_X = 390f, TC_W = 500f, P3_H = 60f;

    // P1, the top rack
    static final float STRIP_Y = 4f, STRIP_H = 20f, STRIP_B = 29f;

    // The bottom rack: H4's lights on the left wing, the tower in the middle, H5 on the right wing
    static final float RACK_Y = 736f;
    /** The tower holding H1, its top, and its half width either side of the middle. */
    static final float TOWER_Y = 640f, TOWER_HALF = 216f;
    /** H3 grows the tower to the zone's top (5.1: bottom centre from 598). */
    static final float DOCK_TOWER_Y = 590f;
    /** Fire control's tower holds F2, 620 wide, with the cue above it. */
    static final float RACK_TOWER_Y = 594f, RACK_TOWER_HALF = 318f;
    /** Docked or latched the tower sinks: only the shelf with the master lamps stays proud. */
    static final float SHELF_Y = 700f, SHELF_HALF = 124f;

    /** H1 in the tower. */
    static final float H1_X = 440f, H1_Y = 688f, H1_W = 400f, H1_H = 96f;
    /** H3 in the grown tower. */
    static final float H3_X = 440f, H3_Y = 636f, H3_W = 400f, H3_H = 148f;
    /** F2 in fire control's tower, rows of 24 under a header. */
    static final float F2_X = 330f, F2_Y = 634f, F2_W = 620f;

    /** The master alert cue: two lamp keys either side of the middle. */
    static final float CUE_W = 104f, CUE_H = 30f;
    static final float CUE_Y_HELM = 648f, CUE_Y_DOCK = 598f, CUE_Y_RACK = 600f, CUE_Y_SHELF = 705f, CUE_Y_DECK = 750f;

    /** H4's lamp cells on the left wing. */
    static final float LIGHTS_X = 16f, LIGHTS_Y = 744f, LIGHTS_W = 300f, LIGHTS_H = 38f;
    /** H4's face rises above its lights; on deck it stands on D1. */
    static final float FACE_B = 736f;
    /** D1, the captain's LCD, bottom left on deck. */
    static final float D1_Y = 702f, D1_H = 86f;

    /** H5: the key on the right wing, or the pod standing out of it. */
    static final float KEY5_X = 1216f, KEY5_Y = 740f, KEY5_S = 48f;
    static final float SCOPE_CX = 1168f, SCOPE_CY = 684f, SCOPE_R = 86f;

    /** The grid's left edge for a group centred on a stage {@code width} wide. */
    static float midX(float width) {
        return Math.round((width - W) / 2f);
    }

    /** The grid's left edge for the right column. */
    static float rightX(float width) {
        return width - W;
    }

    /** The grid's top for the bottom groups, on a stage {@code height} tall. */
    static float bottomY(float height) {
        return height - H;
    }
}
