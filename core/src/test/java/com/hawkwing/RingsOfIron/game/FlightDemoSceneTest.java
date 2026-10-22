package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Flies the flight demo headless: the game's corvette in the game's world, among the demo's rocks. */
class FlightDemoSceneTest {
    static final float FRAME = 1f / 60f;

    FlightDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new FlightDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) demo.update(FRAME);
    }

    private float speed() {
        return demo.gridVelocity(new Vector2()).len();
    }

    @Test
    void theGamesCorvetteFliesAmongTheDemosRocks() {
        demo.load(false);
        assertSame(demo.player, demo.world.getPlayer());
        assertNotNull(demo.player.getComponent(ShipGridComponent.class), "the game's corvette, with its deck");
        assertNotNull(demo.body());
        assertEquals(FlightDemoScene.ASTEROID_COUNT, demo.asteroids.size);
        Vector2 at = PhysicsManager.getPosition(demo.body(), new Vector2());
        for (Entity rock : demo.asteroids) {
            Body body = rock.getComponent(PhysicsBodyComponent.class).body;
            assertTrue(PhysicsManager.getPosition(body, new Vector2()).dst(at) >= FlightDemoScene.ASTEROID_CLEARANCE);
        }
    }

    @Test
    void nothingSlowsTheShipDown() {
        demo.load(false);
        demo.controls().rotationAssist = false;
        float start = speed();
        assertEquals(Vector2.len(FlightDemoScene.START_VX, FlightDemoScene.START_VY), start, 0.1f);
        run(3f);
        assertEquals(start, speed(), 0.1f, "it keeps its speed with nobody at the controls");
        assertEquals(FlightDemoScene.START_SPIN, demo.body().getAngularVelocity(), 0.01f, "and its spin");
    }

    @Test
    void theMainEngineSpeedsTheShipAlongItsNose() {
        demo.load(false);
        Body body = demo.body();
        PhysicsManager.setVelocity(body, 0f, 0f);
        body.setAngularVelocity(0f);
        FlightControlComponent c = demo.controls();
        for (int i = 0; i < 120; i++) {
            c.clear();
            c.forward = 1f;
            demo.update(FRAME);
        }
        Vector2 v = PhysicsManager.getVelocity(body, new Vector2());
        Vector2 nose = new Vector2(1f, 0f).rotateRad(body.getAngle());
        assertTrue(v.dot(nose) > 1f, "accelerated along the nose: " + v);
    }

    @Test
    void rotationAssistStopsTheTumble() {
        demo.load(false);
        demo.controls().rotationAssist = false;
        assertTrue(demo.toggleRotationAssist());
        run(10f);
        assertEquals(0f, demo.body().getAngularVelocity(), 0.02f);
    }

    @Test
    void warpStepsThroughTheGamesPhysicsLevelsOnly() {
        demo.load(false);
        assertEquals(1, demo.warp());
        int[] levels = TimeWarpController.PHYSICS_LEVELS;
        for (int i = 1; i < levels.length + 2; i++) demo.stepWarp(1);
        assertEquals(levels[levels.length - 1], demo.warp());
        assertFalse(demo.world.timeWarp.isRailsWarp());
        demo.stepWarp(-1);
        assertEquals(levels[levels.length - 2], demo.warp());
    }

    @Test
    void aFastPassCrossesTheBubbleAndIsGone() {
        demo.load(false);
        Entity rock = demo.spawnFastPass();
        run(1f);
        KinematicTransientComponent kt = rock.getComponent(KinematicTransientComponent.class);
        assertTrue(kt.hasPrevious, "swept through the bubble");
        assertTrue(kt.relativeSpeed > FlightDemoScene.FAST_PASS_SPEED * 0.9f);
        run(FlightDemoScene.FAST_PASS_LIFETIME);
        assertEquals(0, demo.fastPasses.size);
        assertFalse(demo.world.engine.getEcsEngine().getEntities().contains(rock, true));
    }

    @Test
    void ramIntoARockAndTheNoticeSaysSo() {
        demo.load(false);
        Body ship = demo.body();
        Body rock = demo.asteroids.first().getComponent(PhysicsBodyComponent.class).body;
        Vector2 at = PhysicsManager.getPosition(rock, new Vector2());
        PhysicsManager.setPosition(ship, at.x - 80f, at.y, 0f);
        PhysicsManager.setVelocity(ship, 40f, 0f);
        PhysicsManager.setVelocity(rock, 0f, 0f);
        String seen = null;
        for (int i = 0; i < 60 * 5 && seen == null; i++) {
            demo.update(FRAME);
            seen = demo.impact();
        }
        assertNotNull(seen);
        assertTrue(seen.startsWith("IMPACT"), seen);
    }

    @Test
    void theGridNeverJumpsWhenTheOriginRebases() {
        demo.load(false);
        Body body = demo.body();
        PhysicsManager.setVelocity(body, 300f, 0f);
        double last = demo.gridOriginX() + PhysicsManager.getPosition(body, new Vector2()).x;
        int rebases = 0;
        double lastOrigin = demo.world.physics.getOriginX();
        for (int i = 0; i < 60 * 10; i++) {
            demo.update(FRAME);
            double now = demo.gridOriginX() + PhysicsManager.getPosition(body, new Vector2()).x;
            assertEquals(300.0 * FRAME, now - last, 0.5, "frame " + i);
            last = now;
            if (demo.world.physics.getOriginX() != lastOrigin) rebases++;
            lastOrigin = demo.world.physics.getOriginX();
        }
        assertTrue(rebases > 0, "the origin moved along with the ship");
    }

    @Test
    void inOrbitTheBubbleFallsAroundTheEarth() {
        demo.load(true);
        assertTrue(demo.orbitMode);
        assertEquals(GameScenario.EARTH, demo.world.physics.getAnchorParentUuid());
        assertNotNull(demo.world.physics.getAnchorModel());
        Vector2 tidal = demo.world.physics.getTidalField().accelAt(1000f, 0f, new Vector2());
        assertTrue(tidal.len() > 0f, "neighbors feel the tidal pull");
        demo.controls().rotationAssist = false;
        float start = speed();
        run(5f);
        assertNull(demo.world.getCrash(), "still in orbit");
        // In the frame that falls with the bubble the ship keeps its drift
        assertEquals(start, speed(), 0.5f);
        assertEquals(demo.asteroids.size, FlightDemoScene.ASTEROID_COUNT);
    }

    @Test
    void reloadingStartsAFreshWorld() {
        demo.load(false);
        GameWorld first = demo.world;
        demo.spawnFastPass();
        demo.load(true);
        assertNotSame(first, demo.world);
        assertEquals(0, demo.fastPasses.size);
        demo.load(false);
        assertNull(demo.world.physics.getAnchorParentUuid());
    }
}
