package com.ringsofiron.combatsys.fire;

import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;

/**
 * Roughly how likely a gun's shot is to hit a ship at a range: what a gunner weighs before
 * opening up, and what the firing arcs show the player. A weapon's {@code max_range} is a guide to
 * this, not a limit: a gun fires on the trigger at any range, and firing at will opens up where the
 * chance is fair ({@link #OPEN_FIRE}). A gun firing more than a round a second counts each second's
 * rounds together ({@link #rated}).
 *
 * <p>The shot misses sideways by the barrel's error (the weapon's spread, widened for a green
 * gunner, at {@link #AIM_SPREADS} as a burst settles) times the range. A target that dodges moves
 * up to {@code a t^2 / 2} off where it was aimed at while the round flies, {@code t} the flight
 * time, at its best acceleration {@code a}; it is taken as anywhere in that span. It is hit when
 * the shot passes within its half-width, a share ({@link #BROADSIDE_SHARE}) of its radius since a
 * hull is longer than it is wide. A beam has no flight time to dodge in, and nothing past its reach.
 */
public final class HitChance {
    /** The aim error a burst settles to, in spreads (1 sigma): the opening bias walked mostly off. */
    public static final float AIM_SPREADS = 2f;
    /** A hull's half-width as a share of its radius. */
    public static final float BROADSIDE_SHARE = 0.6f;
    /**
     * The chance of a hit ({@link #rated}, so a second's worth for a fast gun) that a gunner firing at
     * will opens up at. At 0.5 the guns open up about where their max ranges said they would on a
     * corvette: roughly 3.7 km for a heavy cannon, 4.4 for a coilgun.
     */
    public static final float OPEN_FIRE = 0.5f;
    /** Acceleration assumed of a contact whose drive is unknown, m/s^2. */
    public static final float DEFAULT_EVASION = 10f;
    /** Size assumed of a contact with no signature, meters. */
    public static final float DEFAULT_RADIUS = 5f;
    /** Points across the span a dodging target may be in, averaged over. */
    private static final int DODGE_SAMPLES = 9;
    /** The tint ends: green at this chance against a dodging target, red at this one against a still target. */
    public static final float GREEN = 0.9f, RED = 0.01f;

    private HitChance() {
    }

    /**
     * The chance one shot from {@code def} (firing {@code round}, null for a beam; {@code beamReach}
     * its reach) hits a target of {@code radius} at {@code range}, aimed by a gunner of
     * {@code skill}, if the target dodges at {@code evasion} m/s^2 (0: holds still).
     */
    public static float perShot(WeaponDef def, ProjectileDef round, float beamReach, float skill,
                                float range, float radius, float evasion) {
        if (def == null) return 0f;
        float t;
        if (round == null) {
            if (range > beamReach) return 0f;
            t = 0f;
        } else {
            if (round.muzzleSpeed <= 0f) return 0f;
            t = range / round.muzzleSpeed;
            if (t > round.maxLifetime) return 0f;
        }
        float green = 1.5f - MathUtils.clamp(skill, 0f, 1f);
        float sigma = Math.max(1e-3f, AIM_SPREADS * def.spreadRad * green * range);
        float half = Math.max(0.1f, BROADSIDE_SHARE * radius);
        float dodge = 0.5f * Math.max(0f, evasion) * t * t;
        if (dodge < 1e-3f) return inside(half, 0f, sigma);
        float sum = 0f;
        for (int i = 0; i < DODGE_SAMPLES; i++) {
            float x = -dodge + 2f * dodge * (i + 0.5f) / DODGE_SAMPLES;
            sum += inside(half, x, sigma);
        }
        return sum / DODGE_SAMPLES;
    }

    /**
     * {@code chance} with the weapon's rate of fire in it: a gun firing more than a round a second
     * has that many chances a second, so the chance is multiplied by its rounds a second, up to 1.
     */
    public static float rated(WeaponDef def, float chance) {
        float perSecond = def.cooldown > 0f ? 1f / def.cooldown : 1f;
        return perSecond > 1f ? Math.min(1f, chance * perSecond) : chance;
    }

    /**
     * Where a shot sits between red (0) and green (1): green from {@link #GREEN} against a target
     * dodging, red at {@link #RED} against one holding still, and between them by how both
     * compare on a log scale.
     */
    public static float tint(float dodging, float still) {
        return 0.5f * (logShare(dodging) + logShare(still));
    }

    private static float logShare(float p) {
        if (p <= RED) return 0f;
        if (p >= GREEN) return 1f;
        return (float) (Math.log(p / RED) / Math.log(GREEN / RED));
    }

    /**
     * How far {@code def} keeps at least {@code chance} of a hit ({@link #rated}) on a target of
     * {@code radius} dodging at {@code evasion}: the range it opens up at, firing at will. 0 if not
     * even at point blank.
     */
    public static float effectiveRange(WeaponDef def, ProjectileDef round, float beamReach, float skill,
                                       float radius, float evasion, float chance) {
        float far = reach(round, beamReach);
        if (rated(def, perShot(def, round, beamReach, skill, 1f, radius, evasion)) < chance) return 0f;
        if (rated(def, perShot(def, round, beamReach, skill, far, radius, evasion)) >= chance) return far;
        float near = 1f;
        for (int i = 0; i < 24; i++) {
            float mid = 0.5f * (near + far);
            if (rated(def, perShot(def, round, beamReach, skill, mid, radius, evasion)) >= chance) near = mid;
            else far = mid;
        }
        return near;
    }

    /** As far as the round flies before it is spent, or the beam reaches. */
    public static float reach(ProjectileDef round, float beamReach) {
        return round == null ? beamReach : round.muzzleSpeed * round.maxLifetime;
    }

    /** The chance a sideways miss of {@code sigma} about {@code offset} lands within {@code half} of 0. */
    private static float inside(float half, float offset, float sigma) {
        return phi((half - offset) / sigma) - phi((-half - offset) / sigma);
    }

    /** The standard normal distribution function, to about 1e-7 (Abramowitz and Stegun 7.1.26). */
    static float phi(float z) {
        double x = Math.abs(z) / Math.sqrt(2.0);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t * Math.exp(-x * x);
        return (float) (z >= 0 ? 0.5 * (1.0 + erf) : 0.5 * (1.0 - erf));
    }
}
