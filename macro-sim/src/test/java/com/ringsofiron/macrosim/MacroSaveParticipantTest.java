package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomyNodeComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroSaveParticipantTest {

    private final GameEngine engine = new GameEngine();
    private final MacroSimulation sim;

    MacroSaveParticipantTest() {
        RailsUniverse universe = new RailsUniverse(engine.getEcsEngine(), engine.getUuidRegistry(), engine::getSimSeconds);
        OrbitalEventSystem events = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(events);
        OrbitalRailsModel rails = new OrbitalRailsModel(engine, universe, events);
        Entity planet = engine.createEntityWithUuid();
        planet.add(new CelestialBodyComponent("Terra", MacroSimDemo.EARTH_MU, 6_371e3, Double.POSITIVE_INFINITY));
        engine.addEntity(planet);
        sim = MacroSimDemo.build(engine, rails, planet.getComponent(UuidComponent.class).id);
        engine.addSystem(new MacroSimSystem(sim, engine));
        MacroSaveParticipant.register(sim, engine.getSerializationService());
    }

    private void warp(double days) {
        assertTrue(engine.advanceRails(SimClock.secondsToTicks(days * 86400)));
    }

    /** Everything the economy and fleet did, flattened for comparison. */
    private List<Double> fingerprint() {
        List<Double> out = new ArrayList<>();
        for (EconomyNode n : sim.stations()) {
            for (String c : n.inventory.keySet()) out.add(n.stock(c));
            out.add(n.shortfallDays);
        }
        for (FreighterComponent f : sim.freighters().all().values()) {
            out.add(f.credits);
            out.add((double) f.tripsCompleted);
            out.add(f.cargoQuantity);
        }
        return out;
    }

    @Test
    void loadingASaveReplaysTheSameFuture() {
        warp(3);
        int busy = 0;
        for (FreighterComponent f : sim.freighters().all().values()) if (f.destinationUuid != null) busy++;
        String save = engine.getSerializationService().saveGame();
        assertTrue(save.contains("\"macro-sim\""));

        warp(3);
        List<Double> straight = fingerprint();

        engine.getSerializationService().loadGame(save);
        assertEquals(3 * 86400, sim.now(), 1e-6);
        warp(3);
        List<Double> reloaded = fingerprint();

        assertEquals(straight.size(), reloaded.size());
        for (int i = 0; i < straight.size(); i++) {
            assertEquals(straight.get(i), reloaded.get(i), 1e-6 * Math.max(1, Math.abs(straight.get(i))), "value " + i);
        }
        assertTrue(busy > 0 || !straight.isEmpty(), "scenario had traffic");
    }

    @Test
    void componentsAreReattachedToTheirEntities() {
        warp(1);
        String save = engine.getSerializationService().saveGame();
        engine.getSerializationService().loadGame(save);
        for (EconomyNode n : sim.stations()) {
            Entity e = engine.getEntity(n.stationUuid);
            assertNotNull(e);
            assertEquals(n, e.getComponent(EconomyNodeComponent.class).node);
        }
        for (var e : sim.freighters().all().entrySet()) {
            assertEquals(e.getValue(), engine.getEntity(e.getKey()).getComponent(FreighterComponent.class));
        }
        assertEquals(7, sim.stations().size());
        assertEquals(10, sim.freighters().all().size());
    }
}
