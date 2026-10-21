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
    void freightersDockedInTheBubbleTakeOrdersAndTradeWhereTheyBerth() {
        MacroSimulation sim = MacroSimDemo.build(new GameEngine());
        // Every freighter sits at its station inside the bubble
        for (String id : sim.freighters().all().keySet()) sim.freighters().onEnterBubble(id);
        String ship = null;
        for (double t = 0; t <= 86400 && ship == null; t += 600) {
            sim.advanceTo(t);
            for (var e : sim.freighters().all().entrySet()) {
                if (e.getValue().destinationUuid != null) ship = e.getKey();
            }
        }
        assertNotNull(ship, "one took an order");
        FreighterComponent f = sim.freighters().get(ship);
        assertEquals(FreighterComponent.State.IN_BUBBLE, f.state, "the bubble flies it, not the rails");
        assertTrue(f.resumeState == FreighterComponent.State.TO_PICKUP || f.resumeState == FreighterComponent.State.TO_DELIVERY);
        sim.freighters().undockedInBubble(ship);
        assertEquals(null, f.dockedAtUuid);
        if (f.resumeState == FreighterComponent.State.TO_PICKUP) {
            sim.freighters().berthedInBubble(ship, f.destinationUuid);
            assertEquals(FreighterComponent.State.TO_DELIVERY, f.resumeState, "loaded where it berthed");
            assertTrue(f.cargoQuantity > 0);
            sim.freighters().undockedInBubble(ship);
        }
        String to = f.destinationUuid;
        sim.freighters().berthedInBubble(ship, to);
        assertEquals(FreighterComponent.State.IDLE, f.resumeState);
        assertEquals(to, f.dockedAtUuid);
        assertEquals(1, f.tripsCompleted, "delivered");
        assertEquals(0, f.cargoQuantity, 1e-9);
    }

    @Test
    void aFreighterNearItsStationWhenRailsWarpCollapsesTheBubbleArrives() {
        // Round 10, seed 17: rails warp collapsed the bubble with an Ore Barge flying in to Selene
        // Forge; it coasted on through the warp and was re-planned round a 28-day phasing loop
        MacroSimulation sim = MacroSimDemo.build(new GameEngine());
        String ship = null;
        FreighterComponent f = null;
        double t = 0;
        for (; t <= 10 * 86400 && ship == null; t += 60) {
            sim.advanceTo(t);
            for (var e : sim.freighters().all().entrySet()) {
                FreighterComponent c = e.getValue();
                if (c.state == FreighterComponent.State.TO_DELIVERY && c.transitBodyUuid == null && !Double.isNaN(c.arrivalTime)
                        && c.arrivalTime - t < 120 && c.arrivalTime > t) {
                    ship = e.getKey();
                    f = c;
                }
            }
        }
        assertNotNull(ship, "a freighter about to deliver");
        String to = f.destinationUuid;
        int trips = f.tripsCompleted;
        sim.freighters().onEnterBubble(ship);
        sim.freighters().arriveFromBubble(ship, t);
        assertEquals(to, f.dockedAtUuid, "it berths where it was going");
        assertEquals(trips + 1, f.tripsCompleted, "and delivers");
        assertEquals(FreighterComponent.State.IDLE, f.state);
    }

    @Test
    void aFreighterFarFromItsStationWhenRailsWarpCollapsesTheBubbleFliesOn() {
        MacroSimulation sim = MacroSimDemo.build(new GameEngine());
        String ship = null;
        FreighterComponent f = null;
        for (double t = 0; t <= 10 * 86400 && ship == null; t += 600) {
            sim.advanceTo(t);
            for (var e : sim.freighters().all().entrySet()) {
                FreighterComponent c = e.getValue();
                if (c.state == FreighterComponent.State.TO_DELIVERY && c.arrivalTime - t > 86400) {
                    ship = e.getKey();
                    f = c;
                }
            }
        }
        assertNotNull(ship, "a freighter a day out from delivering");
        int trips = f.tripsCompleted;
        sim.freighters().onEnterBubble(ship);
        sim.freighters().arriveFromBubble(ship, sim.now());
        assertEquals(FreighterComponent.State.IN_BUBBLE, f.state, "still out in the bubble");
        assertEquals(trips, f.tripsCompleted);
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
    void backgroundPredictionGivesTheSameForecastsHoweverLongTheThreadTakes() throws Exception {
        assertEquals(bubbleLog(0), bubbleLog(0), "a quick thread replays");
        assertEquals(bubbleLog(0), bubbleLog(15), "a slow thread is waited for, not skipped");
    }

    /**
     * The forecast in use and who is in the bubble after each of 240 steps of 1/60 s, with each
     * background prediction held up by up to {@code maxDelayMs}.
     */
    private static java.util.List<String> bubbleLog(int maxDelayMs) throws Exception {
        java.util.Random delays = new java.util.Random(maxDelayMs);
        ExecutorService exec = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>()) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                try {
                    Thread.sleep(maxDelayMs == 0 ? 0 : delays.nextInt(maxDelayMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        try {
            double mu = MacroSimDemo.EARTH_MU;
            StubRailsModel rails = new StubRailsModel();
            rails.addBody("p", mu);
            StubKeplerOrbit anchor = StubKeplerOrbit.circular(mu, 8_000e3, 0, 0, 1);
            var gateway = new com.ringsofiron.macrosim.bubble.MockBubbleGateway("p", anchor);
            GameEngine engine = new GameEngine();
            engine.getEntityIds().seed(42);
            MacroSimulation sim = new MacroSimulation(engine, rails, gateway, new DefaultCommodityCatalog(), exec, 0);
            sim.registerMassiveBody("p");
            String stn = sim.addStation("Close Station", "farm", "p",
                    StubKeplerOrbit.circular(mu, 8_000e3, 10e3 / 8_000e3, 0, 1).stateAt(0, new StateVector()), 1);
            sim.addFreighter("Neighbour", 100, 0, stn);
            java.util.List<String> log = new java.util.ArrayList<>();
            for (int i = 0; i < 240; i++) {
                sim.advanceTo(i / 60.0);
                var f = sim.bubble().forecast();
                log.add(i + " " + (f == null ? "-" : f.from + " " + f.entries.keySet()) + " " + sim.bubble().inBubble());
            }
            return log;
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
