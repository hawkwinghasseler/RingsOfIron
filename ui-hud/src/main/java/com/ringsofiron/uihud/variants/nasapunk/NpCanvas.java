package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.FloatArray;

/**
 * Draws the Nasa-punk instruments into a Scene2D batch in the mockups' terms: local coordinates in
 * UI units with y growing down from an origin, text placed on its baseline with letter spacing
 * ("track") and an anchor, and strokes centred on their path, as SVG draws them. So a layout reads
 * the same here as in {@code documentation/ui-overhaul/nasapunk/mockups}, and its numbers can be
 * checked against those drawings.
 *
 * <p>Shapes are quads of a 1x1 white region; circles get a pixel of fade on each edge so they read
 * smooth. One canvas serves every actor of a variant, each setting its origin before it draws.
 */
public final class NpCanvas {
    public static final int START = 0, MIDDLE = 1, END = 2;
    /** A thousands separator: B612 has no thin space, so the canvas leaves this much of the size. */
    public static final char THIN = '\u2009';
    private static final float THIN_EM = 0.2f;
    private static final float FEATHER = 1f;

    private final TextureRegion white;
    private final NpFonts fonts;
    private final GlyphLayout layout = new GlyphLayout();
    private final float[] quad = new float[20];
    private final Rectangle clipArea = new Rectangle(), scissor = new Rectangle();
    private final Color tmp = new Color();
    private Batch batch;
    private float ox, oy, alpha = 1f;

    public NpCanvas(TextureRegion white, NpFonts fonts) {
        this.white = white;
        this.fonts = fonts;
    }

    public NpFonts fonts() {
        return fonts;
    }

    /** Starts drawing into {@code batch}, with local (0, 0) at stage ({@code x}, {@code top}). */
    public NpCanvas begin(Batch batch, float x, float top, float parentAlpha) {
        this.batch = batch;
        this.ox = x;
        this.oy = top;
        this.alpha = parentAlpha;
        return this;
    }

    /** Moves the origin: local (0, 0) at stage ({@code x}, {@code top}). */
    public void origin(float x, float top) {
        ox = x;
        oy = top;
    }

    public float originX() {
        return ox;
    }

    public float originTop() {
        return oy;
    }

    /** Ends drawing: the batch's colour back to white. */
    public void end() {
        if (batch != null) batch.setColor(Color.WHITE);
        batch = null;
    }

    private void paint(Color c, float a) {
        batch.setColor(c.r, c.g, c.b, c.a * a * alpha);
    }

    // ---- shapes ---------------------------------------------------------------------------------

    public void fill(float x, float y, float w, float h, Color c) {
        fill(x, y, w, h, c, 1f);
    }

    public void fill(float x, float y, float w, float h, Color c, float a) {
        if (w <= 0f || h <= 0f) return;
        paint(c, a);
        batch.draw(white, ox + x, oy - y - h, w, h);
    }

    /** A rectangle shaded from {@code top} at its top edge to {@code bottom} at its bottom edge. */
    public void fillGradient(float x, float y, float w, float h, Color top, Color bottom) {
        if (w <= 0f || h <= 0f) return;
        float ct = tmp.set(top.r, top.g, top.b, top.a * alpha).toFloatBits();
        float cb = tmp.set(bottom.r, bottom.g, bottom.b, bottom.a * alpha).toFloatBits();
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        float x0 = ox + x, x1 = ox + x + w, y0 = oy - y, y1 = oy - y - h;
        vertex(0, x0, y1, cb, u, v);
        vertex(1, x0, y0, ct, u, v);
        vertex(2, x1, y0, ct, u, v);
        vertex(3, x1, y1, cb, u, v);
        batch.draw(white.getTexture(), quad, 0, 20);
    }

    /** A rectangle with its corners rounded to {@code r}. */
    public void fillRound(float x, float y, float w, float h, float r, Color c, float a) {
        r = Math.min(r, Math.min(w, h) / 2f);
        if (r <= 0.5f) {
            fill(x, y, w, h, c, a);
            return;
        }
        fill(x + r, y, w - 2f * r, h, c, a);
        fill(x, y + r, r, h - 2f * r, c, a);
        fill(x + w - r, y + r, r, h - 2f * r, c, a);
        corner(x + r, y + r, r, 270f, c, a);
        corner(x + w - r, y + r, r, 0f, c, a);
        corner(x + w - r, y + h - r, r, 90f, c, a);
        corner(x + r, y + h - r, r, 180f, c, a);
    }

