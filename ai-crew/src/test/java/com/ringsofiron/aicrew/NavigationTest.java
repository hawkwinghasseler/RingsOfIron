package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

class NavigationTest {

    /** Two rooms split by a thin wall at x = 4 with a door at y = 2. */
    static ShipGridComponent twoRooms() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########");
        for (int y = 1; y <= 3; y++) bp.edgeWall(4, y, Side.LEFT, WeightClass.LIGHT, y == 2);
        return ChassisBlueprintLoader.load(bp).grid;
    }

    @Test
    void pathGoesThroughTheOpenDoor() {
        ShipGridComponent g = twoRooms();
        GridPathfinder pf = new GridPathfinder(g);
        Array<GridNode> path = new Array<>();
        assertTrue(pf.findPath(1, 3, 7, 3, path));
        boolean usedDoor = false;
        for (int i = 1; i < path.size; i++) {
            GridNode a = path.get(i - 1), b = path.get(i);
            assertEquals(1, Math.abs(a.x - b.x) + Math.abs(a.y - b.y), "steps are orthogonal");
            if (a.x == 3 && b.x == 4) {
                assertEquals(2, a.y);
                usedDoor = true;
            }
        }
        assertTrue(usedDoor);
        assertEquals(6 + 2 + 1, path.size, "shortest route: 6 across, 1 down and 1 up");
    }

    @Test
    void sealedDoorCutsTheRouteAndOpeningItRestoresIt() {
        ShipGridComponent g = twoRooms();
        GridPathfinder pf = new GridPathfinder(g);
        Array<GridNode> path = new Array<>();
        EdgeWall door = g.getEdge(4, 2, Side.LEFT);
        door.bulkheadSealed = true;
        assertFalse(pf.findPath(1, 2, 7, 2, path));
        door.bulkheadSealed = false;
        assertTrue(pf.findPath(1, 2, 7, 2, path));
    }

    @Test
    void destroyedWallStopsBlocking() {
        ShipGridComponent g = twoRooms();
        g.getEdge(4, 2, Side.LEFT).bulkheadSealed = true;
        g.getEdge(4, 1, Side.LEFT).integrity = 0f;
        GridPathfinder pf = new GridPathfinder(g);
        assertTrue(pf.findPath(1, 1, 7, 1, new Array<>()));
    }

    @Test
    void mountsAreWalkable() {
        ShipGridComponent g = ChassisBlueprintLoader.load(new String[]{
                "#####",
                "#...#",
                "#.R.#",
                "#...#",
                "#####"}, 1f).grid;
        GridPathfinder pf = new GridPathfinder(g);
        Array<GridNode> path = new Array<>();
        assertTrue(pf.findPath(1, 2, 2, 2, path), "can walk onto the reactor");
        assertTrue(pf.findPath(1, 2, 3, 2, path));
        assertEquals(3, path.size, "straight over the reactor");
    }

    @Test
    void lineOfSightIsBlockedByWallsAndClosedDoors() {
        ShipGridComponent g = twoRooms();
        assertTrue(GridRayTraversal.hasLineOfSight(g, 1, 2, 7, 2), "straight through the open door");
        assertFalse(GridRayTraversal.hasLineOfSight(g, 1, 3, 7, 3), "thin wall");
        g.getEdge(4, 2, Side.LEFT).bulkheadSealed = true;
        assertFalse(GridRayTraversal.hasLineOfSight(g, 1, 2, 7, 2), "sealed door");
        assertTrue(GridRayTraversal.hasLineOfSight(g, 1, 1, 3, 3), "same room diagonal");
    }

    @Test
    void sightCannotSlipBetweenDiagonalWalls() {
        ShipGridComponent g = ChassisBlueprintLoader.load(new String[]{
                "#####",
                "#..I#",
                "#.I.#",
                "#...#",
                "#####"}, 1f).grid;
        // Walls at (3,3) and (2,2): the diagonal from (2,3) to (3,2) passes exactly through their shared corner
        assertFalse(GridRayTraversal.hasLineOfSight(g, 2, 3, 3, 2));
        assertTrue(GridRayTraversal.hasLineOfSight(g, 1, 1, 3, 1));
    }
}
