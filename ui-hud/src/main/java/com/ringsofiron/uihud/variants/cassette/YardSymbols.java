package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * The relation classes (spec section 9.4) as the Yard Standard draws them on its tubes and flaps. Shape
 * carries the class, so it survives a one-colour tube and colour-blind eyes: hostile a diamond, neutral
 * a notched dart along its heading, friendly a dome, own the own-ship cross with a heading tick, inert
 * a saltire. Red is kept for a contact that threatens. A state changes the shape: a slash when disabled,
 * a pennant when struck, an inert dashed saltire when wrecked. A contract is a tag on the symbol.
 */
public final class YardSymbols {
    private YardSymbols() {}

    public enum Rel {
        HOSTILE, NEUTRAL, FRIENDLY, OWN, INERT
    }

    public enum State {
        ACTIVE, DISABLED, STRUCK, WRECKED
    }

    private static final float[] PTS = new float[2 * 24];

    /**
     * Draws {@code rel} at ({@code x}, {@code y}), {@code s} units across its half, in {@code c}.
     * {@code rot} turns the dart and the cross (degrees, clockwise on screen, 0 nose up).
     */
    public static void rel(YardPaint p, Rel rel, float x, float y, float s, Color c, float rot, boolean fill, float w, State state, boolean contract) {
        Rel r = state == State.WRECKED ? Rel.INERT : rel;
        boolean solid = fill && r != Rel.OWN && r != Rel.INERT;
        switch (r) {
            case HOSTILE: {
                float[] d = {x, y - s * 1.25f, x + s * 1.1f, y, x, y + s * 1.25f, x - s * 1.1f, y};
                if (solid) p.fillConvex(d, c, 1f);
                p.polyline(d, true, w, c, 1f);
                break;
            }
            case NEUTRAL: {
                float[] d = {0f, -s * 1.35f, s * 0.72f, s * 0.95f, 0f, s * 0.45f, -s * 0.72f, s * 0.95f};
                turn(d, x, y, rot);
                if (solid) {
                    // The dart is not convex: fill its two halves
                    p.fillConvex(new float[]{d[0], d[1], d[2], d[3], d[4], d[5]}, c, 1f);
                    p.fillConvex(new float[]{d[0], d[1], d[4], d[5], d[6], d[7]}, c, 1f);
                }
                p.polyline(d, true, w, c, 1f);
                break;
            }
            case FRIENDLY: {
                int n = 10;
                float[] d = new float[2 * (n + 1)];
                for (int i = 0; i <= n; i++) {
                    float a = MathUtils.PI * i / n;
                    d[2 * i] = x - s * 1.1f * MathUtils.cos(a);
                    d[2 * i + 1] = y + s * 0.6f - s * 1.1f * MathUtils.sin(a);
                }
                if (solid) p.fillConvex(d, c, 1f);
                p.polyline(d, true, w, c, 1f);
                break;
            }
            case OWN: {
                float[] a = {-s, 0f, s, 0f}, b = {0f, -s * 1.9f, 0f, s};
                turn(a, x, y, rot);
                turn(b, x, y, rot);
                p.line(a[0], a[1], a[2], a[3], w, c);
                p.line(b[0], b[1], b[2], b[3], w, c);
                break;
            }
            case INERT: {
                float k = s * 0.85f;
                if (state == State.WRECKED) {
                    p.dashed(x - k, y - k, x + k, y + k, w, 2f, 2f, c, 1f);
                    p.dashed(x + k, y - k, x - k, y + k, w, 2f, 2f, c, 1f);
                } else {
                    p.line(x - k, y - k, x + k, y + k, w, c);
                    p.line(x + k, y - k, x - k, y + k, w, c);
                }
                break;
            }
        }
        if (state == State.DISABLED) p.line(x - s * 1.5f, y + s * 1.5f, x + s * 1.5f, y - s * 1.5f, w + 0.4f, c);
        if (state == State.STRUCK) {
            p.line(x + s * 1.2f, y - s * 0.2f, x + s * 1.2f, y - s * 2.6f, w, c);
            p.polyline(new float[]{x + s * 1.2f, y - s * 2.6f, x + s * 2.8f, y - s * 2.05f, x + s * 1.2f, y - s * 1.5f}, false, w, c, 1f);
        }
        if (contract) {
            float tx = x + s * 1.2f, ty = y + s;
            p.polyline(new float[]{tx, ty, tx + s * 1.3f, ty, tx + s * 1.3f, ty + s, tx, ty + s, tx - s * 0.45f, ty + s * 0.5f}, true, w * 0.8f, c, 1f);
        }
    }

    /** A station: a square. */
    public static void station(YardPaint p, float x, float y, float s, Color c, float w) {
        p.polyline(new float[]{x - s, y - s, x + s, y - s, x + s, y + s, x - s, y + s}, true, w, c, 1f);
    }

    /** The lock mark: four corners of a square. */
    public static void bracket(YardPaint p, float x, float y, float s, Color c, float w) {
        float k = s * 0.45f;
        p.polyline(new float[]{x - s, y - s + k, x - s, y - s, x - s + k, y - s}, false, w, c, 1f);
        p.polyline(new float[]{x + s - k, y - s, x + s, y - s, x + s, y - s + k}, false, w, c, 1f);
        p.polyline(new float[]{x + s, y + s - k, x + s, y + s, x + s - k, y + s}, false, w, c, 1f);
        p.polyline(new float[]{x - s + k, y + s, x - s, y + s, x - s, y + s - k}, false, w, c, 1f);
    }

    /** The navigation target's mark: square brackets either side. */
    public static void navMark(YardPaint p, float x, float y, Color c, float w) {
        p.polyline(new float[]{x - 9f, y - 8f, x - 11f, y - 8f, x - 11f, y + 8f, x - 9f, y + 8f}, false, w, c, 1f);
        p.polyline(new float[]{x + 9f, y - 8f, x + 11f, y - 8f, x + 11f, y + 8f, x + 9f, y + 8f}, false, w, c, 1f);
    }

    /** A missile: a dot with a leader and a chevron at its tip, pointing along {@code dir} (degrees, clockwise from +x). */
    public static void missile(YardPaint p, float x, float y, float dir, Color c, float k) {
        float cs = MathUtils.cosDeg(dir), sn = MathUtils.sinDeg(dir);
        p.circle(x, y, 2.2f * k, c);
        p.line(x, y, x + 10f * k * cs, y + 10f * k * sn, 1.6f * k, c);
        float tx = x + 10.5f * k * cs, ty = y + 10.5f * k * sn;
        float bx = x + 6f * k * cs, by = y + 6f * k * sn;
        p.polyline(new float[]{bx - 3.5f * k * sn, by + 3.5f * k * cs, tx, ty, bx + 3.5f * k * sn, by - 3.5f * k * cs}, false, 1.6f * k, c, 1f);
    }

    /** Turns {@code pts} (relative to the origin) by {@code deg} clockwise on screen and moves them to ({@code x}, {@code y}). */
    private static void turn(float[] pts, float x, float y, float deg) {
        float cs = MathUtils.cosDeg(deg), sn = MathUtils.sinDeg(deg);
        for (int i = 0; i < pts.length; i += 2) {
            float a = pts[i], b = pts[i + 1];
            pts[i] = x + a * cs - b * sn;
            pts[i + 1] = y + a * sn + b * cs;
        }
    }
}