    /** A quarter disc of radius {@code r} at a corner's centre, from bearing {@code from}. */
    private void corner(float cx, float cy, float r, float from, Color c, float a) {
        float solid = tmp.set(c.r, c.g, c.b, c.a * a * alpha).toFloatBits();
        float clear = tmp.set(c.r, c.g, c.b, 0f).toFloatBits();
        float scx = ox + cx, scy = oy - cy, rr = Math.max(r - FEATHER / 2f, 0f);
        int n = Math.max(3, segments(r) / 4);
        for (int i = 0; i < n; i++) {
            float b0 = (from + 90f * i / n) * MathUtils.degreesToRadians, b1 = (from + 90f * (i + 1) / n) * MathUtils.degreesToRadians;
            float c0 = MathUtils.sin(b0), s0 = MathUtils.cos(b0), c1 = MathUtils.sin(b1), s1 = MathUtils.cos(b1);
            band(scx, scy, c0, s0, c1, s1, 0f, rr, solid, solid);
            band(scx, scy, c0, s0, c1, s1, rr, rr + FEATHER, solid, clear);
        }
    }

    /** A rectangle's outline {@code sw} wide, centred on its edge as SVG strokes are. */
    public void stroke(float x, float y, float w, float h, float sw, Color c) {
        stroke(x, y, w, h, sw, c, 1f);
    }

    public void stroke(float x, float y, float w, float h, float sw, Color c, float a) {
        float hs = sw / 2f;
        fill(x - hs, y - hs, w + sw, sw, c, a);
        fill(x - hs, y + h - hs, w + sw, sw, c, a);
        fill(x - hs, y + hs, sw, h - sw, c, a);
        fill(x + w - hs, y + hs, sw, h - sw, c, a);
    }

    /** A dashed outline: dashes {@code on} long with {@code off} between, from the top left round. */
    public void strokeDashed(float x, float y, float w, float h, float sw, Color c, float on, float off) {
        dashed(x, y, x + w, y, sw, c, on, off);
        dashed(x + w, y, x + w, y + h, sw, c, on, off);
        dashed(x + w, y + h, x, y + h, sw, c, on, off);
        dashed(x, y + h, x, y, sw, c, on, off);
    }

    public void line(float x1, float y1, float x2, float y2, float w, Color c) {
        line(x1, y1, x2, y2, w, c, 1f);
    }

    public void line(float x1, float y1, float x2, float y2, float w, Color c, float a) {
        float sx1 = ox + x1, sy1 = oy - y1, sx2 = ox + x2, sy2 = oy - y2;
        float dx = sx2 - sx1, dy = sy2 - sy1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        paint(c, a);
        batch.draw(white, sx1, sy1 - w / 2f, 0f, w / 2f, len, w, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    public void dashed(float x1, float y1, float x2, float y2, float w, Color c, float on, float off) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float ux = dx / len, uy = dy / len;
        for (float t = 0f; t < len; t += on + off) {
            float t2 = Math.min(len, t + on);
            line(x1 + ux * t, y1 + uy * t, x1 + ux * t2, y1 + uy * t2, w, c);
        }
    }

    /** A line drawn over a dark halo, for symbology on the world. */
    public void haloLine(float x1, float y1, float x2, float y2, float w, Color c) {
        line(x1, y1, x2, y2, w + 2.4f, NpColors.HALO);
        line(x1, y1, x2, y2, w, c);
    }

    /**
     * An open or closed polyline through {@code xy} (x, y pairs), each segment run on by half its
     * width so the corners close square, as a mitred SVG path's do at right angles.
     */
    public void poly(float[] xy, int n, boolean closed, float w, Color c) {
        int pts = n / 2;
        for (int i = 0; i < pts - (closed ? 0 : 1); i++) {
            int j = (i + 1) % pts;
            float x1 = xy[i * 2], y1 = xy[i * 2 + 1], x2 = xy[j * 2], y2 = xy[j * 2 + 1];
            float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-3f) continue;
            float ex = dx / len * w / 2f, ey = dy / len * w / 2f;
            line(x1 - ex, y1 - ey, x2 + ex, y2 + ey, w, c);
        }
    }

    public void poly(float[] xy, boolean closed, float w, Color c) {
        poly(xy, xy.length, closed, w, c);
    }

    /** A convex polygon filled, as a fan of triangles from its first point. */
    public void fillPoly(float[] xy, Color c) {
        fillPoly(xy, xy.length, c, 1f);
    }

