package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Lines, rings and dots drawn with a 1x1 white region, for the HUD widgets that draw shapes in the stage's batch. */
final class HudDraw {
    /** Width of the fade on a ring's or disc's edges, in UI pixels. */
    private static final float FEATHER = 1f;
    /** One quad's vertices for {@link Batch#draw(com.badlogic.gdx.graphics.Texture, float[], int, int)}: x, y, color, u, v. */
    private static final float[] QUAD = new float[20];

    private HudDraw() {
    }

    static void color(Batch batch, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
    }

    static void line(Batch batch, TextureRegion white, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f,
                MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    /**
     * A ring of {@code width} around {@code r}, as one band of quads with a pixel of fade on each edge
     * so it reads as a smooth circle instead of a ring of rotated rectangles.
     */
    static void ring(Batch batch, TextureRegion white, float cx, float cy, float r, float width) {
        float half = Math.max(width / 2f - FEATHER / 2f, 0f);
        float in = Math.max(r - half, 0f), out = r + half;
        Color c = batch.getColor();
        float solid = c.toFloatBits(), clear = Color.toFloatBits(c.r, c.g, c.b, 0f);
        int n = segments(out + FEATHER);
        float step = MathUtils.PI2 / n;
        float cos0 = 1f, sin0 = 0f;
        for (int i = 1; i <= n; i++) {
            float cos1 = MathUtils.cos(step * i), sin1 = MathUtils.sin(step * i);
            band(batch, white, cx, cy, cos0, sin0, cos1, sin1, Math.max(in - FEATHER, 0f), in, clear, solid);
            if (out > in) band(batch, white, cx, cy, cos0, sin0, cos1, sin1, in, out, solid, solid);
            band(batch, white, cx, cy, cos0, sin0, cos1, sin1, out, out + FEATHER, solid, clear);
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    /** A round dot {@code size} across, with the same soft edge as {@link #ring}. */
    static void disc(Batch batch, TextureRegion white, float cx, float cy, float size) {
        float r = Math.max(size / 2f - FEATHER / 2f, 0f);
        Color c = batch.getColor();
        float solid = c.toFloatBits(), clear = Color.toFloatBits(c.r, c.g, c.b, 0f);
        int n = segments(r + FEATHER);
        float step = MathUtils.PI2 / n;
        float cos0 = 1f, sin0 = 0f;
        for (int i = 1; i <= n; i++) {
            float cos1 = MathUtils.cos(step * i), sin1 = MathUtils.sin(step * i);
            band(batch, white, cx, cy, cos0, sin0, cos1, sin1, 0f, r, solid, solid);
            band(batch, white, cx, cy, cos0, sin0, cos1, sin1, r, r + FEATHER, solid, clear);
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    /** Segments for a circle of radius {@code r}: about three pixels each, so the edge never shows corners. */
    static int segments(float r) {
        return MathUtils.clamp(MathUtils.ceil(MathUtils.PI2 * r / 3f), 16, 256);
    }

    /** One quad of a ring: between radii {@code r0} and {@code r1}, from one angle's cos/sin to the next's. */
    private static void band(Batch batch, TextureRegion white, float cx, float cy, float cos0, float sin0,
                             float cos1, float sin1, float r0, float r1, float color0, float color1) {
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        float[] q = QUAD;
        vertex(q, 0, cx + cos0 * r0, cy + sin0 * r0, color0, u, v);
        vertex(q, 1, cx + cos0 * r1, cy + sin0 * r1, color1, u, v);
        vertex(q, 2, cx + cos1 * r1, cy + sin1 * r1, color1, u, v);
        vertex(q, 3, cx + cos1 * r0, cy + sin1 * r0, color0, u, v);
        batch.draw(white.getTexture(), q, 0, q.length);
    }

    private static void vertex(float[] q, int i, float x, float y, float color, float u, float v) {
        int o = i * 5;
        q[o] = x;
        q[o + 1] = y;
        q[o + 2] = color;
        q[o + 3] = u;
        q[o + 4] = v;
    }

    static void dot(Batch batch, TextureRegion white, float x, float y, float size) {
        batch.draw(white, x - size / 2f, y - size / 2f, size, size);
    }
}
