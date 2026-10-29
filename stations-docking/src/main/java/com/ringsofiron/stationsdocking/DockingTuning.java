package com.ringsofiron.stationsdocking;

import com.ringsofiron.interiorgrid.MountFootprint.MountSize;

/**
 * Tuning values for one docking port size: magnets, the active collar, latch tolerances and break
 * limits. All SI. These are prototype numbers, picked so a corvette (about 120 t) docks at walking
 * pace and tears a Small port loose under a second of full thrust.
 */
public final class DockingTuning {
    public static final DockingTuning SMALL = new DockingTuning(
            3f, 3_000f, 1f, 6_000f, 1f, 150f, 0.12f, 6e5f, 6e5f);
    public static final DockingTuning MEDIUM = new DockingTuning(
            4f, 8_000f, 1.25f, 16_000f, 1.5f, 400f, 0.15f, 1.5e6f, 2.5e6f);
    public static final DockingTuning LARGE = new DockingTuning(
            5f, 20_000f, 1.5f, 40_000f, 2f, 900f, 0.2f, 4e6f, 8e6f);

    /** Gap between the two collar faces that still counts as touching, meters. Box2D keeps about 0.15 m. */
    public static final float LATCH_GAP = 0.35f;
    /**
     * Largest angle between the two faces that still latches or captures, radians (2 degrees).
     * Tight, so docked craft line up as one deck; magnets and the collar's tilt square them up first.
     */
    public static final float LATCH_ANGLE = 0.035f;
    /** Fastest the two collar faces may close and still latch, m/s. */
    public static final float LATCH_SPEED = 1.0f;
    /** Ports only attract when facing each other to within this many radians of head-on (60 degrees). */
    public static final float MAGNET_CONE = 1.047f;
    /** Closing speed the magnets' eddy damping brakes at full magnet force, m/s. */
    public static final float EDDY_SPEED = 0.5f;
    /** How fast an active collar telescopes out, m/s. */
    public static final float EXTEND_SPEED = 0.5f;
    /** How fast a captured collar draws the craft together, m/s. */
    public static final float CONTRACT_SPEED = 0.3f;
    /** Collar spring stiffness and damping, as Box2D frequency (Hz) and damping ratio. */
    public static final float SPRING_HZ = 4f, SPRING_DAMPING = 0.7f;
    /** Farthest an active collar head tilts off its port's axis, radians (20 degrees). */
    public static final float MAX_TILT = 0.35f;
    /** How fast the tilt spring turns the head back square, per radian of tilt, 1/s. */
    public static final float TILT_RATE = 3f;
    /**
     * Torque centering a free collar head, as a share of its magnet's pull times half the port
     * width: weak, so the magnet can tilt the head to the other port's angle.
     */
    public static final float TILT_FREE = 0.25f;
    /** Torque a captured head's tilt spring turns the craft square with, as a share of the break torque. */
    public static final float TILT_HOLD = 0.15f;
    /** Friction of collars and collar heads: low, so a collar slides along the other port instead of sticking. */
    public static final float COLLAR_FRICTION = 0.02f;
    /**
     * Extension below which a contracting collar has locked, meters: where the two craft's own
     * collars meet (Box2D keeps touching shapes about 0.2 m apart). That contact is the collar's
     * hard stop; the seating then closes the rest.
     */
    public static final float LOCK_EXTENSION = 0.3f;
    /** Seating ends and the weld goes rigid once the pose is this close: meters and radians. */
    public static final float SEATED_DISTANCE = 0.02f, SEATED_ANGLE = 0.01f;
    /** Longest the seating may take before the weld is made anyway, seconds. */
    public static final float SEATING_TIME = 3f;
    /** Time constant of the low-pass filter on the strain read from the weld, seconds. */
    public static final float STRAIN_FILTER = 0.3f;
    /**
     * One step's strain counts toward the filtered strain only up to this many times the break
     * limit: a single hit's spike is a shock the latch rides out, and only a load kept up (thrust,
     * a shove, many hits close together) tears it (round 6).
     */
    public static final float STRAIN_SPIKE_CAP = 3f;
    /**
     * A single step's strain past this many times the break limit tears the latch at once: an
     * impulse worth about two thirds of a second at the break load, as from a ram, not one round.
     */
    public static final float SHOCK_LIMIT = 40f;
    /**
     * Most torque a welded craft's rotation assist may use, as a share of the latch's break torque:
     * it still stops the pair's spin, but never tears the latch doing it (Oct 2026: with RCS blocks
     * at standard thrust the corvette's assist turned 2.9 MN*m against a Small latch that tears at 0.6).
     */
    public static final float ASSIST_SHARE = 0.5f;
    /** Relative speed the separation springs push the craft apart at on undock, m/s. */
    public static final float SEPARATION_SPEED = 0.3f;

    /** Distance a port's magnets reach, from collar face to collar face, meters. */
    public final float magnetRange;
    /** Pull of an armed passive port at contact, newtons. Falls off with distance. */
    public final float magnetForce;
    /** Reach of an active collar head's magnet, beyond its face, meters. */
    public final float headRange;
    /** Pull of an active collar head at contact, newtons. */
    public final float headForce;
    /** How far an active collar telescopes out, meters. */
    public final float maxExtension;
    /** Mass of an active collar head, kg. */
    public final float headMass;
    /** Largest sideways offset between the port centers that still latches, meters. */
    public final float latchOffset;
    /** Force and torque the locked connection survives, N and N*m. */
    public final float breakForce, breakTorque;

    private DockingTuning(float magnetRange, float magnetForce, float headRange, float headForce, float maxExtension,
                          float headMass, float latchOffset, float breakForce, float breakTorque) {
        this.magnetRange = magnetRange;
        this.magnetForce = magnetForce;
        this.headRange = headRange;
        this.headForce = headForce;
        this.maxExtension = maxExtension;
        this.headMass = headMass;
        this.latchOffset = latchOffset;
        this.breakForce = breakForce;
        this.breakTorque = breakTorque;
    }

    public static DockingTuning of(MountSize size) {
        switch (size) {
            case S: return SMALL;
            case M: return MEDIUM;
            case L: return LARGE;
            default: throw new IllegalArgumentException("There is no " + size + " docking port");
        }
    }
}
