package com.ringsofiron.physicsexterior.anchor;

/**
 * The motion of the bubble's origin (the anchor) through the universe, in doubles.
 *
 * States are {@code {x, y, vx, vy}} in meters and m/s relative to the parent body the bubble is
 * falling around (the current SOI body), or to a fixed universe point when there is no gravity.
 * Times are sim seconds.
 *
 * The anchor must be in free fall: the only forces bubble bodies feel from the parent are the
 * tidal difference between their position and the anchor's (see {@code TidalField}).
 */
public interface AnchorModel {
    /** Gravitational parameter G*M of the parent body, in m^3/s^2. Zero means no gravity. */
    double getMu();

    /** Writes the anchor's state at sim time {@code t} into {@code out[0..3]}. */
    void stateAt(double t, double[] out);

    /**
     * Restarts the anchor's free fall from the given state at sim time {@code t}. Called on every
     * floating origin rebase with the player's true state.
     */
    void reset(double x, double y, double vx, double vy, double t);
}
