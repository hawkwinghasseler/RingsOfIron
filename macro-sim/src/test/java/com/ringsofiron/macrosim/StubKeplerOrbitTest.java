package com.ringsofiron.macrosim;

import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubKeplerOrbitTest {
    static final double MU = 3.986004418e14;

    @Test
    void stateToElementsRoundTripsForEveryConicAndDirection() {
        double[] eccentricities = {0, 0.5, 0.99, 1.5};
        for (double e : eccentricities) {
            for (int dir : new int[]{1, -1}) {
                double a = e < 1 ? 10_000e3 : -10_000e3;
                StubKeplerOrbit orbit = new StubKeplerOrbit(MU, a, e, 0.7, 0.3, 100, dir);
                for (double t : new double[]{100, 1_000, 5_000}) {
                    StateVector s = orbit.stateAt(t, new StateVector());
                    StubKeplerOrbit back = StubKeplerOrbit.fromState(MU, s, t);
                    StateVector s2 = back.stateAt(t + 777, new StateVector());
                    StateVector s1 = orbit.stateAt(t + 777, new StateVector());
                    assertEquals(s1.x, s2.x, 1e-3 * s1.distance() * 1e-3, "x e=" + e + " dir=" + dir);
                    assertEquals(s1.y, s2.y, 1e-3 * s1.distance() * 1e-3, "y e=" + e + " dir=" + dir);
                    assertEquals(s1.vx, s2.vx, 1e-6 * s1.speed(), "vx e=" + e + " dir=" + dir);
                    assertEquals(s1.vy, s2.vy, 1e-6 * s1.speed(), "vy e=" + e + " dir=" + dir);
                }
            }
        }
    }

    @Test
    void energyIsConservedAlongTheOrbit() {
        StubKeplerOrbit orbit = new StubKeplerOrbit(MU, 20_000e3, 0.6, 1.0, 0, 0, 1);
        double expected = -MU / (2 * orbit.a);
        StateVector s = new StateVector();
        for (double t = 0; t < orbit.period(); t += orbit.period() / 37) {
            orbit.stateAt(t, s);
            double energy = s.speed() * s.speed() / 2 - MU / s.distance();
            assertEquals(1.0, energy / expected, 1e-10);
        }
    }

    @Test
    void circularOrbitStartsAtTheRequestedPhaseAndReturnsAfterOnePeriod() {
        StubKeplerOrbit orbit = StubKeplerOrbit.circular(MU, 7_000e3, 1.2, 50, -1);
        StateVector s = orbit.stateAt(50, new StateVector());
        assertEquals(1.2, Math.atan2(s.y, s.x), 1e-12);
        StateVector later = orbit.stateAt(50 + orbit.period(), new StateVector());
        assertEquals(s.x, later.x, 1e-3);
        assertEquals(s.y, later.y, 1e-3);
        assertTrue(s.x * s.vy - s.y * s.vx < 0, "clockwise");
    }
}
