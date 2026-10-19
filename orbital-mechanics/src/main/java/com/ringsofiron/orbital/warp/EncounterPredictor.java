package com.ringsofiron.orbital.warp;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.IdentityMap;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;

/**
 * Finds the first time another rails body comes within a radius of a reference body (the player),
 * for warp drop-out and bubble-entry prediction.
 *
 * <p>Distances are sampled by conservative advancement: never further ahead than the gap to the
 * radius divided by the maximum relative speed, and at least {@code radius / (2 * maxRelativeSpeed)}
 * so that bodies starting inside can be seen to leave. Only outside-to-inside crossings
 * count: bodies already near the player when warp starts do not stop it. If the sample budget runs
 * out first, the scan reports how far it got and the caller warps no further than that this frame.
 */
public class EncounterPredictor {
    public static final int DEFAULT_MAX_SAMPLES = 4000;
    private static final Family RAILS = Family.all(OrbitComponent.class, UuidComponent.class)
            .exclude(CelestialBodyComponent.class).get();

    public static final class Result {
        /** Earliest encounter time, or NaN. */
        public double time = Double.NaN;
        /** The body met at {@link #time}. */
        public Entity other;
        /** Everything before this time was checked. Equals the requested end unless the budget ran out. */
        public double scannedUntil;
    }

    private final RailsUniverse universe;
    private final ImmutableArray<Entity> bodies;
    private final int maxSamples;
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();
    private final IdentityMap<Entity, ParentInfo> byParent = new IdentityMap<>();
    /** The reference's and the current other body's orbits while each is around the scan's frame, else null. */
    private OrbitComponent refDirect, otherDirect;

    /** What a scan needs about the bodies orbiting one parent: the same for each of them. */
    private final class ParentInfo {
        /** Common frame of the reference and these bodies, or null if they share none. */
        final Entity frame;
        /** Upper bounds on the reference's speed, and on the parent's, relative to {@link #frame}. */
        final double referenceSpeed, parentSpeed;

        ParentInfo(Entity parent, Entity refParent, Entity reference) {
            frame = universe.commonAncestor(refParent, parent);
            referenceSpeed = frame == null ? 0 : universe.maxSpeedRelativeTo(reference, frame);
            parentSpeed = frame == null || parent == frame ? 0 : universe.maxSpeedRelativeTo(parent, frame);
        }
    }

    public EncounterPredictor(Engine engine, RailsUniverse universe) {
        this(engine, universe, DEFAULT_MAX_SAMPLES);
    }

    public EncounterPredictor(Engine engine, RailsUniverse universe, int maxSamples) {
        this.universe = universe;
        this.bodies = engine.getEntitiesFor(RAILS);
        this.maxSamples = maxSamples;
    }

    /** Earliest time in (t0, t1] at which any rails body enters {@code radius} of {@code reference}. */
    public Result find(Entity reference, double radius, double t0, double t1, Result out) {
        out.time = Double.NaN;
        out.other = null;
        out.scannedUntil = t1;
        if (universe.orbit(reference) == null) return out;
        Entity refParent = universe.parentOf(reference);
        byParent.clear();
        for (Entity other : bodies) {
            if (other == reference) continue;
            OrbitComponent otherOrbit = universe.orbit(other);
            Entity otherParent = universe.entity(otherOrbit.parentUuid);
            if (otherParent == null) continue;
            // Everything that depends only on the other body's parent is worked out once per parent
            ParentInfo p = byParent.get(otherParent);
            if (p == null) {
                p = new ParentInfo(otherParent, refParent, reference);
                byParent.put(otherParent, p);
            }
            Entity frame = p.frame;
            if (frame == null) continue;
            double end = Double.isNaN(out.time) ? out.scannedUntil : Math.min(out.time, out.scannedUntil);
            if (end <= t0) break;
            // Bodies around the frame itself take their state straight from their own orbit
            refDirect = refParent == frame ? universe.orbit(reference) : null;
            otherDirect = otherParent == frame ? otherOrbit : null;
            double relSpeed = p.referenceSpeed + (RailsUniverse.maxSpeed(otherOrbit.getOrbit()) + p.parentSpeed);
            double step = 0.5 * radius / Math.max(relSpeed, 1e-6);
            double scanEnd = end;

            // Conservative advancement: the gap to the radius cannot close faster than relSpeed, so
            // a body d - radius away can be skipped ahead by (d - radius) / relSpeed without missing
            // a crossing. Far bodies cost a handful of samples; close ones fall back to the fixed step.
            double d = distance(reference, other, frame, t0);
            boolean outside = d > radius;
            double prev = t0;
            int samples = 0;
            while (prev < scanEnd && samples++ < maxSamples) {
                double sample = Math.min(prev + Math.max(step, (d - radius) / relSpeed), scanEnd);
                d = distance(reference, other, frame, sample);
                if (d <= radius && outside) {
                    double hit = bisect(reference, other, frame, radius, prev, sample);
                    if (Double.isNaN(out.time) || hit < out.time) {
                        out.time = hit;
                        out.other = other;
                    }
                    break;
                }
                if (d > radius) outside = true;
                prev = sample;
            }
            if (prev < scanEnd && Double.isNaN(out.time)) scanEnd = prev;
            if (scanEnd < out.scannedUntil && (Double.isNaN(out.time) || scanEnd < out.time)) {
                out.scannedUntil = scanEnd;
            }
        }
        if (!Double.isNaN(out.time) && out.time > out.scannedUntil) {
            out.time = Double.NaN;
            out.other = null;
        }
        return out;
    }

    private double bisect(Entity ref, Entity other, Entity frame, double radius, double lo, double hi) {
        while (hi - lo > 1e-3) {
            double mid = 0.5 * (lo + hi);
            if (distance(ref, other, frame, mid) > radius) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Distance between the two at {@code t}, using {@link #refDirect} and {@link #otherDirect} where set. */
    private double distance(Entity ref, Entity other, Entity frame, double t) {
        if (refDirect != null) refDirect.stateAt(t, a);
        else universe.stateInFrame(ref, frame, t, a);
        if (otherDirect != null) otherDirect.stateAt(t, b);
        else universe.stateInFrame(other, frame, t, b);
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