    public void fillPoly(float[] xy, int n, Color c, float a) {
        int pts = n / 2;
        if (pts < 3) return;
        float col = tmp.set(c.r, c.g, c.b, c.a * a * alpha).toFloatBits();
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        for (int i = 1; i < pts - 1; i++) {
            vertex(0, ox + xy[0], oy - xy[1], col, u, v);
            vertex(1, ox + xy[i * 2], oy - xy[i * 2 + 1], col, u, v);
            vertex(2, ox + xy[i * 2 + 2], oy - xy[i * 2 + 3], col, u, v);
            vertex(3, ox + xy[i * 2 + 2], oy - xy[i * 2 + 3], col, u, v);
            batch.draw(white.getTexture(), quad, 0, 20);
        }
    }

    /** A ring {@code w} wide on radius {@code r}. */
    public void ring(float cx, float cy, float r, float w, Color c) {
        ring(cx, cy, r, w, c, 1f);
    }

    public void ring(float cx, float cy, float r, float w, Color c, float a) {
        arc(cx, cy, r, 0f, 360f, w, c, a);
    }

    /**
     * An arc {@code w} wide on radius {@code r}, from {@code from} to {@code to} degrees, measured
     * clockwise from straight up (a bearing), so a scope's sectors and a heading read as on a dial.
     */
    public void arc(float cx, float cy, float r, float from, float to, float w, Color c, float a) {
        float half = Math.max(w / 2f - FEATHER / 2f, 0f);
        float in = Math.max(r - half, 0f), out = r + half;
        float solid = tmp.set(c.r, c.g, c.b, c.a * a * alpha).toFloatBits();
        float clear = tmp.set(c.r, c.g, c.b, 0f).toFloatBits();
        float sweep = to - from;
        int n = Math.max(2, Math.round(segments(out + FEATHER) * Math.abs(sweep) / 360f));
        float scx = ox + cx, scy = oy - cy;
        for (int i = 0; i < n; i++) {
            float b0 = (from + sweep * i / n) * MathUtils.degreesToRadians, b1 = (from + sweep * (i + 1) / n) * MathUtils.degreesToRadians;
            // a bearing b: x = sin b, y up = cos b
            float c0 = MathUtils.sin(b0), s0 = MathUtils.cos(b0), c1 = MathUtils.sin(b1), s1 = MathUtils.cos(b1);
            band(scx, scy, c0, s0, c1, s1, Math.max(in - FEATHER, 0f), in, clear, solid);
            if (out > in) band(scx, scy, c0, s0, c1, s1, in, out, solid, solid);
            band(scx, scy, c0, s0, c1, s1, out, out + FEATHER, solid, clear);
        }
    }

    /** A round dot {@code d} across. */
    public void disc(float cx, float cy, float d, Color c) {
        disc(cx, cy, d, c, 1f);
    }

    public void disc(float cx, float cy, float d, Color c, float a) {
        float r = Math.max(d / 2f - FEATHER / 2f, 0f);
        float solid = tmp.set(c.r, c.g, c.b, c.a * a * alpha).toFloatBits();
        float clear = tmp.set(c.r, c.g, c.b, 0f).toFloatBits();
        int n = segments(r + FEATHER);
        float scx = ox + cx, scy = oy - cy, step = MathUtils.PI2 / n;
        for (int i = 0; i < n; i++) {
            float c0 = MathUtils.cos(step * i), s0 = MathUtils.sin(step * i), c1 = MathUtils.cos(step * (i + 1)), s1 = MathUtils.sin(step * (i + 1));
            band(scx, scy, c0, s0, c1, s1, 0f, r, solid, solid);
            band(scx, scy, c0, s0, c1, s1, r, r + FEATHER, solid, clear);
        }
    }

    private static int segments(float r) {
        return MathUtils.clamp(MathUtils.ceil(MathUtils.PI2 * r / 3f), 16, 256);
    }

    private void band(float cx, float cy, float cos0, float sin0, float cos1, float sin1, float r0, float r1, float col0, float col1) {
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        vertex(0, cx + cos0 * r0, cy + sin0 * r0, col0, u, v);
        vertex(1, cx + cos0 * r1, cy + sin0 * r1, col1, u, v);
        vertex(2, cx + cos1 * r1, cy + sin1 * r1, col1, u, v);
        vertex(3, cx + cos1 * r0, cy + sin1 * r0, col0, u, v);
        batch.draw(white.getTexture(), quad, 0, 20);
    }

    private void vertex(int i, float x, float y, float color, float u, float v) {
        int o = i * 5;
        quad[o] = x;
        quad[o + 1] = y;
        quad[o + 2] = color;
        quad[o + 3] = u;
        quad[o + 4] = v;
    }

    // ---- clipping -------------------------------------------------------------------------------

