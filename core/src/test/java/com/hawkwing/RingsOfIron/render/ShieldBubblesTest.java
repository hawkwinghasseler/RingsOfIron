package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bubble fits round the hull, comes up and goes out, bursts when it drops, and ripples fade. */
class ShieldBubblesTest {
    @Test
    void theBubbleFitsRoundTheHullsTilesNotItsEmptyGrid() {
        ShipGridComponent grid = new ShipGridComponent(20, 10, 1f, -10f, -5f);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) grid.getTile(x, y).floorType = Tile.FloorType.NONE;
        }
        // An 8 x 4 hull in the grid's top right
        for (int y = 6; y < 10; y++) {
            for (int x = 12; x < 20; x++) grid.getTile(x, y).floorType = Tile.FloorType.FLOOR;
        }
        ShieldBubbles.Bubble b = new ShieldBubbles.Bubble();
        ShieldBubbles.fit(grid, b);
        assertEquals(6f, b.cx, 1e-5f, "centered on the hull, not the grid");
        assertEquals(3f, b.cy, 1e-5f);
        assertEquals(4f * ShieldBubbles.ROOT_2 + ShieldBubbles.MARGIN, b.a, 1e-4f);
        assertEquals(2f * ShieldBubbles.ROOT_2 + ShieldBubbles.MARGIN, b.b, 1e-4f);
        // The hull's corner is inside the bubble
        float dx = 4f / b.a, dy = 2f / b.b;
        assertTrue(dx * dx + dy * dy < 1f, "the corners are covered");
    }

    @Test
    void itComesUpSlowlyAndBurstsWhenTheShieldsDrop() {
        ShieldBubbles.Bubble b = new ShieldBubbles.Bubble();
        ShieldBubbles.step(b, true, 0.3f);
        assertEquals(0.5f, b.level, 1e-5f, "half up after half its rise");
        ShieldBubbles.step(b, true, 1f);
        assertEquals(1f, b.level, 1e-6f);
        assertEquals(ShieldBubbles.COLLAPSE_LIFE, b.collapse, 1e-6f, "no burst while it holds");
        ShieldBubbles.step(b, false, 0.016f);
        assertEquals(0f, b.collapse, 1e-6f, "dropping starts the burst");
        ShieldBubbles.step(b, false, 0.1f);
        assertEquals(0f, b.level, 1e-6f, "and it goes out at once");
        assertEquals(0.1f, b.collapse, 1e-6f, "the burst runs on, not restarted");
    }

    @Test
    void ripplesFadeAndBigHitsFlareBrighter() {
        ShieldBubbles.Bubble b = new ShieldBubbles.Bubble();
        b.ripples.add(new float[]{0f, 0f, 1f});
        ShieldBubbles.step(b, true, ShieldBubbles.RIPPLE_LIFE * 0.5f);
        assertEquals(1, b.ripples.size);
        ShieldBubbles.step(b, true, ShieldBubbles.RIPPLE_LIFE);
        assertEquals(0, b.ripples.size, "gone after its life");
        float autocannon = ShieldBubbles.strength(1.8e5f), railgun = ShieldBubbles.strength(1.8e8f);
        assertTrue(autocannon > 0.25f && autocannon < 0.5f, "an autocannon round shows: " + autocannon);
        assertEquals(1f, railgun, 1e-6f, "a railgun slug flares fully");
        assertEquals(0.2f, ShieldBubbles.strength(10f), 1e-6f, "the smallest still shows");
    }

    @Test
    void theRimIsBrightJustInsideTheEdgeAndClearOutside() {
        assertTrue(ShieldBubbles.rimAlpha(0.93f) > 0.9f);
        assertTrue(ShieldBubbles.rimAlpha(0.2f) < 0.05f, "faint at the middle");
        assertEquals(0f, ShieldBubbles.rimAlpha(1f));
    }
}
