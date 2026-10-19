package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * What a ship's shield emitters do in combat: while one is operational (switched on, powered,
 * not broken or stunned) it takes {@link #ABSORB_SHARE} of every hit, scaled by its health, before
 * the hit reaches the hull. Each joule it takes costs {@link #JOULES_PER_ABSORBED} joules from the
 * emitter's power circuit, the store the reactor fills and the guns draw on, so a shield under heavy
 * fire drains the reactor and starves the guns; with the store empty it takes nothing.
 *
 * <p>Several emitters stack, up to {@link #MAX_SHARE}. Rounds, fragments, beams and ion bolts are
 * all softened; a ram is not (it is a hull meeting a hull).
 */
public final class Shields {
    /** Share of a hit one undamaged emitter takes. */
    public static final float ABSORB_SHARE = 0.5f;
    /** Most of a hit any number of emitters take together. */
    public static final float MAX_SHARE = 0.75f;
    /**
     * Joules from the store per joule taken. A railgun slug (3.6e8 J) costs an emitter about 1.8 MJ,
     * close to a railgun shot's own 3 MJ; an autocannon round (3.6e5 J) under 2 kJ.
     */
    public static final float JOULES_PER_ABSORBED = 0.01f;

    private Shields() {
    }

    /**
     * Lets {@code ship}'s shields take their share of a hit of {@code energy} joules, drawing the
     * power for it. Returns the joules the shields took (0 with no working emitter).
     */
    public static float absorb(Entity ship, float energy) {
        if (ship == null || !(energy > 0f)) return 0f;
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return 0f;
        float share = 0f;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (working(s)) share += ABSORB_SHARE * Math.max(0f, Math.min(1f, s.health));
        }
        if (share <= 0f) return 0f;
        float want = Math.min(MAX_SHARE, share) * energy;
        // Each working emitter pays from its own circuit, in turn, until the hit is covered
        float taken = 0f;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (!working(s) || taken >= want) continue;
            PowerCircuit c = s.circuit;
            double paid = c.draw((want - taken) * (double) JOULES_PER_ABSORBED);
            taken += (float) (paid / JOULES_PER_ABSORBED);
        }
        return Math.min(taken, want);
    }

    /** True if {@code ship} has a shield emitter that would take part of a hit now. */
    public static boolean up(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (working(s) && s.circuit.stored > 0) return true;
        }
        return false;
    }

    private static boolean working(SubsystemComponent s) {
        return s != null && s.type == SubsystemComponent.SubsystemType.SHIELD_EMITTER && s.isOperational
                && s.circuit != null && !s.isDisabled();
    }
}
