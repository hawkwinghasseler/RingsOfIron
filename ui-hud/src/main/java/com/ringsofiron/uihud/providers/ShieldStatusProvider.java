package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

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
        /** Each emitter, in no set order: for a page that lists them by their systems. */
        public final Array<Emitter> each = new Array<>();
        private final Array<Emitter> pool = new Array<>();

        /** A row for one more emitter, reset. */
        public Emitter add() {
            Emitter e = pool.size > 0 ? pool.pop() : new Emitter();
            e.system = null;
            e.heat = 0f;
            e.working = e.tripped = e.starved = false;
            e.restartIn = Float.NaN;
            each.add(e);
            return e;
        }

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
            pool.addAll(each);
            each.clear();
        }
    }

    /** One emitter as the shield model has it this frame. */
    final class Emitter {
        /** Its subsystem's entity, which its switch and its state are on. */
        public Entity system;
        /** Its heat against the heat it trips at, 0..1. */
        public float heat;
        /** Holding its share of the cloud; tripped hot; its circuit couldn't pay to hold its share. */
        public boolean working, tripped, starved;
        /** Seconds until a tripped emitter has shed enough heat to start again; NaN while it hasn't tripped. */
        public float restartIn = Float.NaN;
    }
}
