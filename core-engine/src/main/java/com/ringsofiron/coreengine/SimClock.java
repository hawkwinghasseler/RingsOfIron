package com.ringsofiron.coreengine;

/**
 * Read-only view of authoritative sim time.
 *
 * Sim time is a single {@code long} tick counter at {@link #TICKS_PER_SECOND} Hz. Seconds are
 * derived from it as a double when needed (orbital code, timers). Never accumulate game time in a
 * float.
 */
public interface SimClock {
    int TICKS_PER_SECOND = 60;
    double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;

    /** Ticks elapsed since the start of the game. Advances by one per fixed step, or in jumps under rails warp. */
    long getTick();

    /** {@link #getTick()} in seconds: {@code tick / 60.0}. */
    default double getSimSeconds() {
        return getTick() * TICK_SECONDS;
    }

    /** Converts a duration in seconds to a whole number of ticks (at least one). */
    static long secondsToTicks(double seconds) {
        return Math.max(1L, Math.round(seconds * TICKS_PER_SECOND));
    }
}
