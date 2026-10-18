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

    /** Walking speed, tiles per second. */
    public float moveSpeed = 2.5f;
    /** Speed multiplier while carrying a casualty. */
    public float carrySpeedFactor = 0.5f;

    /** Fire intensity removed per second when fighting a fire. */
    public float extinguishRate = 0.3f;
    /** Subsystem health restored per second when repairing. */
    public float repairRate = 0.1f;
    /** Seconds to patch a hull breach with an emergency seal. */
    public float sealTime = 3f;

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
