package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.uihud.FocusNav;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of FILAMENT's menus and map plot that need no window: the focus tint, fields, a row's scroll, the lit dots. */
class FilamentScreensTest {
    private static final float P = 3f;

    @Test
    void thePainterKnowsTheFocusTintTheMenusUse() throws Exception {
        // The menus tint the focused control; FILAMENT reads the tint and shows the inverse field instead
        Field f = FocusNav.class.getDeclaredField("FOCUS");
        f.setAccessible(true);
        Color tint = (Color) f.get(null);
        assertEquals(tint, SetupPaint.FOCUS);
        assertTrue(SetupPaint.isFocus(new Color(tint.r, tint.g, tint.b, 0.4f)), "at any alpha, as in a fade");
        assertFalse(SetupPaint.isFocus(Color.WHITE));
    }

    @Test
    void aFieldHasTheCellsThatFitItsWidthTheLastWithNoGapAfterIt() {
        // Six dots a cell; the last cell's gap is not needed
        assertEquals(5, SetupPaint.cells(5 * 12f - 2f, 2f));
        assertEquals(4, SetupPaint.cells(5 * 12f - 2.1f, 2f));
        assertEquals(16, SetupPaint.cells(200f, 2f));
        assertEquals(0, SetupPaint.cells(0f, 1.5f));
    }

    @Test
    void aRowTooLongScrollsACellAtATimeHoldingAtEachEnd() {
        int over = 5;
        assertEquals(0, SetupPaint.scrolled(over, 0f));
        assertEquals(0, SetupPaint.scrolled(over, 0.76f), "held three steps at the start");
        assertEquals(1, SetupPaint.scrolled(over, 1.01f));
        assertEquals(over, SetupPaint.scrolled(over, (over + 3) / 4f + 0.01f));
        assertEquals(over, SetupPaint.scrolled(over, (over + 5) / 4f + 0.01f), "held at the end");
        assertEquals(0, SetupPaint.scrolled(over, (over + 6) / 4f + 0.01f), "then round again");
        assertEquals(0, SetupPaint.scrolled(0, 10f), "a row that fits never moves");
    }

    @Test
    void theBrightestCallOnADotWins() {
        Plot plot = new Plot(P);
        plot.on(3, 4, Plot.DIM);
        plot.on(3, 4, Plot.FULL);
        plot.on(3, 4, Plot.NORM);
        assertEquals(Plot.FULL, plot.level(3, 4));
        assertEquals(1, plot.size());
        plot.on(-1, 2, Plot.FULL);
        assertEquals(1, plot.size(), "off the stage lights nothing");
    }

    @Test
    void aDotsKeyGivesBackItsColumnAndRow() {
        Plot plot = new Plot(P);
        plot.on(1234, 567, Plot.NORM);
        IntIntMap.Entry e = plot.entries().next();
        assertEquals(1234, Plot.colOf(e.key));
        assertEquals(567, Plot.rowOf(e.key));
        assertEquals(Plot.NORM, e.value);
    }

    @Test
    void aChainLightsEveryNthDotItPasses() {
        Plot plot = new Plot(P);
        plot.chain(plot.x(0), plot.y(2), plot.x(9), plot.y(2), Plot.NORM, 3, true);
        assertEquals(4, plot.size());
        for (int i : new int[] {0, 3, 6, 9}) assertEquals(Plot.NORM, plot.level(i, 2), "column " + i);
        plot.clear();
        plot.chain(plot.x(0), plot.y(2), plot.x(9), plot.y(2), Plot.FULL, 1, true);
        assertEquals(10, plot.size(), "solid");
    }

