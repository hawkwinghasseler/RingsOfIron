package com.ringsofiron.uihud.variants.nasapunk.context;

/**
 * The context spec's situations (functional-context.md, section 4.2), each with its way in and
 * out. Several hold at once; two hold on after their trigger goes, so panels don't flicker: an
 * approach until the port is 5.5 km off or its trigger has been gone 3 s, combat for 10 s after
 * the last of its triggers. Times are real seconds, so a warped clock doesn't hurry them.
 */
final class Situations {
    /** The approach starts with a port this close and holds until it is past the second, m. */
    static final float APPROACH_IN = 5_000f, APPROACH_OUT = 5_500f;
    /** How long the approach and combat hold on after their triggers go, s. */
    static final float APPROACH_HOLD = 3f, COMBAT_HOLD = 10f;

    /** What the situations are read from, each frame. */
    static final class Input {
        boolean warp, docked, latched;
        /** The selected port's distance, m, NaN with none in reach. */
        float portRange = Float.NaN;
        /** The nav target is the port's craft, a port is armed, or the helm has a dock order. */
        boolean approachTrigger;
        /** Battle stations, a missile inbound, or a hostile locked within weapons range. */
        boolean combatTrigger;
        /** Latched to another ship, or a beaten one in reach on EVA. */
        boolean boarding;
        /** Disabled with no power due back by itself, abandoning, or the captain's suit air short. */
        boolean emergency;
    }

    boolean warp, approach, docked, latched, combat, boarding, emergency;
    private float approachGone, combatGone = COMBAT_HOLD;

    void update(Input in, float delta) {
        warp = in.warp;
        docked = in.docked;
        latched = in.latched;
        boarding = in.boarding;
        emergency = in.emergency;
        boolean inReach = !Float.isNaN(in.portRange);
        if (in.docked || in.latched || !inReach || in.portRange > APPROACH_OUT) {
            approach = false;
        } else if (in.approachTrigger) {
            if (in.portRange <= APPROACH_IN) approach = true;
            approachGone = 0f;
        } else if (approach) {
            approachGone += delta;
            if (approachGone > APPROACH_HOLD) approach = false;
        }
        if (in.combatTrigger) {
            combat = true;
            combatGone = 0f;
        } else {
            combatGone += delta;
            if (combatGone > COMBAT_HOLD) combat = false;
        }
    }

    /** Nothing but cruise holds: no warp, approach, docking, combat, boarding or emergency. */
    boolean cruise() {
        return !warp && !approach && !docked && !latched && !combat && !boarding && !emergency;
    }
}
