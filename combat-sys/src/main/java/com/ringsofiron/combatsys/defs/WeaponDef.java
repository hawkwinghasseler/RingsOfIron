package com.ringsofiron.combatsys.defs;

/** A gun, launcher or beam, the {@code [weapon.*]} table. */
public class WeaponDef {
    public enum MountType {
        TURRET,
        SPINAL,
        LAUNCHER,
        BEAM
    }

    public String id;
    public MountType mount = MountType.TURRET;
    public float slewRateDeg = 60f;
    /** Full traverse arc centred on the mount's facing, degrees. 360 is unrestricted. */
    public float firingArcDeg = 360f;
    /** Seconds between shots. */
    public float cooldown = 1f;
    /** Watts drawn while charging. */
    public float powerDraw;
    /** Projectile def fired by guns. */
    public String projectile;
    /** Missile def fired by launchers. */
    public String missile;
    /** Aim jitter at gunnery skill 0, radians (1 sigma). */
    public float spreadRad = 0.002f;
    /** Distance from the hardpoint along the barrel where rounds appear, meters. */
    public float muzzleOffset = 2f;
}
