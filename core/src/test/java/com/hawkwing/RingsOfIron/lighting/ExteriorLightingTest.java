package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What lights the hulls outside each frame: their own lights, nozzles that fire, and flashes that fade. */
class ExteriorLightingTest {
    static final EmissiveLights ART = EmissiveLightsTest.art();

    @Test
    void aHullsCastingLightsLandOnItsPose() {
        LightSpots body = new LightSpots();
        new HullLightRig(ART).collect("corvette", null, body);
        int casting = 0;
        for (int i = 0; i < body.size(); i++) if (body.get(i).casts()) casting++;
        assertTrue(casting > 0, "the corvette's hull art has lights that reach past it");

        ExteriorLighting l = new ExteriorLighting(ART);
        l.begin();
        l.addHull("corvette", null, 100f, -50f, MathUtils.HALF_PI);
        assertEquals(casting, l.spots().size(), "only the lights that reach past their glow");
        // Turned a quarter: body (x, y) lands at (100 - y, -50 + x)
        LightSpot first = null;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i).casts()) {
                first = body.get(i);
                break;
            }
        }
        assertEquals(100f - first.y, l.spots().get(0).x, 1e-3f);
        assertEquals(-50f + first.x, l.spots().get(0).y, 1e-3f);

        l.begin();
        assertEquals(0, l.spots().size(), "a new frame starts with no lights");
    }

    @Test
    void aNozzleLightsOnlyWhileItFiresAndReachesFurtherAtFullBurn() {
        ExteriorLighting l = new ExteriorLighting(ART);
        l.begin();
        l.nozzle(0f, 0f, 0f, 0f, ExteriorLighting.THRUSTER, 4f);
        assertEquals(0, l.spots().size());
        l.nozzle(0f, 0f, 0f, 0.2f, ExteriorLighting.THRUSTER, 4f);
        l.nozzle(0f, 0f, MathUtils.PI, 1f, ExteriorLighting.THRUSTER, 4f);
        LightSpot low = l.spots().get(0), full = l.spots().get(1);
        assertTrue(full.reach > low.reach && full.strength > low.strength);
        assertEquals(4f, full.reach, 1e-4f);
        assertTrue(low.x > 0f, "pushed down the exhaust, toward +x");
        assertTrue(full.x < 0f, "pushed down the exhaust, toward -x");
    }

    @Test
    void flashesFadeOutAndTheOldestGoFirst() {
        ExteriorLighting l = new ExteriorLighting(ART);
        l.flash(0f, 0f, ExteriorLighting.BLAST, 40f, 1f, 0.5f);
        l.flash(0f, 0f, ExteriorLighting.MUZZLE, 8f, 1f, 0.1f);
        l.age(0f);
        assertEquals(2, l.flashes(), "nothing fades while paused");
        l.age(0.2f);
        assertEquals(1, l.flashes(), "the muzzle flash is gone, the blast still fading");
        l.age(0.4f);
        assertEquals(0, l.flashes());
        for (int i = 0; i < ExteriorLighting.MAX_FLASHES + 10; i++) l.flash(0f, 0f, ExteriorLighting.MUZZLE, 8f, 1f, 1f);
        assertEquals(ExteriorLighting.MAX_FLASHES, l.flashes());
    }
}
