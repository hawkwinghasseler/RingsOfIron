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
    void theGridMovesWithTheBodiesOnARebase() {
        BubbleGrid grid = new BubbleGrid(() -> null);
        grid.shift(250, -40);
        grid.shift(1000, 0);
        // A line was at local 0; bodies moved by -250, so it is now at -250: -250 + frame is a whole step
        assertEquals(0.0, Math.IEEEremainder(-250 + grid.frameX(), 100), 1e-9);
        assertEquals(0.0, Math.IEEEremainder(40 + grid.frameY(), 100), 1e-9);
    }
}
