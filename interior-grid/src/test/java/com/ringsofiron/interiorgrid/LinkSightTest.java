package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Line of sight from one craft into another through their joined ports. */
class LinkSightTest {

    /** A room with a Small port on top whose opening is tile (2, 4), and a wall stub at (1, 2). */
    private static ShipGridComponent craft() {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(new String[]{
                "#DDD#",
                "#...#",
                "##..#",
                "#...#",
                "#####",
        }).tileSize(1f)).grid;
    }

    private static EdgeWall door(ShipGridComponent g) {
        return g.getEdge(2, 4, Side.TOP);
    }

    @Test
    void seesThroughTwoOpenPortDoorsAndNotThroughAShutOne() {
        ShipGridComponent a = craft(), b = craft();
        GridLink.join(new Entity(), a, 2, 4, Side.TOP, new Entity(), b, 2, 4, Side.TOP).setGap(1f);
        door(a).bulkheadSealed = false;
        assertFalse(GridRayTraversal.hasLineOfSight(a, 2, 1, b, 2, 1), "the far door is shut");
        door(b).bulkheadSealed = false;
        assertTrue(GridRayTraversal.hasLineOfSight(a, 2, 1, b, 2, 1), "straight through both");
        assertTrue(GridRayTraversal.hasLineOfSight(b, 2, 1, a, 2, 1), "and back");
        assertTrue(GridRayTraversal.hasLineOfSight(a, 3, 1, b, 3, 1), "corner to corner through the opening");
    }

    @Test
    void wallsOnEitherCraftBlock() {
        ShipGridComponent a = craft(), b = craft();
        GridLink.join(new Entity(), a, 2, 4, Side.TOP, new Entity(), b, 2, 4, Side.TOP);
        door(a).bulkheadSealed = false;
        door(b).bulkheadSealed = false;
        // b is turned to face a, so its (1, 2) wall stub lies between a's door and b's (1, 1)... seen from (3, 3)
        assertFalse(GridRayTraversal.hasLineOfSight(a, 3, 3, b, 1, 1), "behind the stub on b");
        assertFalse(GridRayTraversal.hasLineOfSight(a, 1, 1, b, 2, 1), "behind the stub on a");
        assertFalse(GridRayTraversal.hasLineOfSight(a, 3, 1, b, 1, 1), "not through the frame beside the opening");
    }

    @Test
    void noLinkNoSight() {
        ShipGridComponent a = craft(), b = craft();
        door(a).bulkheadSealed = false;
        door(b).bulkheadSealed = false;
        assertFalse(GridRayTraversal.hasLineOfSight(a, 2, 1, b, 2, 1));
    }
}
