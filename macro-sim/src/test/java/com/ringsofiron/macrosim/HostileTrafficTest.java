package com.ringsofiron.macrosim;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent.Role;
import com.ringsofiron.macrosim.hostile.HostileComponent.State;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pirates and patrols on the demo system's rails. */
class HostileTrafficTest {
    private final List<String> plunders = new ArrayList<>();
    private final List<String> intercepts = new ArrayList<>();

    private static String ship(GameEngine engine) {
        Entity e = engine.createEntityWithUuid();
        engine.addEntity(e);
        return e.getComponent(UuidComponent.class).id;
    }

    private static String station(MacroSimulation sim, String name) {
        for (EconomyNode n : sim.stations()) if (n.displayName.equals(name)) return n.stationUuid;
        throw new AssertionError(name);
    }

    private static double distance(MacroSimulation sim, String a, String b) {
        StateVector sa = sim.rails().stateAt(a, sim.now(), new StateVector());
        StateVector sb = sim.rails().stateAt(b, sim.now(), new StateVector());
        return Math.hypot(sa.x - sb.x, sa.y - sb.y);
    }

    private static HostileComponent pirate(String name, double firepower) {
        return new HostileComponent(name, "raider_light", "pirates", Role.PIRATE, firepower);
    }

    private void listen(MacroSimulation sim) {
        sim.hostiles().addListener(new HostileTrafficController.Listener() {
            @Override
            public void intercepting(String hostile, String target, double t) {
                intercepts.add(target);
            }

            @Override
            public void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
                plunders.add(freighter);
            }
        });
    }

    @Test
    void aPirateLurksOnItsStationsOrbitBehindIt() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String depot = station(sim, "Tank Farm Depot");
        String raider = ship(engine);
        sim.addPirate(raider, pirate("Raider", 1), depot);

        HostileComponent h = sim.hostiles().get(raider);
        assertSame(h, engine.getEntity(raider).getComponent(HostileComponent.class));
        assertEquals(State.HOLDING, h.state);
        assertEquals(HostileTrafficController.LURK_OFFSET, distance(sim, raider, depot), 100);
        StateVector s = sim.rails().stateAt(raider, 0, new StateVector());
        StateVector d = sim.rails().stateAt(depot, 0, new StateVector());
        assertEquals(d.distance(), s.distance(), 1, "on the station's orbit");
        sim.advanceTo(3 * 3600);
        assertEquals(HostileTrafficController.LURK_OFFSET, distance(sim, raider, depot), 100, "and stays there");
    }

    @Test
    void piratesTakeLoadedFreightersCargoAsTheyComeInAndTheBooksStillBalance() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        listen(sim);
        List<String> pirates = new ArrayList<>();
        for (EconomyNode n : new ArrayList<>(sim.stations())) {
            String id = ship(engine);
            sim.addPirate(id, pirate("Raider of " + n.displayName, 1), n.stationUuid);
            pirates.add(id);
        }
        int trips = 0;
        for (double t = 0; t <= 10 * 86400 && plunders.isEmpty(); t += 600) sim.advanceTo(t);
        assertFalse(plunders.isEmpty(), "some freighter was robbed");
        MacroLossTest.assertBooksBalance(sim);

        String robbed = plunders.get(0);
        FreighterComponent f = sim.freighters().get(robbed);
        assertNotNull(f, "the freighter lives on");
        assertEquals(0, f.cargoQuantity, 1e-9);
        assertEquals(0, f.orderId, "its order is gone");
        double loot = 0;
        for (String p : pirates) loot += sim.hostiles().get(p).lootQuantity;
        assertTrue(loot > 0, "the pirates hold what they took");

        for (FreighterComponent x : sim.freighters().all().values()) trips += x.tripsCompleted;
        sim.advanceTo(sim.now() + 5 * 86400);
        MacroLossTest.assertBooksBalance(sim);
        int after = 0;
        for (FreighterComponent x : sim.freighters().all().values()) after += x.tripsCompleted;
        assertTrue(after > trips, "trade goes on");
        for (String p : pirates) {
            HostileComponent h = sim.hostiles().get(p);
            assertTrue(h.state == State.HOLDING || h.state == State.INTERCEPTING || h.state == State.AMBUSHING
                    || h.state == State.RETURNING, h.state.name());
        }
    }

    @Test
    void piratesLeaveAloneFreightersBoundForAStationAPatrolThatOutgunsThemGuards() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        listen(sim);
        for (EconomyNode n : new ArrayList<>(sim.stations())) {
            sim.addPatrol(ship(engine), new HostileComponent("Guard", "gunship_heavy", "patrol", Role.PATROL, 10),
                    n.stationUuid);
            sim.addPirate(ship(engine), pirate("Raider", 1), n.stationUuid);
        }
        for (double t = 0; t <= 5 * 86400; t += 600) sim.advanceTo(t);
        assertTrue(plunders.isEmpty(), "every station is guarded");
        assertTrue(intercepts.isEmpty(), "so no pirate went hunting");
        MacroLossTest.assertBooksBalance(sim);
    }

    @Test
    void aPatrolFliesBetweenItsStationsAndHoldsBesideEach() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String depot = station(sim, "Tank Farm Depot");
        String refinery = station(sim, "Crucible Refinery");
        String patrol = ship(engine);
        sim.addPatrol(patrol, new HostileComponent("Patrol", "gunship_heavy", "patrol", Role.PATROL, 10), depot, refinery);
        HostileComponent h = sim.hostiles().get(patrol);
        assertEquals(HostileTrafficController.HOLD_OFFSET, distance(sim, patrol, depot), 100);

        boolean left = false, arrived = false;
        for (double t = 0; t <= 2 * 86400 && !arrived; t += 60) {
            sim.advanceTo(t);
            if (h.state == State.PATROLLING) left = true;
            if (left && h.state == State.HOLDING) arrived = true;
        }
        assertTrue(arrived, "flew its leg");
        assertEquals(refinery, h.stationUuid);
        assertEquals(HostileTrafficController.HOLD_OFFSET, distance(sim, patrol, refinery), 500, "holding beside it");
        boolean back = false;
        for (double t = sim.now(); t <= sim.now() + 2 * 86400 && !back; t += 600) {
            sim.advanceTo(t);
            back = h.state == State.HOLDING && depot.equals(h.stationUuid);
        }
        assertTrue(back, "and back again");
    }

    @Test
    void aBoldPirateInterceptsThePlayerAndATimidOneDoesNot() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        listen(sim);
        // The player sits on the farm's orbit; the pirates lurk by the farm
        String farm = station(sim, "Greenhouse Ring");
        sim.hostiles().playerFirepower = () -> 10;
        String timid = ship(engine);
        sim.addPirate(timid, pirate("Timid", 5), farm);
        sim.advanceTo(2 * HostileTrafficController.LOOK_INTERVAL);
        assertFalse(intercepts.contains(HostileComponent.PLAYER), "outgunned by the player");

        String bold = ship(engine);
        sim.addPirate(bold, pirate("Bold", 9), farm);
        HostileComponent h = sim.hostiles().get(bold);
        for (double t = sim.now(); t <= sim.now() + 2 * HostileTrafficController.LOOK_INTERVAL && h.state == State.HOLDING; t += 60) {
            sim.advanceTo(t);
        }
        assertEquals(State.INTERCEPTING, h.state);
        assertTrue(h.huntingPlayer());
        assertEquals(List.of(HostileComponent.PLAYER), intercepts.stream().filter(HostileComponent.PLAYER::equals).toList(),
                "only one pirate goes after the player at a time");

        // The rendezvous brings it into the bubble
        double arrival = h.eventTime;
        for (double t = sim.now(); t <= arrival + 60 && h.state != State.IN_BUBBLE; t += 10) sim.advanceTo(t);
        assertEquals(State.IN_BUBBLE, h.state, "met the player");
        assertTrue(sim.bubble().inBubble().contains(bold));
    }

    @Test
    void piratesLeaveThePlayerAloneDuringTheGrace() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        listen(sim);
        sim.hostiles().playerFirepower = () -> 1;
        sim.hostiles().playerGraceUntil = 86400;
        String bold = ship(engine);
        sim.addPirate(bold, pirate("Bold", 9), station(sim, "Greenhouse Ring"));
        for (double t = 0; t < 86400 - 3600; t += 600) sim.advanceTo(t);
        assertFalse(intercepts.contains(HostileComponent.PLAYER));
    }

    @Test
    void leavingTheBubbleAPirateGoesHomeToRest() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String farm = station(sim, "Greenhouse Ring");
        String raider = ship(engine);
        sim.addPirate(raider, pirate("Raider", 1), farm);
        HostileComponent h = sim.hostiles().get(raider);
        sim.hostiles().onEnterBubble(raider);
        assertEquals(State.IN_BUBBLE, h.state);
        assertTrue(h.eventTime < 0, "nothing scheduled while the bubble flies it");
        sim.hostiles().breakOff(raider, 100);
        sim.hostiles().onExitBubble(raider, 100);
        assertEquals(State.RETURNING, h.state);
        assertFalse(h.brokeOff);
        assertTrue(h.restUntil >= 100 + HostileTrafficController.REST);
        for (double t = 100; t <= 86400 && h.state != State.HOLDING; t += 60) sim.advanceTo(t);
        assertEquals(State.HOLDING, h.state);
        assertEquals(HostileTrafficController.LURK_OFFSET, distance(sim, raider, farm), 500);
    }

    @Test
    void destroyedHostilesAndLostStationsLeaveNothingBehind() {
        GameEngine engine = new GameEngine();
        MacroSimulation sim = MacroSimDemo.build(engine);
        String depot = station(sim, "Tank Farm Depot");
        String refinery = station(sim, "Crucible Refinery");
        String raider = ship(engine);
        sim.addPirate(raider, pirate("Raider", 1), depot);
        String patrol = ship(engine);
        sim.addPatrol(patrol, new HostileComponent("Patrol", "gunship_heavy", "patrol", Role.PATROL, 10), depot, refinery);

        assertTrue(sim.destroy(depot));
        assertNotEquals(depot, sim.hostiles().get(raider).stationUuid, "lurks elsewhere");
        assertArrayEquals(new String[]{refinery}, sim.hostiles().get(patrol).route);
        sim.advanceTo(86400);

        assertTrue(sim.destroy(raider));
        assertNull(sim.hostiles().get(raider));
        assertNull(engine.getEntity(raider));
        assertFalse(sim.rails().isOnRails(raider));
        assertFalse(sim.destroy(raider), "only once");
        sim.advanceTo(2 * 86400);
    }

    @Test
    void savesKeepPiratesAndPatrolsAndTheirPlans() {
        GameEngine engine = new GameEngine();
        OrbitalRailsModel rails = OrbitalRailsModel.standalone(engine, 0);
        Entity planet = engine.createEntityWithUuid();
        planet.add(new CelestialBodyComponent("Terra", MacroSimDemo.EARTH_MU, 6_371e3, Double.POSITIVE_INFINITY));
        engine.addEntity(planet);
        String planetUuid = planet.getComponent(UuidComponent.class).id;
        MacroSimulation sim = MacroSimDemo.build(engine, rails, planetUuid);
        MacroSaveParticipant.register(sim, engine.getSerializationService());
        listen(sim);
        List<String> pirates = new ArrayList<>();
        for (EconomyNode n : new ArrayList<>(sim.stations())) {
            String id = ship(engine);
            sim.addPirate(id, pirate("Raider", 1), n.stationUuid);
            pirates.add(id);
        }
        String patrol = ship(engine);
        sim.addPatrol(patrol, new HostileComponent("Patrol", "gunship_heavy", "patrol", Role.PATROL, 10),
                station(sim, "Tank Farm Depot"), station(sim, "Crucible Refinery"));
        String hunter = null;
        for (double t = 0; t <= 5 * 86400 && hunter == null; t += 600) {
            sim.advanceTo(t);
            for (String p : pirates) if (sim.hostiles().get(p).state == State.INTERCEPTING) hunter = p;
        }
        assertNotNull(hunter, "a pirate went hunting");
        HostileComponent before = sim.hostiles().get(hunter);
        String target = before.targetUuid;
        double due = before.eventTime;

        String save = engine.getSerializationService().saveGame();
        engine.getSerializationService().loadGame(save);
        HostileComponent after = sim.hostiles().get(hunter);
        assertNotNull(after);
        assertNotSame(before, after, "re-attached from the save");
        assertSame(after, engine.getEntity(hunter).getComponent(HostileComponent.class));
        assertEquals(State.INTERCEPTING, after.state);
        assertEquals(target, after.targetUuid);
        assertEquals(due, after.eventTime, 1e-6);
        assertEquals(pirates.size() + 1, sim.hostiles().all().size());
        assertEquals(Role.PATROL, sim.hostiles().get(patrol).role);
        assertEquals(2, sim.hostiles().get(patrol).route.length);

        for (double t = sim.now(); t <= due + 86400 && after.state == State.INTERCEPTING; t += 600) sim.advanceTo(t);
        assertNotEquals(State.INTERCEPTING, after.state, "the loaded plan plays out");
        MacroLossTest.assertBooksBalance(sim);
    }
}
