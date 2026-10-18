package com.ringsofiron.orbital.math;

/**
 * A 2D Keplerian conic around one parent body. Immutable.
 *
 * <p>Six numbers describe it fully in 2D: {@code mu}, semi-major axis {@code a} (negative for
 * hyperbolic orbits), eccentricity {@code e}, argument of periapsis (angle of periapsis from the
 * parent's +X axis), the mean anomaly at {@code epoch} and the direction of motion (+1
 * counter-clockwise, -1 clockwise). Positions are a pure function of time, so nothing is
 * integrated and there is no drift at any warp speed.
 *
 * <p>All units are SI: meters, seconds, m^3/s^2 for mu. Times are sim seconds.
 *
 * <p>Exactly parabolic orbits (e = 1) and purely radial trajectories (zero angular momentum) are not
 * represented; {@link #fromState} nudges e to 1 +/- {@value #PARABOLIC_EPSILON}. If near-parabolic
 * orbits become common, switch to a universal-variable formulation.
 */
public final class KeplerOrbit {
    public static final double PARABOLIC_EPSILON = 1e-9;
    /** Below this eccentricity the orbit is treated as circular and argPeriapsis is set to 0. */
    public static final double CIRCULAR_EPSILON = 1e-11;

    public enum Crossing {
        /** Radius increasing (leaving a sphere of influence). */
        OUTBOUND,
        /** Radius decreasing (hitting a surface). */
        INBOUND,
        EITHER
    }

    public final double mu;
    public final double a;
    public final double e;
    public final double argPeriapsis;
    public final double meanAnomalyAtEpoch;
    public final double epoch;
    public final int direction;

    public KeplerOrbit(double mu, double a, double e, double argPeriapsis,
                       double meanAnomalyAtEpoch, double epoch, int direction) {
        if (!(mu > 0)) throw new IllegalArgumentException("mu must be positive: " + mu);
        if (e < 0) throw new IllegalArgumentException("e must be >= 0: " + e);
        if (e < 1 && !(a > 0)) throw new IllegalArgumentException("elliptic orbit needs a > 0: " + a);
        if (e > 1 && !(a < 0)) throw new IllegalArgumentException("hyperbolic orbit needs a < 0: " + a);
        if (e == 1) throw new IllegalArgumentException("parabolic orbits are not supported");
        this.mu = mu;
        this.a = a;
        this.e = e;
        this.argPeriapsis = argPeriapsis;
        this.meanAnomalyAtEpoch = meanAnomalyAtEpoch;
        this.epoch = epoch;
        this.direction = direction >= 0 ? 1 : -1;
    }

    /** Circular orbit of the given radius, starting at {@code angle} from +X at time {@code epoch}. */
    public static KeplerOrbit circular(double mu, double radius, double angle, double epoch, int direction) {
        // With e = 0 and argPeriapsis = 0, mean anomaly = true anomaly = signed polar angle.
        int dir = direction >= 0 ? 1 : -1;
        return new KeplerOrbit(mu, radius, 0, 0, KeplerSolver.wrapTwoPi(dir * angle), epoch, dir);
    }

    /** Orbit from periapsis and apoapsis radii; the body is at periapsis at time {@code epoch}. */
    public static KeplerOrbit fromApsides(double mu, double periapsis, double apoapsis,
                                          double argPeriapsis, double epoch, int direction) {
        double a = 0.5 * (periapsis + apoapsis);
        double e = (apoapsis - periapsis) / (apoapsis + periapsis);
        return new KeplerOrbit(mu, a, e, argPeriapsis, 0, epoch, direction);
    }

    public boolean isElliptic() {
        return e < 1;
    }

    public boolean isHyperbolic() {
        return e > 1;
    }

    /** Mean motion n, rad/s. */
    public double meanMotion() {
        double absA = Math.abs(a);
        return Math.sqrt(mu / (absA * absA * absA));
    }

    /** Orbital period in seconds, or +infinity for hyperbolic orbits. */
    public double period() {
        return isElliptic() ? 2 * Math.PI / meanMotion() : Double.POSITIVE_INFINITY;
    }

    public double periapsis() {
        return a * (1 - e);
    }

    /** Apoapsis radius, or +infinity for hyperbolic orbits. */
    public double apoapsis() {
        return isElliptic() ? a * (1 + e) : Double.POSITIVE_INFINITY;
    }

