package com.ringsofiron.physicsexterior.events;

import com.badlogic.ashley.core.Entity;

/**
 * A kinematic transient swept through a Box2D body this tick. Dispatched once per transient and
 * body per pass, after the step. Combat resolves the damage; momentum is not applied by physics.
 */
public class FastPassImpactEvent {
    /** The fast-moving body (has a {@code KinematicTransientComponent}). */
    public Entity transientEntity;
    /** The Box2D body's entity, or null if the body has none. */
    public Entity target;
    /** First contact point on the target, in local (bubble) meters. */
    public float pointX, pointY;
    /** Unit direction of the transient's motion relative to the target at that point. */
    public float dirX, dirY;
    /** Closing speed relative to the target at that point, in m/s. */
    public float relativeSpeed;
    /** Surface normal at the contact point. */
    public float normalX, normalY;
    /** The transient's mass in kg, or 0 when nothing knows it. */
    public float transientMass;
}
