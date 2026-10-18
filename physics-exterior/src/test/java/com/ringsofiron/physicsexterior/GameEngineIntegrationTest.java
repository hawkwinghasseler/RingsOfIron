package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.anchor.PointMassAnchor;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Physics driven by core-engine's GameEngine: one sim clock, rails catch-up, save and load. */
class GameEngineIntegrationTest {
    private static final double EARTH_MU = 3.986004418e14;
    private static final double LOW_ORBIT = 6_771_000.0;
    private static final float[] BOX = {-5, -5, 5, -5, 5, 5, -5, 5};

    private PhysicsManager physics;
    private GameEngine game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        physics.setAnchorModel(PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0));
        game.addSystem(new TidalGravitySystem(physics));
        game.addSystem(new PhysicsUpdateSystem(physics));
        PhysicsSaveParticipant.install(physics, game);
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    /** What a ship assembler does: an entity with a UUID and a body at the local origin. */
    private Body addBody(Entity entity) {
        Body body = physics.createPolygonBody(entity, 0f, 0f, 0f, BOX, 10f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        entity.add(new PhysicsBodyComponent(body));
        return body;
    }

    private Entity spawnShip(float x, float y) {
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        Body body = addBody(e);
        PhysicsManager.setPosition(body, x, y, 0.3f);
        game.addEntity(e);
        return e;
    }

    @Test
    void physicsUsesTheGameClock() {
        game.stepTicks(120);
        assertEquals(120L, game.getTick());
        assertEquals(game.getTick(), physics.getTick());

        double[] expected = new double[4];
        PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0).stateAt(2.0, expected);
        assertEquals(expected[0], physics.getOriginX(), 1e-6);
        assertEquals(expected[1], physics.getOriginY(), 1e-6);
    }

    @Test
    void railsWarpMovesTheAnchorToTheNewTime() {
        game.stepTicks(1);
        assertTrue(game.advanceRails(60L * 600));

        double[] expected = new double[4];
        PointMassAnchor.circular(EARTH_MU, LOW_ORBIT, 0.0).stateAt(physics.getSimSeconds(), expected);
        assertEquals(601.0 + 1.0 / 60.0 - 1.0, physics.getSimSeconds(), 1e-9);
        assertEquals(expected[0], physics.getOriginX(), 1e-3);
        assertEquals(expected[1], physics.getOriginY(), 1e-3);
    }

    @Test
    void saveAndLoadRebuildsBodiesWithTheirStateAndMass() {
        Entity ship = spawnShip(1200f, -800f);
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        // Mass from elsewhere (an interior grid), not from fixture density.
        PhysicsManager.setMassData(body, 42_000f, 1.5f, -0.5f, 3.0e6f);
        PhysicsManager.setVelocity(body, 3f, -2f);
        body.setAngularVelocity(0.2f);
        game.stepTicks(600);

        String uuid = ship.getComponent(UuidComponent.class).id;
        double[] before = new double[4];
        physics.getTrueState(body, before);
        float angle = body.getAngle();
        String save = game.getSerializationService().saveGame();
        assertFalse(save.contains("PhysicsBodyComponent"), "live Box2D bodies are not saved");
        assertTrue(save.contains(PhysicsSaveParticipant.SAVE_KEY));

        // Somewhere else entirely before loading.
        physics.rebaseOrigin(5000f, 0f, 40f, 0f);
        game.getSerializationService().loadGame(save);
        assertEquals(1, physics.getWorld().getBodyCount(), "the old body went, the saved one came back");

        Entity loaded = game.getEntity(uuid);
        Body rebuilt = loaded.getComponent(PhysicsBodyComponent.class).body;
        assertSame(loaded, rebuilt.getUserData());
        double[] after = new double[4];
        physics.getTrueState(rebuilt, after);
        for (int i = 0; i < 2; i++) assertEquals(before[i], after[i], 0.01);
        for (int i = 2; i < 4; i++) assertEquals(before[i], after[i], 1e-3);
        assertEquals(angle, rebuilt.getAngle(), 1e-5f);
        assertEquals(0.2f, rebuilt.getAngularVelocity(), 1e-5f);
        assertEquals(42_000f, PhysicsManager.getMass(rebuilt), 1f);
        assertEquals(3.0e6f, PhysicsManager.getInertia(rebuilt), 3.0e6f * 1e-4f);
        assertEquals(1.5f, rebuilt.getLocalCenter().x * PhysicsManager.METERS_PER_UNIT, 1e-4f);
        assertEquals(600L, physics.getTick());

        // The rebuilt body flies on with the bubble: a few meters at a few m/s, not left behind.
        float x0 = rebuilt.getPosition().x * PhysicsManager.METERS_PER_UNIT;
        float y0 = rebuilt.getPosition().y * PhysicsManager.METERS_PER_UNIT;
        game.stepTicks(60);
        float moved = (float) Math.hypot(rebuilt.getPosition().x * PhysicsManager.METERS_PER_UNIT - x0,
                rebuilt.getPosition().y * PhysicsManager.METERS_PER_UNIT - y0);
        assertTrue(moved > 1f && moved < 10f, "moved " + moved + " m");
    }

    @Test
    void stateFromAnOlderSaveWaitsForTheBodyToBeBuilt() {
        Entity ship = spawnShip(1200f, -800f);
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        PhysicsManager.setVelocity(body, 3f, -2f);
        String uuid = ship.getComponent(UuidComponent.class).id;
        double[] before = new double[4];
        physics.getTrueState(body, before);
        // Rewrite the body entries to the old format: the bare state array, no shape.
        JsonValue root = new JsonReader().parse(game.getSerializationService().saveGame());
        JsonValue bodies = root.get("modules").get(PhysicsSaveParticipant.SAVE_KEY).get("bodies");
        for (JsonValue entry = bodies.child; entry != null; entry = entry.next) {
            JsonValue state = entry.get("state");
            entry.setType(JsonValue.ValueType.array);
            entry.child = state.child;
            for (JsonValue v = entry.child; v != null; v = v.next) v.parent = entry;
            entry.size = state.size;
        }
        String save = root.toJson(JsonWriter.OutputType.json);
        assertFalse(save.contains("shape"), save);

        game.getSerializationService().loadGame(save);
        Entity loaded = game.getEntity(uuid);
        assertNull(loaded.getComponent(PhysicsBodyComponent.class), "nothing to rebuild it from");
        Body rebuilt = addBody(loaded);
        double[] after = new double[4];
        physics.getTrueState(rebuilt, after);
        for (int i = 0; i < 4; i++) assertEquals(before[i], after[i], 0.01);
    }

    @Test
    void loadIntoAFreshWorldRestoresTheOrbitingAnchor() {
        physics.setAnchorModel(new KeplerAnchor(KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 0.0, 0.0, 1)), "earth");
        spawnShip(100f, 50f);
        game.stepTicks(600);
        double[] before = {physics.getOriginX(), physics.getOriginY(), physics.getOriginVx(), physics.getOriginVy()};
        String save = game.getSerializationService().saveGame();

        // A new game on its default inertial anchor, as when loading from the title screen.
        GameEngine fresh = new GameEngine();
        PhysicsManager freshPhysics = new PhysicsManager();
        fresh.addSystem(new PhysicsUpdateSystem(freshPhysics));
        PhysicsSaveParticipant.install(freshPhysics, fresh);
        List<String> changes = new ArrayList<>();
        freshPhysics.anchorChanges.add((sig, e) -> changes.add(e.kind + " " + e.parentUuid));
        try {
            fresh.getSerializationService().loadGame(save);
            assertEquals(List.of("SET earth"), changes, "rails tracking hears about the loaded anchor");
            assertTrue(freshPhysics.getAnchorModel() instanceof KeplerAnchor);
            assertEquals(EARTH_MU, freshPhysics.getAnchorModel().getMu(), 1.0);
            assertEquals("earth", freshPhysics.getAnchorParentUuid());
            assertEquals(before[0], freshPhysics.getOriginX(), 1e-3);
            assertEquals(before[1], freshPhysics.getOriginY(), 1e-3);
            assertEquals(before[2], freshPhysics.getOriginVx(), 1e-6);
            assertEquals(before[3], freshPhysics.getOriginVy(), 1e-6);

            // And it keeps falling along the same orbit.
            fresh.stepTicks(600);
            game.stepTicks(600);
            assertEquals(physics.getOriginX(), freshPhysics.getOriginX(), 1e-2);
            assertEquals(physics.getOriginY(), freshPhysics.getOriginY(), 1e-2);
        } finally {
            fresh.clear();
            freshPhysics.dispose();
        }
    }
}
