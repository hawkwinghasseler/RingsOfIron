package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest {
    private final World world = new World();

    /** The world at the weapons console on a 1280 x 800 stage with the prompt line up. */
    private void bounds() {
        world.left = 16f;
        world.right = 1264f;
        world.top = 16f;
        world.bottom = 562f;
        world.colX0 = 280f;
        world.colX1 = 1000f;
        world.colBottom = Geo.TICKER_Y + 66f;
    }

    @Test
    void labelsStayOffTheTopColumnAndBelowTheFloor() {
        bounds();
        assertTrue(world.inside(640f, 400f));
        assertFalse(world.inside(640f, 100f), "under the top column");
        assertTrue(world.inside(100f, 100f), "beside the top column");
        assertFalse(world.inside(640f, 580f), "under the prompt line");
        assertFalse(world.inside(1270f, 400f), "off the edge");
    }

    @Test
    void thingsOffScreenTheSameWayGetMarkersSideBySide() {
        bounds();
        // A missile, the lock and a raider, all a long way below
        world.edge(630f, 2_000f, 1280f, 800f, "MSL 0:18", Phosphor.RED, Drive.FULL);
        world.edge(640f, 2_500f, 1280f, 800f, "FREIGHTER 3.0 KM", Phosphor.ZINC, Drive.FULL);
        world.edge(650f, 3_000f, 1280f, 800f, "RAIDER 11 KM", Phosphor.RED, Drive.NORM);
        // And one off to the right, which stays where it is
        world.edge(5_000f, 300f, 1280f, 800f, "RAIDER 6 2.1 KM", Phosphor.RED, Drive.NORM);
        float rightY = world.edges[3].y;
        world.settleEdges();
        assertEquals(4, world.edgeCount);
        assertEquals(rightY, world.edges[3].y);
        for (int i = 0; i < 3; i++) {
            World.Edge a = world.edges[i];
            assertEquals('d', a.dir);
            float ha = Kit.edgeHalf('d', a.text);
            assertTrue(a.x - ha >= world.left - 1e-3f && a.x + ha <= world.right + 1e-3f, a.text + " off the edge");
            for (int j = i + 1; j < 3; j++) {
                World.Edge b = world.edges[j];
                float hb = Kit.edgeHalf('d', b.text);
                assertTrue(Math.abs(a.x - b.x) >= ha + hb, a.text + " overlaps " + b.text);
            }
        }
        // Each still sits on the bottom edge, above the floor
        for (int i = 0; i < 3; i++) assertTrue(world.edges[i].y <= world.bottom - 14f + 1e-3f);
    }
}
