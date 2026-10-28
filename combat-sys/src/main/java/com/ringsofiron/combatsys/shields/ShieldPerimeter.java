package com.ringsofiron.combatsys.shields;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * The closed curve a ship's shield cloud sits on, in the body frame: halfway between the ellipse
 * through the corners of the hull's box and a line {@link #STANDOFF} off the hull itself, smoothed.
 * Points along it are named by their arc length {@code s} from sample 0, in meters, which wraps.
 * The simulation (where a round meets the cloud) and the particles drawn use the same curve.
 */
public final class ShieldPerimeter {
    static final float ROOT_2 = (float) Math.sqrt(2);
    /** Samples round the curve, one per bearing step. */
    public static final int SAMPLES = 96;
    /** How far the hull-hugging line stands off the hull, meters. */
    public static final float STANDOFF = 2f;
    /** However the blend falls, the curve keeps at least this far off the hull, meters. */
    static final float MIN_CLEARANCE = 1f;

    /** The center of the hull's box, body-local meters: where bearings are measured from. */
    public float cx, cy;
    /** The hull's box half-size, meters. */
    public float halfW, halfH;
    /** The samples, body-local meters, counterclockwise from bearing 0. */
    public final float[] x = new float[SAMPLES], y = new float[SAMPLES];
    /** Arc length from sample 0 to each sample, meters; {@link #length} closes the loop. */
    public final float[] along = new float[SAMPLES + 1];
    /** The whole curve's length, meters. */
    public float length;
    /** Farthest the curve gets from its center, meters: a quick reject for rays. */
    public float reach;

    /** The curve round {@code grid}'s tiles that are not void or crushed. */
    public static ShieldPerimeter fit(ShipGridComponent grid) {
        ShieldPerimeter p = new ShieldPerimeter();
        int x0 = grid.width, y0 = grid.height, x1 = -1, y1 = -1;
        for (int ty = 0; ty < grid.height; ty++) {
            for (int tx = 0; tx < grid.width; tx++) {
                if (!solid(grid.getTile(tx, ty))) continue;
                x0 = Math.min(x0, tx);
                y0 = Math.min(y0, ty);
                x1 = Math.max(x1, tx);
                y1 = Math.max(y1, ty);
            }
        }
        if (x1 < 0) {
            x0 = y0 = 0;
            x1 = grid.width - 1;
            y1 = grid.height - 1;
        }
        float s = grid.tileSize;
        p.halfW = 0.5f * (x1 - x0 + 1) * s;
        p.halfH = 0.5f * (y1 - y0 + 1) * s;
        p.cx = grid.gridOriginLocal.x + x0 * s + p.halfW;
        p.cy = grid.gridOriginLocal.y + y0 * s + p.halfH;
        // The hull's reach at each bearing: the farthest corner or edge midpoint of a solid tile
        float[] hull = new float[SAMPLES];
        for (int ty = 0; ty < grid.height; ty++) {
            for (int tx = 0; tx < grid.width; tx++) {
                if (!solid(grid.getTile(tx, ty))) continue;
                float lx = grid.gridOriginLocal.x + tx * s - p.cx, ly = grid.gridOriginLocal.y + ty * s - p.cy;
                for (int i = 0; i <= 2; i++) {
                    for (int j = 0; j <= 2; j++) {
                        if (i == 1 && j == 1) continue;
                        float px = lx + 0.5f * i * s, py = ly + 0.5f * j * s;
                        int bin = bin(MathUtils.atan2(py, px));
                        hull[bin] = Math.max(hull[bin], (float) Math.sqrt(px * px + py * py));
                    }
                }
            }
        }
        fillGaps(hull);
        float a = p.halfW * ROOT_2, b = p.halfH * ROOT_2;
        float[] r = new float[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            float th = bearing(i), c = MathUtils.cos(th), sn = MathUtils.sin(th);
            float ellipse = a * b / (float) Math.sqrt(b * b * c * c + a * a * sn * sn);
            r[i] = 0.5f * ellipse + 0.5f * (hull[i] + STANDOFF);
        }
        smooth(r, 3);
        p.reach = 0f;
        for (int i = 0; i < SAMPLES; i++) {
            r[i] = Math.max(r[i], hull[i] + MIN_CLEARANCE);
            float th = bearing(i);
            p.x[i] = p.cx + r[i] * MathUtils.cos(th);
            p.y[i] = p.cy + r[i] * MathUtils.sin(th);
            p.reach = Math.max(p.reach, r[i]);
        }
        p.along[0] = 0f;
        for (int i = 0; i < SAMPLES; i++) {
            int j = (i + 1) % SAMPLES;
            p.along[i + 1] = p.along[i] + (float) Math.hypot(p.x[j] - p.x[i], p.y[j] - p.y[i]);
        }
        p.length = p.along[SAMPLES];
        return p;
    }

    private static boolean solid(Tile t) {
        return t != null && !t.isVoid() && !t.isCrushed();
    }

    static float bearing(int i) {
        return i * MathUtils.PI2 / SAMPLES;
    }

