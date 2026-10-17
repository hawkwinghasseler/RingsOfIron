package com.ringsofiron.physicsexterior.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Wraps the Box2D body that drives an entity's exterior motion. The body's user data is the
 * owning {@code Entity}. The body is destroyed when the entity is removed from the engine.
 */
public class PhysicsBodyComponent implements Component {
    public transient Body body;

    public PhysicsBodyComponent() {
    }

    public PhysicsBodyComponent(Body body) {
        this.body = body;
    }
}
