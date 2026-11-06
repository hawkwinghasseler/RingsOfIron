package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * FILAMENT's drawing kit: the 90s vacuum-fluorescent look (documentation/ui-overhaul/style-vfd.md)
 * drawn from geometry, as the mockups' renderer ({@code documentation/ui-overhaul/vfd/src/vfd.js})
 * draws it, so each segment and dot can be ghosted, dimmed or lit on its own and the game matches the
 * mockups.
 *
 * <p>A HUD built on it is a {@link Painter}: one method that draws everything with the calls below.
 * {@link #render} runs it once per layer, and each call draws only what belongs to the layer being
 * drawn: unlit ghosts, glass, bezels and printed silk straight onto the screen; lit phosphor into a
 * buffer that glows ({@link Glow}); the projected HUD over the world, glowing, under the hardware;
 * letters cut out of lit blocks, darkened again after the glow; and each window's filaments and
 * glare last. The painter must draw the same things in every layer of a frame, so it should lay out
 * from data read once per frame, not read the game as it draws.
 *
 * <p>Coordinates are the mockups': UI units from the top-left of the stage, y down. Text sits on its
 * baseline. Options for each call come from {@link #o()}, a shared set reset on each call to it, so a
 * call reads as {@code f.seg7(x, y, 18, "12", f.o().cells(3).color(Phosphor.AMBER))}.
 */
public final class Filament implements Disposable {

    /** Draws a whole HUD with the calls on {@code f}; run once per layer by {@link #render}. */
    public interface Painter {
        void paint(Filament f);
    }

    /** Which cells of a bar or ring are lit: their colour for cell {@code i} of {@code n}. */
    public interface Zone {
        Phosphor color(int i, int n);
    }

    /** A ring's cells: the drive level of cell {@code i} of {@code n}, or NaN to leave it unlit. */
    public interface RingLevels {
        float level(int i, int n);
    }

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    /** The status lamp beside a key: off, or lit in its colour. */
    public enum Led {
        GREEN(90, 255, 120), AMBER(255, 170, 40), RED(255, 60, 50);

        final float r, g, b;

        Led(int r, int g, int b) {
            this.r = r / 255f;
            this.g = g / 255f;
            this.b = b / 255f;
        }
    }

    /** The cap colour of a lamp key (the master warning and caution). */
    public enum Lamp {
        RED(255, 52, 36), AMBER(255, 138, 18), ZINC(70, 246, 204);

        final float r, g, b;

        Lamp(int r, int g, int b) {
            this.r = r / 255f;
            this.g = g / 255f;
            this.b = b / 255f;
        }
    }

    /** The layers {@link #render} draws, in order. */
    enum Pass {
        /** Counts what the frame holds, draws nothing. */
        PROBE,
        /** The projected HUD, over the world. */
        PROJECTED,
        /** Bezels, glass, keys, printed silk, the LCD, and every unlit element's ghost. */
        BASE,
        /** Lit phosphor behind the glass. */
        GLASS,
        /** Letters cut out of lit blocks, darkened again after the glow. */
        CUT,
        /** Lamp-key legends, printed over their own glow. */
        LATE
    }

    // ------------------------------------------------------------------ options

    /** Options for one call. Get it reset from {@link Filament#o()}; every setter returns it. */
    public static final class Opt {
        Phosphor color;
        float level;
        int cells;
        Align align;
        boolean ghost, lit, proj, box, vertical, taper, dpGhost, noDp, ghostOnly, skipFirst, upper, center, glare, pressed, backlight, knock;
        float aspect, thick, slant, spacing, pitch, dot, cellW, size, condense, track, gap, peak, k, r, stroke, step,
                radius, shadowY, start, span, gapDeg;
        int invFrom, invTo, filaments, colons;
        Zone zone;
        Color ink;
        Led led;
        boolean ledOn;

        Opt reset() {
            color = Phosphor.ZINC;
            level = Drive.NORM;
            cells = -1;
            align = null;
            ghost = true;
            lit = true;
            proj = box = vertical = taper = dpGhost = noDp = ghostOnly = skipFirst = center = pressed = backlight = knock = false;
            upper = true;
            glare = true;
            aspect = thick = slant = spacing = size = condense = track = gap = peak = k = stroke = step = radius = shadowY = Float.NaN;
            pitch = 2f;
            dot = 0.8f;
            cellW = 6f;
            r = 0f;
            start = 0f;
            span = 360f;
            gapDeg = 1.8f;
            invFrom = invTo = -1;
            filaments = -1;
            colons = 0;
            zone = null;
            ink = null;
            led = null;
            ledOn = false;
            return this;
        }

        public Opt color(Phosphor c) { color = c; return this; }
        public Opt amber() { color = Phosphor.AMBER; return this; }
        public Opt red() { color = Phosphor.RED; return this; }
        public Opt level(float l) { level = l; return this; }
        public Opt dim() { level = Drive.DIM; return this; }
        public Opt norm() { level = Drive.NORM; return this; }
        public Opt full() { level = Drive.FULL; return this; }
        /** A fixed field of {@code n} cells, padded with blanks. */
        public Opt cells(int n) { cells = n; return this; }
        public Opt left() { align = Align.LEFT; return this; }
        public Opt right() { align = Align.RIGHT; return this; }
        public Opt centre() { align = Align.CENTER; return this; }
        public Opt align(Align a) { align = a; return this; }
        /** Leave the unlit elements' ghosts out. */
        public Opt noGhost() { ghost = false; return this; }
        /** Lit, or only the ghost (a legend that is off). */
        public Opt lit(boolean on) { lit = on; return this; }
        /** On the projected HUD over the world, not behind the glass. */
        public Opt proj() { proj = true; return this; }
        public Opt proj(boolean on) { proj = on; return this; }
        public Opt aspect(float a) { aspect = a; return this; }
        public Opt thick(float t) { thick = t; return this; }
        public Opt slant(float degrees) { slant = degrees; return this; }
        public Opt spacing(float s) { spacing = s; return this; }
        /** Dot pitch of the dot matrix, in units. */
        public Opt pitch(float p) { pitch = p; return this; }
        public Opt dotSize(float d) { dot = d; return this; }
        public Opt cellW(float w) { cellW = w; return this; }
        /** Cells {@code from} to {@code to} (exclusive) shown inverse: lit block, dark letters. */
        public Opt inverse(int from, int to) { invFrom = from; invTo = to; return this; }
        /** Dark letters (or an icon) cut out of a block already lit under them, with no block or ghost of their own. */
        public Opt knock() { knock = true; return this; }
        public Opt size(float s) { size = s; return this; }
        public Opt condense(float c) { condense = c; return this; }
        public Opt track(float t) { track = t; return this; }
        /** A lit box round a legend. */
        public Opt box() { box = true; return this; }
        public Opt vertical() { vertical = true; return this; }
        public Opt gap(float g) { gap = g; return this; }
        /** A bar's peak-hold mark, as a share of its length. */
        public Opt peak(float p) { peak = p; return this; }
        public Opt taper() { taper = true; return this; }
        public Opt zone(Zone z) { zone = z; return this; }
        /** Ghost strength, as a multiple of the ghost level. */
        public Opt k(float strength) { k = strength; return this; }
        /** Corner radius. */
        public Opt r(float radius) { r = radius; return this; }
        public Opt noDp() { noDp = true; return this; }
        public Opt dpGhost() { dpGhost = true; return this; }
        public Opt stroke(float s) { stroke = s; return this; }
        public Opt step(float s) { step = s; return this; }
        public Opt skipFirst() { skipFirst = true; return this; }
        /** Only the ghost, never lit (a blank in a fixed field). */
        public Opt ghostOnly() { ghostOnly = true; return this; }
        /** Printed as written, not folded to capitals. */
        public Opt asWritten() { upper = false; return this; }
        public Opt centred() { center = true; return this; }
        public Opt filaments(int n) { filaments = n; return this; }
        public Opt noGlare() { glare = false; return this; }
        public Opt radius(float rr) { radius = rr; return this; }
        public Opt shadowY(float y) { shadowY = y; return this; }
        /** A ring's first cell, in degrees from straight up, clockwise. */
        public Opt start(float degrees) { start = degrees; return this; }
        public Opt span(float degrees) { span = degrees; return this; }
        public Opt gapDeg(float degrees) { gapDeg = degrees; return this; }
        /** Colons in a seven-segment field, for {@link Filament#seg7Width}. */
        public Opt colons(int n) { colons = n; return this; }
        /** The ink of printed text (silk, LCD), in place of its default. */
        public Opt ink(Color c) { ink = c; return this; }
        public Opt led(Led l, boolean on) { led = l; ledOn = on; return this; }
        public Opt pressed() { pressed = true; return this; }
        public Opt backlight() { backlight = true; return this; }

        float or(float v, float fallback) {
            return Float.isNaN(v) ? fallback : v;
        }
    }

    // ------------------------------------------------------------------ state

    private static final float D = MathUtils.degreesToRadians;
    private static final int MAX_CELLS = 128;

    private final Shapes shapes = new Shapes();
    private final Dots dots;
    private final Type type = new Type();
    private final Glow glow;
    private final Opt opt = new Opt();
    private final Array<Module> modules = new Array<>();
    private final Array<Module> modulePool = new Array<>();
    private final float[] poly = new float[Shapes.MAX_POINTS * 2];
    private final float[] polyColors = new float[Shapes.MAX_POINTS];
    private final float[] clipped = new float[Shapes.MAX_POINTS * 2];
    private final char[] cellChars = new char[MAX_CELLS];
    private final boolean[] cellDp = new boolean[MAX_CELLS];
    private final boolean[] cellColon = new boolean[MAX_CELLS];
    private final float[] cellX = new float[MAX_CELLS];
    private final Color tmp = new Color();

    Pass pass = Pass.BASE;
    /**
     * Unpowered: while set, nothing lights and every element shows its ghost, so a dead module still
     * shows what it would hold (style-vfd.md, section 9.5). Set it round the calls for a dead module.
     */
    public boolean dark;
    private Batch batch;
    private float width, height, unitsPx = 1f;
    /** The origin of what is drawn now, on the stage ({@link #origin}). */
    private float ox, oy;
    private int projected, cuts, lates;
    private boolean erasing;
    /** Strength of the control grid's mesh over lit phosphor; less at small scales, where it would hide the dots. */
    private float mesh = 0.3f;

    public Filament() {
        dots = new Dots();
        glow = new Glow();
    }

    /** The options for the next call, reset to their defaults. */
    public Opt o() {
        return opt.reset();
    }

    /** UI units across the stage. */
    public float width() {
        return width;
    }

    /** UI units down the stage. */
    public float height() {
        return height;
    }

    /** Whether the lit layers glow (false where the shaders did not compile). */
    public boolean glows() {
        return glow.available();
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Draws a frame: runs {@code painter} once per layer into {@code batch}, which must be drawing with
     * the stage's projection (UI units, y up, the whole stage {@code width} x {@code height}), and is
     * drawing again after. {@code unitsPx} is screen pixels per UI unit.
     */
    public void render(Batch batch, float width, float height, float unitsPx, Painter painter) {
        this.batch = batch;
        this.width = width;
        this.height = height;
        this.unitsPx = unitsPx;
        type.setScale(unitsPx);
        shapes.begin(batch, dots.texture, dots.whiteU, dots.whiteV, 1f / unitsPx, height);
        mesh = unitsPx >= 2f ? 0.3f : 0.12f + 0.18f * MathUtils.clamp(unitsPx - 1f, 0f, 1f);
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcA = batch.getBlendSrcFuncAlpha(), dstA = batch.getBlendDstFuncAlpha();
        batch.setColor(Color.WHITE);
        modules.clear();
        projected = cuts = lates = 0;
        boolean wasDark = dark;

        run(Pass.PROBE, painter);
        if (projected > 0) lit(Pass.PROJECTED, painter, 0.8f);
        run(Pass.BASE, painter);
        lit(Pass.GLASS, painter, 1f);
        if (cuts > 0 && glow.available()) run(Pass.CUT, painter);
        finishModules();
        if (lates > 0) run(Pass.LATE, painter);

        dark = wasDark;
        batch.setBlendFunctionSeparate(src, dst, srcA, dstA);
    }

    private void run(Pass p, Painter painter) {
        pass = p;
        dark = false;
        origin(0f, 0f);
        painter.paint(this);
    }

    /**
     * Moves the origin of everything drawn after this call to {@code x, y} on the stage (y down), so a
     * group laid out on the mockups' 1280 x 800 grid can be anchored to a corner or the middle of a
     * bigger stage. Each layer starts at the stage's top left.
     */
    public void origin(float x, float y) {
        ox = x;
        oy = y;
        shapes.origin(x, y);
        type.origin(x, y);
    }

    /** The origin {@link #origin} set: where this drawing's 0, 0 sits on the stage. */
    public float originX() {
        return ox;
    }

    public float originY() {
        return oy;
    }

    /** A lit layer: into the glow buffer and added to the screen with its glow, or straight on where nothing glows. */
    private void lit(Pass p, Painter painter, float strength) {
        if (!glow.available()) {
            run(p, painter);
            return;
        }
        batch.end();
        glow.begin();
        batch.begin();
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        erasing = false;
        run(p, painter);
        batch.end();
        glow.end(unitsPx, 0.6f * strength, 0.85f * strength, 0.42f * strength, p == Pass.GLASS ? mesh : 0f);
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Whether lit content on the projected layer ({@code proj}) or behind the glass draws now. */
    private boolean litNow(boolean proj) {
        if (dark && !proj) return false;
        if (pass == Pass.PROBE) {
            if (proj) projected++;
            return false;
        }
        return proj ? pass == Pass.PROJECTED : pass == Pass.GLASS;
    }

    private boolean baseNow() {
        return pass == Pass.BASE;
    }

    private void erase(boolean on) {
        if (on == erasing) return;
        erasing = on;
        if (on) batch.setBlendFunctionSeparate(GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA);
        else batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ------------------------------------------------------------------ hardware

    /** A bezel plate: satin black moulded plastic, with a soft shadow, a rim and fine brushing. */
    public void rack(float x, float y, float w, float h, Opt o) {
        if (!baseNow()) return;
        float r = o.or(o.radius, 6f), sy = o.or(o.shadowY, -2f);
        shadow(x, y + sy, w, h, r, 0.6f, 14f);
        float[] stops = {0f, Ink.bits(Ink.RACK_TOP, 1f), 0.12f, Ink.bits(Ink.RACK_MID, 1f), 1f, Ink.bits(Ink.RACK_BOTTOM, 1f)};
        shapes.roundRectGradient(x, y, w, h, r, stops);
        shapes.roundRectStroke(x + 0.5f, y + 0.5f, w - 1f, h - 1f, r, 1f, Ink.bits(Ink.BEZEL_HI, 1f));
        float brush = Color.toFloatBits(1f, 1f, 1f, 0.035f);
        for (float yy = y + 3f; yy < y + h - 2f; yy += 2f) shapes.rect(x + 4f, yy, w - 8f, 0.5f, brush);
    }

    public void rack(float x, float y, float w, float h) {
        rack(x, y, w, h, o());
    }

    /** A round housing, for a scope that stands out of a rack. */
    public void pod(float cx, float cy, float r) {
        if (!baseNow()) return;
        shadow(cx - r, cy - r, 2f * r, 2f * r, r, 0.65f, 16f);
        radialDisc(cx, cy, r, cx, cy - r * 0.4f, Color.toFloatBits(0x20 / 255f, 0x21 / 255f, 0x24 / 255f, 1f),
                Color.toFloatBits(0x10 / 255f, 0x11 / 255f, 0x13 / 255f, 1f));
        shapes.arc(cx, cy, r - 0.5f, 279f, 441f, 1f, Ink.bits(Ink.BEZEL_HI, 1f));
    }

    /**
     * A bare bezel plate standing free of the racks, {@code pad} round the box it holds: a group of
     * windows and keys over the world.
     */
    public void plate(float x, float y, float w, float h, float pad, float radius) {
        if (!baseNow()) return;
        float px = x - pad, py = y - pad, pw = w + 2f * pad, ph = h + 2f * pad;
        shadow(px, py + 3f, pw, ph, radius, 0.75f, 12f);
        shapes.roundRectGradient(px, py, pw, ph, radius, new float[] {0f, Ink.bits(Ink.PLATE_TOP, 1f), 1f, Ink.bits(Ink.PLATE_BOTTOM, 1f)});
        shapes.roundRectStroke(px + 0.4f, py + 0.4f, pw - 0.8f, ph - 0.8f, radius, 0.8f, Ink.bits(Ink.PLATE_RIM, 1f));
    }

    /** A slab: a free-standing window with a thin bezel of its own. */
    public void slab(float x, float y, float w, float h, float pad, int filaments, boolean glare) {
        plate(x, y, w, h, pad, 5f);
        Opt o = o().radius(2.5f).filaments(filaments);
        if (!glare) o.noGlare();
        module(x, y, w, h, o);
    }

    /** A key cap: a small rubber key with its legend printed on it, {@code h} tall. Returns its width. */
    public float keycap(float x, float y, CharSequence label, float h, float size, boolean lit) {
        Type.Face face = type.face(size, 0.1f);
        CharSequence s = type.upper(label);
        float lw = type.width(face, s, 0.86f), w = Math.max(h, lw + 7f);
        if (baseNow()) {
            shapes.roundRect(x - 0.8f, y - 0.6f, w + 1.6f, h + 2.2f, 2.6f, Color.toFloatBits(2 / 255f, 2 / 255f, 3 / 255f, 1f));
            float top = lit ? Color.toFloatBits(0x4a / 255f, 0x4d / 255f, 0x52 / 255f, 1f) : Color.toFloatBits(0x3d / 255f, 0x40 / 255f, 0x45 / 255f, 1f);
            shapes.roundRectGradient(x, y, w, h, 2.2f, new float[] {0f, top, 1f, Color.toFloatBits(0x22 / 255f, 0x24 / 255f, 0x27 / 255f, 1f)});
            type.draw(batch, face, s, x + w / 2f - lw / 2f, y + h / 2f + size * 0.37f, 0.86f, Ink.bits(Ink.KEYCAP_LEGEND, 1f), height);
        }
        return w;
    }

    /** How wide {@link #keycap} sets {@code label}. */
    public float keycapWidth(CharSequence label, float h, float size) {
        return Math.max(h, type.width(type.face(size, 0.1f), type.upper(label), 0.86f) + 7f);
    }

    /**
     * A console rack whose top steps: columns side by side from {@code xs[i]} to {@code xs[i + 1]},
     * each standing up to {@code tops[i]}, all down to {@code bottom}; a column lower than the one
     * beside it is a step in the moulding (a folded window sunk into the rack). Its outer corners are
     * rounded by {@code radius}; its shadow falls up, onto the world above it.
     */
    public void rackColumns(float[] xs, float[] tops, float bottom, float radius) {
        if (!baseNow()) return;
        int n = tops.length;
        float y0 = Float.MAX_VALUE;
        for (float t : tops) y0 = Math.min(y0, t);
        for (int i = 0; i < n; i++) shadow(xs[i], tops[i] - 3f, xs[i + 1] - xs[i], bottom - tops[i], radius, 0.7f, 16f);
        float top = Ink.bits(Ink.RACK_TOP, 1f), mid = Ink.bits(Ink.RACK_MID, 1f), low = Ink.bits(Ink.RACK_BOTTOM, 1f);
        float span = bottom - y0, midY = y0 + span * 0.1f;
        for (int i = 0; i < n; i++) {
            // Each column overlaps its neighbours by its radius, so no rounded corner opens a notch at a step
            float x0 = xs[i] - (i > 0 ? radius : 0f), x1 = xs[i + 1] + (i < n - 1 ? radius : 0f);
            float t = tops[i], b = bottom + radius, h = b - t;
            float atTop = t <= midY ? Shapes.lerpColor(top, mid, (t - y0) / (midY - y0)) : Shapes.lerpColor(mid, low, (t - midY) / (bottom - midY));
            float[] stops = t < midY
                    ? new float[] {0f, atTop, (midY - t) / h, mid, (bottom - t) / h, low, 1f, low}
                    : new float[] {0f, atTop, (bottom - t) / h, low, 1f, low};
            shapes.roundRectGradient(x0, t, x1 - x0, h, radius, stops);
        }
        // The rim along the top and down each step, and the brushing across the whole face
        float rim = Ink.bits(Ink.RACK_RIM, 1f);
        for (int i = 0; i < n; i++) {
            float l = xs[i], r = xs[i + 1], t = tops[i] + 0.5f;
            boolean roundL = i == 0 || tops[i - 1] > tops[i], roundR = i == n - 1 || tops[i + 1] > tops[i];
            shapes.line(l + (roundL ? radius : 0f), t, r - (roundR ? radius : 0f), t, 1f, rim);
            if (roundL) shapes.arc(l + radius, t + radius - 0.5f, radius - 0.5f, 270f, 360f, 1f, rim);
            if (roundR) shapes.arc(r - radius, t + radius - 0.5f, radius - 0.5f, 0f, 90f, 1f, rim);
            if (i == 0) shapes.line(l + 0.5f, t + radius, l + 0.5f, bottom, 1f, rim);
            if (i == n - 1) shapes.line(r - 0.5f, t + radius, r - 0.5f, bottom, 1f, rim);
            if (i > 0 && tops[i - 1] > tops[i]) shapes.line(l + 0.5f, t + radius, l + 0.5f, tops[i - 1], 1f, rim);
            if (i < n - 1 && tops[i + 1] > tops[i]) shapes.line(r - 0.5f, t + radius, r - 0.5f, tops[i + 1], 1f, rim);
        }
        float brush = Color.toFloatBits(1f, 1f, 1f, 0.035f);
        for (float yy = y0 + 3f; yy < bottom - 2f; yy += 2f) {
            float from = Float.NaN, to = Float.NaN;
            for (int i = 0; i <= n; i++) {
                boolean in = i < n && tops[i] + 3f <= yy;
                if (in && Float.isNaN(from)) from = xs[i] + (i == 0 ? 3f : 0f);
                if (in) to = xs[i + 1] - (i == n - 1 ? 3f : 0f);
                if (!in && !Float.isNaN(from)) {
                    shapes.rect(from, yy, to - from, 0.5f, brush);
                    from = Float.NaN;
                }
            }
        }
    }

    /** A display window: smoked glass set into the bezel, with filaments across it. */
    public void module(float x, float y, float w, float h, Opt o) {
        int filaments = o.filaments >= 0 ? o.filaments : Math.max(1, Math.round(h / 34f));
        float r = o.or(o.radius, 3f);
        boolean glare = o.glare;
        if (!baseNow()) return;
        shapes.roundRect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, r + 1.5f, Color.toFloatBits(0f, 0f, 0f, 1f));
        shapes.roundRectGradient(x, y, w, h, r, new float[] {0f, Ink.bits(Ink.GLASS_TOP, 1f), 1f, Ink.bits(Ink.GLASS, 1f)});
        shapes.line(x + r, y + 0.6f, x + w - r, y + 0.6f, 0.6f, Color.toFloatBits(1f, 1f, 1f, 0.06f));
        Module m = modulePool.size > 0 ? modulePool.pop() : new Module();
        m.set(x + ox, y + oy, w, h, r, filaments, false, glare);
        modules.add(m);
    }

    public void module(float x, float y, float w, float h) {
        module(x, y, w, h, o());
    }

    /** A round display window, for a scope. */
    public void roundModule(float cx, float cy, float rad, Opt o) {
        int filaments = o.filaments >= 0 ? o.filaments : 4;
        if (!baseNow()) return;
        shapes.disc(cx, cy, rad + 1.5f, Color.toFloatBits(0f, 0f, 0f, 1f));
        radialDisc(cx, cy, rad, cx, cy - rad * 0.5f, Ink.bits(Ink.GLASS_TOP, 1f), Ink.bits(Ink.GLASS, 1f));
        Module m = modulePool.size > 0 ? modulePool.pop() : new Module();
        m.set(cx - rad + ox, cy - rad + oy, 2f * rad, 2f * rad, rad, filaments, true, true);
        modules.add(m);
    }

    /** A soft drop shadow under a box: black at {@code alpha}, fading over {@code blur} units. */
    private void shadow(float x, float y, float w, float h, float r, float alpha, float blur) {
        float spread = blur * 0.5f;
        int n = shapes.roundRectPoints(x - spread, y - spread, w + 2f * spread, h + 2f * spread, r + spread, poly);
        float solid = Color.toFloatBits(0f, 0f, 0f, alpha * 0.55f), clear = Color.toFloatBits(0f, 0f, 0f, 0f);
        // a fan from the middle: solid inside the box, clear at the spread's edge
        float cx = x + w / 2f, cy = y + h / 2f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = poly[2 * i], ay = poly[2 * i + 1], bx = poly[2 * j], by = poly[2 * j + 1];
            // the inner point of each edge: pulled back towards the middle by the spread
            float iax = towards(ax, cx, spread * 2f), iay = towards(ay, cy, spread * 2f);
            float ibx = towards(bx, cx, spread * 2f), iby = towards(by, cy, spread * 2f);
            shapes.quad(iax, iay, solid, ax, ay, clear, bx, by, clear, ibx, iby, solid);
            shapes.triangle(cx, cy, solid, iax, iay, solid, ibx, iby, solid);
        }
    }

    private static float towards(float v, float to, float by) {
        if (Math.abs(to - v) <= by) return to;
        return v + Math.signum(to - v) * by;
    }

    /** A disc filled from {@code inner} at the focus {@code fx, fy} to {@code outer} at the rim. */
    private void radialDisc(float cx, float cy, float r, float fx, float fy, float inner, float outer) {
        int n = MathUtils.clamp(MathUtils.ceil(MathUtils.PI2 * r * unitsPx / 3f), 16, Shapes.MAX_POINTS);
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            shapes.triangle(fx, fy, inner, cx + MathUtils.cos(a0) * r, cy + MathUtils.sin(a0) * r, outer,
                    cx + MathUtils.cos(a1) * r, cy + MathUtils.sin(a1) * r, outer);
        }
    }

    // ------------------------------------------------------------------ printed silk

    /** A legend printed on the bezel: not lit, condensed and tracked; returns its width. */
    public float silk(float x, float y, CharSequence text, Opt o) {
        float size = o.or(o.size, 12f), condense = o.or(o.condense, 0.86f), track = o.or(o.track, 0.12f);
        Color ink = o.ink != null ? o.ink : Ink.SILK;
        Align align = o.align;
        CharSequence s = o.upper ? type.upper(text) : text;
        Type.Face face = type.face(size, track);
        float w = type.width(face, s, condense);
        if (baseNow()) type.draw(batch, face, s, aligned(x, w, align), y, condense, Ink.bits(ink, 1f), height);
        return w;
    }

    public float silk(float x, float y, CharSequence text) {
        return silk(x, y, text, o());
    }

    /** A printed rule. */
    public void silkRule(float x0, float y, float x1, Opt o) {
        if (!baseNow()) return;
        Color ink = o.ink != null ? o.ink : Ink.SILK_DIM;
        shapes.line(x0, y, x1, y, o.or(o.stroke, 0.6f), Ink.bits(ink, 1f));
    }

    /** How wide a silk legend sets. */
    public float silkWidth(CharSequence text, Opt o) {
        Type.Face face = type.face(o.or(o.size, 12f), o.or(o.track, 0.12f));
        return type.width(face, o.upper ? type.upper(text) : text, o.or(o.condense, 0.86f));
    }

    private static float aligned(float x, float w, Align align) {
        if (align == Align.RIGHT) return x - w;
        if (align == Align.CENTER) return x - w / 2f;
        return x;
    }

    // ------------------------------------------------------------------ legends in phosphor

    /**
     * An annunciator legend: phosphor laid down in a fixed shape, so its ghost shows while it is off.
     * Returns its width.
     */
    public float legend(float x, float y, CharSequence text, Opt o) {
        float size = o.or(o.size, 13f), condense = o.or(o.condense, 0.8f), track = o.or(o.track, 0.06f);
        Type.Face face = type.face(size, track);
        float w = type.width(face, text, condense);
        if (pass == Pass.PROBE) {
            if (o.lit && o.proj) projected++;
            return w;
        }
        float ax = aligned(x, w, o.align);
        if (baseNow() && o.ghost && !o.proj) {
            float g = o.color.ghost(1.3f);
            type.draw(batch, face, text, ax, y, condense, g, height);
            if (o.box) box(ax, y, w, size, g);
        }
        if (o.lit && litNow(o.proj)) {
            float c = o.color.lit(o.level);
            type.draw(batch, face, text, ax, y, condense, c, height);
            if (o.box) box(ax, y, w, size, c);
        }
        return w;
    }

    public float legend(float x, float y, CharSequence text) {
        return legend(x, y, text, o());
    }

    private void box(float ax, float y, float w, float size, float color) {
        float lw = 0.85f, x0 = ax - 2.5f, x1 = ax + w + 2.5f, y0 = y - size * 0.86f, y1 = y + size * 0.28f;
        shapes.rect(x0, y0, x1 - x0, lw, color);
        shapes.rect(x0, y1 - lw, x1 - x0, lw, color);
        shapes.rect(x0, y0 + lw, lw, y1 - y0 - 2f * lw, color);
        shapes.rect(x1 - lw, y0 + lw, lw, y1 - y0 - 2f * lw, color);
    }

    /** How wide a legend sets. */
    public float legendWidth(CharSequence text, Opt o) {
        Type.Face face = type.face(o.or(o.size, 13f), o.or(o.track, 0.06f));
        return type.width(face, text, o.or(o.condense, 0.8f));
    }

    /**
     * Dark letters cut out of a lit block: the block must be drawn first. Returns the text's width.
     */
    public float knockout(float x, float y, CharSequence text, Opt o) {
        float size = o.or(o.size, 13f), condense = o.or(o.condense, 0.8f), track = o.or(o.track, 0.06f);
        Type.Face face = type.face(size, track);
        float w = type.width(face, text, condense);
        if (dark) return w;
        float ax = aligned(x, w, o.align);
        if (pass == Pass.PROBE) {
            if (o.proj) projected++;
            else cuts++;
        } else if (litNow(o.proj)) {
            if (glow.capturing()) {
                erase(true);
                type.draw(batch, face, text, ax, y, condense, Color.toFloatBits(0f, 0f, 0f, 1f), height);
                erase(false);
            } else {
                type.draw(batch, face, text, ax, y, condense, Ink.bits(Ink.CUT, 0.92f), height);
            }
        } else if (pass == Pass.CUT && !o.proj) {
            type.draw(batch, face, text, ax, y, condense, Ink.bits(Ink.CUT, 0.8f), height);
        }
        return w;
    }

    // ------------------------------------------------------------------ seven segments

    /** Seven-segment digits {@code h} tall from {@code x, y} (top left); '.' and ':' join the cell before. Returns the width. */
    public float seg7(float x, float y, float h, CharSequence text, Opt o) {
        float aspect = o.or(o.aspect, 0.54f), thick = o.or(o.thick, 0.13f), slant = o.or(o.slant, 8f), spacing = o.or(o.spacing, 0.26f);
        float w = h * aspect, t = h * thick, gap = h * 0.022f, s = (float) Math.tan(slant * D);
        float adv = w + h * spacing;
        int count = 0;
        for (int i = 0; i < text.length() && count < MAX_CELLS; i++) {
            char ch = text.charAt(i);
            if (ch == '.' && count > 0) {
                cellDp[count - 1] = true;
                continue;
            }
            if (ch == ':' && count > 0) {
                cellColon[count - 1] = true;
                continue;
            }
            cellChars[count] = ch;
            cellDp[count] = false;
            cellColon[count] = false;
            count++;
        }
        int n = o.cells >= 0 ? Math.min(o.cells, MAX_CELLS) : count;
        // pad with blanks: on the left unless left-aligned
        if (count < n) {
            int pad = n - count;
            if (o.align != Align.LEFT) {
                for (int i = count - 1; i >= 0; i--) {
                    cellChars[i + pad] = cellChars[i];
                    cellDp[i + pad] = cellDp[i];
                    cellColon[i + pad] = cellColon[i];
                }
                for (int i = 0; i < pad; i++) {
                    cellChars[i] = ' ';
                    cellDp[i] = false;
                    cellColon[i] = false;
                }
            } else {
                for (int i = count; i < n; i++) {
                    cellChars[i] = ' ';
                    cellDp[i] = false;
                    cellColon[i] = false;
                }
            }
            count = n;
        }
        float colonW = h * 0.2f;
        float cx0 = x;
        for (int i = 0; i < count; i++) {
            cellX[i] = cx0;
            cx0 += adv + (cellColon[i] ? colonW : 0f);
        }
        float total = cx0 - x - h * spacing;
        boolean lit = litNow(o.proj), base = baseNow() && o.ghost;
        if (!lit && !base) return total;
        float litC = o.color.lit(o.level), ghostC = o.color.ghost(1f);
        for (int i = 0; i < count; i++) {
            float ox = cellX[i];
            int on = dark ? 0 : Rom.seg7(cellChars[i]);
            for (int k = 0; k < 7; k++) {
                boolean segOn = (on & (1 << k)) != 0;
                if (segOn ? !lit : !base) continue;
                seg7Segment(k, ox, y, w, h, t, gap, s);
                shapes.convex(poly, 6, segOn ? litC : ghostC);
            }
            if (!o.noDp) {
                boolean dpOn = cellDp[i] && !dark;
                if (dpOn ? lit : base && o.dpGhost) {
                    float dx = w + h * 0.06f;
                    shearBox(ox, y, h, s, dx, h - t, dx + t, h);
                    shapes.convex(poly, 4, dpOn ? litC : ghostC);
                }
            }
            if (cellColon[i]) {
                for (int c = 0; c < 2; c++) {
                    float cy = h * (c == 0 ? 0.3f : 0.7f);
                    float cxx = w + (adv - w + colonW) / 2f - t / 2f;
                    if (dark ? base : lit) {
                        shearBox(ox, y, h, s, cxx, cy - t / 2f, cxx + t, cy + t / 2f);
                        shapes.convex(poly, 4, dark ? ghostC : litC);
                    }
                }
            }
        }
        return total;
    }

    /** The width of an {@code n} cell seven-segment field {@code h} tall (with {@link Opt#colons}). */
    public float seg7Width(float h, int n, Opt o) {
        float aspect = o.or(o.aspect, 0.54f), spacing = o.or(o.spacing, 0.26f);
        return n * h * (aspect + spacing) - h * spacing + o.colons * h * 0.2f;
    }

    /** Segment {@code k} (a to g) of a seven-segment cell at {@code ox}, into {@link #poly} as a hexagon. */
    private void seg7Segment(int k, float ox, float y, float w, float h, float t, float gap, float s) {
        float ht = t / 2f;
        float x0, y0, x1, y1;
        switch (k) {
            case 0: x0 = ht; y0 = ht; x1 = w - ht; y1 = ht; break;            // a
            case 1: x0 = w - ht; y0 = ht; x1 = w - ht; y1 = h / 2f; break;    // b
            case 2: x0 = w - ht; y0 = h / 2f; x1 = w - ht; y1 = h - ht; break; // c
            case 3: x0 = ht; y0 = h - ht; x1 = w - ht; y1 = h - ht; break;    // d
            case 4: x0 = ht; y0 = h / 2f; x1 = ht; y1 = h - ht; break;        // e
            case 5: x0 = ht; y0 = ht; x1 = ht; y1 = h / 2f; break;            // f
            default: x0 = ht; y0 = h / 2f; x1 = w - ht; y1 = h / 2f; break;   // g
        }
        segPoly(x0, y0, x1, y1, t, gap, ox, y, h, s);
    }

    /**
     * A segment from one point to another, in a cell's own units: a stretched hexagon with pointed
     * ends, shortened by {@code gap} at each, sheared by the slant {@code s} and placed at {@code ox, y}.
     */
    private void segPoly(float x0, float y0, float x1, float y1, float t, float gap, float ox, float y, float h, float s) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
        float a = gap, b = len - gap, hw = t / 2f, tip = Math.min(hw, (b - a) / 2f);
        point(0, x0, y0, ux, uy, nx, ny, a, 0f, ox, y, h, s);
        point(1, x0, y0, ux, uy, nx, ny, a + tip, hw, ox, y, h, s);
        point(2, x0, y0, ux, uy, nx, ny, b - tip, hw, ox, y, h, s);
        point(3, x0, y0, ux, uy, nx, ny, b, 0f, ox, y, h, s);
        point(4, x0, y0, ux, uy, nx, ny, b - tip, -hw, ox, y, h, s);
        point(5, x0, y0, ux, uy, nx, ny, a + tip, -hw, ox, y, h, s);
    }

    private void point(int i, float x0, float y0, float ux, float uy, float nx, float ny, float along, float off,
                       float ox, float y, float h, float s) {
        float px = x0 + ux * along + nx * off, py = y0 + uy * along + ny * off;
        poly[2 * i] = ox + px + (h - py) * s;
        poly[2 * i + 1] = y + py;
    }

    /** A box in a cell's own units, sheared and placed, into {@link #poly}. */
    private void shearBox(float ox, float y, float h, float s, float x0, float y0, float x1, float y1) {
        poly[0] = ox + x0 + (h - y0) * s; poly[1] = y + y0;
        poly[2] = ox + x1 + (h - y0) * s; poly[3] = y + y0;
        poly[4] = ox + x1 + (h - y1) * s; poly[5] = y + y1;
        poly[6] = ox + x0 + (h - y1) * s; poly[7] = y + y1;
    }

    // ------------------------------------------------------------------ fourteen segments

    /** Fourteen-segment characters {@code h} tall, for words that must read as one glance. Returns the width. */
    public float seg14(float x, float y, float h, CharSequence text, Opt o) {
        float aspect = o.or(o.aspect, 0.6f), thick = o.or(o.thick, 0.1f), slant = o.or(o.slant, 8f), spacing = o.or(o.spacing, 0.24f);
        float w = h * aspect, t = h * thick, gap = h * 0.025f, s = (float) Math.tan(slant * D);
        float adv = w + h * spacing;
        int len = Math.min(text.length(), MAX_CELLS);
        int n = o.cells >= 0 ? Math.min(o.cells, MAX_CELLS) : len;
        int pad = Math.max(0, n - len);
        boolean right = o.align == Align.RIGHT;
        float total = n * adv - h * spacing;
        boolean lit = litNow(o.proj), base = baseNow() && o.ghost;
        if (!lit && !base) return total;
        float litC = o.color.lit(o.level), ghostC = o.color.ghost(1f);
        float hw = t / 2f, cx = w / 2f, cy = h / 2f, dt = t * 0.95f;
        for (int i = 0; i < n; i++) {
            int src = right ? i - pad : i;
            char ch = src >= 0 && src < len ? Character.toUpperCase(text.charAt(src)) : ' ';
            float ox = x + i * adv;
            int on = dark ? 0 : Rom.seg14(ch);
            for (int k = 0; k < 14; k++) {
                boolean segOn = (on & (1 << k)) != 0;
                if (segOn ? !lit : !base) continue;
                float x0, y0, x1, y1, th = t;
                boolean diag = false;
                switch (k) {
                    case 0: x0 = hw; y0 = hw; x1 = w - hw; y1 = hw; break;              // a
                    case 1: x0 = w - hw; y0 = hw; x1 = w - hw; y1 = cy; break;          // b
                    case 2: x0 = w - hw; y0 = cy; x1 = w - hw; y1 = h - hw; break;      // c
                    case 3: x0 = hw; y0 = h - hw; x1 = w - hw; y1 = h - hw; break;      // d
                    case 4: x0 = hw; y0 = cy; x1 = hw; y1 = h - hw; break;              // e
                    case 5: x0 = hw; y0 = hw; x1 = hw; y1 = cy; break;                  // f
                    case 6: x0 = hw; y0 = cy; x1 = cx; y1 = cy; break;                  // g1
                    case 7: x0 = cx; y0 = cy; x1 = w - hw; y1 = cy; break;              // g2
                    case 8: x0 = t * 1.25f; y0 = t * 1.25f; x1 = cx - t * 0.55f; y1 = cy - t * 0.55f; th = dt; diag = true; break;          // h
                    case 9: x0 = cx; y0 = hw; x1 = cx; y1 = cy; break;                  // i
                    case 10: x0 = w - t * 1.25f; y0 = t * 1.25f; x1 = cx + t * 0.55f; y1 = cy - t * 0.55f; th = dt; diag = true; break;     // j
                    case 11: x0 = t * 1.25f; y0 = h - t * 1.25f; x1 = cx - t * 0.55f; y1 = cy + t * 0.55f; th = dt; diag = true; break;    // k
                    case 12: x0 = cx; y0 = cy; x1 = cx; y1 = h - hw; break;             // l
                    default: x0 = w - t * 1.25f; y0 = h - t * 1.25f; x1 = cx + t * 0.55f; y1 = cy + t * 0.55f; th = dt; diag = true; break; // m
                }
                segPoly(x0, y0, x1, y1, th, diag ? gap * 0.6f : gap, ox, y, h, s);
                shapes.convex(poly, 6, segOn ? litC : ghostC);
            }
        }
        return total;
    }

    public float seg14Width(float h, int n, Opt o) {
        float aspect = o.or(o.aspect, 0.6f), spacing = o.or(o.spacing, 0.24f);
        return n * h * (aspect + spacing) - h * spacing;
    }

    // ------------------------------------------------------------------ dot matrix

    /**
     * Text on 5x7 cells, six dots a cell across, from {@code x, y} (top left of the first cell).
     * {@link Opt#cells} pads to a fixed field and cuts what will not fit; {@link Opt#inverse} lights the
     * cells' unlit dots instead. Returns the width.
     */
    public float dm(float x, float y, CharSequence text, Opt o) {
        float p = o.pitch, d = p * o.dot;
        int len = text.length();
        int n = o.cells >= 0 ? Math.min(o.cells, MAX_CELLS) : Math.min(len, MAX_CELLS);
        int pad = Math.max(0, n - len);
        boolean right = o.align == Align.RIGHT;
        float adv = p * o.cellW;
        float total = n * adv - p;
        boolean ghost = o.ghost && !o.proj && !o.knock;
        int inv0 = dark ? -1 : o.knock ? 0 : o.invFrom, inv1 = dark ? -1 : o.knock ? n : o.invTo;
        boolean inverse = inv0 >= 0 && inv1 > inv0;
        if (pass == Pass.PROBE) {
            if (o.proj && !dark) projected++;
            if (inverse && !o.proj) cuts++;
            return total;
        }
        boolean lit = litNow(o.proj), base = baseNow() && ghost, cut = pass == Pass.CUT && inverse && !o.proj;
        if (!lit && !base && !cut) return total;
        float litC = o.color.lit(o.level);
        if (lit && inverse && !o.knock) {
            float bx = x + inv0 * adv - p * 0.5f, bw = (inv1 - inv0) * adv;
            shapes.roundRect(bx, y - p, bw, 9f * p, p * 0.4f, litC);
        }
        float ghostC = o.color.ghost(0.8f);
        float dq = d * Dots.DOT_QUAD, off = (p - d) / 2f - (dq - d) / 2f;
        for (int i = 0; i < n; i++) {
            int src = right ? i - pad : i;
            char ch = src >= 0 && src < len ? text.charAt(src) : ' ';
            boolean flip = inverse && i >= inv0 && i < inv1;
            float cx = x + i * adv;
            if (base && !flip) {
                shapes.sprite(dots.texture, cx - p * 0.5f, y - p * 0.5f, 6f * p, 8f * p, dots.ghostU0, dots.ghostV0, dots.ghostU1, dots.ghostV1, ghostC);
            }
            if (!(lit || cut) || dark) continue;
            long g = Rom.glyph(ch);
            if (g == 0L) continue;
            if (flip) {
                if (lit && glow.capturing()) erase(true);
                float holeC = lit ? (glow.capturing() ? Color.toFloatBits(0f, 0f, 0f, 1f) : Ink.bits(Ink.CUT, 0.92f)) : Ink.bits(Ink.CUT, 0.8f);
                for (int r = 0; r < Rom.ROWS; r++) {
                    for (int c = 0; c < Rom.COLS; c++) {
                        if (Rom.lit(g, r, c)) shapes.rect(cx + c * p + p * 0.04f, y + r * p + p * 0.04f, p * 0.92f, p * 0.92f, holeC);
                    }
                }
                if (lit) erase(false);
            } else if (lit) {
                for (int r = 0; r < Rom.ROWS; r++) {
                    for (int c = 0; c < Rom.COLS; c++) {
                        if (Rom.lit(g, r, c)) {
                            shapes.sprite(dots.texture, cx + c * p + off, y + r * p + off, dq, dq, dots.dotU0, dots.dotV0, dots.dotU1, dots.dotV1, litC);
                        }
                    }
                }
            }
        }
        return total;
    }

    /** The width of an {@code n} cell dot-matrix field at {@link Opt#pitch}. */
    public float dmWidth(int n, Opt o) {
        return n * o.pitch * o.cellW - o.pitch;
    }

    /**
     * A graphic dot panel's own dots, unlit, every one ghosting through the glass: a field of them
     * {@code pitch} apart over {@code x, y, w, h}, the first dot centred half a pitch in from its top
     * left corner. One quad, however many dots; lit dots go over it with {@link #dot}.
     */
    public void dotField(float x, float y, float w, float h, float pitch, Opt o) {
        if (!baseNow() || w <= 0f || h <= 0f || pitch <= 0f) return;
        shapes.sprite(dots.field, x, y, w, h, 0f, 0f, w / pitch, h / pitch, o.color.ghost(o.or(o.k, 1.13f)));
    }

    /** A free bitmap on the dot grid: rows of '#' and '.', for the projected HUD's sprites. */
    public void sprite(float x, float y, String[] rows, Opt o) {
        if (!litNow(o.proj) || rows.length == 0) return;
        float p = o.pitch, d = p * o.dot, dq = d * Dots.DOT_QUAD;
        float cx = o.center ? x - rows[0].length() * p / 2f : x, cy = o.center ? y - rows.length * p / 2f : y;
        float c = o.color.lit(o.level);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int col = 0; col < row.length(); col++) {
                if (row.charAt(col) != '#') continue;
                float dx = cx + col * p + (p - d) / 2f - (dq - d) / 2f, dy = cy + r * p + (p - d) / 2f - (dq - d) / 2f;
                shapes.sprite(dots.texture, dx, dy, dq, dq, dots.dotU0, dots.dotV0, dots.dotU1, dots.dotV1, c);
            }
        }
    }

    /** A chain of dots from one point to another, every {@link Opt#step} units. */
    public void dotLine(float x0, float y0, float x1, float y1, Opt o) {
        if (!litNow(o.proj)) return;
        float step = o.or(o.step, 4f), d = o.or(o.size, 1.6f);
        float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        int n = Math.max(1, (int) Math.floor(len / step));
        float c = o.color.lit(o.level), dq = d * Dots.DOT_QUAD;
        for (int i = o.skipFirst ? 1 : 0; i <= n; i++) {
            float t = i / (float) n, x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t;
            shapes.sprite(dots.texture, x - dq / 2f, y - dq / 2f, dq, dq, dots.dotU0, dots.dotV0, dots.dotU1, dots.dotV1, c);
        }
    }

    /** One dot, lit, or only its ghost with {@link Opt#ghostOnly}. */
    public void dot(float x, float y, Opt o) {
        float d = o.or(o.size, 1.6f), dq = d * Dots.DOT_QUAD;
        float c;
        if (o.ghostOnly) {
            if (!baseNow()) return;
            c = o.color.ghost(o.or(o.k, 1f));
        } else {
            if (!litNow(o.proj)) return;
            c = o.color.lit(o.level);
        }
        shapes.sprite(dots.texture, x - dq / 2f, y - dq / 2f, dq, dq, dots.dotU0, dots.dotV0, dots.dotU1, dots.dotV1, c);
    }

    // ------------------------------------------------------------------ bars, rings and blocks

    /**
     * A bar of {@code n} segments with {@code frac} of them lit from the start (the bottom of a vertical
     * bar). {@link Opt#zone} colours segments by place, as printed filters would.
     */
    public void bar(float x, float y, float w, float h, int n, float frac, Opt o) {
        boolean vertical = o.vertical;
        float gap = o.or(o.gap, 1.2f);
        float length = vertical ? h : w, segL = (length - gap * (n - 1)) / n;
        int litN = dark ? 0 : Math.round(frac * n);
        int peak = !Float.isNaN(o.peak) && !dark ? Math.min(n - 1, Math.round(o.peak * n) - 1) : -1;
        boolean lit = litNow(o.proj), base = baseNow() && o.ghost;
        if (!lit && !base) return;
        for (int i = 0; i < n; i++) {
            Phosphor color = o.zone != null ? o.zone.color(i, n) : o.color;
            float sx, sy, sw, sh;
            if (vertical) {
                sx = x;
                sw = w;
                sh = segL;
                sy = y + h - (i + 1) * segL - i * gap;
            } else {
                sy = y;
                sh = h;
                sw = segL;
                sx = x + i * (segL + gap);
                if (o.taper) {
                    float tick = 0.55f + 0.45f * (n > 1 ? i / (float) (n - 1) : 1f);
                    sy = y + h * (1f - tick);
                    sh = h * tick;
                }
            }
            boolean on = i < litN || i == peak;
            if (on && lit) shapes.rect(sx, sy, sw, sh, color.lit(i == peak && i >= litN ? Drive.NORM : o.level));
            else if (!on && base) shapes.rect(sx, sy, sw, sh, color.ghost(1.1f));
        }
    }

    /**
     * A ring of {@code n} cells between radii {@code r0} and {@code r1}, from {@link Opt#start} degrees
     * (0 up, clockwise) round {@link Opt#span}; {@code levels} lights each cell or leaves it NaN.
     */
    public void ring(float cx, float cy, float r0, float r1, int n, RingLevels levels, Opt o) {
        boolean lit = litNow(o.proj), base = baseNow() && o.ghost;
        if (!lit && !base) return;
        float step = o.span / n;
        for (int i = 0; i < n; i++) {
            float level = dark ? Float.NaN : levels.level(i, n);
            boolean on = !Float.isNaN(level);
            if (on ? !lit : !base) continue;
            Phosphor color = o.zone != null ? o.zone.color(i, n) : o.color;
            float s0 = o.start + i * step + o.gapDeg / 2f, s1 = o.start + (i + 1) * step - o.gapDeg / 2f;
            shapes.annulus(cx, cy, r0, r1, s0, s1, on ? color.lit(level) : color.ghost(1.1f));
        }
    }

    /** A lit block (tile frames, inverse blocks), or its ghost when off or unpowered. */
    public void rectLit(float x, float y, float w, float h, Opt o) {
        boolean ghost = o.ghostOnly || dark;
        if (ghost ? !baseNow() : !litNow(o.proj)) return;
        float c = ghost ? o.color.ghost(o.or(o.k, 1.2f)) : o.color.lit(o.level);
        if (o.r > 0f) shapes.roundRect(x, y, w, h, o.r, c);
        else shapes.rect(x, y, w, h, c);
    }

    /** An outlined cell of four separate bars with gaps at the corners, as segments would be. */
    public void frameLit(float x, float y, float w, float h, float t, Opt o) {
        float g = o.or(o.gap, 1.2f);
        boolean ghost = o.ghostOnly || dark;
        if (ghost ? !baseNow() : !litNow(o.proj)) return;
        float c = ghost ? o.color.ghost(o.or(o.k, 1.2f)) : o.color.lit(o.level);
        shapes.rect(x + g, y, w - 2f * g, t, c);
        shapes.rect(x + g, y + h - t, w - 2f * g, t, c);
        shapes.rect(x, y + g, t, h - 2f * g, c);
        shapes.rect(x + w - t, y + g, t, h - 2f * g, c);
    }

    /** A lit line (scope graticules, leader lines). */
    public void lineLit(float x0, float y0, float x1, float y1, float width, Opt o) {
        boolean ghost = o.ghostOnly || dark;
        if (ghost ? !baseNow() : !litNow(o.proj)) return;
        shapes.line(x0, y0, x1, y1, width, ghost ? o.color.ghost(o.or(o.k, 1.2f)) : o.color.lit(o.level));
    }

    /** A lit arc, {@code from} to {@code to} degrees (0 up, clockwise), {@code width} wide on radius {@code r}. */
    public void arcLit(float cx, float cy, float r, float from, float to, float width, Opt o) {
        boolean ghost = o.ghostOnly || dark;
        if (ghost ? !baseNow() : !litNow(o.proj)) return;
        shapes.arc(cx, cy, r, from, to, width, ghost ? o.color.ghost(o.or(o.k, 1.2f)) : o.color.lit(o.level));
    }

    /** A lit disc. */
    public void discLit(float cx, float cy, float r, Opt o) {
        boolean ghost = o.ghostOnly || dark;
        if (ghost ? !baseNow() : !litNow(o.proj)) return;
        shapes.disc(cx, cy, r, ghost ? o.color.ghost(o.or(o.k, 1.2f)) : o.color.lit(o.level));
    }

    /** A scrim: a dark disc or box laid on the glass under a lit mark so it reads over a bright world. */
    public void scrim(float x, float y, float w, float h, float radius, float alpha) {
        if (pass != Pass.BASE) return;
        shapes.roundRect(x, y, w, h, radius, Color.toFloatBits(0f, 0f, 0f, alpha));
    }

    /**
     * Brownout: everything lit inside the box keeps only {@code k} of its drive (the dimmer bus). Call
     * it after drawing what it dims.
     */
    public void dimRegion(float x, float y, float w, float h, float k) {
        if (pass != Pass.GLASS || !glow.capturing()) return;
        erase(true);
        shapes.rectHard(x, y, w, h, Color.toFloatBits(0f, 0f, 0f, MathUtils.clamp(1f - k, 0f, 1f)));
        erase(false);
    }

    // ------------------------------------------------------------------ icons

    /**
     * An icon ({@link Icons}) {@code size} units square from {@code x, y}, lit, with its ghost behind; or
     * with {@link Opt#knock} cut out of a lit block drawn first, as {@link #knockout}'s letters are.
     */
    public void icon(String name, float x, float y, float size, Opt o) {
        Icons.Part[] parts = Icons.get(name);
        if (parts == null) return;
        float k = size / 16f, lw = o.or(o.stroke, 1.5f) * k;
        if (o.knock) {
            if (dark) return;
            if (pass == Pass.PROBE) {
                cuts++;
            } else if (litNow(false)) {
                if (glow.capturing()) {
                    erase(true);
                    iconParts(parts, x, y, k, lw, Color.toFloatBits(0f, 0f, 0f, 1f));
                    erase(false);
                } else {
                    iconParts(parts, x, y, k, lw, Ink.bits(Ink.CUT, 0.92f));
                }
            } else if (pass == Pass.CUT) {
                iconParts(parts, x, y, k, lw, Ink.bits(Ink.CUT, 0.8f));
            }
            return;
        }
        boolean lit = o.lit && litNow(o.proj), base = baseNow() && o.ghost && !o.proj;
        if (!lit && !base) return;
        if (base) iconParts(parts, x, y, k, lw, o.color.ghost(1.3f));
        if (lit) iconParts(parts, x, y, k, lw, o.color.lit(o.level));
    }

    private void iconParts(Icons.Part[] parts, float x, float y, float k, float lw, float c) {
        for (Icons.Part part : parts) {
            float[] d = part.data;
            switch (part.kind) {
                case POLY: {
                    int n = d.length / 2;
                    for (int i = 0; i < n; i++) {
                        poly[2 * i] = x + d[2 * i] * k;
                        poly[2 * i + 1] = y + d[2 * i + 1] * k;
                    }
                    short[] tri = part.triangles;
                    for (int i = 0; i + 2 < tri.length; i += 3) {
                        shapes.triangle(poly[2 * tri[i]], poly[2 * tri[i] + 1], c, poly[2 * tri[i + 1]], poly[2 * tri[i + 1] + 1], c,
                                poly[2 * tri[i + 2]], poly[2 * tri[i + 2] + 1], c);
                    }
                    // a pixel's fade round the outline, as the convex shapes have
                    for (int i = 0; i < n; i++) {
                        int j = (i + 1) % n;
                        shapes.line(poly[2 * i], poly[2 * i + 1], poly[2 * j], poly[2 * j + 1], shapes.feather() * 0.5f, c);
                    }
                    break;
                }
                case LINE: {
                    int n = d.length / 2;
                    boolean closed = n > 2 && d[0] == d[2 * n - 2] && d[1] == d[2 * n - 1];
                    for (int i = 0; i + 1 < n; i++) {
                        float ax = x + d[2 * i] * k, ay = y + d[2 * i + 1] * k, bx = x + d[2 * i + 2] * k, by = y + d[2 * i + 3] * k;
                        float dx = bx - ax, dy = by - ay, l = (float) Math.sqrt(dx * dx + dy * dy);
                        if (l < 1e-5f) continue;
                        float ex = dx / l * lw / 2f, ey = dy / l * lw / 2f;
                        // extend into each joint by half the width so mitred corners close
                        boolean joinStart = i > 0 || closed, joinEnd = i + 2 < n || closed;
                        shapes.line(joinStart ? ax - ex : ax, joinStart ? ay - ey : ay, joinEnd ? bx + ex : bx, joinEnd ? by + ey : by, lw, c);
                    }
                    break;
                }
                case ARC:
                    shapes.arc(x + d[0] * k, y + d[1] * k, d[2] * k, d[3], d[4], lw, c);
                    break;
                default:
                    shapes.disc(x + d[0] * k, y + d[1] * k, d[2] * k, c);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ keys and lamps

    /** A rubber key with a printed legend, and a status lamp above it when {@link Opt#led} is set. */
    public void key(float x, float y, float w, float h, CharSequence label, Opt o) {
        float size = o.or(o.size, 12f);
        boolean pressed = o.pressed;
        Led led = o.led;
        boolean ledOn = o.ledOn;
        Color labelInk = o.ink != null ? o.ink : Ink.KEY_LEGEND;
        if (baseNow()) {
            shapes.roundRect(x - 1f, y - 1f, w + 2f, h + 2.5f, 3.2f, Color.toFloatBits(5 / 255f, 5 / 255f, 6 / 255f, 1f));
            float top = pressed ? Color.toFloatBits(0x1d / 255f, 0x1f / 255f, 0x22 / 255f, 1f) : Ink.bits(Ink.KEY_HI, 1f);
            shapes.roundRectGradient(x, y, w, h, 2.6f, new float[] {0f, top, 0.5f, Ink.bits(Ink.KEY, 1f), 1f,
                    Color.toFloatBits(0x1b / 255f, 0x1c / 255f, 0x1f / 255f, 1f)});
            shapes.line(x + 2.5f, y + 0.6f, x + w - 2.5f, y + 0.6f, 0.6f, Color.toFloatBits(1f, 1f, 1f, 0.08f));
            if (label != null && label.length() > 0) {
                Type.Face face = type.face(size, 0.12f);
                CharSequence s = type.upper(label);
                float lw = type.width(face, s, 0.86f);
                type.draw(batch, face, s, x + w / 2f - lw / 2f, y + h / 2f + size * 0.36f, 0.86f, Ink.bits(labelInk, 1f), height);
            }
        }
        if (led != null) led(x + w / 2f, y - 4.5f, led, ledOn, 1.5f);
    }

    /** A status lamp: dark red, amber or green glass, glowing when on. */
    public void led(float x, float y, Led color, boolean on, float r) {
        on = on && !dark;
        if (baseNow()) {
            shapes.disc(x, y, r + 0.7f, Color.toFloatBits(5 / 255f, 5 / 255f, 5 / 255f, 1f));
            shapes.disc(x, y, r, Color.toFloatBits(color.r, color.g, color.b, on ? 1f : 0.13f));
        }
        if (on && litNow(false)) shapes.disc(x, y, r, Color.toFloatBits(color.r, color.g, color.b, 0.9f));
    }

    /** A lamp key: a translucent cap lit from behind (the master warning and caution), legend on one or two lines. */
    public void lampKey(float x, float y, float w, float h, CharSequence line1, CharSequence line2, Lamp color, boolean on) {
        on = on && !dark;
        if (pass == Pass.PROBE) {
            lates++;
            return;
        }
        if (baseNow()) {
            shapes.roundRect(x - 1.2f, y - 1.2f, w + 2.4f, h + 3f, 3f, Color.toFloatBits(5 / 255f, 5 / 255f, 6 / 255f, 1f));
            float cap = on ? Color.toFloatBits(color.r * 0.78f, color.g * 0.78f, color.b * 0.78f, 1f)
                    : Color.toFloatBits(color.r * 0.14f + 0.86f * 20 / 255f, color.g * 0.14f + 0.86f * 20 / 255f, color.b * 0.14f + 0.86f * 20 / 255f, 1f);
            shapes.roundRect(x, y, w, h, 2.5f, cap);
        }
        if (on && litNow(false)) shapes.roundRect(x, y, w, h, 2.5f, Color.toFloatBits(color.r, color.g, color.b, 0.42f));
        if (pass == Pass.LATE) {
            float ink = on ? (color == Lamp.ZINC ? Color.toFloatBits(0f, 30 / 255f, 22 / 255f, 0.92f) : Color.toFloatBits(40 / 255f, 6 / 255f, 0f, 0.92f))
                    : Color.toFloatBits(150 / 255f, 156 / 255f, 160 / 255f, 0.55f);
            if (line2 != null && line2.length() > 0) {
                capLegend(x + w / 2f, y + h / 2f - 1.5f, line1, ink);
                capLegend(x + w / 2f, y + h / 2f + 11f, line2, ink);
            } else {
                capLegend(x + w / 2f, y + h / 2f + 4.3f, line1, ink);
            }
        }
    }

    private void capLegend(float cx, float y, CharSequence text, float ink) {
        Type.Face face = type.face(12f, 0.1f);
        float w = type.width(face, text, 0.82f);
        type.draw(batch, face, text, cx - w / 2f, y, 0.82f, ink, height);
    }

    // ------------------------------------------------------------------ LCD (the personal kit)

    /** The reflective LCD of the personal kit, or a backlit one with {@link Opt#backlight}. */
    public void lcdPanel(float x, float y, float w, float h, Opt o) {
        boolean backlight = o.backlight;
        if (baseNow()) {
            shapes.roundRect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 3f, Color.toFloatBits(0f, 0f, 0f, 1f));
            Color top = backlight ? Ink.LCD_LIT_TOP : Ink.LCD_TOP, bottom = backlight ? Ink.LCD_LIT_BOTTOM : Ink.LCD_BOTTOM;
            shapes.roundRectGradient(x, y, w, h, 2f, new float[] {0f, Ink.bits(top, 1f), 1f, Ink.bits(bottom, 1f)});
        }
        if (backlight && litNow(false)) shapes.roundRect(x, y, w, h, 2f, Color.toFloatBits(60 / 255f, 220 / 255f, 200 / 255f, 0.045f));
    }

    /** LCD digits: dark segments casting a faint shadow on the reflector, their unset segments barely there. */
    public void lcdSeg7(float x, float y, float h, CharSequence text, Opt o) {
        if (!baseNow()) return;
        Color ink = o.ink != null ? o.ink : Ink.LCD_INK;
        float slant = o.or(o.slant, 6f);
        lcdSegs(x + 0.7f, y + 0.8f, h, text, Color.toFloatBits(0f, 0f, 0f, 0.16f), 0f, slant);
        lcdSegs(x, y, h, text, Ink.bits(ink, 1f), Color.toFloatBits(0f, 0f, 0f, 0.045f), slant);
    }

    private void lcdSegs(float x, float y, float h, CharSequence text, float color, float ghost, float slant) {
        float w = h * 0.54f, t = h * 0.13f, gap = h * 0.022f, s = (float) Math.tan(slant * D), adv = w + h * 0.26f;
        int cell = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            float ox = x + cell * adv;
            if ((ch == ':' || ch == '.') && cell >= 0) {
                if (ch == ':') {
                    for (int c = 0; c < 2; c++) {
                        float cy = h * (c == 0 ? 0.3f : 0.7f);
                        float cx = ox + w + h * 0.1f + (h - cy) * s;
                        shapes.rect(cx, y + cy - t / 2f, t, t, color);
                    }
                } else {
                    shapes.rect(ox + w + h * 0.06f, y + h - t, t, t, color);
                }
                continue;
            }
            cell++;
            ox = x + cell * adv;
            int on = Rom.seg7(ch);
            for (int k = 0; k < 7; k++) {
                boolean segOn = (on & (1 << k)) != 0;
                if (!segOn && ghost == 0f) continue;
                seg7Segment(k, ox, y, w, h, t, gap, s);
                shapes.convex(poly, 6, segOn ? color : ghost);
            }
        }
    }

    /** Text on the LCD, with its faint shadow. Returns the width. */
    public float lcdText(float x, float y, CharSequence text, Opt o) {
        float size = o.or(o.size, 13f);
        Color ink = o.ink != null ? o.ink : Ink.LCD_INK;
        Type.Face face = type.face(size, 0.08f);
        float w = type.width(face, text, 0.82f);
        if (baseNow()) {
            float ax = aligned(x, w, o.align);
            type.draw(batch, face, text, ax + 0.6f, y + 0.7f, 0.82f, Color.toFloatBits(0f, 0f, 0f, 0.16f), height);
            type.draw(batch, face, text, ax, y, 0.82f, Ink.bits(ink, 1f), height);
        }
        return w;
    }

    // ------------------------------------------------------------------ finishing

    /** One display window, kept from the base layer for its filaments and glare. */
    private static final class Module {
        float x, y, w, h, r;
        int filaments;
        boolean circle, glare;

        void set(float x, float y, float w, float h, float r, int filaments, boolean circle, boolean glare) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.r = r;
            this.filaments = filaments;
            this.circle = circle;
            this.glare = glare;
        }
    }

    /** Each window's filaments (fine dark wires with a faint warm glow) and the glare off its glass. */
    private void finishModules() {
        // Windows were kept where they sit on the stage
        origin(0f, 0f);
        float wire = Color.toFloatBits(0f, 0f, 0f, 0.30f), warm = Color.toFloatBits(1f, 120 / 255f, 60 / 255f, 0.035f);
        for (Module m : modules) {
            for (int i = 1; i <= m.filaments; i++) {
                float fy = m.y + m.h * i / (m.filaments + 1);
                float x0 = m.x, x1 = m.x + m.w;
                if (m.circle) {
                    float cy = m.y + m.h / 2f, dy = fy - cy, rr = m.r * m.r - dy * dy;
                    if (rr <= 0f) continue;
                    float hw = (float) Math.sqrt(rr), cx = m.x + m.w / 2f;
                    x0 = cx - hw;
                    x1 = cx + hw;
                }
                shapes.line(x0, fy, x1, fy, 0.45f, wire);
                shapes.line(x0, fy + 0.4f, x1, fy + 0.4f, 0.3f, warm);
            }
            if (m.glare) glare(m);
            modulePool.add(m);
        }
        modules.clear();
    }

    /**
     * The glare: a linear gradient from the window's top-left corner towards a point past its bottom,
     * 5.5% white fading to 1.2% over the first 35% and gone just after, clipped to the window.
     */
    private void glare(Module m) {
        int n;
        if (m.circle) {
            n = MathUtils.clamp(MathUtils.ceil(MathUtils.PI2 * m.r * unitsPx / 4f), 16, Shapes.MAX_POINTS);
            float cx = m.x + m.w / 2f, cy = m.y + m.h / 2f;
            for (int i = 0; i < n; i++) {
                float a = MathUtils.PI2 * i / n;
                poly[2 * i] = cx + MathUtils.cos(a) * m.r;
                poly[2 * i + 1] = cy + MathUtils.sin(a) * m.r;
            }
        } else {
            n = shapes.roundRectPoints(m.x, m.y, m.w, m.h, m.r, poly);
        }
        float gx = m.w * 0.6f, gy = m.h * 1.2f, gl = gx * gx + gy * gy;
        float limit = 0.355f;
        // clip to t <= limit, t being the share along the gradient
        int out = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = poly[2 * i], ay = poly[2 * i + 1], bx = poly[2 * j], by = poly[2 * j + 1];
            float ta = ((ax - m.x) * gx + (ay - m.y) * gy) / gl, tb = ((bx - m.x) * gx + (by - m.y) * gy) / gl;
            boolean ina = ta <= limit, inb = tb <= limit;
            if (ina && out < Shapes.MAX_POINTS) {
                clipped[2 * out] = ax;
                clipped[2 * out + 1] = ay;
                out++;
            }
            if (ina != inb && out < Shapes.MAX_POINTS) {
                float u = (limit - ta) / (tb - ta);
                clipped[2 * out] = ax + (bx - ax) * u;
                clipped[2 * out + 1] = ay + (by - ay) * u;
                out++;
            }
        }
        if (out < 3) return;
        for (int i = 0; i < out; i++) {
            float t = ((clipped[2 * i] - m.x) * gx + (clipped[2 * i + 1] - m.y) * gy) / gl;
            float a = t <= 0.35f ? 0.055f + (0.012f - 0.055f) * Math.max(t, 0f) / 0.35f : 0.012f * (1f - (t - 0.35f) / 0.01f);
            polyColors[i] = Color.toFloatBits(1f, 1f, 1f, Math.max(a, 0f));
        }
        shapes.convexShaded(clipped, polyColors, out);
    }

    @Override
    public void dispose() {
        type.dispose();
        glow.dispose();
        dots.dispose();
    }
}
