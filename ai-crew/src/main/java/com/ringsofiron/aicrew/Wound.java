package com.ringsofiron.aicrew;

/**
 * How badly a crew member is hurt by a hit: a round, a fragment, spall, a blast or small arms
 * ({@link CrewMemberComponent#wound}). Hits wound rather than kill; only a critical wound kills, and
 * only if it goes untreated for {@link CrewDef#bleedOutTime}. Treatment brings a wound down a level
 * at a time ({@link CrewDef#careTime}).
 */
public enum Wound {
    /** Unhurt. */
    NONE("Unhurt"),
    /** Works on, slower ({@link CrewDef#woundedWorkFactor}); heals by itself on a safe tile. */
    LIGHT("Light wound"),
    /** Down: stops and waits for help, and stays down until treated. */
    SERIOUS("Serious wound"),
    /** Down and bleeding: dies after {@link CrewDef#bleedOutTime} without treatment. */
    CRITICAL("Critical wound");

    public final String label;

    Wound(String label) {
        this.label = label;
    }

    /** True for a wound that puts a crew member down. */
    public boolean disabling() {
        return compareTo(SERIOUS) >= 0;
    }

    /** One level better; NONE stays NONE. */
    public Wound better() {
        return this == NONE ? NONE : values()[ordinal() - 1];
    }

    /** One level worse; CRITICAL stays CRITICAL. */
    public Wound worse() {
        return this == CRITICAL ? CRITICAL : values()[ordinal() + 1];
    }
}
