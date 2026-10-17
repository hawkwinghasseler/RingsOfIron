package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.events.CollisionEvent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPhysicsTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    // A 10 m x 10 m box at 10 kg/m^2 weighs 1,000 kg.
    private static final float[] BOX = {-5, -5, 5, -5, 5, 5, -5, 5};
    private static final float BOX_DENSITY = 10f;

    private PhysicsManager physics;
    private Engine engine;
    private FloatingOriginSystem floatingOrigin;

    @BeforeAll
    static void loadNatives() {
        // A running libGDX app loads these itself; headless tests have to ask.
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        floatingOrigin = new FloatingOriginSystem(physics, 1000f, 100f);
        engine.addSystem(floatingOrigin);
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnShip(float x, float y, float angle, ThrusterComponent thruster) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, angle, BOX, BOX_DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        if (thruster != null) {
            ship.add(thruster);
            ship.add(new FlightControlComponent());
        }
        engine.addEntity(ship);
        return ship;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void runTicks(int ticks) {
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    @Test
    void shipDriftsAtConstantVelocityWithNoForces() {
        Entity ship = spawnShip(0, 0, 0, null);
        PhysicsManager.setVelocity(body(ship), 30f, -10f);

        runTicks(120); // 2 seconds

        TransformComponent t = ship.getComponent(TransformComponent.class);
        assertEquals(60f, t.x, 0.01f);
        assertEquals(-20f, t.y, 0.01f);
        Vector2 v = PhysicsManager.getVelocity(body(ship), new Vector2());
        assertEquals(30f, v.x, 1e-4f);
        assertEquals(-10f, v.y, 1e-4f);
    }

    @Test
    void slowDriftDoesNotFallAsleep() {
        Entity ship = spawnShip(0, 0, 0, null);
        PhysicsManager.setVelocity(body(ship), 0.001f, 0f);

        runTicks(600);

        assertEquals(0.01f, ship.getComponent(TransformComponent.class).x, 1e-4f);
    }

    @Test
    void mainEngineObeysFEqualsMa() {
        // 1,000 kg ship, 10 kN main engine: 10 m/s^2 along the nose.
        Entity ship = spawnShip(0, 0, (float) Math.PI / 2f, new ThrusterComponent(10_000f, 5_000f, 2_000f, 0f));
        assertEquals(1000f, PhysicsManager.getMass(body(ship)), 0.01f);
        ship.getComponent(FlightControlComponent.class).forward = 1f;

        runTicks(60); // 1 second

        Vector2 v = PhysicsManager.getVelocity(body(ship), new Vector2());
        assertEquals(0f, v.x, 1e-3f);
        assertEquals(10f, v.y, 1e-3f);

        // Coasting: velocity holds once the throttle is released.
        ship.getComponent(FlightControlComponent.class).forward = 0f;
        runTicks(60);
        assertEquals(10f, PhysicsManager.getVelocity(body(ship), v).y, 1e-3f);
    }

    @Test
    void retroAndLateralThrustUseTheirOwnLimits() {
        Entity ship = spawnShip(0, 0, 0, new ThrusterComponent(10_000f, 5_000f, 2_000f, 0f));
        FlightControlComponent control = ship.getComponent(FlightControlComponent.class);
        control.forward = -1f;
        control.strafe = 1f;

        runTicks(60);

        Vector2 v = PhysicsManager.getVelocity(body(ship), new Vector2());
        assertEquals(-5f, v.x, 1e-3f);
        assertEquals(2f, v.y, 1e-3f);
    }

    @Test
    void torqueSpinsShipAndRotationAssistStopsIt() {
        Entity ship = spawnShip(0, 0, 0, new ThrusterComponent(0f, 0f, 0f, 50_000f));
        FlightControlComponent control = ship.getComponent(FlightControlComponent.class);
        float inertia = PhysicsManager.getInertia(body(ship));
        // 1,000 kg, 10 m square: I = m (w^2 + h^2) / 12
        assertEquals(1000f * 200f / 12f, inertia, 1f);

        control.turn = 1f;
        runTicks(60);
        float spin = body(ship).getAngularVelocity();
        assertEquals(50_000f / inertia, spin, 1e-2f);

        control.turn = 0f;
        runTicks(60);
        assertEquals(spin, body(ship).getAngularVelocity(), 1e-4f, "no assist: spin is conserved");

        control.rotationAssist = true;
        runTicks(120);
        assertEquals(0f, body(ship).getAngularVelocity(), 1e-4f);
    }

    @Test
    void thrustStopsWhenFuelRunsOut() {
        // 1 kg of fuel per 10 kN*s: one second of full burn.
        ThrusterComponent thruster = new ThrusterComponent(10_000f, 0f, 0f, 0f).withFuel(1f, 1e-4f, 0f);
        Entity ship = spawnShip(0, 0, 0, thruster);
        ship.getComponent(FlightControlComponent.class).forward = 1f;

        runTicks(180);

        assertEquals(0f, thruster.fuel, 1e-6f);
        assertTrue(thruster.isOutOfFuel());
        assertEquals(10f, PhysicsManager.getVelocity(body(ship), new Vector2()).x, 0.01f);
    }

    @Test
    void floatingOriginRebaseKeepsUniversePositionsAndVelocities() {
        Entity player = spawnShip(990f, 0f, 0f, null);
        player.add(new PlayerControllerComponent());
        Entity other = spawnShip(1500f, 300f, 0f, null);
        Entity marker = new Entity();
        marker.add(new TransformComponent(-200f, 50f, 0f));
        engine.addEntity(marker);
        PhysicsManager.setVelocity(body(player), 60f, 0f);

        List<WorldShiftEvent> shifts = new ArrayList<>();
        physics.worldShifts.add((signal, e) -> shifts.add(copy(e)));

        runTicks(30); // player passes 1,000 m and triggers one rebase

        assertEquals(1, shifts.size());
        WorldShiftEvent shift = shifts.get(0);
        assertEquals(1, floatingOrigin.getRebaseCount());
        assertEquals(60f, shift.deltaVx, 1e-3f, "a rebase also co-moves the frame with the player");

        Vector2 p = PhysicsManager.getPosition(body(player), new Vector2());
        assertTrue(p.len() < 10f, "player is back near the origin: " + p);
        assertEquals(990.0 + 60.0 * 30 * DT, physics.getOriginX() + p.x, 0.01);

        Vector2 o = PhysicsManager.getPosition(body(other), new Vector2());
        assertEquals(1500.0, physics.getOriginX() + o.x, 0.01);
        assertEquals(300.0, physics.getOriginY() + o.y, 0.01);

        TransformComponent m = marker.getComponent(TransformComponent.class);
        assertEquals(-200.0, physics.getOriginX() + m.x, 0.01);
        Vector2 v = PhysicsManager.getVelocity(body(player), new Vector2());
        assertEquals(0f, v.x, 1e-3f);
        assertEquals(60.0, physics.getOriginVx() + v.x, 1e-3);
    }

    @Test
    void floatingOriginMatchesVelocityAboveSpeedThreshold() {
        Entity player = spawnShip(0f, 0f, 0f, null);
        player.add(new PlayerControllerComponent());
        Entity rock = spawnShip(100f, 300f, 0f, null);
        PhysicsManager.setVelocity(body(player), 150f, 0f);
        PhysicsManager.setVelocity(body(rock), 20f, 5f);

        runTicks(1);

        assertEquals(1, floatingOrigin.getRebaseCount());
        assertEquals(150.0, physics.getOriginVx(), 1e-3);
        Vector2 pv = PhysicsManager.getVelocity(body(player), new Vector2());
        assertEquals(0f, pv.len(), 1e-3f);
        Vector2 rv = PhysicsManager.getVelocity(body(rock), new Vector2());
        assertEquals(-130f, rv.x, 1e-3f);
        assertEquals(5f, rv.y, 1e-3f);

        // Universe positions keep advancing at the true velocities while the frame co-moves.
        Vector2 before = PhysicsManager.getPosition(body(rock), new Vector2());
        double rockUniverseBefore = physics.getOriginX() + before.x;
        runTicks(60);
        Vector2 after = PhysicsManager.getPosition(body(rock), new Vector2());
        assertEquals(rockUniverseBefore + 20.0, physics.getOriginX() + after.x, 0.01);
    }

    @Test
    void shipsFasterThan120MetersPerSecondAreNotClamped() {
        Entity ship = spawnShip(0, 0, 0, null);
        PhysicsManager.setVelocity(body(ship), 900f, 0f);

        engine.removeSystem(floatingOrigin);
        runTicks(60);

        assertEquals(900f, ship.getComponent(TransformComponent.class).x, 0.5f);
    }

    @Test
    void collisionsAreReportedAfterTheStep() {
        Entity a = spawnShip(0, 0, 0, null);
        Entity b = spawnShip(30, 0, 0, null);
        PhysicsManager.setVelocity(body(a), 20f, 0f);

        List<CollisionEvent> hits = new ArrayList<>();
        physics.collisions.add((signal, e) -> {
            assertFalse(physics.getWorld().isLocked());
            hits.add(e);
        });

        runTicks(90);

        assertEquals(1, hits.size());
        CollisionEvent hit = hits.get(0);
        assertTrue((hit.entityA == a && hit.entityB == b) || (hit.entityA == b && hit.entityB == a));
        assertEquals(20f, Math.abs(hit.approachSpeed), 0.5f);
    }

    @Test
    void removingEntityDestroysItsBody() {
        Entity ship = spawnShip(0, 0, 0, null);
        assertEquals(1, physics.getWorld().getBodyCount());

        engine.removeEntity(ship);

        assertEquals(0, physics.getWorld().getBodyCount());
    }

    private static WorldShiftEvent copy(WorldShiftEvent e) {
        WorldShiftEvent c = new WorldShiftEvent();
        c.deltaX = e.deltaX;
        c.deltaY = e.deltaY;
        c.deltaVx = e.deltaVx;
        c.deltaVy = e.deltaVy;
        c.originX = e.originX;
        c.originY = e.originY;
        c.originVx = e.originVx;
        c.originVy = e.originVy;
        return c;
    }
}
