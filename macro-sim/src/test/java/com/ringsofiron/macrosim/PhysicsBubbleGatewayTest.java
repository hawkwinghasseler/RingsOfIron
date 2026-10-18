package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.bubble.BubbleTransitionSystem;
import com.ringsofiron.macrosim.bubble.EncounterPredictionTask;
import com.ringsofiron.macrosim.bubble.PhysicsBubbleGateway;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.anchor.PointMassAnchor;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BubbleTransitionSystem against a real Box2D bubble from physics-exterior. */
class PhysicsBubbleGatewayTest {
    static final double MU = 3.986004418e14;
    static final double R = 7_000e3;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    private final GameEngine engine = new GameEngine();
    private final PhysicsManager physics;
    private final StubRailsModel rails = new StubRailsModel();
    private final PhysicsBubbleGateway gateway;
    private final BubbleTransitionSystem bubble;

    PhysicsBubbleGatewayTest() {
        physics = new PhysicsManager();
        physics.setClock(engine);
        physics.setAnchorModel(PointMassAnchor.circular(MU, R, 0));
        engine.addSystem(new TidalGravitySystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new KinematicTransientSystem(physics));
        rails.addBody("p", MU);
        gateway = new PhysicsBubbleGateway(physics, engine.getUuidRegistry(), "p",
                e -> physics.createCircleBody(e, 0, 0, 20, 100, CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL));
        bubble = new BubbleTransitionSystem(rails, gateway);
    }

    @AfterEach
    void dispose() {
        physics.dispose();
    }

    private String spawn(StubKeplerOrbit orbit) {
        Entity e = engine.createEntityWithUuid();
        engine.addEntity(e);
        String id = e.getComponent(UuidComponent.class).id;
        rails.attach(id, "p", orbit);
        return id;
    }

    private void handoffs() {
        double t = engine.getSimSeconds();
        bubble.setForecast(new EncounterPredictionTask(rails.snapshot(), gateway.anchorTrajectory(), "p", Set.of(),
                t, 10, BubbleTransitionSystem.ENTER_RADIUS).call());
        bubble.update(t, 1);
    }

    @Test
    void neighbourGetsABox2DBodyThatKeepsFollowingItsOrbit() {
        StubKeplerOrbit orbit = StubKeplerOrbit.circular(MU, R, -10e3 / R, 0, 1);
        String id = spawn(orbit);
        handoffs();
        Entity e = engine.getEntity(id);
        assertNotNull(e.getComponent(PhysicsBodyComponent.class));
        assertFalse(rails.isOnRails(id));

        engine.stepTicks(600);
        StateVector got = gateway.trueStateOf(id, engine.getSimSeconds(), new StateVector());
        StateVector want = orbit.stateAt(engine.getSimSeconds(), new StateVector());
        assertEquals(0, Math.hypot(got.x - want.x, got.y - want.y), 0.5, "Box2D plus tidal gravity tracks the conic");

        // Push it out past the exit radius: it must go back on rails at exactly that state.
        double t = engine.getSimSeconds();
        StateVector anchor = gateway.anchorStateAt(t, new StateVector());
        physics.setTrueState(e.getComponent(PhysicsBodyComponent.class).body, anchor.x + 30e3, anchor.y, anchor.vx, anchor.vy);
        StateVector placed = gateway.trueStateOf(id, t, new StateVector());
        handoffs();
        assertTrue(rails.isOnRails(id));
        assertNull(e.getComponent(PhysicsBodyComponent.class));
        StateVector onRails = rails.stateAt(id, t, new StateVector());
        assertEquals(placed.x, onRails.x, 1e-3);
        assertEquals(placed.vy, onRails.vy, 1e-6);
        assertEquals(0, physics.getWorld().getBodyCount());
    }

    @Test
    void retrogradeCrossingBecomesAKinematicTransient() {
        String id = spawn(StubKeplerOrbit.circular(MU, R, 15e3 / R, 0, -1));
        handoffs();
        Entity e = engine.getEntity(id);
        assertNotNull(e.getComponent(KinematicTransientComponent.class));
        assertNull(e.getComponent(PhysicsBodyComponent.class));
        for (int i = 0; i < 300; i++) {
            engine.stepTicks(1);
            handoffs();
        }
        assertNull(e.getComponent(KinematicTransientComponent.class), "pass ended after leaving the bubble");
        assertTrue(rails.isOnRails(id));
    }
}
