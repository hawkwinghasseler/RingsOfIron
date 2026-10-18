package com.ringsofiron.macrosim.rails;

/**
 * A pure function of sim time, parent-relative. Implementations handed to background threads must be
 * immutable, so they can be evaluated off the main thread while elements change on it.
 */
public interface Trajectory {
    /** Writes the state at sim second {@code t} into {@code out} and returns it. */
    StateVector stateAt(double t, StateVector out);

    /** Smallest distance from the parent this trajectory ever reaches, in meters (periapsis). */
    double minRadius();

    /** Largest distance from the parent, in meters ({@code +inf} for escape trajectories). */
    double maxRadius();

    /** An upper bound on speed relative to the parent, in m/s (speed at periapsis). */
    double maxSpeed();
}
