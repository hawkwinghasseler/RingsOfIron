package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.fleet.RoutePlanner;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.OrbitMath;
import com.ringsofiron.orbital.rails.RailsUniverse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freighters hauling between stations around Earth and around the Moon. */
class LunarRoutesTest {
    static final double MU_EARTH = MacroSimDemo.EARTH_MU;
    static final double R_EARTH = 6_371e3;
    static final double MU_MOON = 4.9048695e12;
    static final double R_MOON = 1.7374e6;
    static final double MOON_A = 3.844e8;

    final GameEngine engine = new GameEngine();
    final OrbitalRailsModel rails;
    final MacroSimulation sim;
    final String earth;
    final String moon;
    final String habitat;

    /** Set up as GameWorld does: one event system and clock shared by the game and macro-sim. */
    LunarRoutesTest() {
        RailsUniverse universe = new RailsUniverse(engine.getEcsEngine(), engine.getUuidRegistry(), engine::getSimSeconds);
        OrbitalEventSystem events = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(events);
        rails = new OrbitalRailsModel(engine, universe, events);
        sim = new MacroSimulation(engine, rails, null, new DefaultCommodityCatalog(), null, 0);
        engine.addSystem(new MacroSimSystem(sim, engine));

        Entity e = engine.createEntityWithUuid();
        e.add(new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        engine.addEntity(e);
        earth = e.getComponent(UuidComponent.class).id;
        Entity m = engine.createEntityWithUuid();
        m.add(new CelestialBodyComponent("Moon", MU_MOON, R_MOON, OrbitMath.soiRadius(MOON_A, MU_MOON, MU_EARTH)));
        m.add(new OrbitComponent(earth, KeplerOrbit.circular(MU_EARTH, MOON_A, 2.0, 0, 1)));
        engine.addEntity(m);
        moon = m.getComponent(UuidComponent.class).id;
        sim.registerMassiveBody(earth);
        sim.registerMassiveBody(moon);

        // Water and food are made only at the Moon; the habitat that eats them is around Earth.
        habitat = sim.addStation("Low Earth Habitat", "habitat", earth, circular(MU_EARTH, 7_000e3, 0), 0.4);
        sim.addStation("Fuel Depot", "fuel_depot", earth, circular(MU_EARTH, 7_400e3, 2), 0.4);
        String ice = sim.addStation("Shackleton Ice", "ice_mine", moon, circular(MU_MOON, R_MOON + 100e3, 0), 1);
        sim.addStation("Lunar Farm", "farm", moon, circular(MU_MOON, R_MOON + 300e3, 3), 1);
        for (int i = 0; i < 3; i++) sim.addFreighter("Earth Hauler " + i, 400, 20_000, habitat);
        for (int i = 0; i < 2; i++) sim.addFreighter("Moon Hauler " + i, 400, 20_000, ice);
    }

    static StateVector circular(double mu, double r, double angle) {
        com.ringsofiron.orbital.math.StateVector s = KeplerOrbit.circular(mu, r, angle, 0, 1).stateAt(0);
        return new StateVector(s.x, s.y, s.vx, s.vy);
    }

    /** Rails-warps to {@code end}, counting each freighter's crossings between Earth and Moon. */
    int run(double end, Map<String, String> lastParent) {
        int crossings = 0;
        while (engine.getSimSeconds() < end - 1e-6) {
            assertTrue(engine.advanceRails(SimClock.secondsToTicks(Math.min(600, end - engine.getSimSeconds()))));
            double water = sim.station(habitat).stock("water");
            if (water > lastWater + 1) waterDelivered = true;
            lastWater = water;
            for (String uuid : sim.freighters().all().keySet()) {
                String parent = rails.parentOf(uuid);
                String before = lastParent.put(uuid, parent);
                if (before != null && !before.equals(parent)) crossings++;
            }
        }
        return crossings;
    }

    double lastWater = Double.POSITIVE_INFINITY;
    boolean waterDelivered;

    @Test
    void freightersHaulBetweenEarthAndTheMoon() {
        int crossings = run(40 * 86400, new HashMap<>());
        int trips = 0;
        double worstMiss = 0;
        for (FreighterComponent f : sim.freighters().all().values()) {
            trips += f.tripsCompleted;
            worstMiss = Math.max(worstMiss, f.lastArrivalMiss);
        }
        assertEquals(5, sim.freighters().all().size(), "nobody crashed");
        assertTrue(crossings >= 4, "crossings " + crossings);
        assertTrue(trips >= 3, "trips " + trips);
        assertTrue(worstMiss < 1_000, "worst arrival miss " + worstMiss + " m");
        assertTrue(waterDelivered, "lunar water reaches the habitat");
        MacroLossTest.assertBooksBalance(sim);
    }

    @Test
    void aSaveMidTransferReplaysTheSameFuture() {
        MacroSaveParticipant.register(sim, engine.getSerializationService());
        boolean inTransit = false;
        while (!inTransit && engine.getSimSeconds() < 30 * 86400) {
            run(engine.getSimSeconds() + 600, new HashMap<>());
            for (FreighterComponent f : sim.freighters().all().values()) inTransit |= f.transitBodyUuid != null;
        }
        double t = engine.getSimSeconds();
        assertTrue(inTransit, "some freighter set out for the other body");
        String save = engine.getSerializationService().saveGame();
        run(t + 10 * 86400, new HashMap<>());
        List<Double> straight = fingerprint();

        engine.getSerializationService().loadGame(save);
        assertEquals(t, engine.getSimSeconds(), 1e-6);
        run(t + 10 * 86400, new HashMap<>());
        List<Double> reloaded = fingerprint();
        assertEquals(straight.size(), reloaded.size());
        for (int i = 0; i < straight.size(); i++) {
            assertEquals(straight.get(i), reloaded.get(i), 1e-6 * Math.max(1, Math.abs(straight.get(i))), "value " + i);
        }
    }

    /**
     * Oct 2026: a ship just undocked in low orbit, 0.0006 off circular, got "No transfer" to the Moon:
     * the burn out was sized for a circular orbit, and the few m/s the ship was off put the far end
     * thousands of km short of the Moon from every start searched. One orbit in five a little off
     * circular had no route; from anywhere on them there is one now.
     */
    @Test
    void aShipALittleOffCircularFindsARouteToTheMoon() {
        String ice = null;
        for (EconomyNode n : sim.stations()) if ("Shackleton Ice".equals(n.displayName)) ice = n.stationUuid;
        assertNotNull(ice);
        double t = engine.getSimSeconds();
        StringBuilder none = new StringBuilder();
        for (double e : new double[]{0.0002, 0.0006, 0.001, 0.003}) {
            // The periapsis turned round an eighth at a time, and the ship an eighth at a time along the orbit
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    KeplerOrbit k = new KeplerOrbit(MU_EARTH, 6_767e3, e, i * Math.PI / 4, j * Math.PI / 4, t, 1);
                    com.ringsofiron.orbital.math.StateVector s = k.stateAt(t);
                    RoutePlanner.Route route = RoutePlanner.plan(rails, earth, new StateVector(s.x, s.y, s.vx, s.vy), ice, t, t + 300);
                    if (route == null) none.append(String.format(java.util.Locale.ROOT, " e %.4f w %d/8 M %d/8;", e, i, j));
                    else assertEquals(moon, route.nextParent);
                }
            }
        }
        assertEquals("", none.toString(), "no route from");
    }

    private List<Double> fingerprint() {
        List<Double> out = new ArrayList<>();
        for (EconomyNode n : sim.stations()) for (String c : n.inventory.keySet()) out.add(n.stock(c));
        for (FreighterComponent f : sim.freighters().all().values()) {
            out.add(f.credits);
            out.add((double) f.tripsCompleted);
            out.add(f.deltaVUsed);
        }
        return out;
    }
}
