package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.fire.Track;

/**
 * Guidance, propulsion and seeker state of a missile in flight (combat-sys/Design.md 4.1). Its
 * ballistic state (position, velocity, mass, contact hits) is the {@link ProjectileComponent} on
 * the same entity, flown by {@code BallisticsSystem}; {@code MissileGuidanceSystem} adds thrust
 * and decides when the proximity fuse fires. Missiles are transient and never saved.
 */
public class MissileComponent implements Component {
    public enum Phase {
        /** Thrown clear cold; the motor stays dark until {@code def.armingDelay}. */
        LAUNCH,
        /** Steering on the launcher's track, received by datalink. */
        MIDCOURSE,
        /** Steering on its own seeker. */
        TERMINAL,
        /** No fuel or no guidance: a ballistic, still-armed slug. */
        COAST
    }

    public MissileDef def;
    public Phase phase = Phase.LAUNCH;
    /** Nose direction, radians in the bubble frame. Thrust only acts along it. */
    public float heading;
    /** kg left. */
    public float propellant;
    /** Throttle this tick, 0..1, for exhaust plumes and the thermal signature. */
    public float throttle;
    /** Remaining point-defense damage it can take, J of penetration. */
    public float hitPoints;

    /** The launching ship, whose sensors feed the datalink. */
    public transient Entity launcher;
    /** The launcher's weapon subsystem; the datalink works only while it does. Null means always. */
    public transient Entity datalinkSubsystem;
    /** What it was fired at. */
    public transient Entity target;
    /** What the seeker has locked, which a decoy can make different from {@link #target}. */
    public transient Entity seekerLock;
    /** The seeker's own estimate of {@link #seekerLock}. */
    public final transient Track seekerTrack = new Track();
    /** Seconds since the seeker last saw its lock. */
    public float sinceSeekerFix;
    /**
     * Guidance cut ({@code MissileGuidanceSystem.goBallistic}): no seeker, no datalink, no motor and
     * no proximity fuse. It flies on as a ballistic slug that still hits what it strikes.
     */
    public transient boolean guidanceCut;
    /**
     * Where it comes in from, radians off the line to the target, + to the left: it steers for a
     * point that far to the side of the target, which closes on the target itself as it nears, so
     * the missiles of a salvo arrive from several sides. 0 flies straight in.
     */
    public float approachAngle;
    /** Distance to the fuse target last tick, meters, to detect closest approach. */
    public float lastFuseDistance = Float.MAX_VALUE;

    public MissileComponent() {
        seekerTrack.alpha = 0.7f;
        seekerTrack.beta = 0.3f;
        seekerTrack.gamma = 0.05f;
    }

    public float mass() {
        return def.dryMass + propellant;
    }
}
