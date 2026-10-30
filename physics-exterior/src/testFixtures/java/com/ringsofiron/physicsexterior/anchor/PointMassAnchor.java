package com.ringsofiron.physicsexterior.anchor;

/**
 * A free-falling anchor around a point mass at (0, 0), integrated numerically with RK4.
 *
 * This is a stand-in until the analytic Kepler anchor from the {@code orbital} package is
 * available: same interface, so swapping it in changes nothing else. Steps are at most
 * {@link #MAX_STEP} seconds, which keeps the error far below Box2D's float precision in low
 * orbit. Queries are cheapest when time moves forward a tick at a time, as it does in play.
 */
public class PointMassAnchor implements AnchorModel {
    public static final double MAX_STEP = 1.0 / 60.0;

    private final double mu;
    private final double[] state = new double[4];
    private final double[] k1 = new double[4], k2 = new double[4], k3 = new double[4], k4 = new double[4];
    private final double[] tmp = new double[4];
    private double time;

    public PointMassAnchor(double mu, double x, double y, double vx, double vy, double t) {
        this.mu = mu;
        reset(x, y, vx, vy, t);
    }

    /** An anchor on a circular, counter-clockwise orbit of the given radius, starting on the +X axis. */
    public static PointMassAnchor circular(double mu, double radius, double t) {
        return new PointMassAnchor(mu, radius, 0.0, 0.0, Math.sqrt(mu / radius), t);
    }

    @Override
    public double getMu() {
        return mu;
    }

    @Override
    public void stateAt(double t, double[] out) {
        double remaining = t - time;
        int steps = (int) Math.ceil(Math.abs(remaining) / MAX_STEP - 1e-9);
        if (steps > 0) {
            double h = remaining / steps;
            for (int i = 0; i < steps; i++) rk4(h);
        }
        time = t;
        System.arraycopy(state, 0, out, 0, 4);
    }

    @Override
    public void reset(double x, double y, double vx, double vy, double t) {
        state[0] = x;
        state[1] = y;
        state[2] = vx;
        state[3] = vy;
        time = t;
    }

    private void rk4(double h) {
        derivative(state, k1);
        for (int i = 0; i < 4; i++) tmp[i] = state[i] + 0.5 * h * k1[i];
        derivative(tmp, k2);
        for (int i = 0; i < 4; i++) tmp[i] = state[i] + 0.5 * h * k2[i];
        derivative(tmp, k3);
        for (int i = 0; i < 4; i++) tmp[i] = state[i] + h * k3[i];
        derivative(tmp, k4);
        for (int i = 0; i < 4; i++) state[i] += h / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]);
    }

    private void derivative(double[] s, double[] out) {
        double r2 = s[0] * s[0] + s[1] * s[1];
        double k = -mu / (r2 * Math.sqrt(r2));
        out[0] = s[2];
        out[1] = s[3];
        out[2] = k * s[0];
        out[3] = k * s[1];
    }
}
