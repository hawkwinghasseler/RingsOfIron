package com.ringsofiron.uihud.variants.vfd.task;

/**
 * Where FILAMENT × Task puts things: the zones of functional-task 5 on its 1280 x 800 grid, as the
 * mockups lay them out (documentation/ui-overhaul/vfd/src/task.js). Each zone anchors to its edge or
 * corner of the stage and is drawn on the grid from an origin there: Z1, Z4 and Z5 on the left, Z2
 * and Z8 in the middle, Z3, Z6 and Z7 on the right, and Z5, Z7, Z8 and Z9 on the bottom. On a bigger
 * stage the extra space goes to the clear zone; nothing stretches.
 */
final class Geo {
    private Geo() {
    }

    /** The grid every zone is laid out on. */
    static final float W = 1280f, H = 800f;

    /** The side columns: left at 16, right at 964, 300 wide. */
    static final float LX = 16f, RX = 964f, CW = 300f;
    /** Column text is 5 x 7 at pitch 1.5: 9 units a cell. */
    static final float P = 1.5f, CELL = 9f;

    // Z1 + Z4: the cluster, CHR over the tell-tales, on one plate; PER under it on deck
    static final float CHR_Y = 14f, CHR_H = 52f, ANN_Y = 74f, ANN_H = 80f;
    static final float PER_Y = 168f, PER_H = 64f;

    // Z2: the message centre, a red window over an amber row, 600 wide in the middle
    static final float BX = 340f, BW = 600f, WARN_Y = 12f, WARN_H = 56f, CAUT_Y = 74f, CAUT_H = 34f;
    /** A caution cell, and the gap between cells; the +n cell is what is left. */
    static final float CELL_W = 177f, CELL_GAP = 4f;
    /** The band's foot with its bezel: a screen starts below it. */
    static final float BAND_B = 114f;

    // Z3: the trip computer, OBJ with NTF under it
    static final float OBJ_Y = 14f, OBJ_ROW = 32f, NTF_ROW = 15f;

    // Z6: the target card
    static final float TGT_Y = 380f, TGT_H = 166f, TGT_ROW = 20f;

    // Z7: the weapons pod (WPN beside the scope) and the scope's own pod
    static final float POD_X = 692f, POD_STEP_X = 1080f, POD_R = 1272f, POD_Y = 612f, SCOPE_POD_Y = 570f, POD_B = 764f;
    static final float WPN_X = 700f, WPN_Y = 620f, WPN_W = 380f;
    static final float SNS_X = 1088f, SNS_Y = 578f, SNS_W = 176f, SNS_H = 152f;
    /** The scope's minus and plus keys, under it on the pod. */
    static final float ZOOM_Y = SNS_Y + SNS_H + 6f, ZOOM_S = 24f, ZOOM_OUT_X = SNS_X + SNS_W - 54f, ZOOM_IN_X = SNS_X + SNS_W - 26f;

    // Z5: the schematic
    static final float SCH_Y = 646f, SCH_H = 112f;

    // Z8: FLT, and HLM over it (at gunnery and on deck in FLT's place)
    static final float FLT_X = 420f, FLT_Y = 662f, FLT_W = 440f, FLT_H = 96f;
    static final float HLM_H = 40f, HLM_Y = 718f;
    /** At gunnery HLM sits between the schematic and the weapons pod. */
    static final float HLM_GUN_X = 340f, HLM_GUN_W = 340f;

    // Z9: the key strip along the bottom
    static final float HNT_Y = 768f, HNT_H = 24f;

    // CRS: centre top, under the band
    static final float CRS_X = 390f, CRS_W = 500f, CRS_Y = 124f;

    /** Every zone but Z9 ends above this. */
    static final float ZONE_B = 760f;

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

    /** Dot-matrix cells at pitch {@code p} between two x. */
    static int fit(float x0, float x1, float p) {
        return (int) Math.floor((x1 - x0 + p) / (6f * p));
    }

    static int fit(float x0, float x1) {
        return fit(x0, x1, P);
    }
}
