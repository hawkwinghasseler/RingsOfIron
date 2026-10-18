package com.ringsofiron.macrosim.rails.stub;

import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;

/**
 * Immutable 2D Keplerian conic around one parent, in doubles. Stand-in for the shared
 * {@code com.ringsofiron.orbital} math until it lands; field meanings follow
 * {@code documentation/orbital-mechanics/Design.md} section 2.
 */
public final class StubKeplerOrbit implements Trajectory {
    private static final double TWO_PI = 2 * Math.PI;

    public final double mu;
    /** Semi-major axis, m; negative for hyperbolic orbits. */
    public final double a;
    public final double e;
    public final double argPeriapsis;
    public final double meanAnomalyAtEpoch;
    public final double epoch;
    /** +1 counter-clockwise, -1 clockwise. */
    public final int direction;

    public StubKeplerOrbit(double mu, double a, double e, double argPeriapsis,
                           double meanAnomalyAtEpoch, double epoch, int direction) {
        this.mu = mu;
        this.a = a;
        this.e = e;
        this.argPeriapsis = argPeriapsis;
        this.meanAnomalyAtEpoch = meanAnomalyAtEpoch;
        this.epoch = epoch;
        this.direction = direction >= 0 ? 1 : -1;
    }

    /** Circular orbit of radius {@code r} with the body at polar angle {@code phase} at {@code epoch}. */
    public static StubKeplerOrbit circular(double mu, double r, double phase, double epoch, int direction) {
        // With e = 0 the periapsis is arbitrary; put it on +X so the mean anomaly is the polar angle.
        double m = direction >= 0 ? phase : -phase;
        return new StubKeplerOrbit(mu, r, 0, 0, wrap(m), epoch, direction);
    }

    public boolean isElliptic() {
        return e < 1.0;
    }

    public double meanMotion() {
        return Math.sqrt(mu / Math.abs(a * a * a));
    }

    /** Orbital period in seconds, or {@code +inf} if not elliptic. */
    public double period() {
        return isElliptic() ? TWO_PI / meanMotion() : Double.POSITIVE_INFINITY;
    }

    @Override
    public double minRadius() {
        return isElliptic() ? a * (1 - e) : -a * (e - 1);
    }

    @Override
    public double maxRadius() {
        return isElliptic() ? a * (1 + e) : Double.POSITIVE_INFINITY;
    }

    @Override
    public double maxSpeed() {
        double rp = minRadius();
        return Math.sqrt(mu * (2 / rp - 1 / a));
    }

    @Override
    public StateVector stateAt(double t, StateVector out) {
        double m = meanAnomalyAtEpoch + meanMotion() * (t - epoch);
        double px, py, pvx, pvy;
        if (isElliptic()) {
            m = wrap(m);
            double ea = solveElliptic(m, e);
            double cosE = Math.cos(ea), sinE = Math.sin(ea);
            double b = Math.sqrt(1 - e * e);
            double r = a * (1 - e * cosE);
            double k = Math.sqrt(mu * a) / r;
            px = a * (cosE - e);
            py = a * b * sinE;
            pvx = -k * sinE;
            pvy = k * b * cosE;
        } else {
            double aa = -a;
            double h = solveHyperbolic(m, e);
            double coshH = Math.cosh(h), sinhH = Math.sinh(h);
            double b = Math.sqrt(e * e - 1);
            double r = aa * (e * coshH - 1);
            double k = Math.sqrt(mu * aa) / r;
            px = aa * (e - coshH);
            py = aa * b * sinhH;
            pvx = -k * sinhH;
            pvy = k * b * coshH;
        }
        double c = Math.cos(argPeriapsis), s = Math.sin(argPeriapsis);
        out.x = px * c - py * s;
        out.y = px * s + py * c;
        out.vx = pvx * c - pvy * s;
        out.vy = pvx * s + pvy * c;
        if (direction < 0) {
            out.y = -out.y;
            out.vy = -out.vy;
        }
        return out;
    }

    /** Elements of the conic through state {@code s} (parent-relative) at sim second {@code t}. */
    public static StubKeplerOrbit fromState(double mu, StateVector s, double t) {
        double rx = s.x, ry = s.y, vx = s.vx, vy = s.vy;
        int dir = (rx * vy - ry * vx) < 0 ? -1 : 1;
        if (dir < 0) {
            // Work in a mirrored frame where the motion is counter-clockwise.
            ry = -ry;
            vy = -vy;
        }
        double r = Math.hypot(rx, ry);
        double v2 = vx * vx + vy * vy;
        double rv = rx * vx + ry * vy;
        double ex = ((v2 - mu / r) * rx - rv * vx) / mu;
        double ey = ((v2 - mu / r) * ry - rv * vy) / mu;
        double e = Math.hypot(ex, ey);
        double energy = v2 / 2 - mu / r;
        double a = -mu / (2 * energy);

        double argP;
        double nu;
        if (e < 1e-11) {
            e = 0;
            argP = 0;
            nu = Math.atan2(ry, rx);
        } else {
            argP = Math.atan2(ey, ex);
            nu = Math.atan2(ex * ry - ey * rx, ex * rx + ey * ry);
        }

        double m;
        if (e < 1) {
            double ea = Math.atan2(Math.sqrt(1 - e * e) * Math.sin(nu), e + Math.cos(nu));
            m = wrap(ea - e * Math.sin(ea));
        } else {
            if (Math.abs(e - 1) < 1e-9) e = 1 + 1e-9; // nudge exactly-parabolic states onto a hyperbola
            double sinhH = Math.sqrt(e * e - 1) * Math.sin(nu) / (1 + e * Math.cos(nu));
            double h = asinh(sinhH);
            m = e * Math.sinh(h) - h;
        }
        return new StubKeplerOrbit(mu, a, e, argP, m, t, dir);
    }

    /** Kepler's equation M = E - e sin E by Newton-Raphson. */
    static double solveElliptic(double m, double e) {
        double ea = e < 0.8 ? m : Math.PI;
        for (int i = 0; i < 30; i++) {
            double f = ea - e * Math.sin(ea) - m;
            double d = f / (1 - e * Math.cos(ea));
            ea -= d;
            if (Math.abs(d) < 1e-13) break;
        }
        return ea;
    }

    /** Hyperbolic Kepler's equation M = e sinh H - H by Newton-Raphson. */
    static double solveHyperbolic(double m, double e) {
        double h = asinh(m / e);
        for (int i = 0; i < 50; i++) {
            double f = e * Math.sinh(h) - h - m;
            double d = f / (e * Math.cosh(h) - 1);
            h -= d;
            if (Math.abs(d) < 1e-13) break;
        }
        return h;
    }

    static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    static double wrap(double angle) {
        angle %= TWO_PI;
        return angle < 0 ? angle + TWO_PI : angle;
    }
}
