package com.ringsofiron.orbital.math;

/** Small orbital formulas shared by rails and bubble code. SI units. */
public final class OrbitMath {
    /** Gravitational constant, m^3 / (kg s^2). */
    public static final double G = 6.674e-11;

    private OrbitMath() {
    }

    /** Laplace sphere of influence radius: a * (m / M)^(2/5). */
    public static double soiRadius(double semiMajorAxis, double bodyMu, double parentMu) {
        return semiMajorAxis * Math.pow(bodyMu / parentMu, 0.4);
    }

    /** Speed of a circular orbit at radius r. */
    public static double circularSpeed(double mu, double r) {
        return Math.sqrt(mu / r);
    }

    /** Escape speed at radius r. */
    public static double escapeSpeed(double mu, double r) {
        return Math.sqrt(2 * mu / r);
    }

    /**
     * Tidal (differential) acceleration on a body at offset (dx, dy) m from a free-falling anchor at
     * (ax, ay) m from the parent: {@code g(anchor + d) - g(anchor)} with {@code g(X) = -mu X / |X|^3}.
     *
     * <p>This is all the gravity a bubble body feels in the anchor's frame. Computed in doubles; cast
     * to float only when handing it to Box2D. Result is written into {@code out[0..1]} in m/s^2.
     */
    public static double[] tidalAcceleration(double mu, double ax, double ay, double dx, double dy, double[] out) {
        double bx = ax + dx;
        double by = ay + dy;
        double rb2 = bx * bx + by * by;
        double ra2 = ax * ax + ay * ay;
        double rb3 = rb2 * Math.sqrt(rb2);
        double ra3 = ra2 * Math.sqrt(ra2);
        out[0] = -mu * bx / rb3 + mu * ax / ra3;
        out[1] = -mu * by / rb3 + mu * ay / ra3;
        return out;
    }

    /** Gravitational acceleration at (x, y) m from a body with parameter mu, into {@code out[0..1]}. */
    public static double[] gravity(double mu, double x, double y, double[] out) {
        double r2 = x * x + y * y;
        double r3 = r2 * Math.sqrt(r2);
        out[0] = -mu * x / r3;
        out[1] = -mu * y / r3;
        return out;
    }
}
