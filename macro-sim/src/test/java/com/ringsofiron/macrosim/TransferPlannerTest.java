package com.ringsofiron.macrosim;

import com.ringsofiron.macrosim.fleet.TransferPlanner;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferPlannerTest {
    static final double MU = 3.986004418e14;

    /** Flies the plan on the stub rails and returns the miss distance and relative speed at arrival. */
    private static double[] fly(double r1, double phase1, double r2, double phase2, int dir) {
        StubRailsModel rails = new StubRailsModel();
        rails.addBody("p", MU);
        rails.attach("ship", "p", StubKeplerOrbit.circular(MU, r1, phase1, 0, dir));
        rails.attach("stn", "p", StubKeplerOrbit.circular(MU, r2, phase2, 0, dir));
        TransferPlanner.Plan plan = TransferPlanner.plan(MU, rails.stateAt("ship", 0, new StateVector()),
                rails.stateAt("stn", 0, new StateVector()), 0, 60);
        assertTrue(plan.burns.get(0).time >= 60);
        for (Burn b : plan.burns) rails.scheduleBurn("ship", b);
        rails.processEventsUntil(plan.arrivalTime);
        StateVector s = rails.stateAt("ship", plan.arrivalTime, new StateVector());
        StateVector t = rails.stateAt("stn", plan.arrivalTime, new StateVector());
        return new double[]{Math.hypot(s.x - t.x, s.y - t.y), Math.hypot(s.vx - t.vx, s.vy - t.vy)};
    }

    @Test
    void hohmannTransferMeetsTheStationOutwardAndInward() {
        for (int dir : new int[]{1, -1}) {
            double[] up = fly(7_000e3, 0.3, 42_164e3, 2.0, dir);
            assertTrue(up[0] < 100, "outward miss " + up[0]);
            assertTrue(up[1] < 0.1, "outward speed " + up[1]);
            double[] down = fly(20_000e3, 5.0, 8_000e3, 1.0, dir);
            assertTrue(down[0] < 100, "inward miss " + down[0]);
            assertTrue(down[1] < 0.1, "inward speed " + down[1]);
        }
    }

    @Test
    void phasingOrbitCatchesAStationOnTheSameOrbit() {
        for (double lead : new double[]{0.2, 3.0, 6.0}) {
            double[] r = fly(9_000e3, 0, 9_000e3, lead, 1);
            assertTrue(r[0] < 100, "lead " + lead + " miss " + r[0]);
            assertTrue(r[1] < 0.1, "lead " + lead + " speed " + r[1]);
        }
    }

    @Test
    void circularizeProducesACircularOrbit() {
        StubKeplerOrbit eccentric = new StubKeplerOrbit(MU, 10_000e3, 0.3, 0.4, 1.0, 0, 1);
        StateVector s = eccentric.stateAt(500, new StateVector());
        Burn b = TransferPlanner.circularize(MU, s, 500);
        StubRailsModel rails = new StubRailsModel();
        rails.addBody("p", MU);
        rails.attach("ship", "p", eccentric);
        rails.scheduleBurn("ship", b);
        rails.processEventsUntil(500);
        assertEquals(0, rails.orbitOf("ship").e, 1e-9);
        assertEquals(s.distance(), rails.orbitOf("ship").a, 1e-3);
    }
}
