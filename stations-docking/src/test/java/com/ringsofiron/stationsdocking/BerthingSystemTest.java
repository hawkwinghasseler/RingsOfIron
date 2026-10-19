package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A freighter flying itself in to dock at a station under its own thrusters, and away again. */
class BerthingSystemTest {
    private static final float[] HULL = {-5f, -2f, 5f, -2f, 5f, 2f, -5f, 2f};
    private static final float DENSITY = 500f;

    private GameEngine game;
    private PhysicsManager physics;
    private DockingSystem docking;
    private BerthingSystem berthing;
    private final List<String> heard = new ArrayList<>();

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        game.addSystem(new PhysicsUpdateSystem(physics));
        game.addSystem(new FlightControlSystem(physics));
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        game.addSystem(transients);
        docking = new DockingSystem(physics, game.getUuidRegistry());
        docking.requirePower = false;
        game.addSystem(docking);
        berthing = new BerthingSystem(docking, game.getUuidRegistry());
        berthing.listener = new BerthingSystem.BerthingListener() {
            @Override
            public void berthed(Entity craft, Entity station) {
                heard.add("berthed");
            }

            @Override
            public void departed(Entity craft, Entity station) {
                heard.add("departed");
            }
        };
        game.addSystem(berthing);
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    /** A 9 x 5 station at rest at the origin with one active Small port on top (+y). */
    private Entity station() {
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(new ChassisBlueprint(new String[]{
                "###DDD###",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########",
        }).tileSize(1f).origin(-4.5f, -2.5f));
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        e.add(result.grid);
        e.add(DockingPortsComponent.fromGrid(result.grid, List.of(new int[]{4, 4})));
        e.add(new StationComponent("Test Station", "outpost", "test"));
        e.add(new KinematicTransientComponent((t, out) -> {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }, 10f));
        game.addEntity(e);
        return e;
    }

    /** A 20 t freighter with a passive Small port on its nose (+x), at (x, y) turned {@code angle}. */
    private Entity freighter(Entity station, float x, float y, float angle) {
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        Body body = physics.createPolygonBody(e, x, y, angle, HULL, DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        e.add(new PhysicsBodyComponent(body));
        e.add(new DockingPortsComponent(DockingPortsComponent.hullPort(MountFootprint.MountSize.S, false, 5f, 0f, 1f, 0f)));
        float mass = PhysicsManager.getMass(body);
        float inertia = PhysicsManager.getInertia(body);
        e.add(new ThrusterComponent(mass * 20f, mass * 8f, mass * 6f, inertia * 2.5f));
        e.add(new FlightControlComponent());
        e.add(new BerthingComponent(EntityRef.of(station), 0, BerthingComponent.Phase.APPROACH));
        game.addEntity(e);
        return e;
    }

    private static BerthingComponent berth(Entity e) {
        return e.getComponent(BerthingComponent.class);
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).ports[0];
    }

    private static Vector2 position(Entity e) {
        return PhysicsManager.getPosition(e.getComponent(PhysicsBodyComponent.class).body, new Vector2());
    }

    /** Runs {@code n} frames at 60 fps: berthing flies the craft once a frame, physics once a tick. */
    private void frames(int n) {
        for (int i = 0; i < n; i++) game.update(1f / 60f);
    }

    /** Steps until {@code craft} is in {@code phase}, at most {@code seconds}; returns the seconds it took. */
    private float stepUntil(Entity craft, BerthingComponent.Phase phase, float seconds) {
        for (int i = 0; i < seconds * 60; i++) {
            if (berth(craft).phase == phase) return i / 60f;
            frames(1);
        }
        fail("never reached " + phase + " (is " + berth(craft).phase + ": " + berth(craft).status + ")");
        return 0f;
    }

    @Test
    void aFreighterInFrontOfThePortAsksFliesInAndDocks() {
        Entity s = station();
        Entity f = freighter(s, 10f, 150f, 0f);
        frames(1);
        assertEquals(BerthingComponent.Phase.APPROACH, berth(f).phase);
        frames(2);
        assertEquals(0, berth(f).port, "asked for clearance and got the only port");
        assertTrue(port(s).armed);
        stepUntil(f, BerthingComponent.Phase.BERTHED, 300f);
        assertEquals(DockingPort.State.DOCKED, port(f).state);
        assertEquals(s, docking.dockedTo(f));
        assertTrue(port(f).permitted, "docked with clearance");
        assertEquals("Docked", berth(f).status);
        assertEquals(List.of("berthed"), heard);
        FlightControlComponent c = f.getComponent(FlightControlComponent.class);
        assertEquals(0f, c.forward);
        assertEquals(0f, c.strafe);
        assertEquals(0f, c.turn);
    }

