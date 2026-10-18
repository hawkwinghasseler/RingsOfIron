package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Collapsing the bubble onto rails for rails warp, and rebuilding it on drop-out. */
class RailsBubbleHandoffTest {
    private static final double EARTH_MU = 3.986004418e14;
    private static final double LOW_ORBIT = 6_771_000.0;
    private static final String EARTH = "earth-uuid";
    private static final float[] HULL = {-10, -4, 10, -4, 12, 0, 10, 4, -10, 4};

    private PhysicsManager physics;
    private GameEngine game;
    private RailsBubbleHandoff handoff;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        physics.setAnchorModel(new KeplerAnchor(KeplerOrbit.circular(EARTH_MU, LOW_ORBIT, 0.0, 0.0, 1)), EARTH);
        game.addSystem(new TidalGravitySystem(physics));
        game.addSystem(new PhysicsUpdateSystem(physics));
        handoff = new RailsBubbleHandoff(physics, game.getEcsEngine());
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    private Entity spawn(float x, float y, float vx, float vy) {
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        Body body = physics.createPolygonBody(e, x, y, 0.4f, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        PhysicsManager.setVelocity(body, vx, vy);
        body.setAngularVelocity(0.05f);
        e.add(new PhysicsBodyComponent(body));
        game.addEntity(e);
        return e;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    @Test
    void collapseThenRebuildAtOnceLeavesEverythingWhereItWas() {
        Entity player = spawn(0f, 0f, 2f, 0f);
        player.add(new PlayerControllerComponent());
        Entity wingman = spawn(300f, -150f, 0f, 1f);
        PhysicsManager.setMassData(body(wingman), 42_000f, 1f, 0f, 2.0e6f);
        game.stepTicks(60);
        double[] before = new double[4];
        physics.getTrueState(body(wingman), before);
        Vector2 offset = PhysicsManager.getPosition(body(wingman), new Vector2())
                .sub(PhysicsManager.getPosition(body(player), new Vector2()));

        handoff.collapseToRails();
        assertEquals(0, physics.getWorld().getBodyCount(), "Box2D is empty under rails warp");
        assertNotNull(player.getComponent(OrbitComponent.class));
        assertEquals(EARTH, wingman.getComponent(OrbitComponent.class).parentUuid);
        assertNotNull(wingman.getComponent(BodyShapeComponent.class));

        handoff.rebuildFromRails();
        assertEquals(2, physics.getWorld().getBodyCount());
        assertNull(wingman.getComponent(OrbitComponent.class));
        double[] after = new double[4];
        physics.getTrueState(body(wingman), after);
        for (int i = 0; i < 2; i++) assertEquals(before[i], after[i], 0.01);
        for (int i = 2; i < 4; i++) assertEquals(before[i], after[i], 1e-3);
        Vector2 newOffset = PhysicsManager.getPosition(body(wingman), new Vector2())
                .sub(PhysicsManager.getPosition(body(player), new Vector2()));
        assertEquals(offset.x, newOffset.x, 0.01f);
        assertEquals(offset.y, newOffset.y, 0.01f);
        assertEquals(0f, PhysicsManager.getPosition(body(player), new Vector2()).len(), 0.01f, "anchor is on the player");
        assertEquals(42_000f, PhysicsManager.getMass(body(wingman)), 0.5f, "mass data survives the trip");
        assertEquals(2.0e6f, PhysicsManager.getInertia(body(wingman)), 10f);
        assertEquals(0.05f, body(wingman).getAngularVelocity(), 1e-6f);
    }

    @Test
    void afterRailsWarpBodiesReturnOnTheirOrbitsAndFarOnesStayOnRails() {
        Entity player = spawn(0f, 0f, 0f, 0f);
        player.add(new PlayerControllerComponent());
        Entity near = spawn(800f, 400f, 0.5f, -0.2f);
        Entity far = spawn(15_000f, 0f, 20f, 0f);
        game.stepTicks(1);
        double[] nearStart = new double[4];
        physics.getTrueState(body(near), nearStart);
        double t0 = physics.getSimSeconds();

        handoff.collapseToRails();
        assertTrue(game.advanceRails(60L * 1800)); // half an hour on rails
        handoff.rebuildFromRails();

        double t = physics.getSimSeconds();
        StateVector expected = KeplerOrbit.fromState(nearStart[0], nearStart[1], nearStart[2], nearStart[3], EARTH_MU, t0)
                .stateAt(t);
        double[] actual = new double[4];
        physics.getTrueState(body(near), actual);
        assertEquals(expected.x, actual[0], 0.05);
        assertEquals(expected.y, actual[1], 0.05);

        // 20 m/s outward from 15 km: half an hour later it is far outside the bubble.
        assertNull(far.getComponent(PhysicsBodyComponent.class));
        assertNotNull(far.getComponent(OrbitComponent.class));
        assertNotNull(far.getComponent(BodyShapeComponent.class), "kept for a later entry handoff");
        assertEquals(2, physics.getWorld().getBodyCount());

        // The bubble keeps working afterwards.
        game.stepTicks(60);
        assertEquals(physics.getTick(), game.getTick());
    }
}
