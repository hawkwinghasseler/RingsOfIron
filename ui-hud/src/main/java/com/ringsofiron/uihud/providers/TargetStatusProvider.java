package com.ringsofiron.uihud.providers;

/**
 * The locked target as numbers: what it is, how far, how fast it closes, how near it passes and
 * whether it is shooting at the player. Filled by the game wiring layer from the ship's sensor track.
 */
public interface TargetStatusProvider {
    /** Fills {@code out}, already cleared; leaves {@link TargetStatus#present} false with no lock. */
    void read(TargetStatus out);

    enum Relation {
        HOSTILE, NEUTRAL, FRIENDLY, MISSILE, UNKNOWN
    }

    final class TargetStatus {
        public boolean present;
        /** The marker's label ("Raider", "Kestrel Station"). */
        public String label = "";
        public Relation relation = Relation.UNKNOWN;
        /** Its state as the markers say it ("struck", "silenced", "running", "fit"), or "". */
        public String state = "";
        /** Range, m; closing speed, m/s (+ closing, − opening); speed relative to the player, m/s. */
        public float range = Float.NaN, closing = Float.NaN, relativeSpeed = Float.NaN;
        /** Closest approach at present velocities: distance, m, and seconds to it (NaN when opening). */
        public float cpaDistance = Float.NaN, cpaTime = Float.NaN;
        /** Bearing from the player's nose, degrees counter-clockwise (port +); its course against ours, degrees. */
        public float bearingDeg = Float.NaN, aspectDeg = Float.NaN;
        /** It has the player locked, or its guns are on them. */
        public boolean firingOnUs;
        /** Its hull as a share of whole, 0..1; NaN when the sensors cannot say. */
        public float hull = Float.NaN;
        /** Seconds since the track last had a fix: old tracks drift. */
        public float trackAge = Float.NaN;

        public void clear() {
            present = false;
            label = "";
            relation = Relation.UNKNOWN;
            state = "";
            range = closing = relativeSpeed = Float.NaN;
            cpaDistance = cpaTime = Float.NaN;
            bearingDeg = aspectDeg = Float.NaN;
            firingOnUs = false;
            hull = Float.NaN;
            trackAge = Float.NaN;
        }
    }
}
