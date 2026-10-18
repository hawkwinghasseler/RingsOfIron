package com.ringsofiron.physicsexterior;

/**
 * Where a body that is not simulated by Box2D is at a given sim time, as a true state
 * {@code {x, y, vx, vy}} in meters and m/s relative to the anchor's parent body. A rails orbit's
 * {@code stateAt(t)} fits this directly.
 */
public interface TrueTrajectory {
    void stateAt(double simSeconds, double[] out);
}