    /** Specific orbital energy v^2/2 - mu/r, J/kg. */
    public double specificEnergy() {
        return -mu / (2 * a);
    }

    /** Mean anomaly at time t (elliptic: wrapped into [0, 2 pi); hyperbolic: unwrapped). */
    public double meanAnomalyAt(double t) {
        double m = meanAnomalyAtEpoch + meanMotion() * (t - epoch);
        return isElliptic() ? KeplerSolver.wrapTwoPi(m) : m;
    }

    /** Position and velocity relative to the parent at time t. */
    public StateVector stateAt(double t) {
        return stateAt(t, new StateVector());
    }

    /** Position and velocity relative to the parent at time t, written into {@code out}. */
    public StateVector stateAt(double t, StateVector out) {
        double n = meanMotion();
        double m = meanAnomalyAt(t);
        double px, py, pvx, pvy;
        if (isElliptic()) {
            double E = KeplerSolver.eccentricAnomaly(m, e);
            double cosE = Math.cos(E);
            double sinE = Math.sin(E);
            double b = a * Math.sqrt(1 - e * e);
            double eDot = n / (1 - e * cosE);
            px = a * (cosE - e);
            py = b * sinE;
            pvx = -a * sinE * eDot;
            pvy = b * cosE * eDot;
        } else {
            double H = KeplerSolver.hyperbolicAnomaly(m, e);
            double coshH = Math.cosh(H);
            double sinhH = Math.sinh(H);
            double absA = -a;
            double b = absA * Math.sqrt(e * e - 1);
            double hDot = n / (e * coshH - 1);
            px = absA * (e - coshH);
            py = b * sinhH;
            pvx = -absA * sinhH * hDot;
            pvy = b * coshH * hDot;
        }
        // Clockwise orbits are the mirror image of counter-clockwise ones in the perifocal frame.
        py *= direction;
        pvy *= direction;
        double c = Math.cos(argPeriapsis);
        double s = Math.sin(argPeriapsis);
        return out.set(c * px - s * py, s * px + c * py, c * pvx - s * pvy, s * pvx + c * pvy);
    }

    /**
     * Eccentric anomaly (elliptic) or hyperbolic anomaly (hyperbolic) at time t. Together with
     * {@link #positionAtAnomaly} this draws a conic evenly spaced along its shape rather than in
     * time, so the tight curve near periapsis stays smooth on long, eccentric orbits.
     */
    public double anomalyAt(double t) {
        double m = meanAnomalyAt(t);
        return isElliptic() ? KeplerSolver.eccentricAnomaly(m, e) : KeplerSolver.hyperbolicAnomaly(m, e);
    }

    /** Parent-relative position at an eccentric (or hyperbolic) anomaly; the velocity is set to zero. */
    public StateVector positionAtAnomaly(double anomaly, StateVector out) {
        double px, py;
        if (isElliptic()) {
            px = a * (Math.cos(anomaly) - e);
            py = a * Math.sqrt(1 - e * e) * Math.sin(anomaly);
        } else {
            double absA = -a;
            px = absA * (e - Math.cosh(anomaly));
            py = absA * Math.sqrt(e * e - 1) * Math.sinh(anomaly);
        }
        py *= direction;
        double c = Math.cos(argPeriapsis);
        double sn = Math.sin(argPeriapsis);
        return out.set(c * px - sn * py, sn * px + c * py, 0, 0);
    }

    /** Elements of the conic through {@code state} (parent-relative) at time t. */
    public static KeplerOrbit fromState(StateVector state, double mu, double t) {
        return fromState(state.x, state.y, state.vx, state.vy, mu, t);
    }

