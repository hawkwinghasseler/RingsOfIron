package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Steps the Box2D world on a fixed tick and copies each body's pose into its entity's
 * {@link TransformComponent}: x, y in local (bubble) meters, rotation in degrees counter-clockwise.
 *
 * Entities with a {@link TransformComponent} but no body are treated as fixed in the universe:
 * while the floating origin is moving, they are moved back by the origin's travel each tick.
 *
 * Owns body lifetime: when an entity with a {@link PhysicsBodyComponent} leaves the engine, or the
 * component is removed, its body is destroyed.
 */
public class PhysicsUpdateSystem extends FixedLogicSystem {
    public static final int DEFAULT_PRIORITY = 10;

    private static final Family FAMILY = Family.all(PhysicsBodyComponent.class).get();
    private static final Family BODYLESS = Family.all(TransformComponent.class).exclude(PhysicsBodyComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);

    private final PhysicsManager physics;
    // Remembered per entity because the component is already gone when a removal is reported.
    private final ObjectMap<Entity, Body> trackedBodies = new ObjectMap<>();
    private final EntityListener bodyLifecycle = new BodyLifecycle();
    private final Vector2 position = new Vector2();
    private ImmutableArray<Entity> entities;
    private ImmutableArray<Entity> bodyless;

    public PhysicsUpdateSystem(PhysicsManager physics) {
        this(physics, DEFAULT_PRIORITY);
    }

    public PhysicsUpdateSystem(PhysicsManager physics, int priority) {
        super(priority, PhysicsManager.TIME_STEP);
        this.physics = physics;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
        bodyless = engine.getEntitiesFor(BODYLESS);
        engine.addEntityListener(FAMILY, bodyLifecycle);
        for (Entity entity : entities) {
            bodyLifecycle.entityAdded(entity);
        }
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(bodyLifecycle);
        trackedBodies.clear();
        entities = null;
        bodyless = null;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        physics.step(deltaTime);
        for (Entity entity : entities) {
            syncTransform(entity);
        }

        float originDx = (float) (physics.getOriginVx() * deltaTime);
        float originDy = (float) (physics.getOriginVy() * deltaTime);
        if (originDx != 0f || originDy != 0f) {
            for (Entity entity : bodyless) {
                TransformComponent transform = transformMapper.get(entity);
                transform.x -= originDx;
                transform.y -= originDy;
            }
        }
    }

    private void syncTransform(Entity entity) {
        TransformComponent transform = transformMapper.get(entity);
        Body body = bodyMapper.get(entity).body;
        if (transform == null || body == null) return;

        PhysicsManager.getPosition(body, position);
        transform.x = position.x;
        transform.y = position.y;
        transform.rotation = body.getAngle() * MathUtils.radiansToDegrees;
    }

    private class BodyLifecycle implements EntityListener {
        @Override
        public void entityAdded(Entity entity) {
            Body body = bodyMapper.get(entity).body;
            if (body == null) return;
            if (body.getUserData() == null) body.setUserData(entity);
            trackedBodies.put(entity, body);
            syncTransform(entity);
        }

        @Override
        public void entityRemoved(Entity entity) {
            Body body = trackedBodies.remove(entity);
            if (body != null) physics.destroyBody(body);
        }
    }
}
