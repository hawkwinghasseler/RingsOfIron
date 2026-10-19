package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The deck edges that cast shadows. */
class LightBarriersTest {
    static ShipGridComponent corridor(boolean door) {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#######",
                "#.....#",
                "#######");
        if (door) bp.edgeWall(3, 1, Side.LEFT, WeightClass.LIGHT, true);
        return ChassisBlueprintLoader.load(bp).grid;
    }

    /** Total length of the barriers, tiles. */
    static float length(ShipGridComponent g, FloatArray seg) {
        float total = 0f;
        for (int i = 0; i < seg.size; i += 4) {
            total += Math.abs(seg.get(i + 2) - seg.get(i)) + Math.abs(seg.get(i + 3) - seg.get(i + 1));
        }
        return total / g.tileSize;
    }

    @Test
    void theWallsAroundARoomAreItsFaces() {
        ShipGridComponent g = corridor(false);
        LightBarriers b = new LightBarriers(g);
        assertTrue(b.update());
        assertEquals(12f, length(g, b.segments()), 1e-4f, "two 5-tile walls and two end walls");
        assertEquals(4 * 4, b.segments().size, "each straight run is one segment");
        assertFalse(b.update(), "nothing changed");
    }

    @Test
    void aShutDoorCastsAShadowUntilItOpens() {
        ShipGridComponent g = corridor(true);
        LightBarriers b = new LightBarriers(g);
        b.update();
        float open = length(g, b.segments());
        g.getEdge(3, 1, Side.LEFT).bulkheadSealed = true;
        assertTrue(b.update());
        assertEquals(open + 1f, length(g, b.segments()), 1e-4f);
        g.getEdge(3, 1, Side.LEFT).bulkheadSealed = false;
        assertTrue(b.update());
        assertEquals(open, length(g, b.segments()), 1e-4f);
    }

    @Test
    void aHoleBlownInTheHullLetsLightOut() {
        ShipGridComponent g = corridor(false);
        LightBarriers b = new LightBarriers(g);
        b.update();
        g.getTile(3, 0).setIntegrity(0f);
        assertTrue(b.update());
        // The breached tile is open to the light: its face onto the room goes, its outer edge stays
        assertEquals(12f + 2f, length(g, b.segments()), 1e-4f);
    }
}
