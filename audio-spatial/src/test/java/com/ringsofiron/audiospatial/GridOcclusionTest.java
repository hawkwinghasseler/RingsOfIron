package com.ringsofiron.audiospatial;

import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridOcclusionTest {

    @Test
    void openRoomHasNoWalls() {
        ShipGridComponent g = TestGrids.floor(10, 6);
        assertEquals(0, GridOcclusion.countWalls(g, 0, 0, 9, 5));
        assertEquals(0, GridOcclusion.countWalls(g, 3, 3, 3, 3));
    }

    @Test
    void edgeWallCountsOnceAndDoorsLetSoundThrough() {
        ShipGridComponent g = TestGrids.floor(10, 3);
        EdgeWall[] wall = TestGrids.edgeColumn(g, 4);
        assertEquals(1, GridOcclusion.countWalls(g, 1, 1, 8, 1));
        assertEquals(1, GridOcclusion.countWalls(g, 8, 1, 1, 1), "same both ways");

        for (EdgeWall w : wall) w.hasBulkhead = true; // open doors along the whole wall
        assertEquals(0, GridOcclusion.countWalls(g, 1, 1, 8, 1));
        int[] openings = new int[1];
        GridOcclusion.trace(g, 1, 1, 8, 1, openings);
        assertEquals(1, openings[0], "threads one doorway");
        for (EdgeWall w : wall) w.bulkheadSealed = true;
        assertEquals(1, GridOcclusion.countWalls(g, 1, 1, 8, 1));
        for (EdgeWall w : wall) w.integrity = 0f; // blown out
        assertEquals(0, GridOcclusion.countWalls(g, 1, 1, 8, 1));
    }

    @Test
    void thickTileWallCountsOnce() {
        ShipGridComponent g = TestGrids.floor(10, 1);
        TestGrids.wallTile(g, 4, 0);
        TestGrids.wallTile(g, 5, 0);
        assertEquals(1, GridOcclusion.countWalls(g, 0, 0, 9, 0));
    }

    @Test
    void wallsAddUp() {
        ShipGridComponent g = TestGrids.floor(12, 3);
        TestGrids.edgeColumn(g, 2);
        TestGrids.wallTile(g, 5, 0);
        TestGrids.wallTile(g, 5, 1);
        TestGrids.wallTile(g, 5, 2);
        TestGrids.edgeColumn(g, 8);
        assertEquals(3, GridOcclusion.countWalls(g, 0, 1, 11, 1));
        assertEquals(2, GridOcclusion.countWalls(g, 3, 1, 11, 1));
    }

    @Test
    void destroyedTileWallLetsSoundThrough() {
        ShipGridComponent g = TestGrids.floor(5, 1);
        TestGrids.wallTile(g, 2, 0);
        g.getTile(2, 0).setIntegrity(0f);
        assertEquals(0, GridOcclusion.countWalls(g, 0, 0, 4, 0));
    }

    @Test
    void soundLeaksAroundACorner() {
        // A single wall tile at (1, 0): the diagonal from (0, 0) to (1, 1) can pass by (0, 1).
        ShipGridComponent g = TestGrids.floor(2, 2);
        TestGrids.wallTile(g, 1, 0);
        assertEquals(0, GridOcclusion.countWalls(g, 0, 0, 1, 1));
        TestGrids.wallTile(g, 0, 1);
        assertEquals(1, GridOcclusion.countWalls(g, 0, 0, 1, 1));
    }

    @Test
    void vacuumOutsideTheHullIsABarrier() {
        ShipGridComponent g = TestGrids.floor(7, 1);
        g.getTile(3, 0).floorType = com.ringsofiron.interiorgrid.Tile.FloorType.NONE; // void gap
        assertEquals(1, GridOcclusion.countWalls(g, 0, 0, 6, 0));
    }
}
