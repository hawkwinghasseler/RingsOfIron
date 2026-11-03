package com.ringsofiron.uihud.providers;

/**
 * The player's shield cloud: how much mass protects the ship, where it is thick and thin, and the
 * emitters holding it. Filled by the game wiring layer from combat-sys's shield model.
 */
public interface ShieldStatusProvider {
    /** Fills {@code out}, already cleared; leaves {@link ShieldStatus#present} false for a ship with no emitter. */
    void read(ShieldStatus out);

    /** Sectors of {@link ShieldStatus#sectors}, in order. */
    enum Sector {
        BOW, PORT, STERN, STARBOARD
    }

    final class ShieldStatus {
        public boolean present;
        /** Raised, or lowered by the crew; lowered to dock. */
        public boolean raised, loweredForDock;
        /** Mass, kg: what the ship carries in all, the most it can, protecting now, and scattered, drifting off, stowed. */
        public float stock, capacity, cloud, scattered, adrift, stowed;
        /** "even", "bow", "port", "stern" or "starboard". */
        public String trim = "even";
        /**
         * Each sector's mean density against the whole cloud's mean, 1 for even; 0 bare. Indexed by
         * {@link Sector#ordinal()}.
         */
        public final float[] sectors = new float[4];
        /** Emitters fitted, holding (working), tripped, and the hottest one's heat, 0..1. */
        public int emitters, emittersWorking, emittersTripped;
        public float hottest;
        /** An ion hit has stunned the emitters' power. */
        public boolean powerStunned;

        /** Cloud protecting now against capacity, 0..1; 0 with none. */
        public float share() {
            return capacity > 0f ? Math.max(0f, Math.min(1f, cloud / capacity)) : 0f;
        }

        public void clear() {
            present = raised = loweredForDock = false;
            stock = capacity = cloud = scattered = adrift = stowed = 0f;
            trim = "even";
            for (int i = 0; i < sectors.length; i++) sectors[i] = 0f;
            emitters = emittersWorking = emittersTripped = 0;
            hottest = 0f;
            powerStunned = false;
        }
    }
}
