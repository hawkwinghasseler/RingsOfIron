package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.uihud.systems.NavMarkerSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadarScopeTest {

    @Test
    void contactsInRangePlotToScale() {
        Vector2 p = new Vector2();
        assertTrue(RadarScope.plot(5_000f, 0f, 10_000f, 80f, p));
        assertEquals(40f, p.x, 1e-4f);
        assertEquals(0f, p.y, 1e-4f);
    }

    @Test
    void contactsBeyondRangeSitOnTheRimTheirWay() {
        Vector2 p = new Vector2();
        assertFalse(RadarScope.plot(-30_000f, 40_000f, 10_000f, 80f, p));
        assertEquals(80f, p.len(), 1e-3f);
        assertEquals(-0.6f * 80f, p.x, 1e-3f);
        assertEquals(0.8f * 80f, p.y, 1e-3f);
    }

    @Test
    void progradeIsTheShipsVelocityPlusTheBubbles() {
        // Bubble falling along +y at orbital speed, ship drifting slowly along +x in it
        float a = NavMarkerSystem.progradeAngle(5f, 0f, 0, 7_670);
        assertEquals(Math.PI / 2, a, 1e-3);
        assertEquals(Math.PI, NavMarkerSystem.progradeAngle(-3f, 0f, 0, 0), 1e-6);
        assertTrue(Float.isNaN(NavMarkerSystem.progradeAngle(0.1f, 0.1f, 0, 0)), "too slow to point anywhere");
    }
}
