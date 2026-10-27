package com.ringsofiron.combatsys.defs;

import com.google.gson.annotations.SerializedName;

/** A gun, launcher or beam, the {@code [weapon.*]} table. */
public class WeaponDef {
    public enum MountType {
        @SerializedName(value = "turret", alternate = "TURRET") TURRET,
        @SerializedName(value = "spinal", alternate = "SPINAL") SPINAL,
        @SerializedName(value = "launcher", alternate = "LAUNCHER") LAUNCHER,
        @SerializedName(value = "beam", alternate = "BEAM") BEAM
    }

    public String id;
    /** Name for the player, e.g. "Railgun Mk1"; the id when null. */
    public String name;
    /** Credits a new one costs at a station yard; 0 if yards don't sell it. */
    public double price;
    public MountType mount = MountType.TURRET;
    public float slewRateDeg = 60f;
    /** Full traverse arc centred on the mount's facing, degrees. 360 is unrestricted. */
    public float firingArcDeg = 360f;
    /** Seconds between shots; for a beam, between the starts of two pulses. */
    public float cooldown = 1f;
    /**
     * Watts drawn while charging: each shot takes {@code powerDraw * cooldown} joules from the
     * ship's power grid. A beam draws this while it burns.
     */
    public float powerDraw;
    /** Projectile def fired by guns. */
    public String projectile;
    /** Missile def fired by launchers. */
    public String missile;
    /** Beam: power delivered on the spot it touches, W. */
    public float beamPower;
    /** Beam: how long one pulse burns, seconds. */
    public float beamDuration = 1f;
    /**
     * Aim jitter at gunnery skill 0, radians (1 sigma). A gunner's opening bias, which burst
     * walking takes out, and a beam's wander scale with it too.
     */
    public float spreadRad = 0.002f;
    /** Distance from the hardpoint along the barrel where rounds appear, meters. */
    public float muzzleOffset = 2f;
    /**
     * Point defense: the mount picks its own targets, the most urgent incoming missile its ship
     * tracks, instead of the ship's locked target.
     */
    public boolean pointDefense;
    /**
     * Longest range the gunner engages at, meters; 0 means as far as the round can fly. A beam
     * reaches this far, or {@code WeaponControlSystem.DEFAULT_BEAM_RANGE} when 0.
     */
    public float maxRange;
    /** audio-spatial event played on firing, or null. */
    public String sound;
    /**
     * Smallest turret mount it fits, {@code "S"}, {@code "M"}, {@code "L"} or {@code "XL"}; null
     * fits any. A yard won't fit it to a smaller mount, and a pirate's loadout never does.
     */
    public String minMountSize;
    /**
     * Launcher: missiles it holds when full. Each launch takes one, and an empty launcher fires no
     * more until a station restocks it. 0 means it never runs out.
     */
    public int magazine;

    /**
     * True if it fits a turret mount of {@code size} ({@code S} .. {@code XL}, the names of
     * interior-grid's mount sizes); an unknown size fits only a weapon with no minimum.
     */
    public boolean fitsMount(String size) {
        if (minMountSize == null) return true;
        return size != null && sizeRank(size) >= sizeRank(minMountSize);
    }

    /**
     * Speed against damage, W*m/s: the most a gun of {@code size} may have of sustained muzzle power
     * (its round's energy over its cooldown) times its round's muzzle speed. A Small mount's budget
     * times the mount's tiles (1, 4, 9 and 25 for S to XL), so a faster round always means less
     * damage per second from a gun of the same size; 0 for an unknown size.
     */
    public static double speedBudget(String size) {
        if (size == null) return 0;
        switch (size.toUpperCase(java.util.Locale.ROOT)) {
            case "S": return SMALL_SPEED_BUDGET;
            case "M": return SMALL_SPEED_BUDGET * 4;
            case "L": return SMALL_SPEED_BUDGET * 9;
            case "XL": return SMALL_SPEED_BUDGET * 25;
            default: return 0;
        }
    }

    /** {@link #speedBudget} of a Small mount: 12 MW of rounds at 1.5 km/s, or 3 MW at 6 km/s. */
    public static final double SMALL_SPEED_BUDGET = 1.8e10;

    /** Sustained muzzle power of this gun firing {@code round}, W: the round's kinetic energy over the cooldown. */
    public double sustainedPower(ProjectileDef round) {
        if (round == null || cooldown <= 0f) return 0;
        return 0.5 * round.mass * round.muzzleSpeed * round.muzzleSpeed / cooldown;
    }

    private static int sizeRank(String size) {
        switch (size.toUpperCase(java.util.Locale.ROOT)) {
            case "S": return 0;
            case "M": return 1;
            case "L": return 2;
            case "XL": return 3;
            default: return 99;
        }
    }

    /** {@link #name}, or the id when it has none. */
    public String displayName() {
        return name != null ? name : id;
    }
}
