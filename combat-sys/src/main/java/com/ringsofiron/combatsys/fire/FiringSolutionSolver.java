package com.ringsofiron.combatsys.fire;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.TidalAccel;

/**
 * Pure intercept math shared by AI gunners, the HUD lead reticle and missile guidance
 * (combat-sys/Design.md section 3.2). Allocation-free and stateless apart from scratch vectors,
 * so keep one instance per thread.
 *
 * Solves {@code |d + u*t + k*t^2/2| = s*t} for the smallest positive {@code t}, where
 * {@code d = p_t - p_s}, {@code u = v_t - v_s}, {@code k = a_t - a_proj} and {@code s} is the
 * round's speed relative to the shooter. Seeds with the {@code k = 0} quadratic and refines
 * with fixed-point iteration, re-sampling the round's tidal acceleration at the midpoint of its
 * predicted path on each pass.
 */
public class FiringSolutionSolver {
    /** Stop refining when the time-to-impact moves by less than this, seconds. */
    public static final float TOLERANCE = 1e-3f;
    public static final int MAX_ITERATIONS = 8;

    private final Vector2 aProj = new Vector2();
    private final Vector2 miss = new Vector2();

    /**
     * @param sx,sy     shooter position, local meters
     * @param svx,svy   shooter velocity, m/s
     * @param tx,ty     target position estimate
     * @param tvx,tvy   target velocity estimate
     * @param tax,tay   target acceleration estimate (including gravity), m/s^2
     * @param speed     round speed relative to the shooter, m/s
     * @param maxTime   longest flight time worth solving for, s
     * @param tidal     gravity field the round flies through; null means flat space
     * @return {@code out.valid}
     */
    public boolean solve(float sx, float sy, float svx, float svy,
                         float tx, float ty, float tvx, float tvy, float tax, float tay,
                         float speed, float maxTime, TidalAccel tidal, FiringSolution out) {
        out.invalidate();
        if (speed <= 0f) return false;

        float dx = tx - sx, dy = ty - sy;
        float ux = tvx - svx, uy = tvy - svy;

        // Seed: (u.u - s^2) t^2 + 2 (d.u) t + d.d = 0
        float a = ux * ux + uy * uy - speed * speed;
        float b = 2f * (dx * ux + dy * uy);
        float c = dx * dx + dy * dy;
        float t = smallestPositiveRoot(a, b, c);
        if (Float.isNaN(t) || t > maxTime) return false;

        int iterations = 0;
        float kx = tax, ky = tay;
        for (; iterations < MAX_ITERATIONS; iterations++) {
            // Round's own gravity, sampled halfway along its predicted path.
            float px = dx + ux * t + 0.5f * kx * t * t;
            float py = dy + uy * t + 0.5f * ky * t * t;
            if (tidal != null) {
                tidal.accelAt(sx + 0.5f * px, sy + 0.5f * py, aProj);
            } else {
                aProj.setZero();
            }
            kx = tax - aProj.x;
            ky = tay - aProj.y;

            px = dx + ux * t + 0.5f * kx * t * t;
            py = dy + uy * t + 0.5f * ky * t * t;
            float next = (float) Math.sqrt(px * px + py * py) / speed;
            float delta = Math.abs(next - t);
            t = next;
            if (t > maxTime) return false;
            if (delta < TOLERANCE) {
                iterations++;
                break;
            }
        }
        if (!(t > 0f)) return false;

        miss.set(dx + ux * t + 0.5f * kx * t * t, dy + uy * t + 0.5f * ky * t * t);
        out.aimDir.set(miss).scl(1f / (speed * t));
        // The fixed point leaves |aimDir| within tolerance of 1; make it exact.
        out.aimDir.nor();
        out.timeToImpact = t;
        out.interceptPoint.set(tx + tvx * t + 0.5f * tax * t * t, ty + tvy * t + 0.5f * tay * t * t);
        out.iterations = iterations;
        out.valid = true;
        return true;
    }

    /** Smallest positive root of {@code a t^2 + b t + c = 0}, or NaN if there is none. */
    static float smallestPositiveRoot(float a, float b, float c) {
        if (Math.abs(a) < 1e-6f * Math.max(1f, Math.abs(b) + Math.abs(c))) {
            // Round speed equals closing speed: linear.
            if (b == 0f) return c == 0f ? 0f : Float.NaN;
            float t = -c / b;
            return t > 0f ? t : Float.NaN;
        }
        double disc = (double) b * b - 4.0 * a * c;
        if (disc < 0) return Float.NaN;
        double sq = Math.sqrt(disc);
        // Numerically stable pair of roots.
        double q = -0.5 * (b + Math.copySign(sq, b));
        double r1 = q / a;
        double r2 = q != 0 ? c / q : r1;
        double lo = Math.min(r1, r2), hi = Math.max(r1, r2);
        if (lo > 0) return (float) lo;
        if (hi > 0) return (float) hi;
        return Float.NaN;
    }
}
