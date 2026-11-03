package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Filled shapes for FILAMENT, drawn as quads into a {@link Batch} from one opaque texel, with a
 * one-pixel fade on every edge so small segments and dots stay smooth without multisampling.
 *
 * <p>Coordinates are the mockups': UI units from the top-left corner, y down. {@link #begin} takes
 * the frame's height, and every vertex is turned over into the stage's y-up frame as it is emitted.
 * Angles are in degrees, 0 straight up and growing clockwise, as the mockups' rings and icons have them.
 */
final class Shapes {
    /** Most corners a polygon passed to {@link #convex} may have. */
    static final int MAX_POINTS = 96;

    private final float[] quad = new float[20];
    private final float[] inner = new float[MAX_POINTS * 2];
    private final float[] outer = new float[MAX_POINTS * 2];
    private final float[] points = new float[MAX_POINTS * 2];
    private final float[] colors = new float[MAX_POINTS];

    private Batch batch;
    private Texture texture;
    private float u, v;
    /** One screen pixel, in UI units: the width of every edge's fade. */
    private float feather = 1f;
    private float frameHeight;
    /** Where the drawing's origin sits on the stage, y down: added to every point as it is emitted. */
    private float ox, oy;

    void begin(Batch batch, Texture texture, float u, float v, float feather, float frameHeight) {
        this.batch = batch;
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.feather = feather;
        this.frameHeight = frameHeight;
        ox = oy = 0f;
    }

    /** Moves the origin of what is drawn next to {@code x, y} on the stage (y down). */
    void origin(float x, float y) {
        ox = x;
        oy = y;
    }

    float feather() {
        return feather;
    }

    float frameHeight() {
        return frameHeight;
    }

    Batch batch() {
        return batch;
    }

    // ------------------------------------------------------------------ raw quads

    /** A quad with a colour at each corner, corners in order round it. */
    void quad(float x0, float y0, float c0, float x1, float y1, float c1, float x2, float y2, float c2,
              float x3, float y3, float c3) {
        float[] q = quad;
        float h = frameHeight - oy, dx = ox;
        q[0] = x0 + dx; q[1] = h - y0; q[2] = c0; q[3] = u; q[4] = v;
        q[5] = x1 + dx; q[6] = h - y1; q[7] = c1; q[8] = u; q[9] = v;
        q[10] = x2 + dx; q[11] = h - y2; q[12] = c2; q[13] = u; q[14] = v;
        q[15] = x3 + dx; q[16] = h - y3; q[17] = c3; q[18] = u; q[19] = v;
        batch.draw(texture, q, 0, 20);
    }

    void triangle(float x0, float y0, float c0, float x1, float y1, float c1, float x2, float y2, float c2) {
        quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
    }

    /** A textured quad: the axis-aligned box {@code x, y, w, h} showing the texture between {@code u0, v0} and {@code u1, v1}. */
    void sprite(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float color) {
        float[] q = quad;
        float top = frameHeight - oy - y, bottom = top - h;
        x += ox;
        q[0] = x; q[1] = top; q[2] = color; q[3] = u0; q[4] = v0;
        q[5] = x + w; q[6] = top; q[7] = color; q[8] = u1; q[9] = v0;
        q[10] = x + w; q[11] = bottom; q[12] = color; q[13] = u1; q[14] = v1;
        q[15] = x; q[16] = bottom; q[17] = color; q[18] = u0; q[19] = v1;
        batch.draw(tex, q, 0, 20);
    }

    // ------------------------------------------------------------------ convex polygons

