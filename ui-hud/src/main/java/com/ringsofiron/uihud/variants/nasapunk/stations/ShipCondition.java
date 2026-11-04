package com.ringsofiron.uihud.variants.nasapunk.stations;

/**
 * The ship's condition (functional-stations.md section 12), worked out each frame: Red when fired
 * upon, with a missile inbound, a hostile that still fights inside weapons range, or the crew at
 * battle stations; Yellow with a hostile contact within 50 km; Green otherwise. Red holds 10 s after
 * its last reason and Yellow 5 s, so the band doesn't flicker as a raider weaves at the edge of a
 * threshold. The game's own condition reads its threats differently, so this HUD keeps its own.
 *
 * <p>Three states lie over the condition: docked, latched, and an emergency (the ship disabled, or
 * abandon ship ordered). A blackout, no reactor making power, is kept here too, since every page
 * goes dead in one.
 */
final class ShipCondition {
    enum Level { GREEN, YELLOW, RED }

    /** Red stays this long after its last reason, s. */
    static final float RED_HOLD = 10f;
    /** Yellow stays this long after its last reason, s. */
    static final float YELLOW_HOLD = 5f;

    /** What the condition comes from this frame. */
    static final class Input {
        /** A hostile has the ship locked, or its guns on it. */
        boolean firedUpon;
        boolean missilesInbound;
        /** A hostile ship that still fights is inside weapons range. */
        boolean hostileInRange;
        /** A hostile ship that still fights, or runs, is within 50 km. */
        boolean hostileNear;
        boolean battleStations;
        boolean docked, latched;
        /** The ship is disabled, or the crew are ordered to the pods. */
        boolean disabled, abandoning;
        /** Every reactor's rating, and what they make now, W. */
        double ratedWatts, outputWatts;
    }

    Level level = Level.GREEN;
    boolean docked, latched, emergency, blackout;
    private float redFor, yellowFor;

    void update(Input in, float dt) {
        boolean red = in.firedUpon || in.missilesInbound || in.hostileInRange || in.battleStations;
        redFor = red ? RED_HOLD : Math.max(0f, redFor - dt);
        yellowFor = in.hostileNear ? YELLOW_HOLD : Math.max(0f, yellowFor - dt);
        level = red || redFor > 0f ? Level.RED : in.hostileNear || yellowFor > 0f ? Level.YELLOW : Level.GREEN;
        docked = in.docked;
        latched = in.latched;
        emergency = in.disabled || in.abandoning;
        blackout = in.ratedWatts > 0 && in.outputWatts <= 0;
    }

    /** Back to Green with nothing held, as for another ship. */
    void reset() {
        level = Level.GREEN;
        redFor = yellowFor = 0f;
        docked = latched = emergency = blackout = false;
    }
}
