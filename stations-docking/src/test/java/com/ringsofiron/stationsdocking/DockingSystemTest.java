package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.RailsBubbleHandoff;
import com.ringsofiron.physicsexterior.TrueTrajectory;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Docking with real Box2D: magnets, collars, the weld, undocking, tearing loose and rails warp. */
class DockingSystemTest {
    private static final float[] HULL = {-5f, -2f, 5f, -2f, 5f, 2f, -5f, 2f};
    /** 20 t over the 40 m² hull. */
    private static final float DENSITY = 500f;
    private static final double EARTH_MU = 3.986004418e14;
    private static final double LOW_ORBIT = 6_771_000.0;

    private GameEngine game;
    private PhysicsManager physics;
    private DockingSystem docking;
    private final List<DockingEvent.Kind> events = new ArrayList<>();

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        game.addSystem(new PhysicsUpdateSystem(physics));
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        game.addSystem(transients);
        docking = new DockingSystem(physics, game.getUuidRegistry());
        docking.requirePower = false;
        game.addSystem(docking);
        docking.events.add((signal, e) -> events.add(e.kind));
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    /** A 10 x 4 m, 20 t ship with one port on its nose (+x). */
    private Entity ship(float x, float y, float angle, boolean active) {
        return ship(x, y, angle, DockingPortsComponent.hullPort(MountFootprint.MountSize.S, active, 5f, 0f, 1f, 0f));
    }

    private Entity ship(float x, float y, float angle, DockingPort port) {
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        Body body = physics.createPolygonBody(e, x, y, angle, HULL, DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        e.add(new PhysicsBodyComponent(body));
        e.add(new DockingPortsComponent(port));
        game.addEntity(e);
        return e;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).ports[0];
    }

    private static Vector2 face(Body body, DockingPort p) {
        Vector2 w = body.getWorldPoint(new Vector2(p.faceX(), p.faceY()).scl(PhysicsManager.UNITS_PER_METER));
        return new Vector2(w).scl(PhysicsManager.METERS_PER_UNIT);
    }

