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
        /** Adrift beyond any way back: gone. */
        LEFT_BEHIND
    }

    public Kind kind;
    public Entity crew;
    /** The craft they went out from. */
    public Entity ship;
    /** The line's pull when it snapped, newtons; the hull point's acceleration when they lost their grip, m/s2. */
    public float amount;
}
