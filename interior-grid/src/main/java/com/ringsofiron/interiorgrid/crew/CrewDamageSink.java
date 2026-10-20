package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * Applies injury to a crew member. Implemented by {@code ai-crew}, which owns what the
 * damage does (conscious, unconscious, dead); combat only says how much and why.
 */
public interface CrewDamageSink {

    /** What hurt the crew member, for logs, HUD and death messages. */
    enum DamageCause {
        PENETRATOR,
        SPALL,
        FRAGMENT,
        BLAST,
        FIRE,
        /** A weapon's burn: a laser crossing someone. Wounds, where {@link #FIRE} (standing in one) takes health. */
        BURN,
        ASPHYXIATION,
        SMALL_ARMS,
        OTHER
    }

    /**
     * Subtracts {@code amount} health (in the crew member's health units, where full
     * health is their max) and moves them between conscious, unconscious and dead.
     * Entities that are not crew are ignored.
     */
    void applyDamage(Entity crewEntity, float amount, DamageCause cause);
}
