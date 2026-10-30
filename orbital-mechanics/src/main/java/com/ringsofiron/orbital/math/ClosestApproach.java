package com.ringsofiron.orbital.math;

import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

import java.util.ArrayList;
import java.util.List;

/**
 * The closest approach between the player's planned path (the conic it is on, then the one after
 * each planned burn) and a target over a stretch of time: sampled evenly, then refined around the
 * nearest sample. Both are in one body's frame.
 */
public final class ClosestApproach {
    /** Samples over the whole stretch before refining. */
    public static final int SAMPLES = 360;

    /** The target's state at a time, in the path's frame; false if it can't be placed. */
    public interface Target {
        boolean stateAt(double t, StateVector out);
    }

    /** The player's path: conics, each flown from its start time until the next one's. */
    public static final class Path {
        final List<double[]> starts = new ArrayList<>();
        final List<KeplerOrbit> conics = new ArrayList<>();

        public Path add(double from, KeplerOrbit conic) {
            starts.add(new double[]{from});
            conics.add(conic);
            return this;
        }

        public boolean isEmpty() {
            return conics.isEmpty();
        }

        /** Start time of the last conic. */
        public double lastStart() {
            return starts.get(starts.size() - 1)[0];
        }

        public KeplerOrbit last() {
            return conics.get(conics.size() - 1);
        }

        public StateVector stateAt(double t, StateVector out) {
            int i = conics.size() - 1;
            while (i > 0 && starts.get(i)[0] > t) i--;
            return conics.get(i).stateAt(t, out);
        }
    }

    public static final class Result {
        /** Sim second of the closest approach, NaN when none was found. */
        public double time = Double.NaN;
        public double distance = Double.POSITIVE_INFINITY;
        /** Speed of the one relative to the other then, m/s. */
        public double speed;
        public final StateVector ship = new StateVector();
        public final StateVector target = new StateVector();

        public boolean found() {
            return !Double.isNaN(time);
        }
    }

    private ClosestApproach() {
    }

    /** Finds the closest approach between {@code from} and {@code to} into {@code out}, and returns it. */
    public static Result find(Path path, Target target, double from, double to, Result out) {
        out.time = Double.NaN;
        out.distance = Double.POSITIVE_INFINITY;
        if (path.isEmpty() || !(to > from)) return out;
        StateVector s = new StateVector(), q = new StateVector();
        double step = (to - from) / SAMPLES;
        double best = Double.NaN, bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SAMPLES; i++) {
            double t = from + i * step;
            double d = distance(path, target, t, s, q);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        if (Double.isNaN(best)) return out;
        // Golden-section search in the samples either side of the best one
        double lo = Math.max(from, best - step), hi = Math.min(to, best + step);
        double g = (Math.sqrt(5) - 1) / 2;
        for (int i = 0; i < 50 && hi - lo > 1e-3; i++) {
            double a = hi - g * (hi - lo), b = lo + g * (hi - lo);
            if (distance(path, target, a, s, q) < distance(path, target, b, s, q)) hi = b;
            else lo = a;
        }
        double t = (lo + hi) / 2;
        double d = distance(path, target, t, s, q);
        if (d > bestD) {
            t = best;
            d = distance(path, target, t, s, q);
        }
        out.time = t;
        out.distance = d;
        out.ship.set(s);
        out.target.set(q);
        out.speed = Math.hypot(s.vx - q.vx, s.vy - q.vy);
        return out;
    }

    private static double distance(Path path, Target target, double t, StateVector s, StateVector q) {
        path.stateAt(t, s);
        if (!target.stateAt(t, q)) return Double.POSITIVE_INFINITY;
        return Math.hypot(s.x - q.x, s.y - q.y);
    }
}
