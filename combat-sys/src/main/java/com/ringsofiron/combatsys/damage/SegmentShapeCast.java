package com.ringsofiron.combatsys.damage;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;

/**
 * Segment-versus-shape tests in a shape's own frame, used by the swept narrowphase
 * (combat-sys/Design.md 2.3 step 2). Works in whatever units the shape is in.
 */
public final class SegmentShapeCast {
    private static final Vector2 v0 = new Vector2();
    private static final Vector2 v1 = new Vector2();

    private SegmentShapeCast() {
    }

    /**
     * Casts {@code a -> b} against the shape. On a hit, returns the fraction along the segment in
     * [0, 1] and writes the outward surface normal; returns -1 on a miss. A segment that starts
     * inside the shape reports fraction 0 with the normal facing back along the segment.
     */
    public static float cast(Shape shape, float ax, float ay, float bx, float by, Vector2 normalOut) {
        if (shape instanceof PolygonShape) return castPolygon((PolygonShape) shape, ax, ay, bx, by, normalOut);
        if (shape instanceof CircleShape) {
            CircleShape c = (CircleShape) shape;
            Vector2 p = c.getPosition();
            return castCircle(p.x, p.y, c.getRadius(), ax, ay, bx, by, normalOut);
        }
        return -1f;
    }

    /** Cyrus-Beck clip against a convex counter-clockwise polygon. */
    public static float castPolygon(PolygonShape poly, float ax, float ay, float bx, float by, Vector2 normalOut) {
        int n = poly.getVertexCount();
        float dx = bx - ax, dy = by - ay;
        float tEnter = 0f, tExit = 1f;
        float enterNx = 0f, enterNy = 0f;
        boolean startsInside = true;
        for (int i = 0; i < n; i++) {
            poly.getVertex(i, v0);
            poly.getVertex((i + 1) % n, v1);
            // Outward normal of a CCW edge.
            float ex = v1.x - v0.x, ey = v1.y - v0.y;
            float nx = ey, ny = -ex;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len == 0f) continue;
            nx /= len;
            ny /= len;
            float num = nx * (v0.x - ax) + ny * (v0.y - ay); // > 0 when a is inside this edge
            float den = nx * dx + ny * dy;
            if (num < 0f) startsInside = false;
            if (den == 0f) {
                if (num < 0f) return -1f; // parallel and outside
                continue;
            }
            float t = num / den;
            if (den < 0f) {
                // Entering through this edge.
                if (t > tEnter) {
                    tEnter = t;
                    enterNx = nx;
                    enterNy = ny;
                }
            } else if (t < tExit) {
                tExit = t;
            }
            if (tEnter > tExit) return -1f;
        }
        if (startsInside) {
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > 0f) normalOut.set(-dx / l, -dy / l);
            else normalOut.set(1f, 0f);
            return 0f;
        }
        normalOut.set(enterNx, enterNy);
        return tEnter;
    }

    public static float castCircle(float cx, float cy, float r, float ax, float ay, float bx, float by, Vector2 normalOut) {
        float dx = bx - ax, dy = by - ay;
        float fx = ax - cx, fy = ay - cy;
        float c = fx * fx + fy * fy - r * r;
        float a = dx * dx + dy * dy;
        if (c <= 0f) {
            float l = (float) Math.sqrt(a);
            if (l > 0f) normalOut.set(-dx / l, -dy / l);
            else normalOut.set(1f, 0f);
            return 0f;
        }
        if (a == 0f) return -1f;
        float b = fx * dx + fy * dy;
        float disc = b * b - a * c;
        if (disc < 0f) return -1f;
        float t = (-b - (float) Math.sqrt(disc)) / a;
        if (t < 0f || t > 1f) return -1f;
        normalOut.set(fx + dx * t, fy + dy * t).nor();
        return t;
    }
}