    static int bin(float angle) {
        float u = angle / MathUtils.PI2;
        u -= (float) Math.floor(u);
        return Math.min(SAMPLES - 1, Math.round(u * SAMPLES) % SAMPLES);
    }

    /** Bearings no tile reached take the larger of their nearest filled neighbours either way. */
    private static void fillGaps(float[] r) {
        float[] out = r.clone();
        for (int i = 0; i < SAMPLES; i++) {
            if (r[i] > 0f) continue;
            float left = 0f, right = 0f;
            for (int k = 1; k < SAMPLES && left == 0f; k++) left = r[(i - k + SAMPLES) % SAMPLES];
            for (int k = 1; k < SAMPLES && right == 0f; k++) right = r[(i + k) % SAMPLES];
            out[i] = Math.max(left, right);
        }
        System.arraycopy(out, 0, r, 0, SAMPLES);
    }

    private static void smooth(float[] r, int passes) {
        float[] t = new float[SAMPLES];
        for (int p = 0; p < passes; p++) {
            for (int i = 0; i < SAMPLES; i++) {
                t[i] = 0.25f * r[(i - 1 + SAMPLES) % SAMPLES] + 0.5f * r[i] + 0.25f * r[(i + 1) % SAMPLES];
            }
            System.arraycopy(t, 0, r, 0, SAMPLES);
        }
    }

    /** {@code s} wrapped into [0, length). */
    public float wrap(float s) {
        s %= length;
        return s < 0f ? s + length : s;
    }

    /** Shortest distance along the curve from {@code from} to {@code to}, signed (positive counterclockwise). */
    public float between(float from, float to) {
        float d = wrap(to - from);
        return d > 0.5f * length ? d - length : d;
    }

    /** The point {@code s} meters along the curve, body-local. */
    public Vector2 pointAt(float s, Vector2 out) {
        s = wrap(s);
        int i = segment(s);
        int j = (i + 1) % SAMPLES;
        float seg = along[i + 1] - along[i];
        float f = seg > 0f ? (s - along[i]) / seg : 0f;
        return out.set(x[i] + (x[j] - x[i]) * f, y[i] + (y[j] - y[i]) * f);
    }

    /** The outward unit normal {@code s} meters along the curve. */
    public Vector2 normalAt(float s, Vector2 out) {
        int i = segment(wrap(s));
        int j = (i + 1) % SAMPLES;
        float dx = x[j] - x[i], dy = y[j] - y[i];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        // Counterclockwise curve: outward is the tangent turned clockwise
        return len > 0f ? out.set(dy / len, -dx / len) : out.set(1f, 0f);
    }

    /** The segment of the curve {@code s} meters along it lies in: from sample i to the next. */
    public int segmentAt(float s) {
        return segment(wrap(s));
    }

    private int segment(float s) {
        int lo = 0, hi = SAMPLES - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (along[mid] <= s) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /**
     * Where a ray from ({@code px}, {@code py}) along unit ({@code dx}, {@code dy}) crosses the
     * curve within {@code maxT} meters: the nearest crossing when {@code nearest}, else the
     * farthest. Fills {@code out} with {s, t}; false when it crosses nowhere in range.
     */
    public boolean cross(float px, float py, float dx, float dy, float maxT, boolean nearest, float[] out) {
        // Quick reject: the ray's closest approach to the center is beyond the curve's reach
        float ox = cx - px, oy = cy - py;
        float along0 = ox * dx + oy * dy;
        float closest = Math.max(0f, Math.min(maxT, along0));
        float qx = px + dx * closest - cx, qy = py + dy * closest - cy;
        if (qx * qx + qy * qy > reach * reach) return false;
        float best = nearest ? Float.MAX_VALUE : -1f, bestS = 0f;
        for (int i = 0; i < SAMPLES; i++) {
            int j = (i + 1) % SAMPLES;
            float ex = x[j] - x[i], ey = y[j] - y[i];
            float den = dx * ey - dy * ex;
            if (Math.abs(den) < 1e-9f) continue;
            float wx = x[i] - px, wy = y[i] - py;
            float t = (wx * ey - wy * ex) / den;
            float u = (wx * dy - wy * dx) / den;
            if (t < 0f || t > maxT || u < 0f || u > 1f) continue;
            if (nearest ? t < best : t > best) {
                best = t;
                bestS = along[i] + u * (along[i + 1] - along[i]);
            }
        }
        if (nearest ? best == Float.MAX_VALUE : best < 0f) return false;
        out[0] = bestS;
        out[1] = best;
        return true;
    }

    /** True if body-local ({@code px}, {@code py}) is inside the curve. */
    public boolean contains(float px, float py) {
        boolean in = false;
        for (int i = 0, j = SAMPLES - 1; i < SAMPLES; j = i++) {
            if ((y[i] > py) != (y[j] > py) && px < (x[j] - x[i]) * (py - y[i]) / (y[j] - y[i]) + x[i]) in = !in;
        }
        return in;
    }
}
