package com.ringsofiron.macrosim;

import com.ringsofiron.macrosim.bubble.BubbleTransitionSystem;
import com.ringsofiron.macrosim.bubble.EncounterForecast;
import com.ringsofiron.macrosim.bubble.EncounterPredictionTask;
import com.ringsofiron.macrosim.bubble.MockBubbleGateway;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BubbleTransitionTest {
    static final double MU = 3.986004418e14;
    static final double R = 7_000e3;

    private final StubRailsModel rails = new StubRailsModel();
    private final StubKeplerOrbit anchor = StubKeplerOrbit.circular(MU, R, 0, 0, 1);
    private final MockBubbleGateway gateway = new MockBubbleGateway("p", anchor);
    private final BubbleTransitionSystem bubble = new BubbleTransitionSystem(rails, gateway);

    BubbleTransitionTest() {
        rails.addBody("p", MU);
    }

    private EncounterForecast predict(double t, double horizon) {
        return new EncounterPredictionTask(rails.snapshot(), anchor, "p", Set.of(), t, horizon,
                BubbleTransitionSystem.ENTER_RADIUS).call();
    }

    private void step(double from, double to, double dt) {
        for (double t = from; t <= to; t += dt) {
            bubble.setForecast(predict(t, 60));
            bubble.update(t, 1);
        }
    }

    @Test
    void slowNeighbourEntersBoxTwoDAndLeavesOnTheSameTrajectory() {
        // 15 km behind the anchor on the same circular orbit: inside the bubble and co-moving.
        rails.attach("npc", "p", StubKeplerOrbit.circular(MU, R, -15e3 / R, 0, 1));
        rails.scheduleBurn("npc", new Burn(5_000, 3, 0));
        step(0, 1, 1);
        assertTrue(bubble.inBubble().contains("npc"));
        assertFalse(rails.isOnRails("npc"));
        assertTrue(gateway.hasBody("npc"));

        // The in-bubble pilot thrusts away; at 25 km it must hand back to rails exactly.
        gateway.kick("npc", 1, 0, -8);
        double t = 1;
        while (bubble.inBubble().contains("npc")) {
            t += 1;
            StateVector expected = gateway.trueStateOf("npc", t, new StateVector());
            bubble.update(t, 1);
            if (!bubble.inBubble().contains("npc")) {
                StateVector got = rails.stateAt("npc", t + 600, new StateVector());
                StateVector want = StubKeplerOrbit.fromState(MU, expected, t).stateAt(t + 600, new StateVector());
                assertEquals(want.x, got.x, 1e-3);
                assertEquals(want.y, got.y, 1e-3);
            }
            assertTrue(t < 10_000, "never left the bubble");
        }
        assertTrue(new StateVector().setDifference(rails.stateAt("npc", t, new StateVector()),
                anchor.stateAt(t, new StateVector())).distance() > BubbleTransitionSystem.EXIT_RADIUS);
        assertEquals(1, gateway.spawns);
        assertEquals(1, gateway.destroys);
    }

    @Test
    void retrogradeCrossingIsAFastPassAndStaysOnRails() {
        // Same radius, opposite direction: closes at about 15 km/s.
        rails.attach("debris", "p", StubKeplerOrbit.circular(MU, R, 0.05, 0, -1));
        EncounterForecast f = predict(0, 3600);
        double entry = f.entries.get("debris");
        // Closing speed 2v, entry when the arc gap shrinks to the radius.
        double v = Math.sqrt(MU / R);
        assertEquals((0.05 * R - BubbleTransitionSystem.ENTER_RADIUS) / (2 * v), entry, 2.0);

        boolean sawPass = false;
        for (double t = entry - 1; t < entry + 10; t += 1.0 / 60) {
            bubble.setForecast(f);
            bubble.update(t, 1);
            sawPass |= gateway.transients().contains("debris");
            assertTrue(rails.isOnRails("debris"));
            assertFalse(bubble.inBubble().contains("debris"));
        }
        assertTrue(sawPass);
        assertTrue(gateway.transients().isEmpty(), "fast pass ended after leaving");
    }

    @Test
    void predictionMatchesBruteForceSampling() {
        // A slightly lower orbit that slowly overtakes the anchor from behind.
        rails.attach("tug", "p", StubKeplerOrbit.circular(MU, R - 10e3, -0.02, 0, 1));
        double horizon = 6 * 3600;
        EncounterForecast f = predict(0, horizon);
        double brute = Double.POSITIVE_INFINITY;
        StateVector a = new StateVector(), b = new StateVector();
        for (double t = 0; t < horizon; t += 0.5) {
            rails.stateAt("tug", t, a);
            anchor.stateAt(t, b);
            if (Math.hypot(a.x - b.x, a.y - b.y) <= BubbleTransitionSystem.ENTER_RADIUS) {
                brute = t;
                break;
            }
        }
        assertTrue(brute < horizon, "scenario must have an encounter");
        assertTrue(f.entries.get("tug") <= brute + 1e-6, "prediction never later than the real entry");
        assertEquals(brute, f.entries.get("tug"), 1.0, "and close to it");
        assertEquals(f.entries.get("tug"), f.earliestEntry);
    }

    @Test
    void bodiesAlreadyInsideAreCandidatesButDoNotStopWarp() {
        rails.attach("near", "p", StubKeplerOrbit.circular(MU, R, 5e3 / R, 0, 1));
        rails.attach("tug", "p", StubKeplerOrbit.circular(MU, R - 10e3, -0.02, 0, 1));
        EncounterForecast f = predict(0, 6 * 3600);
        assertEquals(0, f.entries.get("near"), 1e-9);
        assertTrue(f.alreadyInside.contains("near"));
        assertFalse(f.alreadyInside.contains("tug"));
        assertEquals(f.entries.get("tug"), f.earliestEntry, "warp drops out for the approaching tug only");
    }

    @Test
    void bodiesOnADifferentRingAreCulledWithoutSampling() {
        rails.attach("far", "p", StubKeplerOrbit.circular(MU, 40_000e3, 0, 0, 1));
        assertTrue(predict(0, 86400 * 30).entries.isEmpty());
    }
}
