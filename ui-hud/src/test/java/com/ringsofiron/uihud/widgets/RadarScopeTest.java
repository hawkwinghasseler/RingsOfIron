package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
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
    void aClickPicksTheNearestMarkUnderIt() {
        FloatArray offsets = new FloatArray(new float[]{5_000f, 0f, 5_500f, 0f, 0f, -30_000f});
        Vector2 tmp = new Vector2();
        // 10 km on an 80 px scope: the first plots at 40 px, the second at 44, the third on the rim below
        assertEquals(0, RadarScope.pick(39f, 1f, offsets, 10_000f, 80f, tmp));
        assertEquals(1, RadarScope.pick(45f, 0f, offsets, 10_000f, 80f, tmp));
        assertEquals(2, RadarScope.pick(2f, -78f, offsets, 10_000f, 80f, tmp), "contacts on the rim can be picked too");
        assertEquals(-1, RadarScope.pick(-40f, 0f, offsets, 10_000f, 80f, tmp), "empty scope picks nothing");
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
