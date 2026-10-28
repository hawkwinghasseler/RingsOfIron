package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;

/** Something that happened to a crew member on EVA, for notices ({@link EvaSystem#events}). */
public final class EvaEvent {

    public enum Kind {
        /** Out of the airlock onto the hull. */
        OUT,
        /** Back in, the hatch open behind them. */
        BACK,
        /** Torn off the hull: the ship moved harder than their grip could follow, or the line pulled them off. */
        LOST_GRIP,
        /** Caught hold of the hull again. */
        CAUGHT_HULL,
        /** Their tether snapped. */
        TETHER_SNAPPED,
        /** Caught in the exhaust of their ship's main engines ({@link EngineExhaust}), burned and thrown by it. */
        EXHAUST,
        /** Their tether burned through in the exhaust of their ship's main engines. */
        TETHER_BURNED,
        /**
         * Adrift with no line and no way back on their own (their pack can't bring them back, or
         * they are out cold): they wait for the ship to come back for them.
         */
        ADRIFT,
        /** Their suit's air has run out outside. */
        AIR_OUT,
        /** A shipmate out on a line has taken hold of them adrift ({@link EvaEvent#by}), to bring them in. */
        TAKEN_IN_TOW,
        /** Out of the physics bubble adrift, or adrift through a jump: gone. */
        LEFT_BEHIND
    }

    public Kind kind;
    public Entity crew;
    /** Who took them in tow, for {@link Kind#TAKEN_IN_TOW}; else null. */
    public Entity by;
    /** The craft they went out from. */
    public Entity ship;
    /**
     * The line's pull when it snapped, newtons; the hull point's acceleration when they lost their
     * grip, m/s2; how far off their ship they are when adrift, meters; seconds they were adrift when
     * they caught hold of the hull again; the exhaust's push on them when it caught them, newtons.
     */
    public float amount;
}
