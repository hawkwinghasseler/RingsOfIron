package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.macrosim.bubble.PhysicsBubbleGateway;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;

/**
 * macro-sim's bubble over physics-exterior, as the game runs it. The anchor's parent is read live
 * from physics (it changes at SOI crossings), and a body coming in gets back the shape it had when the
 * bubble last collapsed, or a default freighter hull if it never was in Box2D.
 */
public class GameBubbleGateway extends PhysicsBubbleGateway {
    /** Default hull, 40 m by 12 m, nose along +X. */
    static final float[] DEFAULT_HULL = {-20f, -6f, 20f, -6f, 20f, 6f, -20f, 6f};
    /** kg per square meter: about 50 t for the default hull. */
    static final float DEFAULT_HULL_DENSITY = 104f;

    private final PhysicsManager physics;

    public GameBubbleGateway(PhysicsManager physics, UuidRegistry uuids) {
        super(physics, uuids, null, entity -> build(physics, entity));
        this.physics = physics;
    }

    @Override
    public String anchorParentUuid() {
        return physics.getAnchorParentUuid();
    }

    private static Body build(PhysicsManager physics, Entity entity) {
        BodyShapeComponent shape = entity.getComponent(BodyShapeComponent.class);
        if (shape != null) {
            entity.remove(BodyShapeComponent.class);
            return shape.build(physics, entity, 0f, 0f, 0f);
        }
        return physics.createPolygonBody(entity, 0f, 0f, 0f, DEFAULT_HULL, DEFAULT_HULL_DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
    }
}
