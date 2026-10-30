package com.ringsofiron.aicrew;

import com.ringsofiron.interiorgrid.HullSurface;

/** The rope's swing for {@link EvaTether}: its points moved by verlet and held to their lengths. */
final class RopeSwing {
    private final EvaSystem sys;

    RopeSwing(EvaSystem sys) {
        this.sys = sys;
    }

    /**
     * Moves rope {@code r} on by {@code dt} ({@code prev}: its points a step ago): the free points
     * swing on, losing some of their motion relative to the hull beside them; the first goes with the
     * reel at ({@code ax}, {@code ay}) and, if {@code held}, the last with the crew member at ({@code
     * ex}, {@code ey}). Then no segment is let stay longer than {@code rest}, and the rope is pushed
     * out of the hull.
     */
    void swing(float[] r, float[] prev, float ax, float ay, boolean held, float ex, float ey, float rest,
               HullSurface hull, float dt) {
        int n = r.length / 2 - 1, free = held ? 2 * n : 2 * n + 2;
        for (int i = 2; i < free; i += 2) {
            float x = r[i], y = r[i + 1];
            sys.frame.velocityAt(x, y, sys.c4);
            float vx = x - prev[i], vy = y - prev[i + 1];
            float sx = sys.c4[0] * dt, sy = sys.c4[1] * dt;
            vx = sx + (vx - sx) * EvaSystem.ROPE_KEEP;
            vy = sy + (vy - sy) * EvaSystem.ROPE_KEEP;
            prev[i] = x;
            prev[i + 1] = y;
            r[i] = x + vx;
            r[i + 1] = y + vy;
        }
        prev[0] = r[0];
        prev[1] = r[1];
        r[0] = ax;
        r[1] = ay;
        if (held) {
            prev[2 * n] = r[2 * n];
            prev[2 * n + 1] = r[2 * n + 1];
            r[2 * n] = ex;
            r[2 * n + 1] = ey;
        }
        for (int it = 0; it < EvaSystem.ROPE_ITERATIONS; it++) {
            // Sweeping from each end in turn, so a pull at either end travels the length of the rope
            for (int step = 0; step < n; step++) {
                int i = it % 2 == 0 ? step : n - 1 - step;
                int j = 2 * i, k = j + 2;
                float dx = r[k] - r[j], dy = r[k + 1] - r[j + 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d <= rest || d < 1e-6f) continue;
                float wa = i == 0 ? 0f : 1f, wb = held && i == n - 1 ? 0f : 1f;
                if (wa + wb == 0f) continue;
                float move = (d - rest) / d / (wa + wb);
                r[j] += dx * move * wa;
                r[j + 1] += dy * move * wa;
                r[k] -= dx * move * wb;
                r[k + 1] -= dy * move * wb;
            }
            // Out of the hull
            if (it >= EvaSystem.ROPE_ITERATIONS - 3) {
                for (int i = 2; i < free; i += 2) {
                    sys.frame.toTiles(r[i], r[i + 1], sys.q);
                    if (!hull.pushOut(sys.q[0], sys.q[1], sys.b)) continue;
                    sys.frame.toWorld(sys.b[0], sys.b[1], sys.q);
                    r[i] = sys.q[0];
                    r[i + 1] = sys.q[1];
                }
            }
        }
    }
}
