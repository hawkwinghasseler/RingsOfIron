package com.ringsofiron.physicsexterior.events;

import com.badlogic.ashley.core.Entity;

/**
 * Dispatched once per new contact between two bodies, after the physics step that found it.
 * Either entity may be null if its body was created without an entity attached.
 */
public class CollisionEvent {
    public Entity entityA;
    public Entity entityB;
    /** Contact point in local (bubble) coordinates. */
    public float pointX;
    public float pointY;
    /** Speed at which the two bodies were closing along the contact normal, in m/s. */
    public float approachSpeed;
}
