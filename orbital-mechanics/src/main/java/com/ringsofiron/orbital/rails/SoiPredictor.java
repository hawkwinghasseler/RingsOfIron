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
 * SOI is found by sampling the distance with a step small enough that it cannot change by more than
 * half the SOI radius between samples, then bisecting. Only an orbit that grazes the very edge of an
 * SOI can slip between samples.
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
        String id = universe.uuidOf(entity);
        int version = orbit.version;
        KeplerOrbit k = orbit.getOrbit();
        Entity parent = universe.entity(orbit.parentUuid);
        CelestialBodyComponent parentBody = universe.celestial(parent);

        double windowEnd = t + horizon;
        OrbitalEvent best = new OrbitalEvent(OrbitalEvent.Type.REPREDICT, windowEnd, id, null, version);

        if (parentBody != null && universe.orbit(parent) != null && k.apoapsis() > parentBody.soiRadius) {
            double exit = k.nextTimeAtRadius(parentBody.soiRadius, t, KeplerOrbit.Crossing.OUTBOUND);
            if (exit < best.time) best = new OrbitalEvent(OrbitalEvent.Type.SOI_EXIT, exit, id, null, version);
        }
        if (parentBody != null && k.periapsis() < parentBody.radius) {
            double impact = k.nextTimeAtRadius(parentBody.radius, t, KeplerOrbit.Crossing.INBOUND);
            if (impact < best.time) best = new OrbitalEvent(OrbitalEvent.Type.IMPACT, impact, id, null, version);
        }

        universe.childBodies(parent, children);
        for (Entity child : children) {
            if (child == entity) continue;
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
        double end = Math.min(t1, t0 + step * MAX_SAMPLES);
        scanEnd[0] = end;

        // Only an outside-to-inside crossing counts. A body that starts on or inside the boundary has
        // just left this SOI (or is about to), so it must get clear before it can enter again.
        double prevT = t0;
        boolean outside = gap(k, c, soi, t0) > 0;
        for (double tt = t0 + step; prevT < end; tt += step) {
            double sampleT = Math.min(tt, end);
            double f = gap(k, c, soi, sampleT);
            if (f <= 0 && outside) return bisect(k, c, soi, prevT, sampleT);
            if (f > 0) outside = true;
            prevT = sampleT;
        }
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
