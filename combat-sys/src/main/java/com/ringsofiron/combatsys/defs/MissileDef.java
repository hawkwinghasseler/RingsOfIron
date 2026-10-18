package com.ringsofiron.combatsys.defs;

import com.google.gson.annotations.SerializedName;

/** A self-propelled guided missile, the {@code [missile.*]} table. */
public class MissileDef {
    public enum SeekerType {
        @SerializedName(value = "radar", alternate = "RADAR") RADAR,
        @SerializedName(value = "thermal", alternate = "THERMAL") THERMAL,
        @SerializedName(value = "command", alternate = "COMMAND") COMMAND
    }

    /** TOML {@code seeker = { type, fov_deg, range, discrimination }}. */
    public static class SeekerDef {
        public SeekerType type = SeekerType.RADAR;
        /** Full field of view, degrees. */
        public float fovDeg = 30f;
        /** meters */
        public float range = 20000f;
        /** 0..1: how well the seeker tells its target from a decoy. */
        public float discrimination = 0.5f;
    }

    public String id;
    public float dryMass = 100f;
    public float propellantMass = 100f;
    public float exhaustVelocity = 3000f;
    /** N */
    public float maxThrust = 30000f;
    public float turnRateDeg = 90f;
    public SeekerDef seeker = new SeekerDef();
    /** Proportional-navigation gain N, usually 3-5. */
    public float navGain = 4f;
    /** Share of delta-v held back for the terminal phase. */
    public float terminalReserve = 0.3f;
    /**
     * Acceleration spent closing along the line of sight while fuel above the reserve remains,
     * m/s^2. High for "sprint" missiles, low for "hunters" that save delta-v for the endgame.
     */
    public float closingBurn = 30f;
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
