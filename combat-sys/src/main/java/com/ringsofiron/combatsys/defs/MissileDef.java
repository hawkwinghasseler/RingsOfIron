package com.ringsofiron.combatsys.defs;

/** A self-propelled guided missile, the {@code [missile.*]} table. */
public class MissileDef {
    public enum SeekerType {
        RADAR,
        THERMAL,
        COMMAND
    }

    public String id;
    public float dryMass = 100f;
    public float propellantMass = 100f;
    public float exhaustVelocity = 3000f;
    /** N */
    public float maxThrust = 30000f;
    public float turnRateDeg = 90f;
    public SeekerType seekerType = SeekerType.RADAR;
    public float seekerFovDeg = 30f;
    public float seekerRange = 20000f;
    public float discrimination = 0.5f;
    /** Proportional-navigation gain N, usually 3-5. */
    public float navGain = 4f;
    /** Share of delta-v held back for the terminal phase. */
    public float terminalReserve = 0.3f;
    /** Seconds the motor stays dark after launch. */
    public float armingDelay = 0.5f;
    /** Speed the launcher throws the missile clear at, m/s. */
    public float ejectSpeed = 20f;
    public Fuse fuse = Fuse.proximity(15f);
    public String warhead;
    /** Size point defense has to hit, meters. */
    public float hitRadius = 1f;
    /** Point-defense damage, in joules of penetration, the missile can take. */
    public float hitPoints = 2e5f;
    /** Seconds a seeker holds its last estimate after losing lock. */
    public float memoryTime = 2f;
    public float maxLifetime = 120f;

    /** Total delta-v from Tsiolkovsky, m/s. */
    public float deltaV() {
        return (float) (exhaustVelocity * Math.log((dryMass + propellantMass) / dryMass));
    }
}
