package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Missiles in flight for the threat overlay: where each is, where it is heading, and whether it is
 * coming for the player. Implemented in the game wiring from combat-sys, so ui-hud does not depend
 * on combat-sys. World positions are the bubble's meters, as everything else the world camera draws.
 */
public interface ThreatProvider {

    /** Fills {@code out} (already cleared). Called once per frame. */
    void collect(ThreatFrame out);

    enum Side {
        /** Launched by the player. */
        OWN,
        /** Guided at the player's ship. */
        INCOMING,
        /** Anyone else's. */
        OTHER
    }

    final class MissileTrack {
        public Side side = Side.OTHER;
        public float x, y;
        /** End of the predicted path: the intercept point for a missile with a target, else a few seconds ahead. */
        public float pathX, pathY;
        /** Seconds to closest approach with its target, or NaN if it is not closing. */
        public float timeToImpact = Float.NaN;
        /** Motor burning (drawn brighter). */
        public boolean burning;
    }

    final class ThreatFrame {
        public final Array<MissileTrack> missiles = new Array<>();
        private final Array<MissileTrack> pool = new Array<>();

        public MissileTrack add() {
            MissileTrack m = pool.size > 0 ? pool.pop() : new MissileTrack();
            m.side = Side.OTHER;
            m.timeToImpact = Float.NaN;
            m.burning = false;
            missiles.add(m);
            return m;
        }

        public void clear() {
            pool.addAll(missiles);
            missiles.clear();
        }

        public int incoming() {
            int n = 0;
            for (MissileTrack m : missiles) if (m.side == Side.INCOMING) n++;
            return n;
        }

        /** Soonest time to impact among incoming missiles, or NaN when none is closing. */
        public float soonestImpact() {
            float best = Float.NaN;
            for (MissileTrack m : missiles) {
                if (m.side != Side.INCOMING || Float.isNaN(m.timeToImpact)) continue;
                if (Float.isNaN(best) || m.timeToImpact < best) best = m.timeToImpact;
            }
            return best;
        }
    }
}