    /**
     * Fills the convex polygon of {@code n} corners in {@code xy} (x, y pairs, either winding) with a
     * one-pixel fade straddling its edge, so its coverage matches the outline. A polygon thinner than
     * a pixel keeps its weight by being drawn fainter rather than vanishing.
     */
    void convex(float[] xy, int n, float color) {
        if (n < 3) return;
        float f = feather;
        float area = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += xy[2 * i] * xy[2 * j + 1] - xy[2 * j] * xy[2 * i + 1];
        }
        if (Math.abs(area) < 1e-6f) return;
        float sign = area > 0f ? 1f : -1f;
        // How thin it is: the least, over its edges, of how far its farthest corner stands off that edge
        float minWidth = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ex = xy[2 * j] - xy[2 * i], ey = xy[2 * j + 1] - xy[2 * i + 1];
            float len = (float) Math.sqrt(ex * ex + ey * ey);
            if (len < 1e-6f) continue;
            float nx = ey / len, ny = -ex / len;
            float far = 0f;
            for (int k = 0; k < n; k++) {
                float d = Math.abs((xy[2 * k] - xy[2 * i]) * nx + (xy[2 * k + 1] - xy[2 * i + 1]) * ny);
                if (d > far) far = d;
            }
            if (far < minWidth) minWidth = far;
        }
        float inset = Math.min(f / 2f, minWidth * 0.45f);
        float alphaScale = 1f;
        if (inset < f / 2f) alphaScale = MathUtils.clamp(minWidth / (minWidth - 2f * inset + inset + f / 2f), 0f, 1f);
        float solid = color, clear;
        if (alphaScale < 0.999f) {
            Color c = TMP;
            Color.abgr8888ToColor(c, color);
            solid = Color.toFloatBits(c.r, c.g, c.b, c.a * alphaScale);
            clear = Color.toFloatBits(c.r, c.g, c.b, 0f);
        } else {
            clear = transparent(color);
        }
        // Each corner moves along its mitre: in by the inset for the solid core, out by half a pixel for the fade
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n, nx2 = (i + 1) % n;
            float n0x, n0y, n1x, n1y;
            {
                float ex = xy[2 * i] - xy[2 * p], ey = xy[2 * i + 1] - xy[2 * p + 1];
                float len = Math.max((float) Math.sqrt(ex * ex + ey * ey), 1e-6f);
                n0x = sign * ey / len;
                n0y = -sign * ex / len;
            }
            {
                float ex = xy[2 * nx2] - xy[2 * i], ey = xy[2 * nx2 + 1] - xy[2 * i + 1];
                float len = Math.max((float) Math.sqrt(ex * ex + ey * ey), 1e-6f);
                n1x = sign * ey / len;
                n1y = -sign * ex / len;
            }
            float d = 1f + n0x * n1x + n0y * n1y;
            float mx, my;
            if (d < 0.25f) {
                // Very sharp: hold the mitre to twice the offset rather than let it run away
                float mx0 = n0x + n1x, my0 = n0y + n1y;
                float ml = Math.max((float) Math.sqrt(mx0 * mx0 + my0 * my0), 1e-6f);
                mx = mx0 / ml * 2f;
                my = my0 / ml * 2f;
            } else {
                mx = (n0x + n1x) / d;
                my = (n0y + n1y) / d;
            }
            float x = xy[2 * i], y = xy[2 * i + 1];
            inner[2 * i] = x - mx * inset;
            inner[2 * i + 1] = y - my * inset;
            outer[2 * i] = x + mx * f / 2f;
            outer[2 * i + 1] = y + my * f / 2f;
        }
        // The core, as a fan of quads (the last one a triangle when the count is odd)
        for (int k = 1; k + 1 < n; k += 2) {
            int a = k, b = k + 1, c = Math.min(k + 2, n - 1);
            quad(inner[0], inner[1], solid, inner[2 * a], inner[2 * a + 1], solid, inner[2 * b], inner[2 * b + 1], solid,
                    inner[2 * c], inner[2 * c + 1], solid);
        }
        // The fade, a quad along each edge
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            quad(inner[2 * i], inner[2 * i + 1], solid, outer[2 * i], outer[2 * i + 1], clear,
                    outer[2 * j], outer[2 * j + 1], clear, inner[2 * j], inner[2 * j + 1], solid);
        }
    }

    /** A convex polygon with a colour per corner and no fade: gradients and glare. */
    void convexShaded(float[] xy, float[] cornerColors, int n) {
        for (int k = 1; k + 1 < n; k += 2) {
            int a = k, b = k + 1, c = Math.min(k + 2, n - 1);
            quad(xy[0], xy[1], cornerColors[0], xy[2 * a], xy[2 * a + 1], cornerColors[a], xy[2 * b], xy[2 * b + 1], cornerColors[b],
                    xy[2 * c], xy[2 * c + 1], cornerColors[c]);
        }
    }

    /** An axis-aligned box, with faded edges. */
    void rect(float x, float y, float w, float h, float color) {
        if (w <= 0f || h <= 0f) return;
        float[] p = points;
        p[0] = x; p[1] = y;
        p[2] = x + w; p[3] = y;
        p[4] = x + w; p[5] = y + h;
        p[6] = x; p[7] = y + h;
        convex(p, 4, color);
    }

    /** An axis-aligned box with no fade, for fills whose edges something else covers. */
    void rectHard(float x, float y, float w, float h, float color) {
        if (w <= 0f || h <= 0f) return;
        quad(x, y, color, x + w, y, color, x + w, y + h, color, x, y + h, color);
    }

    /** A box with round corners of radius {@code r}, faded at the edge. */
    void roundRect(float x, float y, float w, float h, float r, float color) {
        int n = roundRectPoints(x, y, w, h, r, points);
        convex(points, n, color);
    }

    /**
     * The outline of a round-cornered box, clockwise from its top-left corner's start, into {@code out};
     * returns the corner count. Each corner is a quarter circle of a few points, more for larger radii.
     */
    int roundRectPoints(float x, float y, float w, float h, float r, float[] out) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        if (r < 0.05f) {
            out[0] = x; out[1] = y;
            out[2] = x + w; out[3] = y;
            out[4] = x + w; out[5] = y + h;
            out[6] = x; out[7] = y + h;
            return 4;
        }
        int steps = MathUtils.clamp(MathUtils.ceil(r / feather / 1.5f), 2, 10);
        int n = 0;
        // top-left, top-right, bottom-right, bottom-left corner centres; each arc a quarter turn clockwise (y down)
        float[] cx = {x + r, x + w - r, x + w - r, x + r};
        float[] cy = {y + r, y + r, y + h - r, y + h - r};
        for (int corner = 0; corner < 4; corner++) {
            float start = 180f + 90f * corner;
            for (int s = 0; s <= steps; s++) {
                float a = (start + 90f * s / steps) * MathUtils.degreesToRadians;
                out[2 * n] = cx[corner] + MathUtils.cos(a) * r;
                out[2 * n + 1] = cy[corner] + MathUtils.sin(a) * r;
                n++;
            }
        }
        return n;
    }

    /**
     * A round-cornered box filled with a vertical gradient: {@code stops} pairs of a share of the height
     * (0 at the top) and a packed colour. Drawn in horizontal bands, so the gradient is exact, with no
     * fade at the edge: the bezels and glass this fills always have a rim drawn over it.
     */
    void roundRectGradient(float x, float y, float w, float h, float r, float[] stops) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int arc = r < 0.05f ? 0 : MathUtils.clamp(MathUtils.ceil(r / feather / 2f), 2, 8);
        // y levels: the top arc's slices, the stops between, the bottom arc's slices
        float prevY = y;
        float prevL = edgeInset(0f, r), prevC = gradientAt(stops, 0f);
        int levels = 0;
        float[] ys = points;
        for (int s = 1; s <= arc; s++) ys[levels++] = y + r * s / arc;
        for (int i = 0; i < stops.length; i += 2) {
            float sy = y + stops[i] * h;
            if (sy > y + r && sy < y + h - r) ys[levels++] = sy;
        }
        for (int s = 0; s <= arc; s++) ys[levels++] = y + h - r + r * s / arc;
        if (arc == 0) ys[levels++] = y + h;
        sortAscending(ys, levels);
        for (int i = 0; i < levels; i++) {
            float yy = Math.min(ys[i], y + h);
            if (yy <= prevY + 1e-5f) continue;
            float from = yy - y;
            float inset = from < r ? edgeInset(r - from, r) : from > h - r ? edgeInset(from - (h - r), r) : 0f;
            float c = gradientAt(stops, from / h);
            quad(x + prevL, prevY, prevC, x + w - prevL, prevY, prevC, x + w - inset, yy, c, x + inset, yy, c);
            prevY = yy;
            prevL = inset;
            prevC = c;
        }
    }

    /** How far in a round corner's edge stands {@code dy} from the corner circle's centre line. */
    private static float edgeInset(float dy, float r) {
        if (r <= 0f) return 0f;
        dy = Math.min(dy, r);
        return r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
    }

    private static float gradientAt(float[] stops, float t) {
        if (t <= stops[0]) return stops[1];
        for (int i = 2; i < stops.length; i += 2) {
            if (t <= stops[i]) {
                float t0 = stops[i - 2], t1 = stops[i];
                float k = t1 > t0 ? (t - t0) / (t1 - t0) : 0f;
                return lerpColor(stops[i - 1], stops[i + 1], k);
            }
        }
        return stops[stops.length - 1];
    }

    private static void sortAscending(float[] a, int n) {
        for (int i = 1; i < n; i++) {
            float x = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > x) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = x;
        }
    }

    /** The outline of a round-cornered box as a band {@code width} wide centred on it, with no fade: rims. */
    void roundRectStroke(float x, float y, float w, float h, float r, float width, float color) {
        int n = roundRectPoints(x, y, w, h, r, points);
        float hw = width / 2f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = points[2 * i], ay = points[2 * i + 1], bx = points[2 * j], by = points[2 * j + 1];
            float ex = bx - ax, ey = by - ay, len = (float) Math.sqrt(ex * ex + ey * ey);
            if (len < 1e-6f) continue;
            float nx = -ey / len * hw, ny = ex / len * hw;
            // extend each piece by half the width so corners close
            float tx = ex / len * hw * 0.5f, ty = ey / len * hw * 0.5f;
            quad(ax - nx - tx, ay - ny - ty, color, bx - nx + tx, by - ny + ty, color,
                    bx + nx + tx, by + ny + ty, color, ax + nx - tx, ay + ny - ty, color);
        }
    }

    // ------------------------------------------------------------------ lines, discs and rings

    /** A straight line {@code width} wide with butt ends, faded along its sides and ends. */
    void line(float x0, float y0, float x1, float y1, float width, float color) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-5f) return;
        float nx = -dy / len * width / 2f, ny = dx / len * width / 2f;
        float[] p = points;
        p[0] = x0 + nx; p[1] = y0 + ny;
        p[2] = x1 + nx; p[3] = y1 + ny;
        p[4] = x1 - nx; p[5] = y1 - ny;
        p[6] = x0 - nx; p[7] = y0 - ny;
        convex(p, 4, color);
    }

    /** A filled circle of radius {@code r}. */
    void disc(float cx, float cy, float r, float color) {
        int n = MathUtils.clamp(MathUtils.ceil(MathUtils.PI2 * r / feather / 2.5f), 8, MAX_POINTS);
        float[] p = points;
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            p[2 * i] = cx + MathUtils.cos(a) * r;
            p[2 * i + 1] = cy + MathUtils.sin(a) * r;
        }
        convex(p, n, color);
    }

    /**
     * Part of a ring between radii {@code r0} and {@code r1}, from {@code a0} to {@code a1} degrees
     * (0 up, clockwise), faded on all four sides.
     */
    void annulus(float cx, float cy, float r0, float r1, float a0, float a1, float color) {
        if (r1 <= r0 || a1 <= a0) return;
        float f = feather, half = f / 2f;
        float clear = transparent(color);
        float span = a1 - a0;
        int steps = MathUtils.clamp(MathUtils.ceil(span * MathUtils.degreesToRadians * r1 / f / 2.5f), 1, 180);
        float in0 = r0 + half, in1 = r1 - half;
        boolean thin = in1 <= in0;
        float mid = (r0 + r1) / 2f;
        if (thin) in0 = in1 = mid;
        float solid = thin ? scaleAlpha(color, (r1 - r0) / f) : color;
        // the radial ends fade over half a pixel either side, as an angle at the middle radius
        float endFade = half / Math.max(mid, 0.5f) * MathUtils.radiansToDegrees;
        float s0 = a0 + endFade, s1 = a1 - endFade;
        if (s1 < s0) s0 = s1 = (a0 + a1) / 2f;
        for (int i = 0; i < steps; i++) {
            float b0 = s0 + (s1 - s0) * i / steps, b1 = s0 + (s1 - s0) * (i + 1) / steps;
            float c0 = cosUp(b0), n0 = sinUp(b0), c1 = cosUp(b1), n1 = sinUp(b1);
            if (!thin) band(cx, cy, c0, n0, c1, n1, in0, in1, solid, solid);
            band(cx, cy, c0, n0, c1, n1, r0 - half, in0, clear, solid);
            band(cx, cy, c0, n0, c1, n1, in1, r1 + half, solid, clear);
        }
        // the two ends
        endFade(cx, cy, s0, s0 - 2f * endFade, r0 - half, in0, in1, r1 + half, solid, clear);
        endFade(cx, cy, s1, s1 + 2f * endFade, r0 - half, in0, in1, r1 + half, solid, clear);
    }

    private void endFade(float cx, float cy, float at, float out, float rOut0, float rIn0, float rIn1, float rOut1, float solid, float clear) {
        float ca = cosUp(at), sa = sinUp(at), co = cosUp(out), so = sinUp(out);
        quad(cx + ca * rIn0, cy + sa * rIn0, solid, cx + ca * rIn1, cy + sa * rIn1, solid,
                cx + co * rOut1, cy + so * rOut1, clear, cx + co * rOut0, cy + so * rOut0, clear);
    }

    /** A stroked arc: a ring {@code width} wide centred on radius {@code r}. */
    void arc(float cx, float cy, float r, float a0, float a1, float width, float color) {
        annulus(cx, cy, Math.max(0f, r - width / 2f), r + width / 2f, a0, a1, color);
    }

    private void band(float cx, float cy, float c0, float s0, float c1, float s1, float r0, float r1, float col0, float col1) {
        quad(cx + c0 * r0, cy + s0 * r0, col0, cx + c0 * r1, cy + s0 * r1, col1,
                cx + c1 * r1, cy + s1 * r1, col1, cx + c1 * r0, cy + s1 * r0, col0);
    }

    /** x of the unit vector at {@code deg} degrees, 0 up and clockwise, in the y-down frame. */
    static float cosUp(float deg) {
        return MathUtils.sinDeg(deg);
    }

    /** y of the unit vector at {@code deg} degrees, 0 up and clockwise, in the y-down frame. */
    static float sinUp(float deg) {
        return -MathUtils.cosDeg(deg);
    }

    // ------------------------------------------------------------------ colours

    private static final Color TMP = new Color(), TMP2 = new Color();

    static float transparent(float color) {
        Color.abgr8888ToColor(TMP, color);
        return Color.toFloatBits(TMP.r, TMP.g, TMP.b, 0f);
    }

    static float scaleAlpha(float color, float k) {
        Color.abgr8888ToColor(TMP, color);
        return Color.toFloatBits(TMP.r, TMP.g, TMP.b, MathUtils.clamp(TMP.a * k, 0f, 1f));
    }

    static float lerpColor(float a, float b, float t) {
        Color.abgr8888ToColor(TMP, a);
        Color.abgr8888ToColor(TMP2, b);
        return Color.toFloatBits(TMP.r + (TMP2.r - TMP.r) * t, TMP.g + (TMP2.g - TMP.g) * t,
                TMP.b + (TMP2.b - TMP.b) * t, TMP.a + (TMP2.a - TMP.a) * t);
    }
}
