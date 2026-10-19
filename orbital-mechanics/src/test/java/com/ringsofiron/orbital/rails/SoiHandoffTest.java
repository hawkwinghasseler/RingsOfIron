package com.ringsofiron.orbital.rails;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SOI handoffs right at the boundary, stale predictions, and the event queue and maneuver list underneath. */
class SoiHandoffTest {
    static final double MU_EARTH = 3.986004418e14;
    static final double R_EARTH = 6.371e6;
    static final double MU_MOON = 4.9048695e12;
    static final double R_MOON = 1.7374e6;
    static final double MOON_A = 3.844e8;

    GameEngine game;
    RailsUniverse universe;
    OrbitalEventQueue queue;
    OrbitalEventSystem events;
    Entity earth;
    Entity moon;
    double soi;
    final List<SoiChange> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        universe = new RailsUniverse(game);
        queue = new OrbitalEventQueue();
        events = new OrbitalEventSystem(universe, queue);
        events.soiChanges.add((sig, c) -> {
            SoiChange copy = new SoiChange();
            copy.entityUuid = c.entityUuid;
            copy.oldParentUuid = c.oldParentUuid;
            copy.newParentUuid = c.newParentUuid;
            copy.time = c.time;
            copy.state.set(c.state);
            copy.newOrbit = c.newOrbit;
            changes.add(copy);
        });
        game.addSystem(events);
        soi = OrbitMath.soiRadius(MOON_A, MU_MOON, MU_EARTH);
        earth = add(new UuidComponent("earth"), new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        moon = add(new UuidComponent("moon"), new CelestialBodyComponent("Moon", MU_MOON, R_MOON, soi),
                new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, MOON_A, 0, 0, 1)));
    }

    Entity add(com.badlogic.ashley.core.Component... components) {
        Entity e = game.createEntity();
        for (com.badlogic.ashley.core.Component c : components) e.add(c);
        game.addEntity(e);
        return e;
    }

    void runTo(double t) {
        game.setTick((long) Math.ceil(t * 60));
        events.processUntil(game.getSimSeconds());
    }

    /** An elliptic lunar orbit with periapsis 100 km up and the given apoapsis. */
    Entity lunar(String id, double apoapsis) {
        return add(new UuidComponent(id), new OrbitComponent("moon",
                KeplerOrbit.fromApsides(MU_MOON, R_MOON + 100e3, apoapsis, 0, 0, 1)));
    }

    @Test
    void anApoapsisJustInsideTheSoiNeverLeaves() {
        lunar("inside", soi * 0.999);
        OrbitalEvent next = queue.eventsFor("inside").get(0);
        assertEquals(OrbitalEvent.Type.REPREDICT, next.type, "nothing to happen, only a look further ahead");
        assertEquals(SoiPredictor.DEFAULT_HORIZON, next.time, 1e-6);
        runTo(3 * SoiPredictor.DEFAULT_HORIZON);
        assertTrue(changes.isEmpty());
        assertEquals("moon", universe.orbit(universe.entity("inside")).parentUuid);
    }

    @Test
    void anApoapsisJustOutsideTheSoiLeavesExactlyAtTheBoundary() {
        Entity s = lunar("outside", soi * 1.001);
        OrbitalEvent exit = queue.nextFor("outside");
        assertEquals(OrbitalEvent.Type.SOI_EXIT, exit.type);
        KeplerOrbit k = universe.orbit(s).getOrbit();
        assertEquals(soi, k.stateAt(exit.time).radius(), soi * 1e-9, "the crossing is at the SOI radius");
        assertTrue(k.stateAt(exit.time - 1).radius() < soi);
        assertTrue(exit.time < k.period() / 2, "on the way out, before apoapsis");

        StateVector before = universe.stateInFrame(s, earth, exit.time, new StateVector());
        runTo(exit.time + 1);
        assertEquals(1, changes.size());
        SoiChange c = changes.get(0);
        assertEquals("moon", c.oldParentUuid);
        assertEquals("earth", c.newParentUuid);
        assertEquals(exit.time, c.time, 1e-9);
        assertEquals("earth", universe.orbit(s).parentUuid);
        assertEquals(MU_EARTH, universe.orbit(s).getOrbit().mu);
        // The handover is seamless in the Earth frame: same place, same velocity.
        StateVector after = universe.orbit(s).stateAt(exit.time, new StateVector());
        assertEquals(before.x, after.x, 1e-3);
        assertEquals(before.y, after.y, 1e-3);
        assertEquals(before.vx, after.vx, 1e-6);
        assertEquals(before.vy, after.vy, 1e-6);
    }

    /** Falling into the Moon's SOI from an Earth orbit: entered right at the boundary, with no jump. */
    @Test
    void entryIsAtTheBoundaryAndSeamless() {
        // A circular orbit a little inside the Moon's and so a little faster, starting behind it and
        // outside its SOI: it catches the Moon up within a few days.
        double angle = -(soi * 1.5) / MOON_A;
        Entity s = add(new UuidComponent("chaser"), new OrbitComponent("earth",
                KeplerOrbit.circular(MU_EARTH, MOON_A - 2e7, angle, 0, 1)));
        OrbitalEvent entry = queue.nextFor("chaser");
        assertNotNull(entry);
        assertEquals(OrbitalEvent.Type.SOI_ENTRY, entry.type);
        assertEquals("moon", entry.targetUuid);
        assertTrue(universe.stateInFrame(s, moon, entry.time - 0.01, new StateVector()).radius() > soi, "outside just before");
        assertEquals(soi, universe.stateInFrame(s, moon, entry.time, new StateVector()).radius(), 1.0, "on the boundary");

        StateVector before = universe.stateInFrame(s, earth, entry.time, new StateVector());
        runTo(entry.time + 1);
        assertEquals("moon", universe.orbit(s).parentUuid);
        assertEquals(1, changes.size());
        assertEquals("earth", changes.get(0).oldParentUuid);
        StateVector after = universe.stateInFrame(s, earth, entry.time, new StateVector());
        assertEquals(before.x, after.x, 1e-3);
        assertEquals(before.y, after.y, 1e-3);
        assertEquals(before.vx, after.vx, 1e-6);
        assertEquals(before.vy, after.vy, 1e-6);
        // The handed-over conic and state agree.
        assertEquals(changes.get(0).state.x, changes.get(0).newOrbit.stateAt(entry.time).x, 1e-3);
    }

    @Test
    void aStalePredictionIsDroppedWhenTheOrbitChanged() {
        Entity s = lunar("ship", soi * 1.5);
        OrbitalEvent exit = queue.nextFor("ship");
        assertEquals(OrbitalEvent.Type.SOI_EXIT, exit.type);
        // Something outside the event system lowers the orbit and forgets to call orbitChanged.
        universe.orbit(s).setOrbit(KeplerOrbit.fromApsides(MU_MOON, R_MOON + 100e3, soi * 0.5, 0, 0, 1));
        runTo(exit.time + 1);
        assertTrue(changes.isEmpty(), "the old exit was for an orbit that no longer exists");
        assertEquals("moon", universe.orbit(s).parentUuid);
    }

    @Test
    void anOrbitChangeReplacesThePredictions() {
        Entity s = lunar("ship", soi * 0.5);
        assertNull(queue.nextFor("ship"));
        universe.orbit(s).setOrbit(KeplerOrbit.fromApsides(MU_MOON, R_MOON + 100e3, soi * 1.5, 0, 0, 1));
        events.orbitChanged(s);
        assertEquals(OrbitalEvent.Type.SOI_EXIT, queue.nextFor("ship").type);
        assertEquals(1, queue.eventsFor("ship").size(), "only the new prediction is queued");
    }

    @Test
    void removingAnEntityDropsItsEvents() {
        Entity s = lunar("ship", soi * 1.5);
        s.add(new ManeuverNodeComponent().add(100, 1, 0));
        events.orbitChanged(s);
        assertEquals(2, queue.eventsFor("ship").size());
        game.removeEntity(s);
        assertTrue(queue.eventsFor("ship").isEmpty());
    }

    @Test
    void severalManeuversFireInOrderAtTheirOwnTimes() {
        Entity s = lunar("ship", soi * 0.3);
        List<Double> times = new ArrayList<>();
        events.applied.add((sig, e) -> { if (e.type == OrbitalEvent.Type.MANEUVER) times.add(e.time); });
        s.add(new ManeuverNodeComponent().add(900, 1, 0).add(300, 1, 0).add(600, -1, 0));
        events.orbitChanged(s);
        runTo(1000);
        assertEquals(List.of(300.0, 600.0, 900.0), times, "applied at their exact times, not the tick that noticed");
        assertNull(s.getComponent(ManeuverNodeComponent.class).next());
        assertEquals(900, universe.orbit(s).epoch, 1e-9);
    }

    @Test
    void maneuverNodesStaySortedAndKeepTiesInOrder() {
        ManeuverNodeComponent m = new ManeuverNodeComponent()
                .add(50, 1, 0).add(10, 2, 0).add(50, 3, 0).add(30, 4, 0);
        double[] progrades = new double[m.nodes.size];
        for (int i = 0; i < m.nodes.size; i++) progrades[i] = m.nodes.get(i).prograde;
        assertArrayEquals(new double[]{2, 4, 1, 3}, progrades);
        assertEquals(10, m.next().time);
        assertNull(new ManeuverNodeComponent().next());
    }

    @Test
    void theQueueHandsOutEventsInTimeOrderAndOnlyWhenDue() {
        OrbitalEventQueue q = new OrbitalEventQueue();
        q.schedule(new OrbitalEvent(OrbitalEvent.Type.IMPACT, 30, "a", null, 0));
        q.schedule(new OrbitalEvent(OrbitalEvent.Type.MANEUVER, 10, "b", null, -1));
        q.schedule(new OrbitalEvent(OrbitalEvent.Type.SOI_EXIT, 20, "a", null, 0));
        q.schedule(new OrbitalEvent(OrbitalEvent.Type.REPREDICT, 5, "a", null, 0));
        q.schedule(new OrbitalEvent(OrbitalEvent.Type.SOI_EXIT, Double.NaN, "c", null, 0));
        assertEquals(4, q.size(), "a NaN time (never happens) is not queued");
        assertEquals(OrbitalEvent.Type.SOI_EXIT, q.nextFor("a").type, "REPREDICT markers are not real events");
        assertNull(q.pollDue(4));
        assertEquals(5, q.pollDue(10).time);
        assertEquals(10, q.pollDue(10).time);
        assertNull(q.pollDue(10));
        q.removeFor("a", OrbitalEvent.Type.SOI_EXIT);
        assertEquals(List.of(OrbitalEvent.Type.IMPACT), q.eventsFor("a").stream().map(e -> e.type).toList());
        q.removeFor("a");
        assertEquals(0, q.size());
    }

    @Test
    void attitudeIsCarriedAcrossAnOrbitChange() {
        OrbitComponent o = new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, 7e6, 0, 0, 1));
        o.attitude = 0.5;
        o.angularVelocity = 0.01;
        assertEquals(0.5 + 0.01 * 100, o.attitudeAt(100), 1e-12);
        o.setOrbit(KeplerOrbit.circular(MU_EARTH, 8e6, 0, 100, 1));
        assertEquals(100, o.epoch);
        assertEquals(1.5, o.attitude, 1e-12, "the angle at the new epoch");
        assertEquals(1.5 + 0.01 * 50, o.attitudeAt(150), 1e-12);
    }

    @Test
    void theConicIsRebuiltFromTheSavedFields() {
        KeplerOrbit k = new KeplerOrbit(MU_EARTH, 2e7, 0.4, 1.2, 0.3, 50, -1);
        OrbitComponent saved = new OrbitComponent("earth", k);
        // What a load produces: the plain fields, no cached conic.
        OrbitComponent loaded = new OrbitComponent();
        loaded.parentUuid = saved.parentUuid;
        loaded.mu = saved.mu;
        loaded.a = saved.a;
        loaded.e = saved.e;
        loaded.argPeriapsis = saved.argPeriapsis;
        loaded.meanAnomalyAtEpoch = saved.meanAnomalyAtEpoch;
        loaded.epoch = saved.epoch;
        loaded.direction = saved.direction;
        StateVector a = k.stateAt(12_345);
        StateVector b = loaded.stateAt(12_345, new StateVector());
        assertEquals(a.x, b.x, 1e-9);
        assertEquals(a.vy, b.vy, 1e-12);
    }
}
