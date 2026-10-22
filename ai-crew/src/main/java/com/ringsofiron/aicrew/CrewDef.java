package com.ringsofiron.aicrew;

/**
 * Tuning data for a kind of crew member: health thresholds, hazard rates, speeds and
 * work rates. Plain data so it can later be loaded from mod JSON.
 *
 * Health is in points out of {@link #maxHealth}; thresholds are fractions of it.
 */
public class CrewDef {

    public float maxHealth = 100f;
    /** Share of max health at or below which the crew member passes out. */
    public float unconsciousThreshold = 0.25f;
    /** Extra share above the threshold an unconscious crew member must reach to wake. */
    public float wakeMargin = 0.05f;

    /** Compartment oxygen ratio below which crew start to suffocate. */
    public float asphyxiationThreshold = 0.3f;
    /** Health lost per second in vacuum; scales down linearly to 0 at the threshold. */
    public float asphyxiationRate = 8f;
    /** Tile temperature above which crew take burns, K. */
    public float burnTemperature = 330f;
    /** Health lost per second per kelvin above {@link #burnTemperature}. */
    public float burnRatePerKelvin = 0.05f;
    /** Health lost per second standing in a fully involved fire. */
    public float fireDamageRate = 15f;

    /** Health regained per second while unconscious on a safe tile. */
    public float recoveryRate = 0.5f;
    /** Recovery multiplier while a medic is treating. */
    public float treatedRecoveryFactor = 4f;
    /** Health regained per second by conscious crew on a safe tile. */
    public float regenRate = 0.1f;

    /** Injury from one hit, in health points, below which it is only a graze: no wound. */
    public float grazeWound = 5f;
    /**
     * Injury, added up over hits, at which a wound is serious: about where the same damage to
     * health would once have knocked a crew member out (at 75).
     */
    public float seriousWound = 70f;
    /** Injury at which a wound is critical: short of the 100 that would once have killed. */
    public float criticalWound = 90f;
    /**
     * Injury from one hit that kills outright: twice a crew member's health, more than a round
     * can do (at most 100 in the first room it crosses) but not a warhead going off beside them.
     */
    public float lethalWound = 200f;
    /** Seconds a critical wound can go untreated before it kills. */
    public float bleedOutTime = 300f;
    /**
     * Seconds a crew member of baseline medicine who isn't a medic takes to stabilise a critical
     * wound ({@link StabiliseTask}); a medic takes {@link #MEDIC_STABILISE} of it.
     */
    public float stabiliseTime = 15f;
    /** A medic's share of {@link #stabiliseTime}. */
    public static final float MEDIC_STABILISE = 0.5f;
    /**
     * Seconds a serious wound takes to ease to a light one by itself on a safe tile, for a crew
     * with nobody left on their feet to treat them.
     */
    public float seriousHealTime = 1200f;
    /** Seconds a light wound takes to heal by itself on a safe tile. */
    public float lightHealTime = 1800f;
    /** Work and walking speed multiplier with a light wound. */
    public float woundedWorkFactor = 0.7f;
    /** Work speed multiplier while suited. */
    public float suitWorkFactor = 0.8f;
    /** Care rate of a crew member who isn't a medic, before skill; a light wound is first aid, which anyone gives at a medic's rate. */
    public static final float UNTRAINED_CARE = 0.4f;
    /** Care rate when the ship has no medical stores left. */
    public static final float NO_STORES_CARE = 0.25f;
    /**
     * Share of the care rate a light wound is treated at while anyone aboard is seriously or
     * critically wounded with nobody treating them (round 7 had it always; round 8 treats it at
     * the full rate otherwise). It uses no medical stores, so a crew with nothing else to do
     * patches up its grazes between fights.
     */
    public static final float LIGHT_CARE = 0.5f;
    /** Care rate multiplier for a patient lying in a medbay (round 7). */
    public static final float MEDBAY_CARE = 1.5f;

