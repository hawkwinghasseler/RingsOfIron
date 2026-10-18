package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** macro-sim on the real orbital-mechanics rails. */
class OrbitalRailsModelTest {
    static final double MU = MacroSimDemo.EARTH_MU;

    private static String addPlanet(GameEngine engine) {
        Entity planet = engine.createEntityWithUuid();
        planet.add(new CelestialBodyComponent("Terra", MU, 6_371e3, Double.POSITIVE_INFINITY));
        engine.addEntity(planet);
        return planet.getComponent(UuidComponent.class).id;
    }

    private static String addShip(GameEngine engine) {
        Entity ship = engine.createEntityWithUuid();
        engine.addEntity(ship);
        return ship.getComponent(UuidComponent.class).id;
    }

    @Test
    void burnsFireThroughTheOrbitalEventSystem() {
        GameEngine engine = new GameEngine();
        OrbitalRailsModel rails = new OrbitalRailsModel(engine, 0);
        String planet = addPlanet(engine);
        String ship = addShip(engine);
        StateVector s = new StateVector(7_000e3, 0, 0, Math.sqrt(MU / 7_000e3));
        rails.attach(ship, planet, s, 0);
        rails.scheduleBurn(ship, new Burn(100, 50, 0));
        assertEquals(100, rails.nextEventTime(), 1e-9);

        rails.processEventsUntil(200);
        KeplerOrbit expected = KeplerOrbit.circular(MU, 7_000e3, 0, 0, 1).withBurn(100, 50, 0);
        StateVector got = rails.stateAt(ship, 5_000, new StateVector());
        com.ringsofiron.orbital.math.StateVector want = expected.stateAt(5_000);
        assertEquals(want.x, got.x, 1e-3);
        assertEquals(want.y, got.y, 1e-3);

        rails.scheduleBurn(ship, new Burn(1_000, -10, 0));
        List<Burn> goals = rails.detach(ship);
        assertEquals(1, goals.size());
        assertFalse(rails.isOnRails(ship));
        rails.processEventsUntil(2_000); // nothing left to fire for a detached body
        assertTrue(rails.snapshot().entries.isEmpty());
    }

    @Test
    void demoEconomyRunsOnTheOrbitalRails() {
        GameEngine engine = new GameEngine();
        OrbitalRailsModel rails = new OrbitalRailsModel(engine, 0);
        String planet = addPlanet(engine);
        MacroSimulation sim = MacroSimDemo.build(engine, rails, planet);
        double worstMiss = 0;
        for (double t = 0; t <= 10 * 86400; t += 600) {
            sim.advanceTo(t);
            for (FreighterComponent f : sim.freighters().all().values()) worstMiss = Math.max(worstMiss, f.lastArrivalMiss);
        }
        int trips = 0;
        for (FreighterComponent f : sim.freighters().all().values()) trips += f.tripsCompleted;
        assertTrue(trips >= 10, "trips " + trips);
        assertTrue(worstMiss < 1_000, "worst arrival miss " + worstMiss + " m");
    }
}
