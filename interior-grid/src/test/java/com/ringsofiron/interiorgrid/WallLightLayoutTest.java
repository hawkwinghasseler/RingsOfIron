package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where wall lights hang when a layout does not place them. */
class WallLightLayoutTest {
    static ShipGridComponent grid(ChassisBlueprint bp) {
        return ChassisBlueprintLoader.load(bp).grid;
    }

    /** True if every floor tile of the grid is within reach in front of some light. */
    static boolean allLit(ShipGridComponent g, Array<WallLight> lights) {
        int[] room = WallLightLayout.rooms(g);
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                if (room[y * g.width + x] < 0) continue;
                boolean lit = false;
                for (WallLight p : lights) {
                    float dx = x + 0.5f - p.edgeX(), dy = y + 0.5f - p.edgeY();
                    lit |= room[p.y * g.width + p.x] == room[y * g.width + x]
                            && dx * p.side.dx + dy * p.side.dy < 0f
                            && dx * dx + dy * dy <= WallLightLayout.COVER * WallLightLayout.COVER;
                }
                if (!lit) return false;
            }
        }
        return true;
    }

    @Test
    void aSmallRoomGetsOneLight() {
        ShipGridComponent g = grid(new ChassisBlueprint(
                "#####",
                "#...#",
                "#...#",
                "#####"));
        Array<WallLight> lights = WallLightLayout.of(g);
        assertEquals(1, lights.size);
        assertTrue(allLit(g, lights));
    }

    @Test
    void aLongCorridorGetsLightsAlongIt() {
        ShipGridComponent g = grid(new ChassisBlueprint(
                "##################",
                "#................#",
                "#................#",
                "##################"));
        Array<WallLight> lights = WallLightLayout.of(g);
        assertTrue(lights.size >= 3 && lights.size <= 6, "spread along 16 tiles: " + lights.size);
        assertTrue(allLit(g, lights));
        for (int i = 0; i < lights.size; i++) {
            for (int j = i + 1; j < lights.size; j++) {
                float dx = lights.get(i).edgeX() - lights.get(j).edgeX(), dy = lights.get(i).edgeY() - lights.get(j).edgeY();
                assertTrue(dx * dx + dy * dy >= WallLightLayout.MIN_SPACING * WallLightLayout.MIN_SPACING);
            }
        }
    }

    @Test
    void eachRoomBehindADoorGetsItsOwnLightAndNoneHangOnTheDoor() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#.......#",
                "#########");
        bp.edgeWall(4, 1, Side.LEFT, WeightClass.LIGHT, true);
        bp.edgeWall(4, 2, Side.LEFT, WeightClass.LIGHT, false);
        ShipGridComponent g = grid(bp);
        int[] room = WallLightLayout.rooms(g);
        assertNotEquals(room[g.width + 1], room[g.width + 6], "the door parts two rooms");
        Array<WallLight> lights = WallLightLayout.of(g);
        boolean left = false, right = false;
        for (WallLight p : lights) {
            left |= p.x < 4;
            right |= p.x >= 4;
            assertFalse(p.y == 1 && (p.x == 4 && p.side == Side.LEFT || p.x == 3 && p.side == Side.RIGHT), "not on the door");
        }
        assertTrue(left && right);
        assertTrue(allLit(g, lights));
    }

    @Test
    void theArtTurnsToPutItsTopAgainstTheWall() {
        assertEquals(0, new WallLight(0, 0, Side.TOP).rotation());
        assertEquals(1, new WallLight(0, 0, Side.LEFT).rotation());
        assertEquals(2, new WallLight(0, 0, Side.BOTTOM).rotation());
        assertEquals(3, new WallLight(0, 0, Side.RIGHT).rotation());
    }
}