    @Test
    void aFreighterBehindTheStationFliesRoundItFirst() {
        Entity s = station();
        // Below the station, with the port on the far side
        Entity f = freighter(s, 0f, -200f, 0f);
        boolean wentRound = false;
        for (int i = 0; i < 60 * 400 && berth(f).phase != BerthingComponent.Phase.BERTHED; i++) {
            frames(1);
            wentRound |= berth(f).status.startsWith("Flying round");
            Vector2 p = position(f);
            assertFalse(Math.abs(p.x) < 5f && Math.abs(p.y) < 3f, "never flies through the station");
        }
        assertTrue(wentRound, "flew round the side");
        assertEquals(BerthingComponent.Phase.BERTHED, berth(f).phase, berth(f).status);
    }

    @Test
    void aRefusedFreighterHoldsOffAndComesInOnceCleared() {
        Entity s = station();
        boolean[] grant = {false};
        docking.clearance = (station, vessel) -> grant[0] ? null : "";
        Entity f = freighter(s, 10f, 150f, 0f);
        frames(60 * 30);
        assertEquals(BerthingComponent.Phase.APPROACH, berth(f).phase);
        assertEquals("Refused clearance; holding off", berth(f).status);
        assertEquals(-1, berth(f).port);
        assertFalse(port(s).armed);
        assertTrue(position(f).y > 50f, "waits off the hull, not at the port");

        grant[0] = true;
        stepUntil(f, BerthingComponent.Phase.BERTHED, 300f);
        assertEquals(List.of("berthed"), heard);
    }

    @Test
    void aBerthedFreighterStaysUntilSentOffThenBacksOutAndLeaves() {
        Entity s = station();
        Entity f = freighter(s, 0f, 0f, 0f);
        int stationPort = 0;
        docking.dockInPlace(f, 0, s, stationPort);
        f.add(BerthingSystem.berthed(s, 0, stationPort));
        frames(60 * 5);
        assertEquals(BerthingComponent.Phase.BERTHED, berth(f).phase);
        assertEquals(DockingPort.State.DOCKED, port(f).state, "stays put while berthed");

        berthing.depart(f);
        assertEquals(BerthingComponent.Phase.DEPART, berth(f).phase);
        assertEquals(DockingPort.State.IDLE, port(f).state, "undocked at once");
        assertNull(docking.dockedTo(f));
        frames(60 * 10);
        assertEquals("Backing off", berth(f).status);
        for (int i = 0; i < 60 * 120 && !heard.contains("departed"); i++) frames(1);
        assertEquals(List.of("departed"), heard);
        assertEquals("Leaving", berth(f).status);
        float distance = position(f).len();
        assertTrue(distance >= BerthingSystem.BACK_OFF, "backed off the port first: " + distance);
        frames(60 * 10);
        assertTrue(position(f).len() > distance + 100f, "and then flies away");
        assertFalse(port(f).armed, "not reaching for the station again");
    }

    @Test
    void departIsIgnoredUnlessBerthed() {
        Entity s = station();
        Entity f = freighter(s, 10f, 150f, 0f);
        berthing.depart(f);
        assertEquals(BerthingComponent.Phase.APPROACH, berth(f).phase);
    }

    @Test
    void tearingLooseSendsTheFreighterOff() {
        Entity s = station();
        Entity f = freighter(s, 0f, 0f, 0f);
        docking.dockInPlace(f, 0, s, 0);
        f.add(BerthingSystem.berthed(s, 0, 0));
        frames(60 * 5);
        Body body = f.getComponent(PhysicsBodyComponent.class).body;
        physics.addStepListener(dt -> {
            if (port(f).intact()) PhysicsManager.applyForce(body, 0f, 3 * DockingTuning.SMALL.breakForce);
        });
        frames(60 * 2);
        assertFalse(port(f).intact());
        assertEquals(BerthingComponent.Phase.DEPART, berth(f).phase);
        assertEquals("Backing off", berth(f).status, "and backs off the station, as after undocking");
    }
}
