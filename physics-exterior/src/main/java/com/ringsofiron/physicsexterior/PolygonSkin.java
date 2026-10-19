package com.ringsofiron.physicsexterior;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * Box2D keeps every polygon wrapped in a thin skin and lets two touching polygons settle with
 * their skins just overlapping, so they rest about {@code 2 * SKIN} apart (0.15 m here). Polygons are made
 * that much smaller ({@link #inset}, {@link #insetBox}) so the surface they collide on is the
 * outline they were given, and drawn grown back out by it ({@link #outset}), so what is drawn
 * is what touches.
 */
public final class PolygonSkin {
    /**
     * How far a polygon collides outside its own vertices, Box2D units: the polygon radius
     * (0.01) less a quarter of the solver's slop (0.005), since two pushed together settle
     * anywhere from just touching to a slop's overlap, depending how hard they are pushed.
     */
    public static final float SKIN_UNITS = 0.00875f;
    /** {@link #SKIN_UNITS} in meters. */
    public static final float SKIN = SKIN_UNITS * PhysicsManager.METERS_PER_UNIT;

    private PolygonSkin() {
    }

    /**
     * A box set on {@code shape} that collides on the half-extents given (Box2D units); returns its true area.
     * Set through Box2D's hull, as a saved polygon is when it is rebuilt, so a save round trip gives
     * back its vertices in the same order.
     */
    public static float insetBox(PolygonShape shape, float hx, float hy, Vector2 center, float angle) {
        float ix = shrink(hx), iy = shrink(hy);
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[] v = new float[8];
        float[][] corners = {{-ix, -iy}, {ix, -iy}, {ix, iy}, {-ix, iy}};
        for (int i = 0; i < 4; i++) {
            v[i * 2] = center.x + c * corners[i][0] - s * corners[i][1];
            v[i * 2 + 1] = center.y + s * corners[i][0] + c * corners[i][1];
        }
        shape.set(v);
        return 4f * ix * iy;
    }

    /** Convex polygon {@code vertices} (x, y pairs, either winding) moved in by the skin, in the same units as {@code skin}. */
    public static float[] inset(float[] vertices, float skin) {
        return offset(vertices, -skin);
    }

    /** Convex polygon {@code vertices} grown out by the skin, for drawing what the polygon collides on. */
    public static float[] outset(float[] vertices, float skin) {
        return offset(vertices, skin);
    }

    /**
     * What {@code poly} on {@code body} collides on, for drawing: its vertices grown out by the
     * skin, in world meters, into {@code out} as x, y pairs. Returns the vertex count.
     */
    public static int outline(Body body, PolygonShape poly, float[] out) {
        int n = poly.getVertexCount();
        float[] local = new float[n * 2];
        for (int i = 0; i < n; i++) {
            poly.getVertex(i, tmp);
            local[i * 2] = tmp.x;
            local[i * 2 + 1] = tmp.y;
        }
        float[] grown = outset(local, SKIN_UNITS);
        for (int i = 0; i < n; i++) {
            Vector2 w = body.getWorldPoint(tmp.set(grown[i * 2], grown[i * 2 + 1]));
            out[i * 2] = w.x * PhysicsManager.METERS_PER_UNIT;
            out[i * 2 + 1] = w.y * PhysicsManager.METERS_PER_UNIT;
        }
        return n;
    }

    private static final Vector2 tmp = new Vector2();

    private static float shrink(float half) {
        // Never thinner than half its size: a sliver would let things through
        return Math.max(half * 0.5f, half - SKIN_UNITS);
    }

    /** Moves every edge of a convex polygon {@code by} along its outward normal. */
    static float[] offset(float[] v, float by) {
        int n = v.length / 2;
        float[] out = new float[v.length];
        if (n < 3) {
            System.arraycopy(v, 0, out, 0, v.length);
            return out;
        }
        float area = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += v[i * 2] * v[j * 2 + 1] - v[j * 2] * v[i * 2 + 1];
        }
        // Outward normal is (dy, -dx) for counter-clockwise, the other way round for clockwise
        float sign = area >= 0f ? 1f : -1f;
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n, next = (i + 1) % n;
            float n1x = edgeNormalX(v, prev, i, sign), n1y = edgeNormalY(v, prev, i, sign);
            float n2x = edgeNormalX(v, i, next, sign), n2y = edgeNormalY(v, i, next, sign);
            // The corner moves to where the two moved edges meet; a sharp corner is capped
            float k = by / Math.max(0.05f, 1f + n1x * n2x + n1y * n2y);
            out[i * 2] = v[i * 2] + (n1x + n2x) * k;
            out[i * 2 + 1] = v[i * 2 + 1] + (n1y + n2y) * k;
        }
        return out;
    }

    private static float edgeNormalX(float[] v, int a, int b, float sign) {
        float dx = v[b * 2] - v[a * 2], dy = v[b * 2 + 1] - v[a * 2 + 1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        return len == 0f ? 0f : sign * dy / len;
    }

    private static float edgeNormalY(float[] v, int a, int b, float sign) {
        float dx = v[b * 2] - v[a * 2], dy = v[b * 2 + 1] - v[a * 2 + 1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        return len == 0f ? 0f : -sign * dx / len;
    }
}
