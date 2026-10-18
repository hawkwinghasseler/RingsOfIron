package com.ringsofiron.orbital.math;

/** Solves Kepler's equation for elliptic and hyperbolic orbits. */
public final class KeplerSolver {
    public static final double TOLERANCE = 1e-12;
    private static final int MAX_ITERATIONS = 50;

    private KeplerSolver() {
    }

    /**
     * Eccentric anomaly E with {@code M = E - e sin E}, for 0 <= e < 1.
     * Newton-Raphson from E = M (e < 0.8) or E = pi, falling back to bisection if Newton stalls.
     */
    public static double eccentricAnomaly(double meanAnomaly, double e) {
        double m = wrapPi(meanAnomaly);
        double E = e < 0.8 ? m : Math.PI * Math.signum(m == 0 ? 1 : m);
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double f = E - e * Math.sin(E) - m;
            double step = f / (1 - e * Math.cos(E));
            E -= step;
            if (Math.abs(step) < TOLERANCE) return E;
        }
        // Newton did not converge (only plausible for e very close to 1). f(E) is monotonic on
        // [-pi, pi], so bisection always finds the root.
        double lo = -Math.PI;
        double hi = Math.PI;
        for (int i = 0; i < 200 && hi - lo > TOLERANCE; i++) {
            double mid = 0.5 * (lo + hi);
            if (mid - e * Math.sin(mid) - m < 0) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /** Hyperbolic anomaly H with {@code M = e sinh H - H}, for e > 1. */
    public static double hyperbolicAnomaly(double meanAnomaly, double e) {
        double m = meanAnomaly;
        // Good start for both small and large |M|.
        double H = Math.signum(m) * Math.log(2 * Math.abs(m) / e + 1.8);
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double f = e * Math.sinh(H) - H - m;
            double step = f / (e * Math.cosh(H) - 1);
            H -= step;
            if (Math.abs(step) < TOLERANCE * Math.max(1, Math.abs(H))) return H;
        }
        return H;
    }

    /** Wraps an angle into [-pi, pi). */
    public static double wrapPi(double angle) {
        double a = (angle + Math.PI) % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        return a - Math.PI;
    }

    /** Wraps an angle into [0, 2 pi). */
    public static double wrapTwoPi(double angle) {
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }
}