    /** Clips what follows to a local rectangle until {@link #unclip()}; false if nothing shows. */
    public boolean clip(Camera camera, float x, float y, float w, float h) {
        batch.flush();
        clipArea.set(ox + x, oy - y - h, w, h);
        ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipArea, scissor);
        return ScissorStack.pushScissors(scissor);
    }

    public void unclip() {
        batch.flush();
        ScissorStack.popScissors();
    }

    // ---- text -----------------------------------------------------------------------------------

    /** How wide {@code s} draws at {@code size}, letter spacing included. */
    public float width(CharSequence s, float size, boolean bold, float track) {
        if (s == null || s.length() == 0) return 0f;
        float w = 0f;
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == THIN) {
                if (i > start) w += segmentWidth(s, start, i, size, bold, track);
                if (i < s.length()) w += size * THIN_EM + track;
                start = i + 1;
            }
        }
        return w;
    }

    private float segmentWidth(CharSequence s, int from, int to, float size, boolean bold, float track) {
        BitmapFont f = fonts.get(size, bold);
        layout.setText(f, s, from, to, Color.WHITE, 0f, Align.left, false, null);
        return penWidth(f) + track * glyphCount();
    }

    private int glyphCount() {
        return layout.runs.size == 0 ? 0 : layout.runs.first().glyphs.size;
    }

    /**
     * The laid-out run's width as SVG measures text: every glyph's advance, the last one's included.
     * (A layout's own width ends at the last glyph's ink, which would close a thin space up.)
     */
    private float penWidth(BitmapFont f) {
        if (layout.runs.size == 0) return 0f;
        GlyphLayout.GlyphRun run = layout.runs.first();
        int n = run.glyphs.size;
        if (n == 0) return 0f;
        float[] adv = run.xAdvances.items;
        float w = 0f;
        for (int i = 1; i < n; i++) w += adv[i];
        BitmapFont.Glyph last = run.glyphs.peek();
        return w + (last.fixedWidth ? adv[n] : last.xadvance * f.getData().scaleX);
    }

    /**
     * Draws {@code s} with its baseline at local {@code y}, anchored at {@code x} by
     * {@link #START}, {@link #MIDDLE} or {@link #END}; returns its width. {@code track} is the space
     * added after each character in UI units, as SVG's letter-spacing.
     */
    public float text(float x, float y, CharSequence s, float size, boolean bold, Color c, float track, int anchor) {
        return text(x, y, s, size, bold, c, track, anchor, 1f);
    }

    public float text(float x, float y, CharSequence s, float size, boolean bold, Color c, float track, int anchor, float a) {
        if (s == null || s.length() == 0) return 0f;
        float w = width(s, size, bold, track);
        float x0 = anchor == MIDDLE ? x - w / 2f : anchor == END ? x - w : x;
        BitmapFont f = fonts.get(size, bold);
        float top = oy - y + f.getCapHeight();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == THIN) {
                if (i > start) x0 = drawSegment(f, s, start, i, ox + x0, top, c, track, a) - ox;
                if (i < s.length()) x0 += size * THIN_EM + track;
                start = i + 1;
            }
        }
        return w;
    }

    /** Text over the world: a dark outline first, so it reads on any background. */
    public float haloText(float x, float y, CharSequence s, float size, boolean bold, Color c, float track, int anchor) {
        float d = 0.9f;
        text(x - d, y, s, size, bold, NpColors.HALO, track, anchor);
        text(x + d, y, s, size, bold, NpColors.HALO, track, anchor);
        text(x, y - d, s, size, bold, NpColors.HALO, track, anchor);
        text(x, y + d, s, size, bold, NpColors.HALO, track, anchor);
        return text(x, y, s, size, bold, c, track, anchor);
    }

    /** Draws one run of text with its pen at stage x; returns the stage x after it, spacing included. */
    private float drawSegment(BitmapFont f, CharSequence s, int from, int to, float sx, float top, Color c, float track, float a) {
        tmp.set(c.r, c.g, c.b, c.a * a * alpha);
        layout.setText(f, s, from, to, tmp, 0f, Align.left, false, null);
        if (track != 0f) {
            for (GlyphLayout.GlyphRun run : layout.runs) {
                FloatArray adv = run.xAdvances;
                for (int i = 1; i < run.glyphs.size; i++) adv.items[i] += track;
            }
        }
        float pen = penWidth(f);
        // the layout puts the first glyph's ink at x; the pen starts its bearing before that
        float bearing = layout.runs.size == 0 ? 0f : -layout.runs.first().xAdvances.first();
        f.draw(batch, layout, sx + bearing, top);
        return sx + pen + track;
    }

    /** The cap height of the font at {@code size}, for centring text on a box. */
    public float capHeight(float size, boolean bold) {
        return fonts.get(size, bold).getCapHeight();
    }
}
