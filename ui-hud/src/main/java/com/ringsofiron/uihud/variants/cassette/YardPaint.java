package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * Draws the Yard Standard's hardware with a {@link Batch}: shapes from a few generated textures, and the
 * instruments the style guide names (lamps, lenses and push-lights, split flaps, drum counters, tubes,
 * keycaps, Dymo tape, the barber flag, blanking plates, edgewise meters, units).
 *
 * <p>Coordinates are UI units measured <em>down</em> from the stage's top-left corner, as the mockups are
 * drawn, so a unit's code reads like its drawing; text is placed by its baseline. {@link #begin} gives
 * the stage's height to turn them over.
 */
public final class YardPaint implements Disposable {
    public final YardFonts fonts = new YardFonts();

    private final Texture barber, hatch, hazard;
    private TextureRegion whiteRegion, discRegion, softRegion;
    private final TextureRegion[] quarter = new TextureRegion[4];
    private NinePatch glowPatch;
    /** The packer the shapes are on now: the fonts' pages, made anew when the scale changes. */
    private PixmapPacker shapesOn;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tmp = new Color();
    private final float[] verts = new float[20];
    private Batch batch;
    private float top;
    /** Where design (0, 0) is drawn: a unit drawn from its mockup's coordinates is moved by this. */
    private float ox, oy;
    private float alpha = 1f;
    private float time;

    /** Makes the two striped textures; the shapes are packed with the fonts on first use. Needs GL. */
    public YardPaint() {
        barber = stripes(16, Yard.hex("E8E2D0"), Yard.RED);
        hatch = stripes(16, new Color(1f, 1f, 1f, 0f), Color.WHITE);
        hazard = stripes(16, Yard.MUSTARD, Yard.hex("1E1A17"));
    }

    /** Packs the shapes onto the fonts' pages, once per set of pages. */
    private void ensureShapes() {
        PixmapPacker packer = fonts.packer();
        if (packer == shapesOn) return;
        shapesOn = packer;
        pack(packer, "white", 4, (x, y) -> 1f);
        int n = 128;
        pack(packer, "disc", n, (x, y) -> {
            float d = (float) Math.hypot(x + 0.5f - n / 2f, y + 0.5f - n / 2f);
            return MathUtils.clamp(n / 2f - d, 0f, 1f);
        });
        pack(packer, "soft", 64, (x, y) -> {
            float d = (float) Math.hypot(x + 0.5f - 32f, y + 0.5f - 32f) / 32f;
            float a = MathUtils.clamp(1f - d, 0f, 1f);
            return a * a;
        });
        // A soft-edged rectangle for a lens's glow: 16 px of falloff round a solid middle
        pack(packer, "softRect", 48, (x, y) -> {
            float dx = Math.max(0f, Math.abs(x + 0.5f - 24f) - 8f), dy = Math.max(0f, Math.abs(y + 0.5f - 24f) - 8f);
            float a = MathUtils.clamp(1f - (float) Math.hypot(dx, dy) / 16f, 0f, 1f);
            return a * a;
        });
        packer.updatePageTextures(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear, false);
        TextureRegion white = region(packer, "white");
        whiteRegion = new TextureRegion(white, 1, 1, 2, 2);
        discRegion = region(packer, "disc");
        quarter[0] = new TextureRegion(discRegion, 0, 0, n / 2, n / 2);          // top left
        quarter[1] = new TextureRegion(discRegion, n / 2, 0, n / 2, n / 2);      // top right
        quarter[2] = new TextureRegion(discRegion, 0, n / 2, n / 2, n / 2);      // bottom left
        quarter[3] = new TextureRegion(discRegion, n / 2, n / 2, n / 2, n / 2);  // bottom right
        softRegion = region(packer, "soft");
        glowPatch = new NinePatch(region(packer, "softRect"), 16, 16, 16, 16);
    }

    private interface Coverage {
        float at(int x, int y);
    }

