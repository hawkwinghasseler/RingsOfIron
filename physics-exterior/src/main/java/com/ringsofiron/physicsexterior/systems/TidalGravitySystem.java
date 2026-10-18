package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.TidalField;

/**
 * Applies the {@link TidalField} to every dynamic Box2D body once per fixed tick, before the step.
 *
 * The anchor carries the parent body's main pull analytically, so this small difference is all
 * the gravity bubble bodies need for their motion to match their true orbits. Does nothing while
 * the anchor has no gravity. Covers every body in the world, with or without an entity.
 */
public class TidalGravitySystem extends EntitySystem implements PhysicsManager.StepListener {
    private final PhysicsManager physics;
    private final Array<Body> bodies = new Array<>();
    private final Vector2 position = new Vector2();
    private final Vector2 accel = new Vector2();

    public TidalGravitySystem(PhysicsManager physics) {
        this.physics = physics;
    }

    @Override
    public void addedToEngine(Engine engine) {
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
    }

    @Override
    public void beforeStep(float deltaTime) {
        if (physics.getAnchorModel().getMu() == 0.0) return;
        TidalField field = physics.getTidalField();
        physics.getWorld().getBodies(bodies);
        for (int i = 0; i < bodies.size; i++) {
            Body body = bodies.get(i);
            if (body.getType() != BodyDef.BodyType.DynamicBody) continue;
            // Center of mass, so the force adds no torque.
            position.set(body.getWorldCenter()).scl(PhysicsManager.METERS_PER_UNIT);
            field.accelAt(position.x, position.y, accel);
            float mass = PhysicsManager.getMass(body);
            PhysicsManager.applyForce(body, accel.x * mass, accel.y * mass);
        }
        bodies.clear();
    }
}
