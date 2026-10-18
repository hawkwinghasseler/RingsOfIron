package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.physicsexterior.anchor.PointMassAnchor;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void saveAndLoadRestoresTrueStatesWhenBodiesAreRebuilt() {
        Entity ship = spawnShip(1200f, -800f);
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
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
        assertEquals(0, physics.getWorld().getBodyCount(), "old bodies went with the old entities");

        Entity loaded = game.getEntity(uuid);
        assertNull(loaded.getComponent(PhysicsBodyComponent.class));
        Body rebuilt = addBody(loaded);

        double[] after = new double[4];
        physics.getTrueState(rebuilt, after);
        for (int i = 0; i < 2; i++) assertEquals(before[i], after[i], 0.01);
        for (int i = 2; i < 4; i++) assertEquals(before[i], after[i], 1e-3);
        assertEquals(angle, rebuilt.getAngle(), 1e-5f);
        assertEquals(0.2f, rebuilt.getAngularVelocity(), 1e-5f);
        assertEquals(600L, physics.getTick());
    }
}
