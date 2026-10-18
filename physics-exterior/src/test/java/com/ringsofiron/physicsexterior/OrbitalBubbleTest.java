package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.anchor.PointMassAnchor;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The free-falling anchor, tidal gravity, fast passes and the SI query helpers. */
class OrbitalBubbleTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final double EARTH_MU = 3.986004418e14;
    /** About 400 km up: a 92-minute orbit. */
    private static final double LOW_ORBIT = 6_771_000.0;
    private static final float[] BOX = {-5, -5, 5, -5, 5, 5, -5, 5};

    private PhysicsManager physics;
    private Engine engine;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        engine.addSystem(new TidalGravitySystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new KinematicTransientSystem(physics));
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnBox(float x, float y) {
        Entity e = new Entity();
        e.add(new TransformComponent());
        e.add(new PhysicsBodyComponent(physics.createPolygonBody(e, x, y, 0f, BOX, 10f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        engine.addEntity(e);
        return e;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void runTicks(int ticks) {
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    @Test
    void tidalFieldMatchesHillApproximation() {
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        double n2 = EARTH_MU / (LOW_ORBIT * LOW_ORBIT * LOW_ORBIT);
        Vector2 a = new Vector2();

        // The anchor starts on +X, so local +X is radial (outward) and +Y is along-track.
        physics.getTidalField().accelAt(25_000f, 0f, a);
        // In this non-rotating frame the radial term is 2 n^2 d (Hill's 3 n^2 d adds the centrifugal part).
        assertEquals(2.0 * n2 * 25_000.0, a.x, 2.0 * n2 * 25_000.0 * 0.01, "radial: about 2 n^2 d outward");
        assertEquals(0f, a.y, 1e-6f);
        assertTrue(a.x > 0.05f && a.x < 0.2f, "under 0.1 m/s^2 at the bubble edge in low orbit: " + a.x);

        physics.getTidalField().accelAt(0f, 10_000f, a);
        assertEquals(-n2 * 10_000.0, a.y, n2 * 10_000.0 * 0.01, "along-track: about n^2 d back toward the anchor");

        physics.getTidalField().accelAt(0f, 0f, a);
        assertEquals(0f, a.len(), 1e-9f);
    }

    @Test
    void releasedShipFollowsItsOwnOrbit() {
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        // 1 km above and 2 km ahead of the anchor, drifting 1 m/s outward.
        Entity ship = spawnBox(1000f, 2000f);
        PhysicsManager.setVelocity(body(ship), 1f, 0f);
        double[] start = new double[4];
        physics.getTrueState(body(ship), start);

        int ticks = 60 * 1380; // a quarter orbit
        runTicks(ticks);

        double[] expected = start.clone();
        PointMassAnchor reference = new PointMassAnchor(EARTH_MU, start[0], start[1], start[2], start[3], 0.0);
        reference.stateAt(ticks / 60.0, expected);
        double[] actual = new double[4];
        physics.getTrueState(body(ship), actual);

        double error = Math.hypot(actual[0] - expected[0], actual[1] - expected[1]);
        double drift = Math.hypot(actual[0] - physics.getOriginX(), actual[1] - physics.getOriginY());
        assertTrue(error < 1.0, "true position within 1 m of a direct two-body orbit, was " + error + " m");
        assertTrue(Math.abs(drift - Math.hypot(1000, 2000)) > 100.0, "and it really drifted from the anchor: " + drift);
        assertEquals(expected[2], actual[2], 0.01);
        assertEquals(expected[3], actual[3], 0.01);
    }

    @Test
    void withoutTidalGravityTheShipWouldStrayFromItsOrbit() {
        engine.removeSystem(engine.getSystem(TidalGravitySystem.class));
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        Entity ship = spawnBox(1000f, 2000f);
        double[] start = new double[4];
        physics.getTrueState(body(ship), start);

        int ticks = 60 * 1380;
        runTicks(ticks);

        double[] expected = new double[4];
        new PointMassAnchor(EARTH_MU, start[0], start[1], start[2], start[3], 0.0).stateAt(ticks / 60.0, expected);
        double[] actual = new double[4];
        physics.getTrueState(body(ship), actual);
        assertTrue(Math.hypot(actual[0] - expected[0], actual[1] - expected[1]) > 100.0,
                "sanity check: the tidal term is what keeps the orbit right");
    }

    @Test
    void rebasingInOrbitKeepsTheTrajectory() {
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        Entity player = spawnBox(0f, 0f);
        Entity other = spawnBox(3000f, -1500f);
        // Off the anchor's orbit: 5 m/s outward, so it climbs and falls behind, staying inside the bubble.
        PhysicsManager.setVelocity(body(player), 5f, 0f);

        double[] playerStart = new double[4], otherStart = new double[4];
        physics.getTrueState(body(player), playerStart);
        physics.getTrueState(body(other), otherStart);

        // Rebase onto the player every 10 s, as FloatingOriginSystem would, re-anchoring the orbit each time.
        int ticks = 60 * 1380;
        Vector2 p = new Vector2(), v = new Vector2();
        for (int i = 0; i < ticks; i += 600) {
            runTicks(Math.min(600, ticks - i));
            PhysicsManager.getPosition(body(player), p);
            PhysicsManager.getVelocity(body(player), v);
            physics.rebaseOrigin(p.x, p.y, v.x, v.y);
        }

        double t = ticks / 60.0;
        double[] expected = new double[4], actual = new double[4];
        new PointMassAnchor(EARTH_MU, playerStart[0], playerStart[1], playerStart[2], playerStart[3], 0.0).stateAt(t, expected);
        physics.getTrueState(body(player), actual);
        assertTrue(Math.hypot(actual[0] - expected[0], actual[1] - expected[1]) < 1.0, "player stays on its orbit");

        new PointMassAnchor(EARTH_MU, otherStart[0], otherStart[1], otherStart[2], otherStart[3], 0.0).stateAt(t, expected);
        physics.getTrueState(body(other), actual);
        double otherError = Math.hypot(actual[0] - expected[0], actual[1] - expected[1]);
        assertTrue(otherError < 1.0, "other body too, error " + otherError + " m, " + Math.hypot(actual[0] - physics.getOriginX(), actual[1] - physics.getOriginY()) + " m from the anchor");
    }

    @Test
    void trueStateRoundTripsThroughTheBubble() {
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        runTicks(10);
        Entity ship = spawnBox(0f, 0f);
        double x = physics.getOriginX() + 12_345.5, y = physics.getOriginY() - 678.25;
        double vx = physics.getOriginVx() - 40.0, vy = physics.getOriginVy() + 12.5;

        physics.setTrueState(body(ship), x, y, vx, vy);

        Vector2 p = PhysicsManager.getPosition(body(ship), new Vector2());
        assertEquals(12_345.5f, p.x, 0.01f);
        assertEquals(-678.25f, p.y, 0.01f);
        double[] back = new double[4];
        physics.getTrueState(body(ship), back);
        assertEquals(x, back[0], 0.01);
        assertEquals(y, back[1], 0.01);
        assertEquals(vx, back[2], 1e-3);
        assertEquals(vy, back[3], 1e-3);
    }

    @Test
    void simTimeCountsTicks() {
        runTicks(90);
        assertEquals(90L, physics.getTick());
        assertEquals(1.5, physics.getSimSeconds(), 1e-12);
    }

    @Test
    void fastPassSweepsThroughShipOnce() {
        Entity ship = spawnBox(0f, 0f);
        Entity rock = new Entity();
        rock.add(new TransformComponent());
        // 3 km/s along +X, starting 2 km to the west: crosses the ship in under a second.
        KinematicTransientComponent rockPass = new KinematicTransientComponent(new LinearTrajectory(-2000.0, 1.0, 3000.0, 0.0, 0.0), 2f);
        rockPass.mass = 500f;
        rock.add(rockPass);
        engine.addEntity(rock);

        List<FastPassImpactEvent> impacts = new ArrayList<>();
        physics.fastPassImpacts.add((signal, e) -> {
            assertFalse(physics.getWorld().isLocked());
            impacts.add(e);
        });

        runTicks(120);

        assertEquals(1, impacts.size());
        FastPassImpactEvent hit = impacts.get(0);
        assertEquals(rock, hit.transientEntity);
        assertEquals(ship, hit.target);
        assertEquals(-5f, hit.pointX, 0.01f);
        assertEquals(-1f, hit.normalX, 1e-3f);
        assertEquals(3000f, hit.relativeSpeed, 1f);
        assertEquals(1f, hit.dirX, 1e-3f);
        assertEquals(500f, hit.transientMass);

        assertEquals(1, physics.getWorld().getBodyCount(), "a fast pass never becomes a Box2D body");
        KinematicTransientComponent kt = rock.getComponent(KinematicTransientComponent.class);
        assertEquals(-2000f + 3000f * 2f, kt.x, 0.1f);
        assertEquals(kt.x, rock.getComponent(TransformComponent.class).x, 1e-3f);
        assertEquals(3000f, kt.relativeSpeed, 0.01f);
    }

    @Test
    void wideFastPassClipsShipWithItsEdge() {
        Entity ship = spawnBox(0f, 0f);
        Entity hulk = new Entity();
        // Center line passes 20 m north of the ship, but the hulk is 30 m wide.
        hulk.add(new KinematicTransientComponent(new LinearTrajectory(-2000.0, 20.0, 3000.0, 0.0, 0.0), 15f));
        // A ship that was in Box2D before: its mass comes from the shape it kept.
        BodyShapeComponent shape = new BodyShapeComponent();
        shape.mass = 2.0e6f;
        hulk.add(shape);
        engine.addEntity(hulk);
        List<FastPassImpactEvent> impacts = new ArrayList<>();
        physics.fastPassImpacts.add((signal, e) -> impacts.add(e));

        runTicks(120);

        assertEquals(1, impacts.size());
        assertEquals(ship, impacts.get(0).target);
        assertEquals(2.0e6f, impacts.get(0).transientMass);
    }

    @Test
    void fastPassStillHitsAcrossARebase() {
        FloatingOriginSystem origin = new FloatingOriginSystem(physics, 2000f, 50f);
        engine.addSystem(origin);
        Entity player = spawnBox(0f, 0f);
        player.add(new PlayerControllerComponent());
        PhysicsManager.setVelocity(body(player), 100f, 0f); // rebases on the first tick
        Entity rock = new Entity();
        rock.add(new KinematicTransientComponent(new LinearTrajectory(-3000.0, 0.0, 2500.0, 0.0, 0.0), 0f));
        engine.addEntity(rock);
        List<FastPassImpactEvent> impacts = new ArrayList<>();
        physics.fastPassImpacts.add((signal, e) -> impacts.add(e));

        runTicks(120);

        assertTrue(origin.getRebaseCount() >= 1);
        assertEquals(1, impacts.size());
        assertEquals(2400f, impacts.get(0).relativeSpeed, 1f, "closing speed is relative to the moving ship");
    }

    @Test
    void rayCastAndQueryWorkInMeters() {
        Entity ship = spawnBox(100f, 50f);
        float[] hitPoint = new float[2];
        physics.rayCast(0f, 50f, 200f, 50f, (fixture, x, y, nx, ny, fraction) -> {
            hitPoint[0] = x;
            hitPoint[1] = y;
            return fraction;
        });
        assertEquals(95f, hitPoint[0], 0.01f);
        assertEquals(50f, hitPoint[1], 0.01f);

        List<Body> found = new ArrayList<>();
        physics.queryAABB(96f, 46f, 98f, 48f, fixture -> found.add(fixture.getBody()));
        assertEquals(1, found.size());
        assertEquals(body(ship), found.get(0));
        found.clear();
        physics.queryAABB(200f, 200f, 210f, 210f, fixture -> found.add(fixture.getBody()));
        assertTrue(found.isEmpty());
    }

    @Test
    void offCenterImpulseMovesAndSpinsInSIUnits() {
        Entity ship = spawnBox(0f, 0f);
        Body b = body(ship);
        // 1,000 kg: 500 N*s gives 0.5 m/s. Applied 5 m off-center: torque arm 5 m.
        PhysicsManager.applyImpulse(b, 0f, 500f, 5f, 0f);
        Vector2 v = PhysicsManager.getVelocity(b, new Vector2());
        assertEquals(0.5f, v.y, 1e-4f);
        assertEquals(500f * 5f / PhysicsManager.getInertia(b), b.getAngularVelocity(), 1e-4f);
    }

    @Test
    void massDataFromTheGridOverridesDensity() {
        Entity ship = spawnBox(0f, 0f);
        Body b = body(ship);
        PhysicsManager.setMassData(b, 25_000f, 2f, -1f, 900_000f);

        assertEquals(25_000f, PhysicsManager.getMass(b), 0.1f);
        assertEquals(900_000f, PhysicsManager.getInertia(b), 1f);
        assertEquals(2f, b.getLocalCenter().x * PhysicsManager.METERS_PER_UNIT, 1e-4f);
        assertEquals(-1f, b.getLocalCenter().y * PhysicsManager.METERS_PER_UNIT, 1e-4f);
    }

    @Test
    void previousPoseIsLastTicksPose() {
        Entity ship = spawnBox(0f, 0f);
        PhysicsManager.setVelocity(body(ship), 60f, 0f);
        runTicks(10);
        float[] pose = new float[3];
        assertTrue(physics.getPreviousPose(body(ship), pose));
        Vector2 now = PhysicsManager.getPosition(body(ship), new Vector2());
        assertEquals(now.x - 1f, pose[0], 1e-3f);
    }

    @Test
    void keplerAnchorAgreesWithIntegratedAnchorAndKeepsShipsOnTheirOrbits() {
        KeplerAnchor kepler = new KeplerAnchor(KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 0.0, 0.0, 1));
        PointMassAnchor integrated = PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0);
        double[] a = new double[4], b = new double[4];
        kepler.stateAt(1380.0, a);
        integrated.stateAt(1380.0, b);
        assertEquals(b[0], a[0], 0.01);
        assertEquals(b[1], a[1], 0.01);

        physics.setAnchorModel(new KeplerAnchor(KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 0.0, 0.0, 1)));
        Entity ship = spawnBox(1000f, 2000f);
        PhysicsManager.setVelocity(body(ship), 1f, 0f);
        double[] start = new double[4];
        physics.getTrueState(body(ship), start);
        int ticks = 60 * 1380;
        Vector2 p = new Vector2(), v = new Vector2();
        for (int i = 0; i < ticks; i += 600) {
            runTicks(Math.min(600, ticks - i));
            // Rebase onto the ship now and then: the anchor becomes the conic through its state.
            PhysicsManager.getPosition(body(ship), p);
            PhysicsManager.getVelocity(body(ship), v);
            physics.rebaseOrigin(p.x, p.y, v.x, v.y);
        }

        StateVector expected = KeplerOrbit.fromState(start[0], start[1], start[2], start[3], EARTH_MU, 0.0)
                .stateAt(ticks / 60.0);
        double[] actual = new double[4];
        physics.getTrueState(body(ship), actual);
        double error = Math.hypot(actual[0] - expected.x, actual[1] - expected.y);
        assertTrue(error < 1.0, "within 1 m of the ship's own conic: " + error);
    }
}
