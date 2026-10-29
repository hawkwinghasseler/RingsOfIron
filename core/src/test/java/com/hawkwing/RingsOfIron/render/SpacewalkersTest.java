package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A tether is drawn along a curve through its rope's points: through each one, straight where the rope is, and round its bends. */
class SpacewalkersTest {
    final FloatArray curve = new FloatArray();

    @Test
    void theLineRunsThroughEachOfTheRopesPoints() {
        FloatArray rope = FloatArray.with(0f, 0f, 1f, 0f, 1f, -1f, 2f, -1f);
        Spacewalkers.curve(rope, curve);
        assertEquals(2 * (3 * Spacewalkers.CURVE_STEPS + 1), curve.size, "the pieces between each two points, and the last");
        for (int i = 0; i < 4; i++) {
            assertEquals(rope.get(2 * i), curve.get(2 * i * Spacewalkers.CURVE_STEPS), 1e-6f);
            assertEquals(rope.get(2 * i + 1), curve.get(2 * i * Spacewalkers.CURVE_STEPS + 1), 1e-6f);
        }
    }

    @Test
    void aStraightRopeIsDrawnStraight() {
        Spacewalkers.curve(FloatArray.with(0f, 0f, 1f, 0f, 2f, 0f, 3f, 0f), curve);
        for (int k = 0; k < curve.size; k += 2) assertEquals(0f, curve.get(k + 1), 1e-6f);
        for (int k = 2; k < curve.size; k += 2) assertTrue(curve.get(k) > curve.get(k - 2), "and in order along it");
    }

    @Test
    void aBendRoundACornerOfTheHullIsRoundedOnItsOutside() {
        // Along the top of a hull lying below and left of the origin, and down its end
        Spacewalkers.curve(FloatArray.with(-2f, 0f, -1f, 0f, 0f, 0f, 0f, -1f, 0f, -2f), curve);
        boolean bent = false;
        for (int k = 0; k < curve.size; k += 2) {
            float x = curve.get(k), y = curve.get(k + 1);
            assertFalse(x < -1e-6f && y < -1e-6f, "never through the hull: " + x + ", " + y);
            bent |= x > 1e-3f || y > 1e-3f;
        }
        assertTrue(bent, "but off the straight round the corner");
    }
}
