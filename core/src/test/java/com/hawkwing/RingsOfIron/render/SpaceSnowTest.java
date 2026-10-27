package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.orbital.math.StateVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The snow's reference frame: a circular orbit at the ship's altitude, turning the ship's way. */
class SpaceSnowTest {
    private static final double MU = 3.986e14;
    private static final double R = 6.771e6;
    private static final double CIRCULAR = Math.sqrt(MU / R);

    @Test
    void aCircularOrbitSeesTheSnowStill() {
        Vector2 d = SpaceSnow.drift(new StateVector(R, 0, 0, CIRCULAR), MU, new Vector2());
        assertEquals(0f, d.len(), 1e-3f);
        // Clockwise too, and anywhere round the body
        double a = 2.1;
        d = SpaceSnow.drift(new StateVector(R * Math.cos(a), R * Math.sin(a),
                CIRCULAR * Math.sin(a), -CIRCULAR * Math.cos(a)), MU, d);
        assertEquals(0f, d.len(), 1e-3f);
    }

    @Test
    void aFasterShipSeesTheSnowDriftBack() {
        Vector2 d = SpaceSnow.drift(new StateVector(R, 0, 0, CIRCULAR + 50), MU, new Vector2());
        assertEquals(0f, d.x, 1e-3f);
        assertEquals(-50f, d.y, 1e-3f);
    }

    @Test
    void aFallingShipSeesTheSnowRiseAndPass() {
        Vector2 d = SpaceSnow.drift(new StateVector(R, 0, -30, CIRCULAR), MU, new Vector2());
        assertEquals(30f, d.x, 1e-3f);
        assertEquals(0f, d.y, 1e-3f);
    }

    @Test
    void aShipAtRestSeesTheSnowGoByAtOrbitalSpeed() {
        Vector2 d = SpaceSnow.drift(new StateVector(0, R, 0, 0), MU, new Vector2());
        assertEquals(CIRCULAR, d.len(), 1e-2);
        assertTrue(d.x < 0f, "counterclockwise when the ship isn't turning");
    }

    @Test
    void theSnowReachesFarBackAndMostOfItIsDeep() {
        assertEquals(1f, SpaceSnow.depth(SpaceSnow.LAYERS - 1), 1e-4f);
        assertTrue(SpaceSnow.depth(0) >= 30f, "the farthest layer is far deeper than the old 2.5");
        float far = 0f;
        for (int k = 0; k < SpaceSnow.LAYERS / 2; k++) far += SpaceSnow.share(k);
        assertTrue(far > 0.7f, "most motes are in the deeper half: " + far);
        for (int k = 1; k < SpaceSnow.LAYERS; k++) {
            assertTrue(SpaceSnow.depth(k) < SpaceSnow.depth(k - 1));
            assertTrue(SpaceSnow.share(k) < SpaceSnow.share(k - 1));
        }
    }

    @Test
    void deeperSnowSlidesSlowerAtAnyZoom() {
        // The nearest layer moves as the true drift looks at the starting zoom; no zoom enters it
        assertEquals(100f / SpaceSnow.REFERENCE_MPP, SpaceSnow.pixelsPerSecond(100f, SpaceSnow.LAYERS - 1), 1e-2f);
        for (int k = 1; k < SpaceSnow.LAYERS; k++) {
            assertTrue(SpaceSnow.pixelsPerSecond(100f, k - 1) < SpaceSnow.pixelsPerSecond(100f, k));
        }
    }
}
