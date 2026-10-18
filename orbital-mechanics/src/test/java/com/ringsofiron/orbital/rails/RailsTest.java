package com.ringsofiron.orbital.rails;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.RailsWarpGuard;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.events.SoiChange;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.OrbitMath;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.EncounterPredictor;
import com.ringsofiron.orbital.warp.TimeWarpController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Rails hierarchy, events, SOI transitions and rails warp, with Earth as the root and the Moon orbiting it. */
class RailsTest {
    static final double MU_EARTH = 3.986004418e14;
    static final double R_EARTH = 6.371e6;
    static final double MU_MOON = 4.9048695e12;
    static final double MOON_A = 3.844e8;
    static final double LEO = R_EARTH + 400e3;

    GameEngine game;
    RailsUniverse universe;
    OrbitalEventQueue queue;
    OrbitalEventSystem events;
    Entity earth;
    Entity moon;
    final List<OrbitalEvent> applied = new ArrayList<>();

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        universe = new RailsUniverse(game);
        queue = new OrbitalEventQueue();
        events = new OrbitalEventSystem(universe, queue);
        events.applied.add((signal, event) -> applied.add(event));
        game.addSystem(events);

        earth = add(new UuidComponent("earth"), new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        moon = add(new UuidComponent("moon"),
                new CelestialBodyComponent("Moon", MU_MOON, 1.7374e6, OrbitMath.soiRadius(MOON_A, MU_MOON, MU_EARTH)),
                new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, MOON_A, 0, 0, 1)));
    }

    Entity add(com.badlogic.ashley.core.Component... components) {
        Entity e = game.createEntity();
        for (com.badlogic.ashley.core.Component c : components) e.add(c);
        game.addEntity(e);
        return e;
    }

    Entity ship(String id, KeplerOrbit orbit) {
        return add(new UuidComponent(id), new OrbitComponent("earth", orbit));
    }

    void runTo(double t) {
        game.setTick((long) Math.ceil(t * 60));
        events.processUntil(game.getSimSeconds());
    }

    /** The test's veto switch, registered as a rails warp guard. */
    boolean allowed = true;

    TimeWarpController warp(Entity player, FakeBubble bubble) {
        game.addRailsWarpGuard(() -> allowed ? null : "Fire aboard");
        return new TimeWarpController(game, bubble, events, universe,
                new EncounterPredictor(game.getEcsEngine(), universe), () -> player);
    }

    @Test
    void stateInFrameSumsTheChain() {
        Entity s = add(new UuidComponent("lunar"), new OrbitComponent("moon", KeplerOrbit.circular(MU_MOON, 2e6, 0, 0, 1)));
        StateVector inEarth = universe.stateInFrame(s, earth, 1000, new StateVector());
        StateVector moonState = universe.orbit(moon).stateAt(1000, new StateVector());
        StateVector local = universe.orbit(s).stateAt(1000, new StateVector());
        assertEquals(moonState.x + local.x, inEarth.x, 1e-6);
        assertEquals(moonState.vy + local.vy, inEarth.vy, 1e-9);
        assertSame(earth, universe.commonAncestor(s, earth));
        assertSame(moon, universe.commonAncestor(s, moon));
    }

    @Test
    void maneuverNodeFiresAtItsTime() {
        Entity s = ship("ship", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        double before = universe.orbit(s).a;
        s.add(new ManeuverNodeComponent().add(600, 50, 0));
        events.orbitChanged(s);
        runTo(599);
        assertEquals(before, universe.orbit(s).a);
        runTo(601);
        assertTrue(universe.orbit(s).a > before);
        assertEquals(OrbitalEvent.Type.MANEUVER, applied.get(0).type);
        assertEquals(600, universe.orbit(s).epoch, 1e-9);
        assertNull(s.getComponent(ManeuverNodeComponent.class).next());
    }

    /** A Hohmann-style transfer from LEO reaches the Moon's SOI, is re-parented, and later leaves it. */
    @Test
    void lunarTransferCrossesSoi() {
        // Burn to an apoapsis at the Moon's distance, timed so the Moon is there on arrival.
        double aTransfer = (LEO + MOON_A) / 2;
        double transferTime = Math.PI * Math.sqrt(aTransfer * aTransfer * aTransfer / MU_EARTH);
        double moonRate = Math.sqrt(MU_EARTH / (MOON_A * MOON_A * MOON_A));
        // Ship starts at angle 0 and arrives at angle pi; the Moon must be at pi then.
        double moonStart = Math.PI - moonRate * transferTime;
        universe.orbit(moon).setOrbit(KeplerOrbit.circular(MU_EARTH, MOON_A, moonStart, 0, 1));

        double vCirc = OrbitMath.circularSpeed(MU_EARTH, LEO);
        double vTransfer = Math.sqrt(MU_EARTH * (2 / LEO - 1 / aTransfer));
        Entity s = ship("ship", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        s.add(new ManeuverNodeComponent().add(0, vTransfer - vCirc, 0));
        events.orbitChanged(s);

        List<SoiChange> soi = new ArrayList<>();
        events.soiChanges.add((sig, c) -> {
            SoiChange copy = new SoiChange();
            copy.entityUuid = c.entityUuid;
            copy.newParentUuid = c.newParentUuid;
            soi.add(copy);
        });
        runTo(transferTime + 86400);
        assertEquals("ship", soi.get(0).entityUuid);
        assertEquals("moon", soi.get(0).newParentUuid);
        List<OrbitalEvent.Type> types = new ArrayList<>();
        for (OrbitalEvent e : applied) if (e.entityUuid.equals("ship")) types.add(e.type);
        assertTrue(types.contains(OrbitalEvent.Type.SOI_ENTRY), types.toString());
        OrbitalEvent entry = applied.stream().filter(e -> e.type == OrbitalEvent.Type.SOI_ENTRY).findFirst().get();
        assertEquals("moon", entry.targetUuid);
        assertTrue(entry.time < transferTime, "enters the SOI before reaching apoapsis");

        // Position is continuous across the re-parenting.
        double tc = entry.time;
        assertTrue(universe.stateInFrame(s, moon, tc, new StateVector()).radius()
                <= universe.celestial(moon).soiRadius * (1 + 1e-6));

        // It either left again or hit the Moon; with this aim it does not stay captured.
        assertTrue(types.contains(OrbitalEvent.Type.SOI_EXIT) || types.contains(OrbitalEvent.Type.IMPACT), types.toString());
    }

    @Test
    void positionIsContinuousAcrossSoiExit() {
        // Escape the Moon on a hyperbola; record Earth-frame state just before and after the exit.
        double r = 2e6;
        double v = OrbitMath.escapeSpeed(MU_MOON, r) * 1.3;
        Entity s = add(new UuidComponent("esc"), new OrbitComponent("moon", KeplerOrbit.fromState(r, 0, 0, v, MU_MOON, 0)));
        double exitTime = queue.nextFor("esc").time;
        assertEquals(OrbitalEvent.Type.SOI_EXIT, queue.nextFor("esc").type);
        StateVector before = universe.stateInFrame(s, earth, exitTime, new StateVector());
        runTo(exitTime + 1);
        assertEquals("earth", universe.orbit(s).parentUuid);
        StateVector after = universe.stateInFrame(s, earth, exitTime, new StateVector());
        assertEquals(before.x, after.x, 1e-3);
        assertEquals(before.y, after.y, 1e-3);
        assertEquals(before.vx, after.vx, 1e-6);
        assertEquals(before.vy, after.vy, 1e-6);
    }

    @Test
    void anchorGetsSoiChanges() {
        List<String> changes = new ArrayList<>();
        events.soiChanges.add((sig, c) -> {
            changes.add(c.toString());
            if (c.anchor) {
                // The handed-over conic is the anchor's new orbit and matches the reported state.
                assertEquals(c.state.x, c.newOrbit.stateAt(c.time).x, 1e-3);
            }
        });
        double r = 2e6;
        KeplerOrbit escape = KeplerOrbit.fromState(r, 0, 0, OrbitMath.escapeSpeed(MU_MOON, r) * 1.3, MU_MOON, 0);
        events.setAnchor("moon", escape);
        double exit = queue.nextFor(OrbitalEventSystem.ANCHOR_ID).time;
        StateVector before = escape.stateAt(exit).add(universe.orbit(moon).stateAt(exit, new StateVector()));
        runTo(exit + 1);
        assertEquals(1, changes.size(), changes.toString());
        assertTrue(changes.get(0).startsWith("anchor moon -> earth"));
        assertEquals("earth", events.getAnchorParentUuid());
        StateVector after = events.getAnchorOrbit().stateAt(exit);
        assertEquals(before.x, after.x, 1e-3);
        assertEquals(before.vy, after.vy, 1e-6);

        // A new anchor orbit (a rebase) replaces the old prediction; clearing it stops tracking.
        events.setAnchor("earth", KeplerOrbit.circular(MU_EARTH, LEO, 0, game.getSimSeconds(), 1));
        assertNull(queue.nextFor(OrbitalEventSystem.ANCHOR_ID), "no SOI change ahead from LEO");
        assertFalse(queue.eventsFor(OrbitalEventSystem.ANCHOR_ID).isEmpty(), "but still tracked");
        events.clearAnchor();
        assertTrue(queue.eventsFor(OrbitalEventSystem.ANCHOR_ID).isEmpty());
    }

    @Test
    void impactIsPredicted() {
        List<OrbitalEvent> impacts = new ArrayList<>();
        events.impacts.add((sig, e) -> impacts.add(e));
        Entity s = ship("faller", KeplerOrbit.fromApsides(MU_EARTH, R_EARTH - 100e3, LEO, 0, 0, 1));
        // Start at apoapsis so it falls.
        universe.orbit(s).setOrbit(KeplerOrbit.fromState(universe.orbit(s).getOrbit().stateAt(
                universe.orbit(s).getOrbit().period() / 2), MU_EARTH, 0));
        events.orbitChanged(s);
        runTo(universe.orbit(s).getOrbit().period());
        assertEquals(1, impacts.size());
        assertEquals(R_EARTH, universe.orbit(s).stateAt(impacts.get(0).time, new StateVector()).radius(), 1e-2);
    }

    @Test
    void railsWarpDropsOutBeforeEncounter() {
        // Player in LEO; a second ship slightly lower catches up from behind.
        Entity player = ship("player", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        ship("other", KeplerOrbit.circular(MU_EARTH, LEO - 2000, -0.05, 0, 1));
        FakeBubble bubble = new FakeBubble();
        TimeWarpController warp = warp(player, bubble);
        List<TimeWarpController.DropReason> drops = new ArrayList<>();
        warp.changes.add((sig, c) -> { if (c.reason != null) drops.add(c.reason); });

        assertTrue(warp.setWarp(100_000));
        assertEquals(1, bubble.collapses);
        for (int frame = 0; frame < 100_000 && warp.isRailsWarp(); frame++) {
            warp.update(1 / 60f);
        }
        assertFalse(warp.isRailsWarp());
        assertEquals(List.of(TimeWarpController.DropReason.ENCOUNTER), drops);
        assertEquals(1, bubble.rebuilds);
        // Dropped within one tick of the encounter, with the other ship just inside 20 km.
        double t = game.getSimSeconds();
        StateVector a = universe.stateInFrame(player, earth, t, new StateVector());
        StateVector b = universe.stateInFrame(universe.entity("other"), earth, t, new StateVector());
        double d = Math.hypot(a.x - b.x, a.y - b.y);
        assertTrue(d <= TimeWarpController.DEFAULT_ENCOUNTER_RADIUS && d > 19_000, "distance " + d);
    }

    @Test
    void railsWarpDropsOutForPlayerManeuverAndVeto() {
        Entity player = ship("player", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        player.add(new ManeuverNodeComponent().add(3000, 10, 0));
        events.orbitChanged(player);
        TimeWarpController warp = warp(player, new FakeBubble());
        assertTrue(warp.setWarp(1000));
        while (warp.isRailsWarp()) warp.update(1 / 60f);
        assertEquals(3000, game.getSimSeconds(), 1.0 / 60);
        assertEquals(3000, universe.orbit(player).epoch, 1e-9);  // the burn was applied

        allowed = false;
        assertFalse(warp.setWarp(1000));
        allowed = true;
        assertTrue(warp.setWarp(1000));
        List<String> reasons = new ArrayList<>();
        warp.changes.add((sig, c) -> reasons.add(c.blockedReason));
        allowed = false;   // e.g. a fire starts aboard
        warp.update(1 / 60f);
        assertFalse(warp.isRailsWarp());
        assertEquals(List.of("Fire aboard"), reasons);
    }

    @Test
    void railsWarpStopsForDropOutSource() {
        Entity player = ship("player", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        TimeWarpController warp = warp(player, new FakeBubble());
        warp.addDropOutSource(() -> 5000.0);
        List<TimeWarpController.DropReason> drops = new ArrayList<>();
        warp.changes.add((sig, c) -> { if (c.reason != null) drops.add(c.reason); });
        assertTrue(warp.setWarp(10_000));
        while (warp.isRailsWarp()) warp.update(1 / 60f);
        assertEquals(List.of(TimeWarpController.DropReason.EXTERNAL), drops);
        assertEquals(5000, game.getSimSeconds(), 1.0 / 60);
    }

    /** Predictions are not saved; they are rebuilt on load and come out the same. */
    @Test
    void saveAndLoadRebuildsPredictions() {
        game.getSerializationService().addParticipant(events);
        Entity s = add(new UuidComponent("esc"), new OrbitComponent("moon",
                KeplerOrbit.fromState(2e6, 0, 0, OrbitMath.escapeSpeed(MU_MOON, 2e6) * 1.3, MU_MOON, 0)));
        s.add(new ManeuverNodeComponent().add(50_000, 5, 1));
        events.orbitChanged(s);
        runTo(100);
        List<OrbitalEvent> before = queue.eventsFor("esc");
        double xAt1000 = universe.orbit(s).getOrbit().stateAt(1000).x;
        String save = game.getSerializationService().saveGame();

        game.getSerializationService().loadGame(save);
        assertEquals(100, game.getSimSeconds(), 1e-9);
        List<OrbitalEvent> after = queue.eventsFor("esc");
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).type, after.get(i).type);
            assertEquals(before.get(i).time, after.get(i).time, 1e-6);
        }
        Entity loaded = universe.entity("esc");
        assertEquals("moon", universe.orbit(loaded).parentUuid);
        assertEquals(50_000, loaded.getComponent(ManeuverNodeComponent.class).next().time);
        assertEquals(xAt1000, universe.orbit(loaded).getOrbit().stateAt(1000).x, 1e-6);
    }

    @Test
    void physicsWarpIsPassedThrough() {
        Entity player = ship("player", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        TimeWarpController warp = warp(player, new FakeBubble());
        assertTrue(warp.setWarp(5));
        assertEquals(5, game.getPhysicsWarp());
        assertFalse(warp.isRailsWarp());
        warp.step(1);
        assertEquals(10, game.getPhysicsWarp());
        warp.step(1);
        assertTrue(warp.isRailsWarp());
        assertEquals(50, warp.getWarp());
    }

    static class FakeBubble implements BubbleHandoff {
        int collapses;
        int rebuilds;

        public void collapseToRails() {
            collapses++;
        }

        public void rebuildFromRails() {
            rebuilds++;
        }
    }
}