    public static KeplerOrbit fromState(double x, double y, double vx, double vy, double mu, double t) {
        double r = Math.sqrt(x * x + y * y);
        if (!(r > 0)) throw new IllegalArgumentException("state is at the parent's center");
        double v2 = vx * vx + vy * vy;
        double h = x * vy - y * vx;
        int direction = h >= 0 ? 1 : -1;

        double energy = 0.5 * v2 - mu / r;
        double rDotV = x * vx + y * vy;
        double ex = ((v2 - mu / r) * x - rDotV * vx) / mu;
        double ey = ((v2 - mu / r) * y - rDotV * vy) / mu;
        double e = Math.sqrt(ex * ex + ey * ey);

        if (Math.abs(e - 1) < PARABOLIC_EPSILON || energy == 0) {
            e = energy < 0 ? 1 - PARABOLIC_EPSILON : 1 + PARABOLIC_EPSILON;
        }
        double a;
        if (energy != 0) {
            a = -mu / (2 * energy);
        } else {
            // Exactly parabolic: use the semi-latus rectum p = h^2/mu = a(1 - e^2).
            a = (h * h / mu) / (1 - e * e);
        }

        double argPeriapsis;
        double trueAnomaly;
        double polar = Math.atan2(y, x);
        if (e < CIRCULAR_EPSILON) {
            e = 0;
            argPeriapsis = 0;
            trueAnomaly = KeplerSolver.wrapTwoPi(direction * polar);
        } else {
            argPeriapsis = Math.atan2(ey, ex);
            trueAnomaly = KeplerSolver.wrapPi(direction * (polar - argPeriapsis));
        }

        double meanAnomaly;
        if (e < 1) {
            double E = Math.atan2(Math.sqrt(1 - e * e) * Math.sin(trueAnomaly), e + Math.cos(trueAnomaly));
            meanAnomaly = KeplerSolver.wrapTwoPi(E - e * Math.sin(E));
        } else {
            double sinhH = Math.sqrt(e * e - 1) * Math.sin(trueAnomaly) / (1 + e * Math.cos(trueAnomaly));
            double H = asinh(sinhH);
            meanAnomaly = e * Math.sinh(H) - H;
        }
        return new KeplerOrbit(mu, a, e, argPeriapsis, meanAnomaly, t, direction);
    }

    /**
     * Earliest time >= t at which the body is at distance {@code radius} from the parent, crossing in
     * the requested sense. NaN if the conic never reaches that radius (or a circular orbit sits on it).
     */
    public double nextTimeAtRadius(double radius, double t, Crossing crossing) {
        if (e == 0) return Double.NaN;
        double ratio = (1 - radius / a) / e;
        double n = meanMotion();
        if (isElliptic()) {
            if (ratio < -1 || ratio > 1) return Double.NaN;
            double eOut = Math.acos(ratio);                 // in [0, pi]: radius increasing
            double mOut = eOut - e * Math.sin(eOut);
            double mIn = 2 * Math.PI - mOut;
            double now = meanAnomalyAt(t);
            double best = Double.POSITIVE_INFINITY;
            if (crossing != Crossing.INBOUND) best = Math.min(best, KeplerSolver.wrapTwoPi(mOut - now));
            if (crossing != Crossing.OUTBOUND) best = Math.min(best, KeplerSolver.wrapTwoPi(mIn - now));
            return t + best / n;
        } else {
            if (ratio < 1) return Double.NaN;
            double hOut = acosh(ratio);
            double mOut = e * Math.sinh(hOut) - hOut;
            double now = meanAnomalyAt(t);
            double best = Double.POSITIVE_INFINITY;
            if (crossing != Crossing.INBOUND && mOut >= now) best = Math.min(best, mOut - now);
            if (crossing != Crossing.OUTBOUND && -mOut >= now) best = Math.min(best, -mOut - now);
            return best == Double.POSITIVE_INFINITY ? Double.NaN : t + best / n;
        }
    }

    /** The same conic with its velocity changed by (dvx, dvy) at time t (an impulsive burn). */
    public KeplerOrbit withImpulse(double t, double dvx, double dvy) {
        StateVector s = stateAt(t);
        return fromState(s.x, s.y, s.vx + dvx, s.vy + dvy, mu, t);
    }

    /**
     * The same conic after a burn of {@code prograde} m/s along the velocity and {@code radial} m/s
     * perpendicular to it, pointing away from the parent's side of the orbit, at time t.
     */
    public KeplerOrbit withBurn(double t, double prograde, double radial) {
        StateVector s = stateAt(t);
        double speed = s.speed();
        double ux = s.vx / speed;
        double uy = s.vy / speed;
        // Rotating the velocity by -90 degrees points outward on a counter-clockwise orbit.
        double rx = uy * direction;
        double ry = -ux * direction;
        return fromState(s.x, s.y, s.vx + prograde * ux + radial * rx,
                s.vy + prograde * uy + radial * ry, mu, t);
    }

    private static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    private static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    @Override
    public String toString() {
        return String.format("KeplerOrbit[a=%.1f e=%.6f w=%.4f M0=%.4f epoch=%.1f dir=%d]",
                a, e, argPeriapsis, meanAnomalyAtEpoch, epoch, direction);
    }
}
