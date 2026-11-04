package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.nasapunk.stations.StationsLayout.Zone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The stations spec's zones (section 6.2) as the variation places them: the table at 1280 x 800, and on other screens. */
class StationsLayoutTest {
    /** What shows at a command seat, and what shows on deck. */
    private static final Zone[] SEATED = {Zone.STRIP, Zone.ANNUNCIATOR, Zone.WARP, Zone.TICKER, Zone.BERTH, Zone.PROMPT_SEATED, Zone.MFD_LEFT, Zone.CLUSTER, Zone.MFD_RIGHT, Zone.BOARD};
    private static final Zone[] DECK = {Zone.STRIP, Zone.ANNUNCIATOR, Zone.WARP, Zone.TICKER, Zone.BERTH, Zone.SUIT, Zone.PROMPT_DECK};

    @Test
    void theZonesAreTheVariationsAtTheDesignSize() {
        float w = 1280f, h = 800f;
        assertArrayEquals(new float[] {280f, 16f, 720f, 24f}, StationsLayout.zone(Zone.STRIP, w, h));
        assertArrayEquals(new float[] {280f, 44f, 720f, 36f}, StationsLayout.zone(Zone.ANNUNCIATOR, w, h));
        assertArrayEquals(new float[] {280f, 84f, 720f, 20f}, StationsLayout.zone(Zone.WARP, w, h));
        assertArrayEquals(new float[] {280f, 108f, 720f, 60f}, StationsLayout.zone(Zone.TICKER, w, h));
        assertArrayEquals(new float[] {16f, 704f, 200f, 80f}, StationsLayout.zone(Zone.SUIT, w, h));
        assertArrayEquals(new float[] {280f, 760f, 720f, 24f}, StationsLayout.zone(Zone.PROMPT_DECK, w, h));
        assertArrayEquals(new float[] {280f, 584f, 720f, 24f}, StationsLayout.zone(Zone.PROMPT_SEATED, w, h));
        assertArrayEquals(new float[] {96f, 616f, 288f, 160f}, StationsLayout.zone(Zone.MFD_LEFT, w, h));
        assertArrayEquals(new float[] {400f, 616f, 480f, 160f}, StationsLayout.zone(Zone.CLUSTER, w, h));
        assertArrayEquals(new float[] {896f, 616f, 288f, 160f}, StationsLayout.zone(Zone.MFD_RIGHT, w, h));
        assertArrayEquals(new float[] {96f, 780f, 1088f, 20f}, StationsLayout.zone(Zone.BOARD, w, h));
    }

    @Test
    void nothingThatShowsTogetherSharesAnyArea() {
        for (float[] size : new float[][] {{1280f, 800f}, {1280f, 720f}, {1440f, 900f}, {1920f, 1080f}}) {
            for (Zone[] set : new Zone[][] {SEATED, DECK}) {
                for (int i = 0; i < set.length; i++) {
                    for (int j = i + 1; j < set.length; j++) {
                        float[] a = StationsLayout.zone(set[i], size[0], size[1]), b = StationsLayout.zone(set[j], size[0], size[1]);
                        assertFalse(StationsLayout.overlap(a, b), set[i] + " and " + set[j] + " at " + size[0] + " x " + size[1]);
                    }
                }
            }
        }
    }

    @Test
    void theBandStandsOnTheBottomEdgeAndTheColumnStaysCentred() {
        float w = 1920f, h = 1080f;
        float[] board = StationsLayout.zone(Zone.BOARD, w, h), band = StationsLayout.zone(Zone.BAND, w, h);
        assertEquals(h, board[1] + board[3], 0.001f);
        assertEquals(h, band[1] + band[3], 0.001f);
        assertEquals(w / 2f, band[0] + band[2] / 2f, 0.001f);
        float[] cluster = StationsLayout.zone(Zone.CLUSTER, w, h);
        assertEquals(w / 2f, cluster[0] + cluster[2] / 2f, 0.001f);
        float[] ticker = StationsLayout.zone(Zone.TICKER, w, h);
        assertEquals(w / 2f, ticker[0] + ticker[2] / 2f, 0.001f);
        // the MFDs and the cluster stand on one line, 4 above the board
        assertEquals(board[1] - 4f, cluster[1] + cluster[3], 0.001f);
        assertEquals(cluster[1], StationsLayout.zone(Zone.MFD_RIGHT, w, h)[1], 0.001f);
    }
}
