package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which point lights shade a hull, and the levels its raised parts shade each other at. */
class HullShadingTest {
    @Test
    void partsOfNearTheSameHeightStandAtOneLevel() {
        assertEquals(HullShading.level(0.5f), HullShading.level(0.51f));
        assertEquals(0.9f, HullShading.level(0.9f), 1e-6f);
        assertTrue(HullShading.level(0.9f) > HullShading.level(0.8f), "a bridge over a spine is a level above it");
    }

    @Test
    void lightsThatReachTheArtAreTurnedIntoTheBodyFrame() {
        HullShading s = new HullShading();
        // A 10 x 4 m hull centered on its body, at (100, 0) turned a quarter; a light 3 m off its nose
        // (world +y) and one too far off to reach it
        s.setPointLights(new float[]{100f, 8f, 4f, 1f, 100f, 40f, 4f, 1f}, 2, 0.5f);
        s.near(-5f, 2f, 10f, 4f, 100f, 0f, MathUtils.HALF_PI);
        assertEquals(1, s.pointCount());
        assertEquals(8f, s.point(0, 0), 1e-4f, "off the nose: body +x");
        assertEquals(0f, s.point(0, 1), 1e-4f);
        assertEquals(4f, s.point(0, 2), 1e-6f);
    }

    @Test
    void theStrongestLightsAreKeptWhenThereAreTooMany() {
        HullShading s = new HullShading();
        int n = HullShading.MAX_POINTS + 4;
        float[] lights = new float[4 * n];
        for (int i = 0; i < n; i++) {
            lights[4 * i] = 0f;
            lights[4 * i + 1] = 0f;
            lights[4 * i + 2] = 10f;
            lights[4 * i + 3] = (i + 1f) / n;
        }
        s.setPointLights(lights, n, 0.5f);
        s.near(-1f, 1f, 2f, 2f, 0f, 0f, 0f);
        assertEquals(HullShading.MAX_POINTS, s.pointCount());
        for (int i = 0; i < s.pointCount(); i++) {
            assertTrue(s.point(i, 3) > 4f / n, "only the " + HullShading.MAX_POINTS + " strongest");
        }
    }
}
