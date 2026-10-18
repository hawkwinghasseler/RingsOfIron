package com.ringsofiron.macrosim;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroSimulationTest {

    @Test
    void freightersCompleteTradeRunsAndArriveOnTheirStations() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        double worstMiss = 0;
        for (double t = 0; t <= 10 * 86400; t += 600) {
            sim.advanceTo(t);
            for (FreighterComponent f : sim.freighters().all().values()) worstMiss = Math.max(worstMiss, f.lastArrivalMiss);
        }
        int trips = 0;
        double credits = 0;
        for (FreighterComponent f : sim.freighters().all().values()) {
            trips += f.tripsCompleted;
            credits += f.credits;
        }
        assertTrue(trips >= 10, "trips " + trips);
        assertTrue(credits > 10 * 20_000, "the fleet made money: " + credits);
        assertTrue(worstMiss < 1_000, "worst arrival miss " + worstMiss + " m");
        // Every station and freighter is an ECS entity with a UUID.
        assertEquals(17, engine.getEcsEngine().getEntities().size());
    }

    @Test
    void oneBigWarpJumpMatchesManySmallSteps() {
        MacroSimulation coarse = MacroSimDemo.build(new GameEngine());
        MacroSimulation fine = MacroSimDemo.build(new GameEngine());
        coarse.advanceTo(3 * 86400);
        for (double t = 0; t <= 3 * 86400; t += 60) fine.advanceTo(t);
        var a = coarse.stations().iterator();
        var b = fine.stations().iterator();
        while (a.hasNext()) {
            var na = a.next();
            var nb = b.next();
            for (String c : na.inventory.keySet()) assertEquals(nb.stock(c), na.stock(c), 1e-6, na.displayName + " " + c);
        }
    }

    @Test
    void backgroundPredictionFeedsTheBubble() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            double mu = MacroSimDemo.EARTH_MU;
            StubRailsModel rails = new StubRailsModel();
            rails.addBody("p", mu);
            StubKeplerOrbit anchor = StubKeplerOrbit.circular(mu, 8_000e3, 0, 0, 1);
            var gateway = new com.ringsofiron.macrosim.bubble.MockBubbleGateway("p", anchor);
            MacroSimulation sim = new MacroSimulation(new GameEngine(), rails, gateway, new DefaultCommodityCatalog(), exec, 0);
            sim.registerMassiveBody("p");
            String stn = sim.addStation("Close Station", "farm", "p",
                    StubKeplerOrbit.circular(mu, 8_000e3, 10e3 / 8_000e3, 0, 1).stateAt(0, new StateVector()), 1);
            String ship = sim.addFreighter("Neighbour", 100, 0, stn);
            for (int i = 0; i < 600 && !sim.bubble().inBubble().contains(ship); i++) {
                sim.advanceTo(i / 60.0);
                Thread.sleep(1);
            }
            assertNotNull(sim.bubble().forecast());
            assertTrue(sim.bubble().inBubble().contains(ship), "docked freighter 10 km away joins the bubble");
            assertTrue(gateway.transients().contains(stn), "stations stay on rails as kinematic transients");
            assertEquals(FreighterComponent.State.IN_BUBBLE, sim.freighters().get(ship).state);
            // The station was inside the radius from the start, so it does not stop rails warp.
            assertEquals(Double.POSITIVE_INFINITY, sim.nextDropOutTime());
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void engineTicksAndRailsWarpDriveTheSimulation() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        engine.addSystem(new MacroSimSystem(sim, engine));
        engine.stepTicks(600);
        assertEquals(10.0, sim.now(), 1e-9);
        assertTrue(engine.advanceRails(com.ringsofiron.coreengine.SimClock.secondsToTicks(2 * 86400)));
        assertEquals(engine.getSimSeconds(), sim.now(), 1e-9);
        int trips = 0;
        for (FreighterComponent f : sim.freighters().all().values()) trips += f.tripsCompleted;
        assertTrue(trips > 0, "freighters traded during the warp jump");
    }
}
