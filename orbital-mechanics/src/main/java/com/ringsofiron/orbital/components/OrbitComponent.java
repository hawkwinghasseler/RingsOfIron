package com.ringsofiron.orbital.components;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

/**
 * A body on rails: a Keplerian conic around the body whose sphere of influence it is in.
 *
 * <p>Plain public fields so core-engine's JSON serializer saves it as-is. Change the elements only
 * through {@link #setOrbit} (on the main thread, at a tick boundary), which keeps the cached
 * {@link KeplerOrbit} in sync and bumps {@link #version} so stale predicted events are dropped.
 */
public class OrbitComponent implements Component {
    /** UUID of the parent body (an entity with a {@code CelestialBodyComponent}). */
    public String parentUuid;
    /** Parent's gravitational parameter G*M, m^3/s^2 (cached copy). */
    public double mu;
    /** Semi-major axis, m. Negative for hyperbolic orbits. */
    public double a;
    public double e;
    /** Angle of periapsis from the parent's +X axis, radians. */
    public double argPeriapsis;
    public double meanAnomalyAtEpoch;
    /** Sim seconds at which {@link #meanAnomalyAtEpoch} holds. */
    public double epoch;
    /** +1 counter-clockwise, -1 clockwise. */
    public int direction = 1;

    /** Attitude kept across rails/bubble handoffs: angle (radians) at {@link #epoch} and spin (rad/s). */
    public double attitude;
    public double angularVelocity;

    /** Bumped on every element change. Predicted events carry the version they were made for. */
    public transient int version;
    private transient KeplerOrbit orbit;

    public OrbitComponent() {
    }

    public OrbitComponent(String parentUuid, KeplerOrbit orbit) {
        this.parentUuid = parentUuid;
        setOrbit(orbit);
    }

    /** The conic, rebuilt from the public fields if needed (e.g. right after loading a save). */
    public KeplerOrbit getOrbit() {
        if (orbit == null) {
            orbit = new KeplerOrbit(mu, a, e, argPeriapsis, meanAnomalyAtEpoch, epoch, direction);
        }
        return orbit;
    }

    public void setOrbit(KeplerOrbit orbit) {
        double attitudeNow = attitudeAt(orbit.epoch);
        this.orbit = orbit;
        this.mu = orbit.mu;
        this.a = orbit.a;
        this.e = orbit.e;
        this.argPeriapsis = orbit.argPeriapsis;
        this.meanAnomalyAtEpoch = orbit.meanAnomalyAtEpoch;
        this.direction = orbit.direction;
        this.attitude = attitudeNow;
        this.epoch = orbit.epoch;
        version++;
    }

    /** Moves to a new parent body with new elements (an SOI transition). */
    public void reparent(String parentUuid, KeplerOrbit orbit) {
        this.parentUuid = parentUuid;
        setOrbit(orbit);
    }

    /** Parent-relative state at sim time t. */
    public StateVector stateAt(double t, StateVector out) {
        return getOrbit().stateAt(t, out);
    }

    /** Body angle in radians at sim time t, assuming constant spin while on rails. */
    public double attitudeAt(double t) {
        return attitude + angularVelocity * (t - epoch);
    }
}
