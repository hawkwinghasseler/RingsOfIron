package com.ringsofiron.orbital.warp;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;

/**
 * Finds the first time another rails body comes within a radius of a reference body (the player),
 * for warp drop-out and bubble-entry prediction.
 *
 * <p>Distances are sampled with a step of {@code radius / (2 * maxRelativeSpeed)}, so the distance
 * cannot change by more than half the radius between samples. Only outside-to-inside crossings
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
        for (Entity other : bodies) {
            if (other == reference) continue;
            Entity frame = universe.commonAncestor(universe.parentOf(reference), universe.parentOf(other));
            if (frame == null) continue;
            double end = Double.isNaN(out.time) ? out.scannedUntil : Math.min(out.time, out.scannedUntil);
            if (end <= t0) break;
            double relSpeed = universe.maxSpeedRelativeTo(reference, frame) + universe.maxSpeedRelativeTo(other, frame);
            double step = 0.5 * radius / Math.max(relSpeed, 1e-6);
            double scanEnd = Math.min(end, t0 + step * maxSamples);

            boolean outside = distance(reference, other, frame, t0) > radius;
            double prev = t0;
            for (double t = t0 + step; prev < scanEnd; t += step) {
                double sample = Math.min(t, scanEnd);
                double d = distance(reference, other, frame, sample);
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

    private double distance(Entity ref, Entity other, Entity frame, double t) {
        universe.stateInFrame(ref, frame, t, a);
        universe.stateInFrame(other, frame, t, b);
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
