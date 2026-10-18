package com.ringsofiron.orbital.rails;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

/**
 * Predicts a rails body's next SOI exit, SOI entry into a child body, and surface impact, once,
 * when its orbit is set. The results go into the event queue so nothing is checked per tick.
 *
 * <p>Exit and impact are analytic (the time the conic reaches a radius). Entry into a moving child
 * SOI is found by conservative advancement on the distance (never stepping further than the gap
 * could close at the maximum relative speed), then bisecting, so no crossing is skipped.
 */
public class SoiPredictor {
    /** Longest look-ahead per prediction; a REPREDICT event continues the search after it. */
    public static final double DEFAULT_HORIZON = 30 * 86400.0;
    public static final int MAX_SAMPLES = 20000;
    private static final double TIME_TOLERANCE = 1e-3;

    private final RailsUniverse universe;
    private final double horizon;
    private final Array<Entity> children = new Array<>();
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    public SoiPredictor(RailsUniverse universe) {
        this(universe, DEFAULT_HORIZON);
    }

    public SoiPredictor(RailsUniverse universe, double horizon) {
        this.universe = universe;
        this.horizon = horizon;
    }

    /** The earliest predicted event for {@code entity} after time t (never null; REPREDICT if nothing sooner). */
    public OrbitalEvent predict(Entity entity, double t) {
        OrbitComponent orbit = universe.orbit(entity);
        return predict(universe.uuidOf(entity), orbit.parentUuid, orbit.getOrbit(), orbit.version, t);
    }

    /**
     * The earliest predicted event for any conic around {@code parentUuid}, e.g. the bubble anchor,
     * which is not an entity. {@code id} and {@code version} are copied into the event.
     */
    public OrbitalEvent predict(String id, String parentUuid, KeplerOrbit k, int version, double t) {
        Entity parent = universe.entity(parentUuid);
        CelestialBodyComponent parentBody = universe.celestial(parent);
        if (parentBody == null) {
            // Parent not loaded yet (or missing): nothing to predict against. Try again later.
            return new OrbitalEvent(OrbitalEvent.Type.REPREDICT, t + 1, id, null, version);
        }

        double windowEnd = t + horizon;
        OrbitalEvent best = new OrbitalEvent(OrbitalEvent.Type.REPREDICT, windowEnd, id, null, version);

        if (universe.orbit(parent) != null && k.apoapsis() > parentBody.soiRadius) {
            double exit = k.nextTimeAtRadius(parentBody.soiRadius, t, KeplerOrbit.Crossing.OUTBOUND);
            if (exit < best.time) best = new OrbitalEvent(OrbitalEvent.Type.SOI_EXIT, exit, id, null, version);
        }
        if (k.periapsis() < parentBody.radius) {
            double impact = k.nextTimeAtRadius(parentBody.radius, t, KeplerOrbit.Crossing.INBOUND);
            if (impact < best.time) best = new OrbitalEvent(OrbitalEvent.Type.IMPACT, impact, id, null, version);
        }

        universe.childBodies(parent, children);
        for (Entity child : children) {
            if (id.equals(universe.uuidOf(child)) || universe.celestial(child) == null) continue;
            double end = best.time;
            double[] scanEnd = {end};
            double entry = findEntry(k, child, t, end, scanEnd);
            if (!Double.isNaN(entry) && entry < best.time) {
                best = new OrbitalEvent(OrbitalEvent.Type.SOI_ENTRY, entry, id, universe.uuidOf(child), version);
            } else if (scanEnd[0] < best.time) {
                // Sample budget ran out before the window ended: look again from there.
                best = new OrbitalEvent(OrbitalEvent.Type.REPREDICT, scanEnd[0], id, null, version);
            }
        }
        return best;
    }

    /**
     * First time in [t0, t1] at which conic {@code k} is inside {@code child}'s SOI, or NaN. Both are
     * relative to the same parent. {@code scanEnd[0]} is set to how far the scan actually got.
     */
    double findEntry(KeplerOrbit k, Entity child, double t0, double t1, double[] scanEnd) {
        OrbitComponent childOrbit = universe.orbit(child);
        CelestialBodyComponent childBody = universe.celestial(child);
        double soi = childBody.soiRadius;
        KeplerOrbit c = childOrbit.getOrbit();

        // Cheap cull: radial bands that never come within an SOI radius of each other.
        if (k.periapsis() > c.apoapsis() + soi || k.apoapsis() < c.periapsis() - soi) {
            scanEnd[0] = t1;
            return Double.NaN;
        }

        double relSpeed = RailsUniverse.maxSpeed(k) + RailsUniverse.maxSpeed(c);
        double step = 0.5 * soi / relSpeed;

        // Only an outside-to-inside crossing counts. A body that starts on or inside the boundary has
        // just left this SOI (or is about to), so it must get clear before it can enter again.
        // Steps use conservative advancement: the gap cannot close faster than relSpeed.
        double prevT = t0;
        double f = gap(k, c, soi, t0);
        boolean outside = f > 0;
        int samples = 0;
        while (prevT < t1 && samples++ < MAX_SAMPLES) {
            double sampleT = Math.min(prevT + Math.max(step, f / relSpeed), t1);
            f = gap(k, c, soi, sampleT);
            if (f <= 0 && outside) {
                scanEnd[0] = t1;
                return bisect(k, c, soi, prevT, sampleT);
            }
            if (f > 0) outside = true;
            prevT = sampleT;
        }
        scanEnd[0] = prevT;
        return Double.NaN;
    }

    private double bisect(KeplerOrbit k, KeplerOrbit c, double soi, double lo, double hi) {
        while (hi - lo > TIME_TOLERANCE) {
            double mid = 0.5 * (lo + hi);
            if (gap(k, c, soi, mid) > 0) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Distance from the child's SOI boundary at time t; negative inside. */
    private double gap(KeplerOrbit k, KeplerOrbit c, double soi, double t) {
        k.stateAt(t, a);
        c.stateAt(t, b);
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy) - soi;
    }
}
