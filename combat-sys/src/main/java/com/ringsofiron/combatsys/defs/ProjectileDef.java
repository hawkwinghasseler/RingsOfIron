package com.ringsofiron.combatsys.defs;

/**
 * Stats for one kind of round, the {@code [projectile.*]} table in combat-sys/Design.md section 6.
 * Field names are the TOML keys in camelCase. Populated by modding-data; plain data only.
 */
public class ProjectileDef {
    public String id;
    /** Its art in assets/art/projectiles, by file name; null to look for one named like the id. */
    public String art;
    /** kg */
    public float mass = 1f;
    /** m/s, relative to the firing hardpoint */
    public float muzzleSpeed = 1000f;
    public DamageType damageType = DamageType.KINETIC;
    /** Share of kinetic energy available to penetrate: high for AP, low for HE and fragments. */
    public float penetrationFactor = 1f;
    /** Above this incidence (degrees from the hull normal) a round that cannot punch through glances. */
    public float ricochetAngleDeg = 70f;
    /** Share of relative speed a glancing round keeps. */
    public float ricochetRestitution = 0.5f;
    public SpallDef spall = new SpallDef();
    public Fuse fuse = Fuse.contact();
    /** Warhead set off by the fuse, or null for an inert slug. */
    public String warhead;
    /** Seconds before the round despawns. */
    public float maxLifetime = 30f;
    /** Seconds after firing during which the round cannot hit its own ship. */
    public float armingTime = 0.05f;
    /** {@link DamageType#ION}: what a hit stuns. TOML {@code stun = { radius_tiles, min_seconds, max_seconds }}. */
    public StunDef stun = new StunDef();

    /** How an ion hit stuns the systems near it. */
    public static class StunDef {
        /** Systems with a tile this close to where the bolt lands are stunned, tiles. */
        public float radiusTiles = 2f;
        /** Each stunned system is out for a time drawn between these, seconds. */
        public float minSeconds = 10f, maxSeconds = 30f;
    }
}
