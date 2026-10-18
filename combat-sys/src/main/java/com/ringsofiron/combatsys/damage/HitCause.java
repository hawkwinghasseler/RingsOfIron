package com.ringsofiron.combatsys.damage;

/** What kind of ray did the damage. Matches the combat causes in ai-crew's {@code CrewDamageSink.DamageCause}. */
public enum HitCause {
    /** The round itself. */
    PENETRATOR,
    /** Secondary fragments off the inside of a penetrated hull. */
    SPALL,
    /** Warhead fragments. */
    FRAGMENT,
    /** Interior overpressure. */
    BLAST,
    /** Burns: a beam burning through. */
    FIRE
}
