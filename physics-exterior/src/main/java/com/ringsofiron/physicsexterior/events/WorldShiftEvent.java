package com.ringsofiron.physicsexterior.events;

/**
 * Dispatched after the floating origin is rebased. Every body and {@code TransformComponent} in
 * the local bubble has already been moved by {@code -deltaX, -deltaY}, and every body's velocity
 * changed by {@code -deltaVx, -deltaVy}. Listeners holding their own local positions or velocities
 * (cameras, particle effects, cached waypoints) should do the same.
 *
 * All values are in meters and meters per second.
 */
public class WorldShiftEvent {
    /** How far the origin moved, in the old local frame. */
    public float deltaX;
    public float deltaY;
    /** How much the origin's velocity changed. Zero for a position-only rebase. */
    public float deltaVx;
    public float deltaVy;
    /** The origin's universe position and velocity after the rebase. */
    public double originX;
    public double originY;
    public double originVx;
    public double originVy;
}
