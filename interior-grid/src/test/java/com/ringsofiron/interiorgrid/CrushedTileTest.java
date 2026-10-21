package com.ringsofiron.interiorgrid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tiles a collision crushed, and the dents round them (combat-sys 5.12). */
class CrushedTileTest {
    static ShipGridComponent room() {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(
                "#####",
                "#...#",
                "#####")).grid;
    }

    private static int tiles(List<float[]> pieces) {
        float a = 0f;
        for (float[] p : pieces) a += (p[2] - p[0]) * (p[5] - p[1]);
        return Math.round(a);
    }

    @Test
    void aCrushedTileIsABreachNobodyWalksIntoAndTheHullLeavesOut() {
        ShipGridComponent g = room();
        Tile floor = g.getTile(3, 1);
        floor.setIntegrity(0f);
        floor.setCrushed(true);
        assertTrue(floor.isHullBreach(), "a crushed deck plate is open to space");
        assertTrue(floor.isSpaceExposed());
        assertFalse(floor.isOpen());
        floor.bulkheadSealed = true;
        assertFalse(floor.isHullBreach(), "the crew can seal it");
        assertFalse(floor.isOpen(), "and still can't stand in it");
        assertTrue(g.hasCrushedTiles());
        assertEquals(14, tiles(HullShape.pieces(g)), "the hull collides on the other 14 tiles");
    }

    @Test
    void rebuildingACrushedTileStandsItAgain() {
        ShipGridComponent g = room();
        g.getTile(4, 1).setCrushed(true);
        g.dent(5, 1, -0.8f, 0f);
        g.dent(5, 2, -0.8f, 0f);
        g.uncrush(4, 1);
        assertFalse(g.hasCrushedTiles());
        assertTrue(g.hullShapeDirty, "the hull is reshaped round it");
        assertEquals(0f, g.dentX(5, 1));
        assertEquals(15, tiles(HullShape.pieces(g)));
    }

    @Test
    void dentsAreCappedAndClampedToTheGrid() {
        ShipGridComponent g = room();
        assertNull(g.dents);
        g.dent(0, 0, 10f, 0f);
        assertEquals(ShipGridComponent.MAX_DENT, g.dentX(0, 0), 1e-5f);
        assertEquals(g.dentX(0, 0), g.dentX(-3, -3), "off the grid reads the nearest corner");
    }

    @Test
    void crushedTilesAndDentsAreSaved() {
        ShipGridComponent g = room();
        g.getTile(0, 1).setIntegrity(0f);
        g.getTile(0, 1).setCrushed(true);
        g.dent(0, 1, 0.5f, -0.25f);
        ShipGridComponent back = WallLightSaveTest.roundTrip(g);
        assertTrue(back.getTile(0, 1).isCrushed());
        assertFalse(back.getTile(1, 1).isCrushed());
        assertEquals(0.5f, back.dentX(0, 1), 1e-5f);
        assertEquals(-0.25f, back.dentY(0, 1), 1e-5f);
        assertNull(WallLightSaveTest.roundTrip(room()).dents, "an undented hull saves none");
    }
}
