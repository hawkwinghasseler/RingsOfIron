package com.ringsofiron.combatsys;

import com.ringsofiron.combatsys.fire.FiringSolution;
import com.ringsofiron.combatsys.fire.FiringSolutionSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiringSolutionSolverTest {
    private final FiringSolutionSolver solver = new FiringSolutionSolver();
    private final FiringSolution out = new FiringSolution();

    @Test
    void stationaryTargetIsAStraightShot() {
        assertTrue(solver.solve(0, 0, 0, 0, 3000, 4000, 0, 0, 0, 0, 1000f, 60f, null, out));
        assertEquals(0.6f, out.aimDir.x, 1e-5f);
        assertEquals(0.8f, out.aimDir.y, 1e-5f);
        assertEquals(5f, out.timeToImpact, 1e-4f);
        assertEquals(3000f, out.interceptPoint.x, 1e-2f);
    }

    @Test
    void crossingTargetMatchesClosedFormLead() {
        // Target 1000 m ahead crossing at 300 m/s; round 500 m/s. 1000^2 + (300t)^2 = (500t)^2 -> t = 2.5 s
        assertTrue(solver.solve(0, 0, 0, 0, 1000, 0, 0, 300, 0, 0, 500f, 60f, null, out));
        assertEquals(2.5f, out.timeToImpact, 1e-4f);
        assertEquals(750f, out.interceptPoint.y, 1e-2f);
        assertEquals(1000f / 1250f, out.aimDir.x, 1e-4f);
        assertEquals(750f / 1250f, out.aimDir.y, 1e-4f);
    }

    @Test
    void shooterVelocityIsRelative() {
        // Same geometry as above, but everything shares a 7 km/s drift: the answer doesn't change.
        assertTrue(solver.solve(0, 0, 7000, 0, 1000, 0, 7000, 300, 0, 0, 500f, 60f, null, out));
        assertEquals(2.5f, out.timeToImpact, 1e-3f);
    }

    @Test
    void noSolutionWhenTargetIsFasterAndReceding() {
        assertFalse(solver.solve(0, 0, 0, 0, 1000, 0, 800, 0, 0, 0, 500f, 60f, null, out));
        assertFalse(out.valid);
    }

    @Test
    void noSolutionBeyondMaxFlightTime() {
        assertFalse(solver.solve(0, 0, 0, 0, 100_000, 0, 0, 0, 0, 0, 1000f, 30f, null, out));
    }

    @Test
    void acceleratingTargetConverges() {
        // Target burning at 20 m/s^2 across the line of fire.
        assertTrue(solver.solve(0, 0, 0, 0, 2000, 0, 0, 100, 0, 20, 1000f, 60f, null, out));
        float t = out.timeToImpact;
        float px = 2000f, py = 100f * t + 10f * t * t;
        float dist = (float) Math.sqrt(px * px + py * py);
        assertEquals(1000f * t, dist, 1f, "round and target meet");
        assertTrue(out.iterations <= FiringSolutionSolver.MAX_ITERATIONS);
        assertEquals(py, out.interceptPoint.y, 0.5f);
    }

    @Test
    void roundGravityIsLedOut() {
        // A tidal field pulls the round toward +y; the solver must aim low to compensate.
        TidalAccel field = (x, y, o) -> o.set(0f, 5f);
        assertTrue(solver.solve(0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 1000f, 60f, field, out));
        assertTrue(out.aimDir.y < 0f, "aims below the target");
        // Fly it: p = aim*s*t + 1/2 a t^2 lands on the target.
        float t = out.timeToImpact;
        float x = out.aimDir.x * 1000f * t;
        float y = out.aimDir.y * 1000f * t + 2.5f * t * t;
        assertEquals(5000f, x, 1f);
        assertEquals(0f, y, 1f);
    }
}
