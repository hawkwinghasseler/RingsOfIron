package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.bubble.GameBubbleGateway;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameBubbleGatewayTest {
    GameWorld world;
    GameBubbleGateway gateway;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        gateway = new GameBubbleGateway(world.physics, world.engine.getUuidRegistry());
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private static final Trajectory STILL = new Trajectory() {
        @Override
        public StateVector stateAt(double t, StateVector out) {
            return out.set(0, 0, 0, 0);
        }

        @Override
        public double minRadius() {
            return 0;
        }

        @Override
        public double maxRadius() {
            return 0;
        }

        @Override
        public double maxSpeed() {
            return 0;
        }
    };

    private Entity rails(BodyShapeComponent shape) {
        Entity e = world.engine.createEntityWithUuid();
        if (shape != null) e.add(shape);
        world.engine.addEntity(e);
        return e;
    }

    @Test
    void aFastPassWeighsWhatItsBodyWould() {
        Entity never = rails(null);
        gateway.beginKinematicTransient(never.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id, STILL);
        assertEquals(40f * 12f * GameBubbleGateway.DEFAULT_HULL_DENSITY,
                never.getComponent(KinematicTransientComponent.class).mass, 1f);

        BodyShapeComponent shape = new BodyShapeComponent();
        shape.mass = 123_000f;
        Entity collapsed = rails(shape);
        gateway.beginKinematicTransient(collapsed.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id, STILL);
        assertEquals(123_000f, collapsed.getComponent(KinematicTransientComponent.class).mass);
    }

    @Test
    void theOldBoxIsKnownWhenItComesBackSaved() {
        // A freighter saved on the old box comes back on its own hull, not the box
        Entity e = rails(null);
        Body box = world.physics.createPolygonBody(e, 0f, 0f, 0f, GameBubbleGateway.DEFAULT_HULL,
                GameBubbleGateway.DEFAULT_HULL_DENSITY, (short) 1, (short) -1);
        assertTrue(GameBubbleGateway.isDefaultHull(BodyShapeComponent.capture(box)));
        Body other = world.physics.createPolygonBody(e, 0f, 0f, 0f, new float[]{-10f, -6f, 10f, -6f, 10f, 6f, -10f, 6f},
                GameBubbleGateway.DEFAULT_HULL_DENSITY, (short) 1, (short) -1);
        assertFalse(GameBubbleGateway.isDefaultHull(BodyShapeComponent.capture(other)));
    }
}
