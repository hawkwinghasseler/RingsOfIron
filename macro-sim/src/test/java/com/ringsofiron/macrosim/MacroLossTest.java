package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroLossTest {

    /** Reservations and expected deliveries at every station match the claimed orders exactly. */
    private static void assertBooksBalance(MacroSimulation sim) {
        Map<String, Map<String, Double>> out = new HashMap<>();
        Map<String, Map<String, Double>> in = new HashMap<>();
        for (TradeOrder o : sim.economy().orders()) {
            if (o.claimedBy == null) continue;
            FreighterComponent f = sim.freighters().get(o.claimedBy);
            assertNotNull(f, "order " + o.id + " claimed by a freighter that exists");
            boolean loaded = f.cargoCommodity != null;
            if (!loaded) out.computeIfAbsent(o.fromUuid, k -> new HashMap<>()).merge(o.commodity, o.quantity, Double::sum);
            in.computeIfAbsent(o.toUuid, k -> new HashMap<>()).merge(o.commodity, loaded ? f.cargoQuantity : o.quantity, Double::sum);
        }
        for (EconomyNode n : sim.stations()) {
            for (String c : sim.economy().catalog().commodities().stream().map(x -> x.id).toArray(String[]::new)) {
                assertEquals(out.getOrDefault(n.stationUuid, Map.of()).getOrDefault(c, 0.0),
                        n.reservedOut.getOrDefault(c, 0.0), 1e-6, n.displayName + " reserved " + c);
                assertEquals(in.getOrDefault(n.stationUuid, Map.of()).getOrDefault(c, 0.0),
                        n.incoming.getOrDefault(c, 0.0), 1e-6, n.displayName + " incoming " + c);
            }
        }
    }

    private static void assertGone(GameEngine engine, MacroSimulation sim, String uuid) {
        assertNull(sim.station(uuid));
        assertNull(sim.freighters().get(uuid));
        assertNull(engine.getEntity(uuid), "entity removed");
        assertFalse(sim.rails().isOnRails(uuid));
        for (TradeOrder o : sim.economy().orders()) {
            assertNotEquals(uuid, o.fromUuid);
            assertNotEquals(uuid, o.toUuid);
            assertNotEquals(uuid, o.claimedBy);
        }
        for (FreighterComponent f : sim.freighters().all().values()) {
            assertNotEquals(uuid, f.destinationUuid);
            assertNotEquals(uuid, f.dockedAtUuid);
        }
    }

    /** Runs until some freighter is in {@code state}, returning its UUID. */
    private static String runUntil(MacroSimulation sim, FreighterComponent.State state) {
        for (double t = sim.now(); t < sim.now() + 10 * 86400; t += 600) {
            sim.advanceTo(t);
            for (Map.Entry<String, FreighterComponent> e : sim.freighters().all().entrySet()) {
                if (e.getValue().state == state) return e.getKey();
            }
        }
        throw new AssertionError("no freighter ever reached " + state);
    }

    private static int trips(MacroSimulation sim) {
        int n = 0;
        for (FreighterComponent f : sim.freighters().all().values()) n += f.tripsCompleted;
        return n;
    }

    @Test
    void losingTheStationAFreighterIsHeadingForReroutesItAndKeepsTheBooks() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String ship = runUntil(sim, FreighterComponent.State.TO_PICKUP);
        assertBooksBalance(sim);
        FreighterComponent f = sim.freighters().get(ship);
        String lost = f.destinationUuid;
        int entities = engine.getEcsEngine().getEntities().size();

        assertTrue(sim.destroy(lost));
        assertGone(engine, sim, lost);
        assertBooksBalance(sim);
        assertEquals(entities - 1, engine.getEcsEngine().getEntities().size());
        assertEquals(0, f.orderId);

        int before = trips(sim);
        sim.advanceTo(sim.now() + 5 * 86400);
        assertGone(engine, sim, lost);
        assertBooksBalance(sim);
        assertTrue(trips(sim) > before, "trade goes on without it");
    }

    @Test
    void aLostFreighterTakesItsCargoAndFreesItsOrder() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String ship = runUntil(sim, FreighterComponent.State.TO_DELIVERY);
        FreighterComponent f = sim.freighters().get(ship);
        String buyer = f.destinationUuid;
        String commodity = f.cargoCommodity;
        double stockBefore = sim.station(buyer).stock(commodity);
        int fleet = sim.freighters().all().size();

        assertTrue(sim.destroy(ship));
        assertGone(engine, sim, ship);
        assertBooksBalance(sim);
        assertEquals(fleet - 1, sim.freighters().all().size());
        assertEquals(stockBefore, sim.station(buyer).stock(commodity), 1e-9, "cargo never arrives");
        assertFalse(sim.destroy(ship), "only once");
    }

    @Test
    void aStationThatFallsIntoThePlanetIsDestroyedAndSavesStillLoad() {
        GameEngine engine = new GameEngine();
        OrbitalRailsModel rails = OrbitalRailsModel.standalone(engine, 0);
        Entity planet = engine.createEntityWithUuid();
        planet.add(new CelestialBodyComponent("Terra", MacroSimDemo.EARTH_MU, 6_371e3, Double.POSITIVE_INFINITY));
        engine.addEntity(planet);
        String planetUuid = planet.getComponent(UuidComponent.class).id;
        MacroSimulation sim = MacroSimDemo.build(engine, rails, planetUuid);
        MacroSaveParticipant.register(sim, engine.getSerializationService());

        // Half orbital speed at 6,600 km: it falls to the surface within the hour.
        double r = 6_600e3;
        String doomed = sim.addStation("Sinking Platform", "farm", planetUuid,
                new StateVector(r, 0, 0, 0.5 * Math.sqrt(MacroSimDemo.EARTH_MU / r)), 1);
        sim.advanceTo(3600);
        assertGone(engine, sim, doomed);
        assertBooksBalance(sim);

        String save = engine.getSerializationService().saveGame();
        sim.advanceTo(3 * 86400);
        assertBooksBalance(sim);
        engine.getSerializationService().loadGame(save);
        assertNull(sim.station(doomed));
        sim.advanceTo(3 * 86400);
        assertBooksBalance(sim);
    }
}
