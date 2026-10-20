package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.macrosim.bubble.PhysicsBubbleGateway;
import com.ringsofiron.macrosim.rails.Trajectory;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;

/**
 * macro-sim's bubble over physics-exterior, as the game runs it. The anchor's parent is read live
 * from physics (it changes at SOI crossings), and a body coming in gets back the shape it had when the
 * bubble last collapsed; else a ship with a layout (pirates, patrols) gets a body from {@link #ships},
 * and anything else a default freighter hull.
 */
public class GameBubbleGateway extends PhysicsBubbleGateway {
    /** Default hull, 40 m by 12 m, nose along +X. */
    static final float[] DEFAULT_HULL = {-20f, -6f, 20f, -6f, 20f, 6f, -20f, 6f};
    /** kg per square meter: about 50 t for the default hull. */
    static final float DEFAULT_HULL_DENSITY = 104f;

    /** Builds a ship's body from its own layout, or returns null if it has none. */
    public interface ShipBodies {
        Body build(Entity ship);
    }

    private final PhysicsManager physics;
    private final UuidRegistry uuids;
    private final Builder builder;

    public GameBubbleGateway(PhysicsManager physics, UuidRegistry uuids) {
        this(physics, uuids, new Builder(physics));
    }

    private GameBubbleGateway(PhysicsManager physics, UuidRegistry uuids, Builder builder) {
        super(physics, uuids, null, builder);
        this.physics = physics;
        this.uuids = uuids;
        this.builder = builder;
    }

    /** Where ships with a layout get their bodies; null (the default) gives them the default hull. */
    public void setShipBodies(ShipBodies ships) {
        builder.ships = ships;
    }

    /** A fast pass weighs what its body would: its saved shape's mass, or the default hull's. */
    @Override
    public void beginKinematicTransient(String uuid, Trajectory path) {
        super.beginKinematicTransient(uuid, path);
        Entity entity = uuids.getEntity(uuid);
        BodyShapeComponent shape = entity.getComponent(BodyShapeComponent.class);
        entity.getComponent(KinematicTransientComponent.class).mass =
                shape != null ? shape.mass : polygonArea(DEFAULT_HULL) * DEFAULT_HULL_DENSITY;
    }

    @Override
    public String anchorParentUuid() {
        return physics.getAnchorParentUuid();
    }

    private static final class Builder implements BodyBuilder {
        private final PhysicsManager physics;
        private ShipBodies ships;

        Builder(PhysicsManager physics) {
            this.physics = physics;
        }

        @Override
        public Body build(Entity entity) {
            BodyShapeComponent shape = entity.getComponent(BodyShapeComponent.class);
            if (shape != null) {
                entity.remove(BodyShapeComponent.class);
                return shape.build(physics, entity, 0f, 0f, 0f);
            }
            Body ship = ships == null ? null : ships.build(entity);
            if (ship != null) return ship;
            return physics.createPolygonBody(entity, 0f, 0f, 0f, DEFAULT_HULL, DEFAULT_HULL_DENSITY,
                    CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        }
    }

    /** Area of a simple polygon given as x, y pairs, in square meters. */
    static float polygonArea(float[] v) {
        float twice = 0f;
        for (int i = 0; i < v.length; i += 2) {
            int j = (i + 2) % v.length;
            twice += v[i] * v[j + 1] - v[j] * v[i + 1];
        }
        return Math.abs(twice) / 2f;
    }
}
