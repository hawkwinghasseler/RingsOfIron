package com.ringsofiron.uihud.variants.vfd.stations;

/**
 * Where FILAMENT × Stations puts things, on the spec's 1280 x 800 grid (functional-stations 6.2) as
 * the mockups lay it out (documentation/ui-overhaul/vfd/src/stations.js). The top column is anchored
 * at the top centre of the stage and the band at the bottom centre, so a bigger window gives the
 * world the room; {@link #topX} and {@link #bandY} move the grid there.
 */
final class Geo {
    private Geo() {
    }

    /** The grid every zone is laid out on. */
    static final float W = 1280f, H = 800f;

    // The seated band: one console rack, MFDs either side of the cluster, the board under them
    static final float BAND_X = 96f, BAND_Y = 616f, BAND_W = 1088f, MFD_W = 288f, CLUSTER_W = 480f, CLUSTER_H = 160f, BOARD_Y = 780f;
    static final float LMFD = BAND_X, CL = BAND_X + MFD_W + 16f, RMFD = CL + CLUSTER_W + 16f;
    /** An MFD's glass ends here; its bottom row holds the soft keys' labels. */
    static final float GLASS_B = 740f;
    /** A folded MFD keeps only that row: the rail. */
    static final float RAIL_Y = 722f;
    /** The keys under it. */
    static final float KEY_Y = 746f, KEY_H = 24f;
    /** A page key's width: three of them and the pin | fold key make the rail. */
    static final float SLOT = 80f;
    /** The page area of an open MFD. */
    static final float PAGE_Y = BAND_Y + 24f, PAGE_H = 78f;

    // The top column
    static final float TOP_X = 280f, TOP_W = 720f, STRIP_Y = 16f, ANN_Y = 44f, WARP_Y = 84f, TICKER_Y = 108f;

    // The deck: the suit readout bottom left, the prompt line bottom centre
    static final float SUIT_X = 16f, SUIT_Y = 704f, SUIT_W = 200f, SUIT_H = 80f;
    /** The prompt line's top: on deck, and seated, where it clears the band's rack (its top 10 above the glass) by 3. */
    static final float PROMPT_DECK_Y = 760f, PROMPT_SEATED_Y = 576f;

    // The panels (10.1): one at a time, 480 wide and 16 in from the right; seated it stops 8 above the
    // band. The Yard and the Shipyard take the screen's width under the top column, the Plot its height.
    static final float PANEL_W = 480f, PANEL_MARGIN = 16f, PANEL_GAP = 8f;
    /** A wide panel's top: under the ticker, so the top column stands over it as it stands over the world. */
    static final float WIDE_Y = TICKER_Y + 60f + 8f;

    /** A side panel's left on a stage {@code width} wide. */
    static float panelX(float width) {
        return width - PANEL_MARGIN - PANEL_W;
    }

    /** The top column's offset (as {@link #topX}) that centres it in the world left of a panel at {@code panelX}. */
    static float columnLeftOf(float panelX) {
        return Math.round((panelX - TOP_W) / 2f) - TOP_X;
    }

    /** Where the grid's left edge sits on a stage {@code width} wide: the grid is centred. */
    static float topX(float width) {
        return Math.round((width - W) / 2f);
    }

    /** Where the grid's top sits for the band, on a stage {@code height} tall: the band sits on the bottom. */
    static float bandY(float height) {
        return height - H;
    }
}
