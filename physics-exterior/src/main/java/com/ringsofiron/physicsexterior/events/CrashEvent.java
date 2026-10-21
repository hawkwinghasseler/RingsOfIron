package com.ringsofiron.physicsexterior.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Two bodies met faster than the crash speed ({@code PhysicsManager.setCrashHandling}). Box2D did
 * not push them apart: for the tick they met in they passed into each other, and whoever listens
 * (combat-sys's collision damage) decides what is crushed and what momentum they trade. Dispatched
 * after the step, with the world unlocked, before that step's {@link CollisionEvent}s.
 *
 * The instance is reused; copy anything you keep.
 */
public class CrashEvent {
    public Body bodyA, bodyB;
    /** The bodies' entities, or null for a body made without one. */
    public Entity entityA, entityB;
    /** Where they met, in each body's own frame, meters: the same point, on A and on B. */
    public final Vector2 localPointA = new Vector2(), localPointB = new Vector2();
    /** The contact normal when they met, unit, from A toward B, in the bubble frame. */
    public final Vector2 normal = new Vector2();
    /** A's velocity less B's at the contact point when they met, m/s. */
    public final Vector2 relativeVelocity = new Vector2();
    /** Speed at which they were closing along {@link #normal}, m/s. */
    public float approachSpeed;
}