    private static int collars(Body body) {
        int n = 0;
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0) n++;
        }
        return n;
    }

    /** Steps until {@code port} reaches {@code state}, at most {@code seconds}; returns the seconds it took. */
    private float stepUntil(DockingPort port, DockingPort.State state, float seconds) {
        for (int i = 0; i < seconds * 60; i++) {
            if (port.state == state) return i / 60f;
            game.stepTicks(1);
        }
        fail("port never reached " + state + " (is " + port.state + ")");
        return 0f;
    }

    /** Two ships nose to nose with their passive ports 2 m apart, a little off line. */
    private Entity[] approachPassive() {
        Entity a = ship(0f, 0f, 0f, false);
        // Its nose faces -x; faces 2 m apart, 0.3 m off to the side and turned 3 degrees.
        Entity b = ship(13f, 0.3f, MathUtils.PI + 0.05f, false);
        assertNull(docking.arm(a, 0));
        assertNull(docking.arm(b, 0));
        return new Entity[]{a, b};
    }

    @Test
    void portsGetTheirCollarsAndNoMass() {
        Entity a = ship(0f, 0f, 0f, false);
        float mass = PhysicsManager.getMass(body(a));
        game.stepTicks(1);
        assertEquals(1, collars(body(a)), "the collar is a fixture half a tile past the hull");
        assertEquals(mass, PhysicsManager.getMass(body(a)), 1e-3f, "collars weigh nothing extra");
    }

    @Test
    void armedPassivePortsPullTogetherLatchAndWeld() {
        Entity[] pair = approachPassive();
        float took = stepUntil(port(pair[0]), DockingPort.State.DOCKED, 60f);
        assertTrue(took > 1f, "the weak magnets take a while: " + took);
        assertEquals(DockingPort.State.DOCKED, port(pair[1]).state);
        assertEquals(pair[1], docking.dockedTo(pair[0]));
        assertTrue(port(pair[0]).permitted, "both crews armed their ports");
        game.stepTicks(60 * 4);
        assertTrue(docking.isRigid(pair[0]), "seated into line and welded");
        Vector2 fa = face(body(pair[0]), port(pair[0])), fb = face(body(pair[1]), port(pair[1]));
        assertEquals(0f, fa.dst(fb), 0.03f, "collar faces together");
        float angle = body(pair[1]).getAngle() - body(pair[0]).getAngle() - MathUtils.PI;
        assertEquals(0f, MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle)), 0.02f, "nose to nose");
        assertEquals(List.of(DockingEvent.Kind.DOCKED), events);
    }

    @Test
    void unarmedPortsDoNotAttract() {
        Entity a = ship(0f, 0f, 0f, false);
        Entity b = ship(13f, 0.3f, MathUtils.PI, false);
        game.stepTicks(60 * 10);
        assertEquals(DockingPort.State.IDLE, port(a).state);
        assertEquals(0f, PhysicsManager.getVelocity(body(a), new Vector2()).len(), 1e-4f);
        assertEquals(DockingPort.State.IDLE, port(b).state);
    }

    @Test
    void anActiveCollarReachesOutCapturesAndDrawsTheShipsTogether() {
        Entity a = ship(0f, 0f, 0f, true);
        // Unarmed and passive: docking relies on a's collar, with no say from b.
        Entity b = ship(12.5f, 0.2f, MathUtils.PI, false);
        assertNull(docking.arm(a, 0));
        game.stepTicks(30);
        assertNotNull(port(a).head, "the collar head is out");
        stepUntil(port(a), DockingPort.State.CAPTURED, 20f);
        assertEquals(DockingPort.State.CAPTURED, port(b).state);
        assertNotNull(docking.railsWarpBlockedReason(), "no rails warp mid-capture");
        stepUntil(port(a), DockingPort.State.DOCKED, 30f);
        assertNull(port(a).head, "the collar is home");
        assertFalse(port(a).permitted, "b never armed: no clearance");
        game.stepTicks(60 * 4);
        assertTrue(docking.isRigid(a));
        assertNull(docking.railsWarpBlockedReason());
        assertEquals(0f, face(body(a), port(a)).dst(face(body(b), port(b))), 0.03f);
        assertEquals(List.of(DockingEvent.Kind.CAPTURED, DockingEvent.Kind.DOCKED), events);
    }

    @Test
    void aShipComingInTurnedAwayIsCaughtByTheTiltingHeadAndTurnedSquare() {
        Entity a = ship(0f, 0f, 0f, true);
        // Ten degrees off square: far too much for a latch, but the head tilts to lie flat on b's
        // port while its magnet turns b (light, and free to turn) the rest of the way
        Entity b = ship(12.4f, 0.1f, MathUtils.PI + 10f * MathUtils.degreesToRadians, false);
        assertNull(docking.arm(a, 0));
        float tilt = 0f;
        for (int i = 0; i < 60 * 20 && port(a).state == DockingPort.State.IDLE; i++) {
            game.stepTicks(1);
            tilt = Math.max(tilt, Math.abs(DockingSystem.tiltOf(port(a))));
        }
        assertEquals(DockingPort.State.CAPTURED, port(a).state);
        assertTrue(tilt > 1f * MathUtils.degreesToRadians, "the head tilted to meet the port: " + tilt);
        stepUntil(port(a), DockingPort.State.DOCKED, 30f);
        game.stepTicks(60 * 4);
        assertTrue(docking.isRigid(a));
        assertEquals(0f, face(body(a), port(a)).dst(face(body(b), port(b))), 0.03f, "face to face");
        float angle = body(b).getAngle() - body(a).getAngle() - MathUtils.PI;
        assertEquals(0f, MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle)), 0.01f, "square, as one deck");
    }

    @Test
    void pushingIntoTheCollarStopsAtFullCompression() {
        Entity a = ship(0f, 0f, 0f, true);
        // Turned too far for the head to tilt onto: it only gets pushed in
        Entity b = ship(12.4f, 0f, MathUtils.PI + 30f * MathUtils.degreesToRadians, false);
        assertNull(docking.arm(a, 0));
        game.stepTicks(60 * 3);
        float least = Float.MAX_VALUE;
        for (int i = 0; i < 60 * 6; i++) {
            Body bb = body(b);
            Vector2 push = bb.getWorldVector(new Vector2(1f, 0f)).cpy().scl(PhysicsManager.getMass(bb) * 20f);
            PhysicsManager.applyForce(bb, push.x, push.y);
            game.stepTicks(1);
            if (port(a).head != null) least = Math.min(least, port(a).extension);
        }
        assertTrue(least > -0.06f, "the head stops at its stop, not inside the hull: " + least);
        assertNotEquals(DockingPort.State.DOCKED, port(a).state, "a ship 30 degrees off never docks");
    }

    @Test
    void portsPushedTogetherRestFaceOnFaceWithNoGap() {
        Entity a = ship(0f, 0f, 0f, false);
        Entity b = ship(13.5f, 0f, MathUtils.PI, false);
        for (int i = 0; i < 60 * 8; i++) {
            Body bb = body(b);
            Vector2 push = bb.getWorldVector(new Vector2(1f, 0f)).cpy().scl(PhysicsManager.getMass(bb) * 0.5f);
            PhysicsManager.applyForce(bb, push.x, push.y);
            game.stepTicks(1);
        }
        float gap = face(body(b), port(b)).x - face(body(a), port(a)).x;
        assertEquals(0f, gap, 0.02f, "the collars touch where they are drawn");
    }

    @Test
    void undockingPushesTheShipsApartAndDisarms() {
        Entity[] pair = approachPassive();
        stepUntil(port(pair[0]), DockingPort.State.DOCKED, 60f);
        game.stepTicks(60 * 4);
        assertTrue(docking.undock(pair[0]));
        assertEquals(DockingPort.State.IDLE, port(pair[0]).state);
        assertEquals(DockingPort.State.IDLE, port(pair[1]).state);
        assertFalse(port(pair[0]).armed, "magnets off, or they would pull straight back");
        assertFalse(port(pair[1]).armed);
        assertNull(docking.dockedTo(pair[0]));
        game.stepTicks(1);
        Vector2 va = PhysicsManager.getVelocity(body(pair[0]), new Vector2());
        Vector2 vb = PhysicsManager.getVelocity(body(pair[1]), new Vector2());
        assertEquals(DockingTuning.SEPARATION_SPEED, va.dst(vb), 0.02f);
        game.stepTicks(60 * 3);
        assertTrue(face(body(pair[0]), port(pair[0])).dst(face(body(pair[1]), port(pair[1]))) > 0.6f);
        assertEquals(List.of(DockingEvent.Kind.DOCKED, DockingEvent.Kind.UNDOCKED), events);
    }

    @Test
    void pullingTooHardTearsBothCollarsOff() {
        Entity[] pair = approachPassive();
        stepUntil(port(pair[0]), DockingPort.State.DOCKED, 60f);
        game.stepTicks(60 * 4);
        float mass = PhysicsManager.getMass(body(pair[0]));
        // Full thrust astern, three times what a Small port holds.
        physics.addStepListener(dt -> {
            if (port(pair[0]).intact()) PhysicsManager.applyForce(body(pair[0]), -3 * DockingTuning.SMALL.breakForce, 0f);
        });
        game.stepTicks(60 * 2);
        assertTrue(events.contains(DockingEvent.Kind.TORN), events.toString());
        assertEquals(DockingPort.State.IDLE, port(pair[0]).state);
        assertFalse(port(pair[0]).intact());
        assertFalse(port(pair[1]).intact());
        assertNotNull(docking.arm(pair[0], 0), "a torn port cannot arm");
        assertEquals(0, collars(body(pair[0])), "the collar is gone from the hull");
        assertEquals(mass, PhysicsManager.getMass(body(pair[0])), 1e-3f, "losing the collar keeps the ship's mass");
    }

    @Test
    void aBriefNudgeDoesNotTearTheLatch() {
        Entity[] pair = approachPassive();
        stepUntil(port(pair[0]), DockingPort.State.DOCKED, 60f);
        game.stepTicks(60 * 4);
        int[] ticks = {0};
        physics.addStepListener(dt -> {
            if (ticks[0]++ < 3) PhysicsManager.applyForce(body(pair[0]), -3 * DockingTuning.SMALL.breakForce, 0f);
        });
        game.stepTicks(60 * 2);
        assertFalse(events.contains(DockingEvent.Kind.TORN));
        assertEquals(DockingPort.State.DOCKED, port(pair[0]).state);
    }

    /** A 9 x 5 station with an active Small port on top, at the origin moving at 3 m/s along +x. */
    private Entity station(boolean grants) {
        return station(grants, (t, out) -> {
            out[0] = 3.0 * t;
            out[1] = 0.0;
            out[2] = 3.0;
            out[3] = 0.0;
        });
    }

    private Entity station(boolean grants, TrueTrajectory trajectory) {
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
        StationComponent station = new StationComponent("Test Station", "outpost", "test");
        station.grantsClearance = grants;
        e.add(station);
        e.add(new KinematicTransientComponent(trajectory, 10f));
        game.addEntity(e);
        return e;
    }

    private Body stationBody(Entity station) {
        return station.getComponent(KinematicTransientComponent.class).body;
    }

    @Test
    void stationsAreKinematicBodiesShapedLikeTheirGridThatFollowTheirOrbit() {
        Entity s = station(true);
        Body body = stationBody(s);
        assertNotNull(body);
        assertEquals(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.KinematicBody, body.getType());
        game.stepTicks(120);
        Vector2 p = PhysicsManager.getPosition(body, new Vector2());
        assertEquals(3.0 * physics.getSimSeconds(), p.x, 1e-3);
        assertEquals(3f, PhysicsManager.getVelocity(body, new Vector2()).x, 1e-3f);
        assertEquals(1, collars(body));
        s.remove(KinematicTransientComponent.class);
        assertEquals(0, physics.getWorld().getBodyCount(), "the body goes with the transient");
    }

    @Test
    void aStationGivingClearanceReachesOutWithItsCollarAndHoldsTheShip() {
        Entity s = station(true);
        // Port side down, 1.5 m above the station's port, keeping pace with it.
        DockingPort shipPort = DockingPortsComponent.hullPort(MountFootprint.MountSize.S, false, 0f, -2f, 0f, -1f);
        Entity a = ship(0f, 2.5f + 0.5f + 1.5f + 0.5f + 2f, 0f, shipPort);
        PhysicsManager.setVelocity(body(a), 3f, 0f);
        game.stepTicks(1);
        assertNull(docking.arm(a, 0));
        DockingSystem.Clearance clearance = docking.requestClearance(a, 0);
        assertEquals(DockingSystem.Clearance.Outcome.GRANTED, clearance.outcome);
        assertEquals(s, clearance.station);
        assertEquals(game.getUuidRegistry().view().entrySet().stream()
                .filter(en -> en.getValue() == a).findFirst().get().getKey(), port(s).armedFor);

        stepUntil(port(a), DockingPort.State.DOCKED, 30f);
        assertTrue(port(a).permitted, "docked with clearance");
        game.stepTicks(60 * 4);
        assertTrue(docking.isRigid(a));
        Vector2 offset = PhysicsManager.getPosition(body(a), new Vector2()).sub(PhysicsManager.getPosition(stationBody(s), new Vector2()));
        game.stepTicks(60 * 10);
        Vector2 later = PhysicsManager.getPosition(body(a), new Vector2()).sub(PhysicsManager.getPosition(stationBody(s), new Vector2()));
        assertEquals(offset.x, later.x, 0.01f, "rides along with the station");
        assertEquals(offset.y, later.y, 0.01f);
        assertEquals(0f, face(body(a), port(a)).dst(face(stationBody(s), port(s))), 0.03f);
    }

    @Test
    void aStationThatRefusesClearanceKeepsItsPortsUnarmed() {
        Entity s = station(false);
        DockingPort shipPort = DockingPortsComponent.hullPort(MountFootprint.MountSize.S, false, 0f, -2f, 0f, -1f);
        Entity a = ship(0f, 7f, 0f, shipPort);
        game.stepTicks(1);
        assertEquals(DockingSystem.Clearance.Outcome.DENIED, docking.requestClearance(a, 0).outcome);
        assertFalse(port(s).armed);
    }

    @Test
    void tearingDamagesTheHullAroundThePort() {
        Entity s = station(true);
        ShipGridComponent grid = s.getComponent(ShipGridComponent.class);
        DockingPort p = port(s);
        MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
        PortDamage.tear(grid, p);
        EdgeWall door = DockingPortLayout.door(grid, m, 4, 4);
        assertEquals(0.4f, door.integrity, 1e-6f, "the door leaks but holds");
        assertEquals(0.5f, grid.getTile(3, 4).getIntegrity(), 1e-6f, "the frame is damaged");
    }

    @Test
    void aDockedPairGoesThroughRailsWarpAsOneBody() {
        game.clear();
        physics.dispose();
        game = new GameEngine();
        physics = new PhysicsManager();
        physics.setAnchorModel(new KeplerAnchor(KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 0.0, 0.0, 1)), "earth");
        game.addSystem(new TidalGravitySystem(physics));
        game.addSystem(new PhysicsUpdateSystem(physics));
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        game.addSystem(transients);
        docking = new DockingSystem(physics, game.getUuidRegistry());
        docking.requirePower = false;
        game.addSystem(docking);
        DockingBubbleHandoff handoff = new DockingBubbleHandoff(new RailsBubbleHandoff(physics, game.getEcsEngine()),
                docking, physics, game.getEcsEngine(), game.getUuidRegistry());

        // The station 50 m ahead of the anchor on the same circular orbit
        KeplerOrbit stationOrbit = KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 50.0 / LOW_ORBIT, 0.0, 1);
        StateVector sv = new StateVector();
        Entity s = station(true, (t, out) -> {
            stationOrbit.stateAt(t, sv);
            out[0] = sv.x;
            out[1] = sv.y;
            out[2] = sv.vx;
            out[3] = sv.vy;
        });
        DockingPort shipPort = DockingPortsComponent.hullPort(MountFootprint.MountSize.S, false, 0f, -2f, 0f, -1f);
        Entity a = ship(0f, 0f, 0f, shipPort);
        a.add(new PlayerControllerComponent());
        docking.dockInPlace(a, 0, s, 0);
        game.stepTicks(60 * 2);
        assertTrue(docking.isRigid(a), "put straight onto the station and welded");
        assertEquals(0f, face(body(a), port(a)).dst(face(stationBody(s), port(s))), 0.03f);

        handoff.collapseToRails();
        assertTrue(game.advanceRails(60L * 1800), "half an hour on rails");
        handoff.rebuildFromRails();
        game.stepTicks(60);
        assertEquals(DockingPort.State.DOCKED, port(a).state, "still docked after the warp");
        assertTrue(docking.isRigid(a), "welded again");
        assertEquals(0f, face(body(a), port(a)).dst(face(stationBody(s), port(s))), 0.05f, "where it was docked");
        assertEquals(0f, PhysicsManager.getPosition(body(a), new Vector2()).len(), 50f, "the bubble is on the player");
        assertNull(a.getComponent(DockedRideComponent.class));
    }
}
