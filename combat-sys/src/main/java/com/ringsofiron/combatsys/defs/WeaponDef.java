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
}
