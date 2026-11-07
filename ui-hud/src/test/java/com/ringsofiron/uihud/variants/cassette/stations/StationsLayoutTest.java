package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.variants.cassette.stations.StationsFrame.Seat;
import com.ringsofiron.uihud.variants.cassette.stations.StationsLayout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Ship Stations Cassette HUD's layout (framework rule 3, Variants.md): at 1280 x 800, at the largest
 * scale a 1080p window allows (1422 x 800), at 1080p's own 1920 x 1080, at a 5:4 window and at 2048 x 1152,
 * in every presence and with every unit the visibility rules can put up, no two units overlap and all stay
 * on screen; the top column's units keep their heights whatever else is up (6.2, "slots hold still").
 */
class StationsLayoutTest {
    private static final float[][] SIZES = {{1280f, 800f}, {1422f, 800f}, {1920f, 1080f}, {1280f, 1024f}, {2048f, 1152f}};
    /** The guide card at its tallest: the longest objective, wrapped. */
    private static final float GUIDE_TALLEST = 190f;

    @Test
    void nothingOverlapsInAnyPresence() {
        int checked = 0;
        for (float[] size : SIZES) {
            for (Seat seat : Seat.values()) {
                for (int mask = 0; mask < 1 << 11; mask++) {
                    StationsLayout l = layout(size, seat, mask);
                    if (l == null) continue;
                    l.solve();
                    check(l, seat + " mask " + Integer.toBinaryString(mask) + " at " + (int) size[0] + "x" + (int) size[1]);
                    checked++;
                }
            }
        }
        assertTrue(checked > 10_000, "checked " + checked);
    }

    /** One situation; null for one the visibility rules never make (docked, so the berth card, never warps). */
    private static StationsLayout layout(float[] size, Seat seat, int mask) {
        StationsLayout l = new StationsLayout();
        l.width = size[0];
        l.height = size[1];
        l.seat = seat;
        l.strip = (mask & 1) != 0;
        l.ann = (mask & 2) != 0;
        l.warpStrip = (mask & 4) != 0;
        l.ticker = (mask & 8) != 0;
        l.map = (mask & 16) != 0;
        l.leftFolded = (mask & 32) != 0;
        l.rightFolded = (mask & 64) != 0;
        l.suit = (mask & 128) != 0;
        l.prompt = (mask & 256) != 0;
        l.berth = (mask & 512) != 0;
        l.guideH = (mask & 1024) != 0 ? GUIDE_TALLEST : 0f;
        if (l.berth && l.warpStrip) return null;
        if (seat == Seat.NONE && (l.suit || l.prompt || l.berth)) return null;
        return l;
    }

    private static void check(StationsLayout l, String where) {
        Rect[] all = {l.column, l.mfdL, l.mfdR, l.cluster, l.board, l.sight, l.suitR, l.promptR, l.guideR, l.berthR};
        String[] names = {"column", "left MFD", "right MFD", "cluster", "board", "sight", "suit", "prompt", "guide", "berth"};
        for (int i = 0; i < all.length; i++) {
            Rect r = all[i];
            if (!r.on) continue;
            if (r.x < 0f || r.y < 0f || r.right() > l.width + 0.01f || r.bottom() > l.height + 0.01f) {
                fail(names[i] + " " + r + " is off screen, " + where);
            }
            for (int j = i + 1; j < all.length; j++) {
                if (r.overlaps(all[j])) fail(names[i] + " " + r + " overlaps " + names[j] + " " + all[j] + ", " + where);
            }
        }
    }

    @Test
    void overAScreenAcrossTheScreenTheGuideCardKeepsClearOfItsTitle() {
        for (float[] size : SIZES) {
            StationsLayout l = new StationsLayout();
            l.width = size[0];
            l.height = size[1];
            l.seat = Seat.HELM;
            l.strip = l.ann = true;
            l.guideH = GUIDE_TALLEST;
            l.map = true;
            l.solve();
            String where = " at " + (int) size[0] + "x" + (int) size[1];
            assertEquals(size[0] - StationsLayout.MARGIN, l.guideR.right(), 0.01f, "the top right, less the margin" + where);
            assertEquals(StationsLayout.MARGIN, l.guideR.y, 0.01f, where);
            assertFalse(l.guideR.overlaps(l.column), "beside the column" + where);
            l.map = false;
            l.solve();
            assertEquals(StationsLayout.MARGIN, l.guideR.x, 0.01f, "back at the top left as the screen goes" + where);
        }
    }