    /** How bad a wound {@code injury} points of hits make. */
    public Wound woundFor(float injury) {
        return injury >= criticalWound ? Wound.CRITICAL : injury >= seriousWound ? Wound.SERIOUS
                : injury >= grazeWound ? Wound.LIGHT : Wound.NONE;
    }

    /** The least injury of a wound of level {@code wound}, where treatment leaves it. */
    public float injuryFor(Wound wound) {
        switch (wound) {
            case CRITICAL: return criticalWound;
            case SERIOUS: return seriousWound;
            case LIGHT: return grazeWound;
            default: return 0f;
        }
    }

    /** Seconds of a baseline medic's care that bring {@code wound} down one level. */
    public static float careTime(Wound wound) {
        switch (wound) {
            case CRITICAL: return 60f;
            case SERIOUS: return 120f;
            case LIGHT: return 90f;
            default: return 0f;
        }
    }

    /** Walking speed, tiles per second. */
    public float moveSpeed = 2.5f;
    /** Speed multiplier while carrying a casualty. */
    public float carrySpeedFactor = 0.5f;

    /** Most cargo carried by hand on one trip, m³. */
    public float carryVolume = 2f;
    /** Speed multiplier while carrying a load of goods. */
    public float loadSpeedFactor = 0.8f;
    /** Seconds to lift a load off a bay, and to set one down. */
    public float loadTime = 2f;

    /** Seconds of air a space suit holds; 0 for a crew member with no suit. */
    public float suitAir = 600f;
    /** Seconds to put a suit on; taking it off takes half as long. */
    public float suitTime = 5f;
    /** Speed multiplier while suited. */
    public float suitSpeedFactor = 0.85f;
    /**
     * Share of {@link #suitAir} left, out of breathable air, at which a crew member goes to fill
     * their suit from the ship's oxygen (round 6): a ship holed past its patch kits used to leave
     * its crew to suffocate once their suits ran dry.
     */
    public float suitRefillShare = 0.3f;
    /** Seconds to fill a suit from the ship's oxygen, at an oxygen tank or working life support. */
    public float suitRefillTime = 20f;
    /** Kilograms of oxygen a full suit takes. */
    public static final double SUIT_OXYGEN_KG = 0.05;

    /** Fire intensity removed per second when fighting a fire. */
    public float extinguishRate = 0.3f;
    /** Subsystem health restored per second when repairing. */
    public float repairRate = 0.1f;
    /** Seconds to patch a hull breach with an emergency seal. */
    public float sealTime = 3f;
    /** Seconds to patch a leaking wall or deck plate from the ship's stores. */
    public float patchTime = 6f;
    /** Seconds to reset a short circuit on a system or conduit. */
    public float resetTime = 5f;
    /** Seconds to shut a leaking tank's valve. */
    public float isolateTime = 4f;

    /** Tiles within which hostiles are noticed (also needs line of sight). */
    public float sightRange = 12f;
    /** Tiles within which a crew member can shoot. */
    public float weaponRange = 8f;
    /** Seconds between shots. */
    public float fireInterval = 1f;
    /** Damage per hit. */
    public float weaponDamage = 10f;
    /** Chance each shot hits. */
    public float hitChance = 0.4f;

    /** Skill at a ship's guns, 0 (green) .. 1 (veteran): aim jitter, reaction and burst walking. */
    public float gunnerySkill = 0.5f;
    /** Skill on a ship's sensors, 0 (green) .. 1 (veteran): how well tracks follow a maneuvering target. */
    public float trackingSkill = 0.5f;

    /** A combat-ready default for marines. */
    public static CrewDef marine() {
        CrewDef d = new CrewDef();
        d.weaponDamage = 20f;
        d.hitChance = 0.7f;
        d.maxHealth = 120f;
        d.gunnerySkill = 0.75f;
        return d;
    }

    public static CrewDef forRole(CrewRole role) {
        return role == CrewRole.MARINE ? marine() : new CrewDef();
    }
}
