package com.ringsofiron.combatsys;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridRayTraversalTest {

    private static Array<String> walk(float sx, float sy, float dx, float dy, float maxT) {
        final Array<String> seen = new Array<>();
        GridRayTraversal.traverse(sx, sy, dx, dy, maxT, new GridRayTraversal.Visitor() {
            @Override
            public boolean tile(int x, int y, float tEnter, float tExit) {
                seen.add(x + "," + y);
                return true;
            }

            @Override
            public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                seen.add("|");
                return true;
            }
        });
        return seen;
    }

    @Test
    void horizontalRayVisitsEachTileWithEdgesBetween() {
        assertEquals("[0,0, |, 1,0, |, 2,0, |, 3,0]", walk(0.5f, 0.5f, 1f, 0f, 3f).toString());
    }

    @Test
    void shallowDiagonalVisitsEveryCrossedTile() {
        // From (0.5, 0.5) toward (4.5, 1.5): crosses y = 1 at x = 2.5.
        Array<String> tiles = walk(0.5f, 0.5f, 4f, 1f, 1f);
        tiles.removeValue("|", false);
        while (tiles.removeValue("|", false)) { }
        assertEquals("[0,0, 1,0, 2,0, 2,1, 3,1, 4,1]", tiles.toString());
    }

    @Test
    void exactCornerStepsXThenY() {
        Array<String> tiles = walk(0.5f, 0.5f, 1f, 1f, 1.6f);
        while (tiles.removeValue("|", false)) { }
        assertEquals("[0,0, 1,0, 1,1, 2,1, 2,2]", tiles.toString());
    }

    @Test
    void negativeDirection() {
        Array<String> tiles = walk(2.5f, 2.5f, -1f, 0f, 2f);
        while (tiles.removeValue("|", false)) { }
        assertEquals("[2,2, 1,2, 0,2]", tiles.toString());
    }

    @Test
    void lineOfSightBlockedByWallTileAndEdgeWall() {
        ShipGridComponent grid = new ShipGridComponent(6, 3, 1f);
        for (int x = 0; x < 6; x++) grid.getTile(x, 1).floorType = Tile.FloorType.FLOOR;
        assertTrue(GridRayTraversal.hasLineOfSight(grid, 0, 1, 5, 1));

        grid.setEdge(2, 1, Side.RIGHT, new EdgeWall(WeightClass.LIGHT));
        assertFalse(GridRayTraversal.hasLineOfSight(grid, 0, 1, 5, 1));
        grid.getEdge(2, 1, Side.RIGHT).integrity = 0f;
        assertTrue(GridRayTraversal.hasLineOfSight(grid, 0, 1, 5, 1), "destroyed wall no longer blocks");

        grid.getTile(4, 1).wallType = Tile.WallType.INTERNAL;
        assertFalse(GridRayTraversal.hasLineOfSight(grid, 0, 1, 5, 1));
    }
}
