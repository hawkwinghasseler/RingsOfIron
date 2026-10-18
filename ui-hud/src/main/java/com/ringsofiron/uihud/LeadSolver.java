package com.ringsofiron.uihud;

/**
 * First-order intercept: how long until a projectile fired now at a constant speed (relative
 * to the shooter) can meet a target moving at a constant relative velocity.
 */
public final class LeadSolver {
    private LeadSolver() {}

    /**
     * @param dx, dy   target position minus shooter position, meters
     * @param vx, vy   target velocity minus shooter velocity, m/s
     * @param speed    projectile speed relative to the shooter, m/s
     * @return the earliest positive intercept time in seconds, or NaN if the shot can never catch up.
     */
    public static float interceptTime(float dx, float dy, float vx, float vy, float speed) {
        // |d + v t| = speed * t  ->  (v.v - s^2) t^2 + 2 (d.v) t + d.d = 0
        double a = (double) vx * vx + (double) vy * vy - (double) speed * speed;
        double b = 2.0 * ((double) dx * vx + (double) dy * vy);
        double c = (double) dx * dx + (double) dy * dy;
        if (c == 0.0) return 0f;
        if (Math.abs(a) < 1e-9) {
            // Target moves exactly as fast as the shot: one linear solution.
            if (b >= 0.0) return Float.NaN;
            return (float) (-c / b);
        }
        double disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return Float.NaN;
        double sq = Math.sqrt(disc);
        double t1 = (-b - sq) / (2.0 * a);
        double t2 = (-b + sq) / (2.0 * a);
        double t = Double.NaN;
        if (t1 > 0.0) t = t1;
        if (t2 > 0.0 && (Double.isNaN(t) || t2 < t)) t = t2;
        return (float) t;
    }
}