    private static void pack(PixmapPacker packer, String name, int n, Coverage c) {
        Pixmap p = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, c.at(x, y)));
        }
        packer.pack(name, p);
        p.dispose();
    }

    private static TextureRegion region(PixmapPacker packer, String name) {
        PixmapPacker.PixmapPackerRectangle r = packer.getRect(name);
        return new TextureRegion(packer.getPage(name).getTexture(), r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /** Diagonal stripes, half {@code a} and half {@code b}, tiling. */
    private static Texture stripes(int n, Color a, Color b) {
        Pixmap p = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) p.drawPixel(x, y, Color.rgba8888(((x + y) % n) < n / 2 ? a : b));
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        p.dispose();
        return t;
    }

    /** Starts a frame's painting on {@code batch}, whose stage is {@code stageHeight} units tall. */
    public void begin(Batch batch, float stageHeight, float parentAlpha, float time) {
        ensureShapes();
        this.batch = batch;
        this.top = stageHeight;
        this.alpha = parentAlpha;
        this.time = time;
        ox = oy = 0f;
    }

    /**
     * Moves everything drawn after this by ({@code dx}, {@code dy}) design units, so a unit can be
     * drawn from its mockup's coordinates wherever the layout puts it. {@link #begin} resets it.
     */
    public void origin(float dx, float dy) {
        ox = dx;
        oy = dy;
    }

    public float originX() {
        return ox;
    }

    public float originY() {
        return oy;
    }

    /** Seconds since the HUD came up, for blinking and pulsing. */
    public float time() {
        return time;
    }

    public Batch batch() {
        return batch;
    }

    /** The stage's y (up) for the design y (down). */
    public float sy(float y) {
        return top - (y + oy);
    }

    /** The stage's x for the design x. */
    public float sx(float x) {
        return x + ox;
    }

    // --- Shapes ------------------------------------------------------------------------------

    private void color(Color c, float a) {
        batch.setColor(c.r, c.g, c.b, c.a * a * alpha);
    }

    public void rect(float x, float y, float w, float h, Color c) {
        rect(x, y, w, h, c, 1f);
    }

    public void rect(float x, float y, float w, float h, Color c, float a) {
        if (w <= 0f || h <= 0f) return;
        color(c, a);
        batch.draw(whiteRegion, sx(x), sy(y) - h, w, h);
    }

    /** A rectangle with corners of radius {@code r}. */
    public void rrect(float x, float y, float w, float h, float r, Color c) {
        rrect(x, y, w, h, r, c, 1f);
    }

    public void rrect(float x, float y, float w, float h, float r, Color c, float a) {
        if (w <= 0f || h <= 0f) return;
        r = Math.min(r, Math.min(w, h) / 2f);
        if (r < 0.3f) {
            rect(x, y, w, h, c, a);
            return;
        }
        color(c, a);
        float by = sy(y) - h;
        x = sx(x);
        batch.draw(whiteRegion, x + r, by, w - 2 * r, h);
        batch.draw(whiteRegion, x, by + r, r, h - 2 * r);
        batch.draw(whiteRegion, x + w - r, by + r, r, h - 2 * r);
        batch.draw(quarter[0], x, by + h - r, r, r);
        batch.draw(quarter[1], x + w - r, by + h - r, r, r);
        batch.draw(quarter[2], x, by, r, r);
        batch.draw(quarter[3], x + w - r, by, r, r);
    }

    /** A vertical gradient from {@code a} at the top to {@code b} at the bottom. */
    public void vgrad(float x, float y, float w, float h, Color a, Color b) {
        vgrad(x, y, w, h, a, b, 1f);
    }

    public void vgrad(float x, float y, float w, float h, Color a, Color b, float alphaMul) {
        if (w <= 0f || h <= 0f) return;
        float ca = Color.toFloatBits(a.r, a.g, a.b, a.a * alphaMul * alpha), cb = Color.toFloatBits(b.r, b.g, b.b, b.a * alphaMul * alpha);
        x = sx(x);
        float x2 = x + w, y1 = sy(y) - h, y2 = sy(y);
        float u = whiteRegion.getU(), v = whiteRegion.getV(), u2 = whiteRegion.getU2(), v2 = whiteRegion.getV2();
        int i = 0;
        verts[i++] = x; verts[i++] = y1; verts[i++] = cb; verts[i++] = u; verts[i++] = v2;
        verts[i++] = x; verts[i++] = y2; verts[i++] = ca; verts[i++] = u; verts[i++] = v;
        verts[i++] = x2; verts[i++] = y2; verts[i++] = ca; verts[i++] = u2; verts[i++] = v;
        verts[i++] = x2; verts[i++] = y1; verts[i++] = cb; verts[i++] = u2; verts[i] = v2;
        batch.draw(whiteRegion.getTexture(), verts, 0, 20);
    }

    /** The glass sheen over a lens or a scale: a faint light falling from the top left. */
    public void glass(float x, float y, float w, float h) {
        vgrad(x, y, w, h * 0.4f, new Color(1f, 1f, 1f, 0.09f), new Color(1f, 1f, 1f, 0.015f));
    }

    public void circle(float cx, float cy, float r, Color c) {
        circle(cx, cy, r, c, 1f);
    }

    public void circle(float cx, float cy, float r, Color c, float a) {
        color(c, a);
        batch.draw(discRegion, sx(cx) - r, sy(cy) - r, 2 * r, 2 * r);
    }

    /** A soft round glow of radius {@code r}. */
    public void glow(float cx, float cy, float r, Color c, float a) {
        color(c, a);
        batch.draw(softRegion, sx(cx) - r, sy(cy) - r, 2 * r, 2 * r);
    }

    /** A soft glow round a rectangle, {@code spread} units out. */
    public void glowRect(float x, float y, float w, float h, float spread, Color c, float a) {
        color(c, a);
        glowPatch.setLeftWidth(spread);
        glowPatch.setRightWidth(spread);
        glowPatch.setTopHeight(spread);
        glowPatch.setBottomHeight(spread);
        glowPatch.draw(batch, sx(x) - spread, sy(y) - h - spread, w + 2 * spread, h + 2 * spread);
    }

    /** A straight line {@code width} units wide. */
    public void line(float x1, float y1, float x2, float y2, float width, Color c) {
        line(x1, y1, x2, y2, width, c, 1f);
    }

    public void line(float x1, float y1, float x2, float y2, float width, Color c, float a) {
        float dx = x2 - x1, dy = y1 - y2;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return;
        color(c, a);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.draw(whiteRegion, sx(x1), sy(y1) - width / 2f, 0f, width / 2f, len, width, 1f, 1f, angle);
    }

    /** An outline {@code width} units wide just inside the rectangle. */
    public void frame(float x, float y, float w, float h, float width, Color c, float a) {
        rect(x, y, w, width, c, a);
        rect(x, y + h - width, w, width, c, a);
        rect(x, y + width, width, h - 2 * width, c, a);
        rect(x + w - width, y + width, width, h - 2 * width, c, a);
    }

    /** The barber flag's red and white stripes over a rectangle: "not working". */
    public void barber(float x, float y, float w, float h) {
        stripes(barber, x, y, w, h, Color.WHITE, 1f, 6f);
    }

    /** Mustard and black hazard stripes: the crisis placard's case. */
    public void hazard(float x, float y, float w, float h) {
        stripes(hazard, x, y, w, h, Color.WHITE, 1f, 9f);
    }

    /** Diagonal hatching in {@code c}, for a hazard drawn in its tiles. */
    public void hatch(float x, float y, float w, float h, Color c, float a) {
        stripes(hatch, x, y, w, h, c, a, 5f);
    }

    private void stripes(Texture t, float x, float y, float w, float h, Color c, float a, float pitch) {
        if (w <= 0f || h <= 0f) return;
        color(c, a);
        float by = sy(y) - h, bx = sx(x);
        // Pinned to the stage, so neighbouring flags line up
        float u = bx / (pitch * 2f), v = -by / (pitch * 2f);
        batch.draw(t, bx, by, w, h, u, v + h / (pitch * 2f), u + w / (pitch * 2f), v);
    }

    // --- Text --------------------------------------------------------------------------------

    public BitmapFont font(YardFonts.Face face, float size) {
        return fonts.get(face, size);
    }

    public float width(YardFonts.Face face, float size, CharSequence s) {
        if (s == null || s.length() == 0) return 0f;
        layout.setText(font(face, size), s);
        return layout.width;
    }

    /** Draws {@code s} with its baseline at {@code baseline}, starting at {@code x}; returns its width. */
    public float text(YardFonts.Face face, float size, CharSequence s, float x, float baseline, Color c) {
        return text(face, size, s, x, baseline, c, 1f);
    }

    public float text(YardFonts.Face face, float size, CharSequence s, float x, float baseline, Color c, float a) {
        if (s == null || s.length() == 0) return 0f;
        BitmapFont f = font(face, size);
        f.setColor(c.r, c.g, c.b, c.a * a * alpha);
        layout.setText(f, s);
        f.draw(batch, layout, sx(x), sy(baseline) + f.getCapHeight());
        return layout.width;
    }

    /** Centred on {@code cx}. */
    public float textC(YardFonts.Face face, float size, CharSequence s, float cx, float baseline, Color c) {
        float w = width(face, size, s);
        text(face, size, s, cx - w / 2f, baseline, c);
        return w;
    }

    /** Ending at {@code right}. */
    public float textR(YardFonts.Face face, float size, CharSequence s, float right, float baseline, Color c) {
        float w = width(face, size, s);
        text(face, size, s, right - w, baseline, c);
        return w;
    }

    /** An engraved legend in the unit's ink, 12 units unless said. */
    public float legend(CharSequence s, float x, float baseline, Color ink) {
        return text(YardFonts.Face.LEGEND, 12f, s, x, baseline, ink);
    }

    /**
     * Phosphor text on a tube, with the beam's bloom round it. {@code bold} for a code or a key name.
     */
    public float tube(CharSequence s, float x, float baseline, float size, Color c, boolean bold) {
        if (s == null || s.length() == 0) return 0f;
        YardFonts.Face face = bold ? YardFonts.Face.TUBE_BOLD : YardFonts.Face.TUBE;
        float w = width(face, size, s);
        // The bloom: the line drawn soft behind itself
        glowRect(x, baseline - size * 0.72f, w, size * 0.8f, size * 0.45f, c, 0.12f);
        text(face, size, s, x, baseline, c);
        return w;
    }

    /** The warning row's large title, in VT323. */
    public float title(CharSequence s, float x, float baseline, float size, Color c) {
        if (s == null || s.length() == 0) return 0f;
        float w = width(YardFonts.Face.TITLE, size, s);
        glowRect(x, baseline - size * 0.62f, w, size * 0.66f, size * 0.4f, c, 0.16f);
        text(YardFonts.Face.TITLE, size, s, x, baseline, c);
        return w;
    }

    /** Reversed tube text, dark on a lit block, for a key name inside a tube's line. Returns the block's width. */
    public float tubeKey(CharSequence s, float x, float baseline, float size, Color c) {
        float w = width(YardFonts.Face.TUBE_BOLD, size, s) + 8f;
        glowRect(x, baseline - size + 1.5f, w, size + 3f, 3f, c, 0.25f);
        rect(x, baseline - size + 1.5f, w, size + 3f, c);
        text(YardFonts.Face.TUBE_BOLD, size, s, x + 4f, baseline, Yard.CRT_BLACK);
        return w;
    }

    // --- Instruments -------------------------------------------------------------------------

    /**
     * A unit: the dark chassis housing (running off screen on the sides named in {@code attach}: t, b, l,
     * r), the post's identity stripe on the edge that faces the view, and the faceplate. Returns nothing:
     * the face is inset 5 units on the open sides, 3 on the attached ones.
     */
    public void unit(float x, float y, float w, float h, Yard.Finish fin, String attach) {
        boolean t = attach.indexOf('t') >= 0, b = attach.indexOf('b') >= 0, l = attach.indexOf('l') >= 0, r = attach.indexOf('r') >= 0;
        float ex = 14f;
        float bx = l ? x - ex : x, by = t ? y - ex : y, bw = w + (l ? ex : 0) + (r ? ex : 0), bh = h + (t ? ex : 0) + (b ? ex : 0);
        // A cast shadow, then the housing
        rrect(bx + 2f, by + 3f, bw, bh, 7f, Yard.SHADOW, 0.6f);
        shade(bx, by, bw, bh, 7f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        if (fin.stripe != null) {
            if (t && !b) rect(x + (l ? 0 : 7), y + h - 2.8f, w - (l ? 0 : 7) - (r ? 0 : 7), 2.4f, fin.stripe);
            else if (b && !t && !l && !r) rect(x + 7, y + 0.4f, w - 14, 2.4f, fin.stripe);
            else if (l && !r) rect(x + w - 2.8f, y + 7, 2.4f, h - 14, fin.stripe);
            else if (r && !l) rect(x + 0.4f, y + 7, 2.4f, h - 14, fin.stripe);
        }
        float fx = x + (l ? 3 : 5), fy = y + (t ? 3 : 5), fw = w - (l ? 3 : 5) - (r ? 3 : 5), fh = h - (t ? 3 : 5) - (b ? 3 : 5);
        face(fx, fy, fw, fh, fin, false);
    }

    /** A rounded box shaded top to bottom. */
    public void shade(float x, float y, float w, float h, float r, Color topColor, Color bottomColor) {
        // The corners in the mean colour, the body as a gradient between them
        tmp.set(topColor).lerp(bottomColor, 0.5f);
        rrect(x, y, w, h, r, tmp);
        vgrad(x + r * 0.3f, y + 0.5f, w - r * 0.6f, h - 1f, topColor, bottomColor);
    }

    /** A faceplate in its finish: enamel (or gun-metal, or avocado) with a lit top edge and a shaded foot. */
    public void face(float x, float y, float w, float h, Yard.Finish fin, boolean fixings) {
        shade(x, y, w, h, 3f, fin.top, fin.bottom);
        rect(x + 3f, y + 0.2f, w - 6f, 1.1f, fin.hi, 0.9f);
        rect(x + 3f, y + h - 1.2f, w - 6f, 1.1f, fin.lo, 0.8f);
        rect(x + w - 1.2f, y + 3f, 1.1f, h - 6f, fin.lo, 0.6f);
        if (fixings) {
            fixing(x + 6f, y + 6f, fin);
            fixing(x + w - 6f, y + 6f, fin);
            fixing(x + 6f, y + h - 6f, fin);
            fixing(x + w - 6f, y + h - 6f, fin);
        }
    }

    /** A screw, or a rivet on gun-metal. */
    public void fixing(float cx, float cy, Yard.Finish fin) {
        if (fin.rivets) {
            circle(cx, cy, 2.9f, Yard.SCREW_SLOT);
            circle(cx, cy, 2.4f, Yard.SCREW);
            circle(cx - 0.7f, cy - 0.8f, 0.8f, Color.WHITE, 0.5f);
        } else screw(cx, cy);
    }

    public void screw(float cx, float cy) {
        circle(cx, cy, 3.8f, Yard.SCREW_SLOT);
        circle(cx, cy, 3.2f, Yard.SCREW);
        circle(cx - 0.9f, cy - 1f, 1.3f, Color.WHITE, 0.35f);
        line(cx - 2f, cy, cx + 2f, cy, 1f, Yard.SCREW_SLOT);
        line(cx, cy - 2f, cx, cy + 2f, 1f, Yard.SCREW_SLOT);
    }

    /** A lamp: a lens in a dark bezel ring, lit or dark. */
    public void lamp(float cx, float cy, float r, Yard.Lamp lamp, boolean lit) {
        if (lit) glow(cx, cy, r * 2.8f, lamp.on, 0.55f);
        circle(cx, cy, r + 2.9f, Yard.hex("2B2723"));
        circle(cx, cy, r + 1.6f, Yard.ALU);
        circle(cx, cy, r + 0.9f, Yard.hex("211D1A"));
        circle(cx, cy, r, lit ? lamp.on : lamp.off);
        circle(cx - r * 0.3f, cy - r * 0.35f, r * 0.35f, Color.WHITE, lit ? 0.5f : 0.12f);
    }

    /** What a push-light or lens is. */
    public enum Kind {
        /** An order: its legend is bright on the smoked lens, so it reads as available while dark. */
        ORDER,
        /** An indicator (a state): its legend is faint while dark, the dark cockpit. */
        INDICATOR
    }

    /**
     * A push-light or lens with a fixed legend of one or two lines. {@code unavailable} (an order that
     * can't be given) runs the barber strip along its foot. A lit lens glows; {@code hard} glows harder
     * (a warning).
     */
    public void push(float x, float y, float w, float h, String[] lines, Yard.Lamp lamp, boolean lit, Kind kind, boolean unavailable, boolean hard) {
        rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2.5f, Yard.ALU);
        rrect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2f, Yard.LENS_RIM);
        if (lit) glowRect(x, y, w, h, hard ? 9f : 6f, lamp.on, hard ? 0.7f : 0.45f);
        rrect(x, y, w, h, 1.5f, lit ? lamp.on : kind == Kind.INDICATOR ? lamp.off : Yard.SMOKED);
        glass(x, y, w, h);
        Color tc = lit ? Yard.LEGEND_ON_LIT : kind == Kind.INDICATOR || unavailable ? Yard.LEGEND_FAINT : Yard.LEGEND_ORDER;
        for (int i = 0; i < lines.length; i++) {
            float base = y + h / 2f + (i - (lines.length - 1) / 2f) * 13f + 4.3f - (unavailable ? 1.5f : 0f);
            textC(YardFonts.Face.LEGEND, 12f, lines[i], x + w / 2f, base, tc);
        }
        if (unavailable) barber(x, y + h - 4.5f, w, 4.5f);
    }

    /** An indicator lens with a severity glyph drawn before its legend while lit (◆ warning, ▲ caution). */
    public void severityLens(float x, float y, float w, float h, String name, Yard.Lamp lamp, boolean lit, boolean warning, boolean blink) {
        boolean on = lit && (!blink || (time * 2.5f) % 1f < 0.62f);
        push(x, y, w, h, new String[]{""}, lamp, on, Kind.INDICATOR, false, warning && on);
        Color tc = on ? Yard.LEGEND_ON_LIT : Yard.LEGEND_FAINT;
        float tw = width(YardFonts.Face.LEGEND, 12f, name), gw = lit ? 9f : 0f;
        float x0 = x + (w - tw - gw) / 2f, base = y + h / 2f + 4.3f;
        if (lit) {
            if (warning) diamond(x0 + 3.5f, base - 4.2f, 3.6f, tc);
            else triangle(x0 + 3.5f, base - 4.4f, 3.8f, tc);
        }
        text(YardFonts.Face.LEGEND, 12f, name, x0 + gw, base, tc);
    }

    /** An advisory: the white lamp lit softly, no glyph. */
    public void softLens(float x, float y, float w, float h, String name) {
        rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2.5f, Yard.ALU);
        rrect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2f, Yard.LENS_RIM);
        glowRect(x, y, w, h, 5f, Yard.WHITE_LAMP.on, 0.2f);
        rrect(x, y, w, h, 1.5f, Yard.hex("B9B2A1"));
        glass(x, y, w, h);
        textC(YardFonts.Face.LEGEND, 12f, name, x + w / 2f, y + h / 2f + 4.3f, Yard.hex("2A2016"));
    }

    /** A blanking plate where a ship has no such system: no legend, two screws. */
    public void blank(float x, float y, float w, float h) {
        rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2.5f, Yard.ALU);
        rrect(x, y, w, h, 1.5f, Yard.hex("8F8B82"));
        screw(x + 7f, y + h / 2f);
        screw(x + w - 7f, y + h / 2f);
    }

    /** The warning glyph, a filled diamond. */
    public void diamond(float cx, float cy, float r, Color c) {
        float s = r * 1.41421f;
        color(c, 1f);
        batch.draw(whiteRegion, sx(cx) - s / 2f, sy(cy) - s / 2f, s / 2f, s / 2f, s, s, 1f, 1f, 45f);
    }

    /** The caution glyph, a filled triangle pointing up: three thin strips stacked. */
    public void triangle(float cx, float cy, float r, Color c) {
        int rows = 7;
        float h = r * 1.8f, y0 = cy - h * 0.55f;
        for (int i = 0; i < rows; i++) {
            float t = (i + 0.5f) / rows, half = r * t;
            rect(cx - half, y0 + i * h / rows, 2 * half, h / rows + 0.15f, c);
        }
    }

    /**
     * Split flaps: one cell a character, {@code cw} wide and {@code ch} tall, in a dark frame. For words and
     * names that change; {@code text} is padded or cut to {@code cells}.
     */
    public void flaps(float x, float y, CharSequence text, int cells, float cw, float ch, float size, Color ink) {
        rrect(x - 2f, y - 2f, cells * cw + 4f, ch + 4f, 1.5f, Yard.FLAP_FRAME);
        BitmapFont f = font(YardFonts.Face.FLAP, size);
        float base = y + ch / 2f + size * 0.36f;
        for (int i = 0; i < cells; i++) {
            float cx = x + i * cw;
            rect(cx + 0.5f, y, cw - 1f, ch, Yard.FLAP_CELL);
            rect(cx + 0.5f, y, cw - 1f, ch / 2f, Color.WHITE, 0.025f);
            // The split between the two flaps, a whole unit tall so every cell shows it alike, and under the
            // figure: across a word at this size a dark line over the letters reads as struck through
            rect(cx + 0.5f, y + ch / 2f - 0.5f, cw - 1f, 1f, Color.BLACK, 0.75f);
            char c = text != null && i < text.length() ? text.charAt(i) : ' ';
            if (c != ' ') {
                String g = glyph(c);
                layout.setText(f, g);
                text(YardFonts.Face.FLAP, size, g, cx + (cw - layout.width) / 2f, base, ink);
            }
        }
    }

    /** Flaps sized to their text. */
    public void flaps(float x, float y, CharSequence text, float cw, float ch, float size) {
        flaps(x, y, text, text.length(), cw, ch, size, Yard.FLAP_TEXT);
    }

    /** The width {@code n} flap cells take, frame included. */
    public static float flapsWidth(int n, float cw) {
        return n * cw + 4f;
    }

    private static final String[] GLYPHS = new String[128];

    private static String glyph(char c) {
        if (c < 128) {
            String g = GLYPHS[c];
            if (g == null) GLYPHS[c] = g = String.valueOf(c);
            return g;
        }
        return String.valueOf(c);
    }

    /** The width of a drum counter showing {@code s}: figures {@code cw} wide, a point 5, a colon 6. */
    public static float drumWidth(CharSequence s, float cw) {
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += c == '.' ? 5f : c == ':' ? 6f : cw;
        }
        return w;
    }

    /**
     * A drum counter: exact numbers, one wheel a figure. Leading zeros are dimmed when {@code dimLead}.
     * Returns its width without the frame.
     */
    public float drums(float x, float y, CharSequence s, float cw, float ch, float size, boolean dimLead) {
        float w = drumWidth(s, cw);
        rrect(x - 3f, y - 3f, w + 6f, ch + 6f, 2.5f, Yard.BAKELITE);
        float off = 0f;
        boolean lead = true;
        BitmapFont f = font(YardFonts.Face.FLAP, size);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            float xx = x + off;
            if (c == '.') {
                circle(xx + 2.5f, y + ch - 4f, 1.6f, Yard.FLAP_TEXT);
                off += 5f;
                continue;
            }
            if (c == ':') {
                circle(xx + 3f, y + ch * 0.35f, 1.4f, Yard.FLAP_TEXT);
                circle(xx + 3f, y + ch * 0.68f, 1.4f, Yard.FLAP_TEXT);
                off += 6f;
                lead = false;
                continue;
            }
            if (c != '0' && c != ' ') lead = false;
            if (i == s.length() - 1 || s.charAt(i + 1) == '.' || s.charAt(i + 1) == ':') lead = false;
            off += cw;
            // The wheel: dark at its edges, lit across its middle
            vgrad(xx + 0.8f, y, cw - 1.6f, ch / 2f, Yard.hex("050505"), Yard.hex("34302D"));
            vgrad(xx + 0.8f, y + ch / 2f, cw - 1.6f, ch / 2f, Yard.hex("34302D"), Yard.hex("050505"));
            if (c != ' ') {
                String g = glyph(c);
                layout.setText(f, g);
                text(YardFonts.Face.FLAP, size, g, xx + (cw - layout.width) / 2f, y + ch / 2f + size * 0.36f, dimLead && lead ? Yard.DRUM_LEAD : Yard.FLAP_TEXT);
            }
        }
        glass(x - 3f, y - 3f, w + 6f, ch + 6f);
        return w;
    }

    /**
     * Drums ending at {@code right}, so the unit after them stays put however many figures there are; past
     * {@code maxW} the wheels narrow, and their figures with them. Returns the width.
     */
    public float drumsR(float right, float y, CharSequence s, float cw, float ch, float size, float maxW, boolean dimLead) {
        float w = drumWidth(s, cw);
        if (w > maxW) {
            float fixed = 0f;
            int wheels = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '.') fixed += 5f;
                else if (c == ':') fixed += 6f;
                else wheels++;
            }
            float k = wheels == 0 ? 1f : Math.max(0.6f, (maxW - fixed) / (wheels * cw));
            cw *= k;
            size *= k;
            w = drumWidth(s, cw);
        }
        drums(right - w, y, s, cw, ch, size, dimLead);
        return w;
    }

    /** A legend no wider than {@code maxW}: a long one is engraved smaller (never under 9.5 units). Returns its width. */
    public float legendFit(CharSequence s, float x, float baseline, float maxW, Color ink) {
        float size = 12f, w = width(YardFonts.Face.LEGEND, size, s);
        if (w > maxW) size = Math.max(9.5f, size * maxW / w);
        return text(YardFonts.Face.LEGEND, size, s, x, baseline, ink);
    }

    /** A keycap with its key's name: rubber, a lighter top, the name in drum figures. Returns its width. */
    public float key(float x, float y, String name) {
        float w = Math.max(15f, 7f + width(YardFonts.Face.FLAP, 12f, name) + 1f), h = 15f;
        rrect(x, y, w, h, 2.5f, Yard.RUBBER);
        rrect(x + 1.2f, y + 0.8f, w - 2.4f, h - 4f, 2f, Yard.hex("4D4945"));
        textC(YardFonts.Face.FLAP, 12f, name, x + w / 2f, y + h - 4.6f, Yard.INK_LIGHT);
        return w;
    }

    /** The width {@link #key} will take. */
    public float keyWidth(String name) {
        return Math.max(15f, 7f + width(YardFonts.Face.FLAP, 12f, name) + 1f);
    }

    /** Dymo tape: white raised capitals on black, for a name a refit changes. Returns its width. */
    public float dymo(float x, float y, String t) {
        float w = width(YardFonts.Face.DYMO, 12f, t) + 9f, h = 15f;
        rrect(x, y, w, h, 1.5f, Yard.hex("1C1B1A"));
        rect(x, y + h - 2f, w, 2f, Color.BLACK, 0.35f);
        textC(YardFonts.Face.DYMO, 12f, t, x + w / 2f + 0.5f, y + h / 2f + 4.3f, Yard.hex("F4EFE6"));
        return w;
    }

    /** The striped tab that drops in front of something not working, in its aluminium frame. */
    public void barberTab(float x, float y, float w, float h) {
        rrect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2f, Yard.ALU);
        barber(x, y, w, h);
    }

    /**
     * A tube's bezel and face: a dark bakelite surround, the black screen. Draw its text after, then
     * {@link #tubeOver} for the scanlines and sheen.
     */
    public void tube(float x, float y, float w, float h, float r, float bezel, boolean dead) {
        rrect(x - bezel, y - bezel, w + 2 * bezel, h + 2 * bezel, r + 2f, Yard.BAKELITE);
        glass(x - bezel, y - bezel, w + 2 * bezel, h + 2 * bezel);
        rrect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, r + 0.5f, Yard.CRT_BEZEL);
        rrect(x, y, w, h, r, dead ? Yard.CRT_OFF : Yard.CRT_BLACK);
    }

    /** The tube's scanlines and glass, over what was drawn on it. */
    public void tubeOver(float x, float y, float w, float h) {
        for (float yy = y + 1.4f; yy < y + h; yy += 2f) rect(x, yy, w, 0.6f, Color.BLACK, 0.22f);
        glass(x, y, w, h);
    }

    /** A round tube: bakelite surround and the black screen of radius {@code r}. */
    public void roundTube(float cx, float cy, float r, float bezel) {
        circle(cx, cy, r + bezel, Yard.BAKELITE);
        circle(cx, cy, r + 1.5f, Yard.CRT_BEZEL);
        circle(cx, cy, r, Yard.CRT_BLACK);
    }

    /**
     * An edgewise meter: a cream scale with ticks, coloured bands along its foot, and an orange pointer at
     * {@code value}. {@code bracket} (two values, or null) draws a bracket over the scale, and
     * {@code home} (NaN for none) the home bug under it.
     */
    public void meter(float x, float y, float w, float h, float min, float max, float value, int major, float[][] zones, Color[] zoneColors, float[] bracket, float home) {
        rrect(x - 4f, y - 4f, w + 8f, h + 8f, 2.5f, Yard.BAKELITE);
        rect(x, y, w, h, Yard.hex("F2ECDC"));
        if (zones != null) {
            for (int i = 0; i < zones.length; i++) {
                float a = at(x, w, min, max, zones[i][0]), b = at(x, w, min, max, zones[i][1]);
                rect(a, y + h - 5f, b - a, 5f, zoneColors[i]);
            }
        }
        for (int i = 0; i <= major * 2; i++) {
            float xx = x + w * i / (major * 2f);
            rect(xx - (i % 2 == 1 ? 0.35f : 0.6f), y + h - (i % 2 == 1 ? 4f : 8f), i % 2 == 1 ? 0.7f : 1.2f, i % 2 == 1 ? 4f : 8f, Yard.INK);
        }
        if (bracket != null) {
            float a = at(x, w, min, max, bracket[0]), b = at(x, w, min, max, bracket[1]);
            rect(a, y + 2f, b - a, 1.6f, Yard.INK);
            rect(a - 0.8f, y + 2f, 1.6f, 5f, Yard.INK);
            rect(b - 0.8f, y + 2f, 1.6f, 5f, Yard.INK);
        }
        float px = at(x, w, min, max, value);
        rect(px - 0.65f, y + 1f, 1.3f, h - 10f, Yard.hex("1A1614"));
        pointer(px, y + h - 1f, Yard.ORANGE);
        if (!Float.isNaN(home)) homeBug(at(x, w, min, max, home), y + h + 3f);
        glass(x, y, w, h);
    }

    private static float at(float x, float w, float min, float max, float v) {
        return x + (MathUtils.clamp(v, min, max) - min) / (max - min) * w;
    }

    /** A pointer: a small triangle standing on its tip at ({@code x}, {@code tipY}), pointing down. */
    public void pointer(float x, float tipY, Color c) {
        int rows = 6;
        float h = 11f;
        for (int i = 0; i < rows; i++) {
            float t = (i + 0.5f) / rows, half = 5f * (1f - t) + 0.4f;
            rect(x - half - 0.4f, tipY - h + i * h / rows - 0.4f, 2 * half + 0.8f, h / rows + 0.8f, Yard.hex("1A1614"));
        }
        for (int i = 0; i < rows; i++) {
            float t = (i + 0.5f) / rows, half = 5f * (1f - t);
            rect(x - half, tipY - h + i * h / rows, 2 * half, h / rows + 0.2f, c);
        }
    }

    /** The home bug: a house-shaped index in ink, never a triangle (the caution glyph). */
    public void homeBug(float x, float y) {
        rect(x - 5f, y + 4f, 10f, 6f, Yard.hex("F3EEE2"));
        rect(x - 4.4f, y + 4.6f, 8.8f, 4.8f, Yard.INK);
        for (int i = 0; i < 4; i++) {
            float half = 1.2f + i * 1.1f;
            rect(x - half, y + i, 2 * half, 1.2f, Yard.INK);
        }
    }

    /** A level bar: a cream scale, ticks every tenth, the level as an ink bar with an orange edge. */
    public void level(float x, float y, float w, float h, float frac, float[][] zones, Color[] zoneColors) {
        rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2f, Yard.BAKELITE);
        rect(x, y, w, h, Yard.hex("F2ECDC"));
        if (zones != null) {
            for (int i = 0; i < zones.length; i++) rect(x + zones[i][0] * w, y + h - 3f, (zones[i][1] - zones[i][0]) * w, 3f, zoneColors[i]);
        }
        for (int i = 0; i <= 10; i++) rect(x + w * i / 10f - 0.4f, y, i % 5 == 0 ? 1.2f : 0.7f, i % 5 == 0 ? 6f : 3f, Yard.INK);
        float px = x + MathUtils.clamp(frac, 0f, 1f) * w;
        rect(x, y + h / 2f - 1.5f, px - x, 3f, Yard.INK, 0.75f);
        rect(px - 1.2f, y + 1f, 2.4f, h - 2f, Yard.ORANGE);
        glass(x, y, w, h);
    }

    /** A strip of typed paper with a faint shadow; draw its lines after with {@link #typed}. */
    public void paper(float x, float y, float w, float h) {
        rect(x + 1.5f, y + 2f, w, h, Yard.SHADOW, 0.5f);
        vgrad(x, y, w, h, Yard.hex("F3ECDA"), Yard.hex("E6DDC6"));
    }

    /** A typed line on paper. */
    public float typed(CharSequence s, float x, float baseline, float size, Color ink) {
        return text(YardFonts.Face.PAPER, size, s, x, baseline, ink);
    }

    /**
     * A toggle switch, its lever up or down; {@code guard} puts the red guard over it (an order given by
     * pressing twice: lifting the guard is the first press), shut or {@code guardOpen}.
     */
    public void toggle(float x, float y, boolean up, boolean guard, boolean guardOpen) {
        if (guard) rrect(x - 12f, y - 20f, 24f, 40f, 2.5f, Yard.ALU);
        circle(x, y, 9f, Yard.ALU);
        circle(x, y, 5.5f, Yard.hex("5C5852"));
        circle(x, y, 3.4f, Yard.hex("2A2725"));
        float d = up ? -1f : 1f;
        // The lever: a tapered bat from the boss to its tip
        for (int i = 0; i < 8; i++) {
            float t0 = i / 8f, w = 2.2f + 1.3f * t0;
            rect(x - w, y + d * 16f * t0 - (d < 0 ? 2f : 0f), 2 * w, 2.2f, Yard.hex("C9C4BA"));
        }
        circle(x, y + d * 16f, 3.5f, Yard.hex("3A3430"));
        if (guard) {
            if (guardOpen) {
                rrect(x - 11f, y - 44f, 22f, 24f, 3f, Yard.RED);
                rect(x - 11f, y - 23f, 22f, 4f, Yard.hex("7A221B"));
            } else {
                rrect(x - 11f, y - 19f, 22f, 38f, 3f, Yard.RED, 0.94f);
                glass(x - 11f, y - 19f, 22f, 38f);
                line(x - 7f, y - 13f, x + 7f, y - 13f, 1.5f, Yard.hex("7A221B"));
            }
        }
    }

    /** A square rubber button with a sign on it (the scope's minus and plus). */
    public void keyButton(float x, float y, float w, float h, String sign) {
        rrect(x + 1f, y + 2f, w, h, 3f, Yard.SHADOW, 0.6f);
        rrect(x, y, w, h, 3f, Yard.BAKELITE);
        rrect(x + 2f, y + 1.5f, w - 4f, h - 6f, 2f, Yard.hex("3A3430"));
        textC(YardFonts.Face.FLAP, 17f, sign, x + w / 2f, y + h / 2f + 3.5f, Yard.INK_LIGHT);
    }

    /** A strip of fixed-legend windows with a pointer under {@code sel} (the hull estimate's four bands). */
    public void bands(float x, float y, float w, String[] names, int sel) {
        float cw = w / names.length;
        rrect(x - 3f, y - 3f, w + 6f, 24f, 2f, Yard.BAKELITE);
        for (int i = 0; i < names.length; i++) {
            boolean on = i == sel;
            rect(x + i * cw + 0.5f, y, cw - 1f, 18f, on ? Yard.hex("F2ECDC") : Yard.hex("8F877A"));
            textC(YardFonts.Face.LEGEND, 12f, names[i], x + i * cw + cw / 2f, y + 13.3f, on ? Yard.INK : Yard.hex("3F3A33"));
        }
        if (sel >= 0) {
            float px = x + sel * cw + cw / 2f;
            for (int i = 0; i < 6; i++) {
                float half = 6f * (i + 0.5f) / 6f;
                rect(px - half, y + 18f + i * 7f / 6f, 2 * half, 7f / 6f + 0.15f, Yard.ORANGE);
            }
        }
    }

    /** A paper tag on a string: a contract on a target, a slip in the rack. */
    public void paperTag(float x, float y, float w, String t) {
        paper(x, y, w, 20f);
        circle(x + 7f, y + 10f, 2.6f, Yard.hex("4A3F36"));
        typed(t, x + 14f, y + 14.5f, 13f, Yard.PAPER_INK);
    }

    /**
     * A line of tube text with keys in it: each segment is either text, drawn in {@code c}, or a key
     * (a segment starting with {@link #KEY}), drawn as a reversed block. Returns the line's width.
     */
    public float tubeLine(float x, float baseline, float size, Color c, String... segs) {
        float lx = x;
        for (String seg : segs) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.charAt(0) == KEY) {
                lx += tubeKey(seg.substring(1), lx, baseline, size, c) + 6f;
            } else {
                lx += tube(seg, lx, baseline, size, c, false);
            }
        }
        return lx - x;
    }

    /** Marks a {@link #tubeLine} segment as a key. */
    public static final char KEY = '\u0001';

    /** The width {@link #tubeLine} would take. */
    public float tubeLineWidth(float size, String... segs) {
        float w = 0f;
        for (String seg : segs) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.charAt(0) == KEY) w += width(YardFonts.Face.TUBE_BOLD, size, seg.substring(1)) + 8f + 6f;
            else w += width(YardFonts.Face.TUBE, size, seg);
        }
        return w;
    }

    /** A line with round joins: segments with a disc at each end. */
    public void polyline(float[] pts, boolean closed, float width, Color c, float a) {
        int n = pts.length / 2;
        for (int i = 0; i < n - (closed ? 0 : 1); i++) {
            int j = (i + 1) % n;
            line(pts[2 * i], pts[2 * i + 1], pts[2 * j], pts[2 * j + 1], width, c, a);
        }
        if (width > 1.1f) for (int i = 0; i < n; i++) circle(pts[2 * i], pts[2 * i + 1], width / 2f, c, a);
    }

    /** A dashed line: {@code dash} on, {@code gap} off. */
    public void dashed(float x1, float y1, float x2, float y2, float width, float dash, float gap, Color c, float a) {
        float len = (float) Math.hypot(x2 - x1, y2 - y1);
        if (len < 1e-3f) return;
        float ux = (x2 - x1) / len, uy = (y2 - y1) / len;
        for (float d = 0f; d < len; d += dash + gap) {
            float e = Math.min(len, d + dash);
            line(x1 + ux * d, y1 + uy * d, x1 + ux * e, y1 + uy * e, width, c, a);
        }
    }

    /** Fills a convex polygon, by rows. */
    public void fillConvex(float[] pts, Color c, float a) {
        int n = pts.length / 2;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, pts[2 * i + 1]);
            maxY = Math.max(maxY, pts[2 * i + 1]);
        }
        float step = Math.max(0.5f, (maxY - minY) / 24f);
        for (float y = minY; y < maxY; y += step) {
            float yc = Math.min(maxY, y + step / 2f), lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ax = pts[2 * i], ay = pts[2 * i + 1], bx = pts[2 * j], by = pts[2 * j + 1];
                if ((ay <= yc && by >= yc) || (by <= yc && ay >= yc)) {
                    float t = Math.abs(by - ay) < 1e-4f ? 0f : (yc - ay) / (by - ay);
                    float xx = ax + (bx - ax) * t;
                    lo = Math.min(lo, Math.min(xx, Math.abs(by - ay) < 1e-4f ? Math.min(ax, bx) : xx));
                    hi = Math.max(hi, Math.max(xx, Math.abs(by - ay) < 1e-4f ? Math.max(ax, bx) : xx));
                }
            }
            if (hi > lo) rect(lo, y, hi - lo, Math.min(step + 0.2f, maxY - y), c, a);
        }
    }

    @Override
    public void dispose() {
        fonts.dispose();
        shapesOn = null;
        barber.dispose();
        hatch.dispose();
        hazard.dispose();
    }
}
