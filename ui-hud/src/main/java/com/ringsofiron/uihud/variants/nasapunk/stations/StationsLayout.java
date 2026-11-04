package com.ringsofiron.uihud.variants.nasapunk.stations;

/**
 * The spec's zones (functional-stations.md section 6.2) as the variation places them
 * (variations/nasapunk-stations/stations.js, {@code Z}). The top column, 720 across and centred,
 * holds the status strip, the annunciator, the warp strip and the ticker, and the berth card under
 * them. At a command seat the band stands flush on the bottom edge, as hardware does: the two MFDs
 * either side of the cluster, 16 apart, and the station board under all three; the prompt line sits
 * 8 above it. On deck the suit readout has the bottom left corner and the prompt line drops near
 * the bottom edge.
 *
 * <p>Each zone is {x, y down from the top, w, h} in UI units, for a screen {@code w} by {@code h}.
 */
final class StationsLayout {
    enum Zone { STRIP, ANNUNCIATOR, WARP, TICKER, BERTH, SUIT, PROMPT_DECK, PROMPT_SEATED, BAND, MFD_LEFT, CLUSTER, MFD_RIGHT, BOARD }

    static final float COLUMN_W = 720f;
    static final float BAND_W = 1088f;
    static final float MFD_W = 288f, MFD_H = 160f;
    static final float CLUSTER_W = 480f, CLUSTER_H = 160f;
    static final float BOARD_H = 20f;
    /** A folded MFD: its soft-key rail, at the foot of its zone. */
    static final float RAIL_H = 24f;
    static final float PROMPT_H = 24f;
    static final float SUIT_W = 200f, SUIT_H = 80f;
    static final float BERTH_H = 28f;
    /** The gap between the MFDs and the cluster. */
    private static final float GAP = 16f;
    /** The band's top above the bottom edge: the MFDs, 4 between, then the board. */
    private static final float BAND_RISE = MFD_H + 4f + BOARD_H;

    private StationsLayout() {}

    static float[] zone(Zone z, float w, float h) {
        float col = w / 2f - COLUMN_W / 2f;
        float band = w / 2f - BAND_W / 2f, top = h - BAND_RISE;
        switch (z) {
            case STRIP: return new float[] {col, 16f, COLUMN_W, 24f};
            case ANNUNCIATOR: return new float[] {col, 44f, COLUMN_W, 36f};
            case WARP: return new float[] {col, 84f, COLUMN_W, 20f};
            case TICKER: return new float[] {col, 108f, COLUMN_W, 60f};
            case BERTH: return new float[] {col, 172f, COLUMN_W, BERTH_H};
            case SUIT: return new float[] {16f, h - 96f, SUIT_W, SUIT_H};
            case PROMPT_DECK: return new float[] {col, h - 40f, COLUMN_W, PROMPT_H};
            case PROMPT_SEATED: return new float[] {col, top - 32f, COLUMN_W, PROMPT_H};
            case BAND: return new float[] {band, top, BAND_W, BAND_RISE};
            case MFD_LEFT: return new float[] {band, top, MFD_W, MFD_H};
            case CLUSTER: return new float[] {band + MFD_W + GAP, top, CLUSTER_W, CLUSTER_H};
            case MFD_RIGHT: return new float[] {band + MFD_W + GAP + CLUSTER_W + GAP, top, MFD_W, MFD_H};
            case BOARD: return new float[] {band, h - BOARD_H, BAND_W, BOARD_H};
            default: throw new IllegalArgumentException(z.name());
        }
    }

    /** Two zones share some area. */
    static boolean overlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }
}
