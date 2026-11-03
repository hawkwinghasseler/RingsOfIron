package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * The player's power grid: each circuit's reactors and store, and every system that is not working
 * with why and for how long. Filled by the game wiring layer from interior-grid's power routing.
 */
public interface PowerStatusProvider {
    /** Fills {@code out}, already cleared. */
    void collect(PowerFrame out);

    /** Why a system is not working, worst first. */
    enum Fault {
        DESTROYED,
        /** Damaged below working order. */
        DISABLED,
        /** A short tripped it off its circuit; {@link SystemRow#timer} is the breaker's reclose. */
        SHORTED,
        /** An ion hit; {@link SystemRow#timer} is the stun left. */
        STUNNED,
        /** Switched on but no reactor's circuit reaches it, or the circuit is dry. */
        NO_POWER,
        /** Switched off by the crew. */
        OFF
    }

    final class Circuit {
        /** Reactors on it, their rated output and what they make now, W. */
        public int reactors;
        public double ratedWatts, outputWatts;
        /** Its store: J held and J it holds full. */
        public double stored, capacity;
        /** Systems it feeds. */
        public int systems;

        /** Store share 0..1; 0 with none. */
        public float charge() {
            return capacity > 0 ? (float) Math.max(0, Math.min(1, stored / capacity)) : 0f;
        }
    }

    final class SystemRow {
        /** The system's kind as the HUD names it ("REACTOR", "LIFE SUP", "TURRET"). */
        public String system = "";
        /** The room it is in ({@code RoomNames}), or "". */
        public String room = "";
        public Fault fault = Fault.OFF;
        /** Seconds until it comes back by itself (reclose, stun), NaN for none. */
        public float timer = Float.NaN;
        public float health = 1f;
        /** One of its tiles, for pointing at it on a schematic. */
        public int tileX = -1, tileY = -1;
    }

    final class PowerFrame {
        public final Array<Circuit> circuits = new Array<>();
        public final Array<SystemRow> faults = new Array<>();
        private final Array<Circuit> circuitPool = new Array<>();
        private final Array<SystemRow> rowPool = new Array<>();
        /** Totals over every circuit, W. */
        public double ratedWatts, outputWatts;

        public Circuit addCircuit() {
            Circuit c = circuitPool.size > 0 ? circuitPool.pop() : new Circuit();
            c.reactors = 0;
            c.ratedWatts = c.outputWatts = c.stored = c.capacity = 0;
            c.systems = 0;
            circuits.add(c);
            return c;
        }

        public SystemRow addFault() {
            SystemRow r = rowPool.size > 0 ? rowPool.pop() : new SystemRow();
            r.system = "";
            r.room = "";
            r.fault = Fault.OFF;
            r.timer = Float.NaN;
            r.health = 1f;
            r.tileX = r.tileY = -1;
            faults.add(r);
            return r;
        }

        public void clear() {
            circuitPool.addAll(circuits);
            circuits.clear();
            rowPool.addAll(faults);
            faults.clear();
            ratedWatts = outputWatts = 0;
        }

        /** Every circuit's store, as one share 0..1. */
        public float charge() {
            double s = 0, c = 0;
            for (Circuit k : circuits) {
                s += k.stored;
                c += k.capacity;
            }
            return c > 0 ? (float) Math.max(0, Math.min(1, s / c)) : 0f;
        }
    }
}
