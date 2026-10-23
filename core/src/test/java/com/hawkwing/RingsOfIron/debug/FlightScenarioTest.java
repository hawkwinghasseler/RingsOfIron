package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.FlightField;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The flight scenario on a real career's world, headless: the corvette adrift among asteroids. */
class FlightScenarioTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    DevConsole dev;
    FlightScenario flight;
    @TempDir
    Path scripts;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        dev = new DevConsole(world, game, scripts, null);
        new BuiltInOverlays().install(dev.overlays);
        DebugScenarios.Entry entry = DebugScenarios.find("flight");
        assertNotNull(entry, "the debug menu lists it");
        flight = (FlightScenario) entry.create();
        flight.setUp(new ScenarioSetup(entry, world, game, assets, dev, dev.overlays, dev.menuActions, null));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) {
            world.update(FRAME);
            flight.update(FRAME);
        }
    }

    private Body body() {
        return world.docking.bodyOf(game.player);
    }

    @Test
    void theCorvetteIsTakenClearOfTheCareerAndPutAmongRocks() {
        assertEquals(FlightField.ASTEROID_COUNT, flight.field.asteroids.size);
        Vector2 at = PhysicsManager.getPosition(body(), new Vector2());
        for (Entity rock : flight.field.asteroids) {
            Body b = rock.getComponent(PhysicsBodyComponent.class).body;
            assertTrue(PhysicsManager.getPosition(b, new Vector2()).dst(at) >= 150f, "none on top of the ship");
        }
        Body raider = world.docking.bodyOf(game.raider);
        assertTrue(PhysicsManager.getPosition(raider, new Vector2()).dst(at) > 5_000f, "well clear of the raider");
        assertTrue(dev.overlays.isShown(FlightScenario.ROCKS), "the rocks are drawn");
        assertTrue(dev.overlays.isShown(BuiltInOverlays.GRID));
        assertTrue(dev.overlays.isShown(BuiltInOverlays.VELOCITY));
        assertNotNull(dev.menuActions.find("Fast pass"));
        assertNotNull(dev.menuActions.find("Scatter rocks"));
    }

    /** The ship's speed past a far rock: the bubble's origin is rebased to follow the ship, so its own velocity isn't kept. */
    private float speedPastFarRock() {
        Body rock = flight.field.asteroids.peek().getComponent(PhysicsBodyComponent.class).body;
        return PhysicsManager.getVelocity(body(), new Vector2()).sub(PhysicsManager.getVelocity(rock, new Vector2())).len();
    }

    @Test
    void nothingSlowsTheShipDown() {
        game.player.getComponent(com.ringsofiron.physicsexterior.components.FlightControlComponent.class).rotationAssist = false;
        float start = speedPastFarRock();
        assertTrue(start > 15f, "it starts adrift: " + start);
        run(3f);
        assertEquals(start, speedPastFarRock(), 0.5f, "and keeps its speed");
        assertTrue(Math.abs(body().getAngularVelocity()) > 0.3f, "and its tumble");
    }

    @Test
    void aFastPassCrossesTheBubbleAndIsGone() {
        dev.menuActions.find("Fast pass").run.run();
        Entity rock = flight.field.fastPasses.first();
        run(1f);
        KinematicTransientComponent kt = rock.getComponent(KinematicTransientComponent.class);
        assertTrue(kt.hasPrevious, "swept through the bubble");
        assertTrue(kt.relativeSpeed > FlightField.FAST_PASS_SPEED * 0.9f);
        Object path = kt.trajectory;
        run(4f);
        assertEquals(0, flight.field.fastPasses.size);
        // The engine pools entities and components, so look for the rock's path, not its entity
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(KinematicTransientComponent.class).get())) {
            assertNotSame(path, e.getComponent(KinematicTransientComponent.class).trajectory, "the rock is gone");
        }
    }

    @Test
    void ramARockAndTheNoticeSaysSo() {
        Body ship = body();
        Body rock = flight.field.asteroids.first().getComponent(PhysicsBodyComponent.class).body;
        Vector2 at = PhysicsManager.getPosition(rock, new Vector2());
        PhysicsManager.setPosition(ship, at.x - 80f, at.y, 0f);
        PhysicsManager.setVelocity(ship, 40f, 0f);
        PhysicsManager.setVelocity(rock, 0f, 0f);
        boolean seen = false;
        for (int i = 0; i < 60 * 5 && !seen; i++) {
            run(FRAME);
            seen = world.notices.history().stream().anyMatch(n -> n.text.startsWith("IMPACT"));
        }
        assertTrue(seen, "the player was told");
    }

    @Test
    void scatteringAgainMakesAFreshField() {
        run(0.5f);
        int bodies = world.physics.getWorld().getBodyCount();
        dev.menuActions.find("Scatter rocks").run.run();
        run(0.5f);
        assertEquals(FlightField.ASTEROID_COUNT, flight.field.asteroids.size);
        assertEquals(bodies, world.physics.getWorld().getBodyCount(), "the old rocks are gone");
        assertTrue(speedPastFarRock() > 15f, "and the ship is adrift again");
    }
}