    @Test
    void aPathInPiecesKeepsOneRhythmUnlessItStartsAgain() {
        Plot plot = new Plot(P);
        plot.chain(plot.x(0), plot.y(0), plot.x(4), plot.y(0), Plot.NORM, 3, true);
        plot.chain(plot.x(4), plot.y(0), plot.x(9), plot.y(0), Plot.NORM, 3, false);
        assertEquals(4, plot.size());
        for (int i : new int[] {0, 3, 6, 9}) assertEquals(Plot.NORM, plot.level(i, 0), "column " + i);
        plot.clear();
        plot.chain(plot.x(0), plot.y(0), plot.x(4), plot.y(0), Plot.NORM, 3, true);
        plot.chain(plot.x(4), plot.y(0), plot.x(9), plot.y(0), Plot.NORM, 3, true);
        assertEquals(Plot.NORM, plot.level(4, 0), "a fresh chain lights its first dot");
        assertEquals(Plot.NORM, plot.level(7, 0));
        assertEquals(0, plot.level(6, 0));
    }

    @Test
    void aLabelsGroundKeepsTheDotsUnderItDark() {
        Plot plot = new Plot(P);
        plot.ground(30f, 30f, 30f, 12f);
        assertTrue(plot.grounded(plot.x(12), plot.y(12)));
        assertFalse(plot.grounded(plot.x(9), plot.y(12)), "the dot left of it");
        assertFalse(plot.grounded(plot.x(12), plot.y(15)), "the dot under it");
        plot.clear();
        assertFalse(plot.grounded(plot.x(12), plot.y(12)), "a frame's grounds go with it");
    }

    @Test
    void aBodyIsASolidLimbOverADitherSparserWhenItIsTheGround() {
        Plot plot = new Plot(P);
        // A small body: its limb at L2, a checker inside at L1
        float cx = plot.x(50), cy = plot.y(50);
        plot.disc(cx, cy, 30, 0f, 0f, 1000f, 1000f);
        assertEquals(Plot.NORM, plot.level(60, 50), "the limb");
        assertEquals(Plot.DIM, plot.level(50, 50));
        assertEquals(0, plot.level(51, 50));
        float small = share(plot, 50, 50, 6);
        assertTrue(small > 0.4f && small < 0.6f, "half the inside lit: " + small);
        // A body past 60 units is the ground under the orbits: its limb at L1, a quarter of the inside lit
        plot.clear();
        cx = plot.x(100);
        cy = plot.y(100);
        plot.disc(cx, cy, 90, 0f, 0f, 1000f, 1000f);
        assertEquals(Plot.DIM, plot.level(130, 100), "the limb");
        float big = share(plot, 100, 100, 20);
        assertTrue(big > 0.18f && big < 0.32f, "a quarter of the inside lit: " + big);
        // Only what is on the panel is worked out
        plot.clear();
        plot.disc(cx, cy, 90, 0f, 0f, 330f, 330f);
        assertTrue(plot.size() > 0);
        for (IntIntMap.Entry e : plot.entries()) assertTrue(Plot.colOf(e.key) <= plot.col(330f) && Plot.rowOf(e.key) <= plot.row(330f));
    }

    @Test
    void aSpriteSitsOnTheDotNearestItsPoint() {
        Plot plot = new Plot(P);
        plot.sprite(new String[] {"#.#", ".#.", "#.#"}, plot.x(10) + 0.5f, plot.y(20) - 0.5f, Plot.FULL);
        assertEquals(5, plot.size());
        assertEquals(Plot.FULL, plot.level(10, 20));
        assertEquals(Plot.FULL, plot.level(9, 19));
        assertEquals(Plot.FULL, plot.level(11, 21));
        assertEquals(0, plot.level(10, 19));
    }

    /** The share of the dots in a square {@code half} dots out from {@code ci, cj} that are lit. */
    private static float share(Plot plot, int ci, int cj, int half) {
        int lit = 0, all = 0;
        for (int j = cj - half; j <= cj + half; j++) {
            for (int i = ci - half; i <= ci + half; i++) {
                all++;
                if (plot.level(i, j) > 0) lit++;
            }
        }
        return lit / (float) all;
    }
}
