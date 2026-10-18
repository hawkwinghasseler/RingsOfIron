package com.ringsofiron.uihud.providers;

/**
 * Orbit readout for the player ship. Implemented by orbital-mechanics; returns null while the
 * ship is not in a meaningful orbit (or before orbital-mechanics is wired in), and the HUD
 * then shows "No orbit".
 */
public interface OrbitInfoProvider {
    /** Current orbit of the player ship, or null. The HUD only reads it during the call. */
    OrbitInfo getPlayerOrbit();

    /** Plain data for one frame's orbit readout. Distances in meters above the body's surface. */
    final class OrbitInfo {
        public String bodyName = "";
        public double altitude;
        public double apoapsis;
        public double periapsis;
        /** Orbital period in seconds, or {@code Double.POSITIVE_INFINITY} on an escape trajectory. */
        public double period;
        /** True when periapsis is below the surface or atmosphere: the ship is on a collision course. */
        public boolean impactWarning;
    }
}