    @Test
    void aSidePanelLeavesTheColumnTheCardsAndThePromptInTheWorldLeftOfIt() {
        int checked = 0;
        for (float[] size : SIZES) {
            for (Seat seat : Seat.values()) {
                for (int mask = 0; mask < 1 << 11; mask++) {
                    // With the map up a window goes across the screen: no side panel
                    if ((mask & 16) != 0) continue;
                    StationsLayout l = layout(size, seat, mask);
                    if (l == null) continue;
                    // The panel as the windows hang it: 480 wide, 16 in from the right, from 16 down to its bottom
                    l.panelLeft = l.width - 16f - 480f;
                    l.solve();
                    String where = seat + " mask " + Integer.toBinaryString(mask) + " at " + (int) size[0] + "x" + (int) size[1];
                    check(l, where);
                    float bottom = l.panelBottom() > 0f ? l.panelBottom() : l.height - 16f;
                    Rect panel = new Rect();
                    panel.set(l.panelLeft, 16f, 480f, bottom - 16f);
                    Rect[] all = {l.column, l.mfdL, l.mfdR, l.cluster, l.board, l.sight, l.suitR, l.promptR, l.guideR, l.berthR};
                    String[] names = {"column", "left MFD", "right MFD", "cluster", "board", "sight", "suit", "prompt", "guide", "berth"};
                    for (int i = 0; i < all.length; i++) {
                        if (all[i].on && all[i].overlaps(panel)) fail(names[i] + " " + all[i] + " is under the panel " + panel + ", " + where);
                    }
                    if (panel.h < 300f) fail("the panel is only " + panel.h + " tall, " + where);
                    checked++;
                }
            }
        }
        assertTrue(checked > 5_000, "checked " + checked);
    }

    @Test
    void theTickerHoldsStillWhateverIsAboveIt() {
        for (int mask = 0; mask < 8; mask++) {
            StationsLayout l = new StationsLayout();
            l.strip = (mask & 1) != 0;
            l.ann = (mask & 2) != 0;
            l.warpStrip = (mask & 4) != 0;
            l.ticker = true;
            l.solve();
            assertEquals(StationsLayout.TICKER_Y + StationsLayout.TICKER_H, l.column.bottom(), 0.01f, "mask " + mask);
            assertEquals(0f, l.column.y, 0.01f);
        }
    }

    @Test
    void theBandStandsFlushWithTheFootAtACommandSeat() {
        for (float[] size : SIZES) {
            for (Seat seat : new Seat[]{Seat.HELM, Seat.WEAPONS}) {
                StationsLayout l = new StationsLayout();
                l.width = size[0];
                l.height = size[1];
                l.seat = seat;
                l.leftFolded = true;
                l.solve();
                float foot = size[1] - StationsLayout.BOARD_H;
                assertEquals(foot, l.cluster.bottom(), 0.01f);
                assertEquals(foot, l.mfdL.bottom(), 0.01f, "a folded MFD stands on the band's foot too");
                assertEquals(foot, l.mfdR.bottom(), 0.01f);
                assertEquals(StationsLayout.FOLDED_H, l.mfdL.h, 0.01f);
                assertEquals(size[1], l.board.bottom(), 0.01f);
                assertEquals(StationsLayout.BAND_W + 16f, l.board.w, 0.01f);
                assertEquals(Math.round((size[0] - StationsLayout.BAND_W) / 2f), l.bandX, 0.01f, "centred");
                assertFalse(l.sight.on);
            }
        }
    }

    @Test
    void aGunHasOnlyItsSightAndItsOwnTile() {
        StationsLayout l = new StationsLayout();
        l.seat = Seat.GUN;
        l.prompt = true;
        l.solve();
        assertTrue(l.sight.on);
        assertTrue(l.board.on);
        assertEquals(StationsLayout.TILE_W + 16f, l.board.w, 0.01f);
        assertFalse(l.mfdL.on || l.mfdR.on || l.cluster.on);
        assertTrue(l.promptR.bottom() <= l.sight.y);
    }

    @Test
    void theMapHidesTheBandAndTheDeckKeepsTheSuit() {
        StationsLayout l = new StationsLayout();
        l.seat = Seat.HELM;
        l.map = true;
        l.suit = true;
        l.solve();
        assertFalse(l.mfdL.on || l.mfdR.on || l.cluster.on || l.board.on);
        assertFalse(l.suitR.on, "the suit readout is the deck's only");
        l.seat = Seat.DECK;
        l.map = false;
        l.solve();
        assertTrue(l.suitR.on);
        assertEquals(800f - StationsLayout.MARGIN, l.suitR.bottom(), 0.01f);
    }
}
