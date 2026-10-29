package com.ringsofiron.stationsdocking;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;

/**
 * How far out a captured active collar has to stay so the two hulls clear each other once
 * latched: a craft with something sticking out past its port (an engine, a wing, a turret) would
 * otherwise be drawn into the other hull. The collar locks a whole number of tiles out, so the
 * hulls stay a whole number of tiles apart and their decks line up; this finds the fewest tiles
 * at which no part of one hull overlaps the other in the latched pose.
 */
final class CollarReach {
    /** Overlap of two hulls in the latched pose that still counts as clear, meters: hulls only just touching. */
    static final float TOLERANCE = 0.05f;
    /**
     * How far each hull's drawn outline (and what Box2D collides on) stands out past its fixture,
     * meters: the fixtures are inset by the skin ({@link PolygonSkin}). Two starter corvettes'
     * fixtures cleared by 2 cm, so the collar drew all the way in, yet the fins touched the other
     * hull in the game and held the collar a tile out.
     */
    private static final float SKIN = PolygonSkin.SKIN;
    private static final float M = PhysicsManager.METERS_PER_UNIT;

    private CollarReach() {
    }

    /** The most whole tiles of {@code tile} meters the collar of {@code p} reaches out to. */
    static int maxTiles(DockingPort p, float tile) {
        return Math.max(0, (int) Math.floor(p.tuning().maxExtension / tile + 1e-3f));
    }

    /**
     * The extension, meters, {@code own}'s active port {@code p} should lock at against
     * {@code other}'s port {@code q}: the fewest whole tiles at which the hulls clear each other
     * when latched, or the collar's full whole-tile reach if they never do.
     */
    static float lockExtension(Body own, DockingPort p, Body other, DockingPort q, float tile) {
        Array<float[]> ownHull = hull(own), otherHull = hull(other);
        int max = maxTiles(p, tile);
        float was = p.lockedExtension;
        try {
            for (int k = 0; k <= max; k++) {
                p.lockedExtension = k * tile;
                if (!overlaps(ownHull, p, otherHull, q)) return k * tile;
            }
            return max * tile;
        } finally {
            p.lockedExtension = was;
        }
    }

    /** True if, latched port to port as {@code p} now reaches, any piece of the one hull overlaps the other. */
    static boolean overlaps(Array<float[]> ownHull, DockingPort p, Array<float[]> otherHull, DockingPort q) {
        // own's pose in other's frame when latched: turned by minus the mated angle, faces together
        float angle = -DockingSystem.matedAngle(p, q);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        float ox = q.faceX() - (c * p.faceX() - s * p.faceY());
        float oy = q.faceY() - (s * p.faceX() + c * p.faceY());
        for (float[] a : ownHull) {
            float[] moved = new float[a.length];
            for (int i = 0; i < a.length; i += 2) {
                moved[i] = ox + c * a[i] - s * a[i + 1];
                moved[i + 1] = oy + s * a[i] + c * a[i + 1];
            }
            for (float[] b : otherHull) {
                if (overlap(moved, b) + 2f * SKIN > TOLERANCE) return true;
            }
        }
        return false;
    }

    /** The hull's convex pieces in body-local meters: every polygon fixture of the hull itself, not its collars. */
    static Array<float[]> hull(Body body) {
        Array<float[]> out = new Array<>();
        Vector2 v = new Vector2();
        for (Fixture f : body.getFixtureList()) {
            short category = f.getFilterData().categoryBits;
            if (f.isSensor() || (category & CollisionCategories.CATEGORY_COLLAR) != 0) continue;
            if ((category & (CollisionCategories.CATEGORY_SHIP | CollisionCategories.CATEGORY_STATION)) == 0) continue;
            if (f.getType() != Shape.Type.Polygon) continue;
            PolygonShape poly = (PolygonShape) f.getShape();
            int n = poly.getVertexCount();
            float[] pts = new float[n * 2];
            for (int i = 0; i < n; i++) {
                poly.getVertex(i, v);
                pts[2 * i] = v.x * M;
                pts[2 * i + 1] = v.y * M;
            }
            out.add(pts);
        }
        return out;
    }

    /**
     * How deep two convex polygons ({x0, y0, x1, y1, ...}) overlap, meters: the least overlap of
     * their projections over every edge's normal (separating axes). 0 or less when apart.
     */
    static float overlap(float[] a, float[] b) {
        float least = Float.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            float[] edges = pass == 0 ? a : b;
            int n = edges.length / 2;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float nx = edges[2 * j + 1] - edges[2 * i + 1], ny = edges[2 * i] - edges[2 * j];
                float len = (float) Math.sqrt(nx * nx + ny * ny);
                if (len < 1e-6f) continue;
                nx /= len;
                ny /= len;
                float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE, minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
                for (int k = 0; k < a.length; k += 2) {
                    float d = a[k] * nx + a[k + 1] * ny;
                    minA = Math.min(minA, d);
                    maxA = Math.max(maxA, d);
                }
                for (int k = 0; k < b.length; k += 2) {
                    float d = b[k] * nx + b[k + 1] * ny;
                    minB = Math.min(minB, d);
                    maxB = Math.max(maxB, d);
                }
                least = Math.min(least, Math.min(maxA, maxB) - Math.max(minA, minB));
                if (least <= 0f) return least;
            }
        }
        return least;
    }
}
