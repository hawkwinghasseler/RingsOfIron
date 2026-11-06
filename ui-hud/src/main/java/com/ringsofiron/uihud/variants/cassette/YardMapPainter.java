package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.uihud.widgets.MapPainter;
import com.ringsofiron.uihud.UiScale;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Region;

/**
 * The map in the Yard Standard (style guide 9.9; documentation/ui-overhaul/variations/context-cassette,
 * 09): a plot on a big phosphor tube. The ship's orbit is solid green and a planned path dashed, other
 * orbits in dim green, the target and the words in amber, hostiles red; the own ship is its cross on a
 * dark knockout, a burn's handles are double chevrons, and the labels are tube capitals. The map keeps
 * where everything goes and the clicks; this only draws each piece.
 *
 * <p>A path's brightness, its fading along the way the ship flies and its dotting where planned burns
 * take it, come through each piece's alpha and pieces, so they take this look as they are drawn.
 *
 * <p>Shapes are drawn with the variant's {@link YardPaint}; the labels have a capitals font of their
 * own, which the map measures them with, drawn again when the paint's scale changes. Dispose it with
 * the variant.
 */
public class YardMapPainter extends MapPainter implements Disposable {
    /** The labels' size, UI units. */
    static final float LABEL = 13f;

    private static final Color BACK = Yard.hex("16130F");
    private static final Color GREEN = Yard.P_GREEN, GDIM = Yard.P_GDIM, AMBER = Yard.P_AMBER, ADIM = Yard.P_DIM;
    private static final Color RED = Yard.P_RED, WHITE = Yard.P_WHITE;
    private static final Color DISC = Yard.hex("0A170C");
    private static final Color MOON_ORBIT = new Color(GDIM.r, GDIM.g, GDIM.b, 0.8f);
    private static final Color TARGET_ORBIT = new Color(ADIM.r, ADIM.g, ADIM.b, 0.75f);
    private static final Color CURRENT_LEG = new Color(GREEN.r, GREEN.g, GREEN.b, 0.7f);
    private static final Color GHOST_SOI = new Color(GDIM.r, GDIM.g, GDIM.b, 0.5f);

    private final YardPaint paint;
    /** Whether {@link #paint} is its own, made for it, which it disposes. */
    private final boolean ownPaint;
    private BitmapFont labels;
    private float labelScale = -1f;
    private final GlyphLayout layout = new GlyphLayout(), halo = new GlyphLayout();
    private final Color ink = new Color();
    private final Color tmp = new Color();
    /** Where the tube's face is: the canvas, as the variant's insets leave it (left, top, right, bottom). */
    private float insetL, insetT, insetR, insetB;
    private boolean drawsTitle = true, drawsInstructions = true, drawsRegions = true;
    /** The dash pattern carried from one piece of a path to the next, so the dashes run on. */
    private Role dashRole;
    private float dashX, dashY, dashAlong;

    /** Draws with {@code paint}, the variant's, which it keeps; needs GL for its own white pixel. */
    public YardMapPainter(YardPaint paint) {
        this(paint, false);
    }

    /** Draws with a paint of its own, at the UI scale the screen has now, and disposes it; needs GL. */
    public YardMapPainter() {
        this(new YardPaint(), true);
        rescale(Gdx.graphics == null ? 1f : UiScale.effective(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
    }

    private YardMapPainter(YardPaint paint, boolean own) {
        super(whitePixel(), null);
        this.paint = paint;
        this.ownPaint = own;
    }

    /** The UI is drawn {@code uiScale} times its units now: the labels are drawn again at it, if the paint is its own. */
    public void rescale(float uiScale) {
        if (ownPaint) paint.fonts.setScale(uiScale);
    }

    /** A white pixel of its own for the pieces drawn as the classic look draws them (the disc, the marks). */
    private static TextureRegion whitePixel() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        return new TextureRegion(t);
    }

    /** The canvas the variant leaves the map, as its {@code setCanvasInsets}: the tube's face sits there. */
    public void setInsets(float left, float top, float right, float bottom) {
        insetL = left;
        insetT = top;
        insetR = right;
        insetB = bottom;
    }

    /** Which of the map's own lines to draw; a variant that shows them in its panels draws none here. */
    public void setDraws(boolean title, boolean instructions, boolean regions) {
        drawsTitle = title;
        drawsInstructions = instructions;
        drawsRegions = regions;
    }

    @Override
    public Color color(Role role) {
        switch (role) {
            case BACKDROP: return Yard.CRT_BLACK;
            case BODY: return DISC;
            case BODY_FAR:
            case MOON: return GDIM;
            case MOON_ORBIT: return MOON_ORBIT;
            case GHOST: return GDIM;
            case GHOST_SOI: return GHOST_SOI;
            case CURRENT:
            case PLANNED:
            case PLANNED_LEG:
            case SHIP:
            case STATION:
            case HAULER:
            case PROGRADE: return GREEN;
            case CURRENT_LEG: return CURRENT_LEG;
            case PROPOSED:
            case RADIAL: return WHITE;
            case TARGET:
            case SELECTED:
            case OBJECTIVE:
            case TITLE: return AMBER;
            case TARGET_ORBIT: return TARGET_ORBIT;
            case IMPACT:
            case HOSTILE: return RED;
            case OTHER: return GDIM;
            case SCALE:
            case DIM: return ADIM;
            default: return GDIM;
        }
    }

    @Override
    public BitmapFont font() {
        float s = paint.fonts.scale();
        if (labels == null) {
            labels = YardFonts.own(YardFonts.Face.TUBE, LABEL, s, true);
            labelScale = s;
        } else if (Math.abs(s - labelScale) > 1e-3f) {
            YardFonts.remake(labels, YardFonts.Face.TUBE, LABEL, s, true);
            labelScale = s;
        }
        return labels;
    }

    @Override
    public float lineHeight() {
        return 15f;
    }

    // --- Painting in the stage's coordinates -----------------------------------------------------

    /** The paint, begun on {@code batch} so a design y is the stage's y turned over ({@link #d}). */
    private YardPaint p(Batch batch, float alpha) {
        paint.begin(batch, 0f, alpha, paint.time());
        return paint;
    }

    /** The paint's y for the stage's {@code y}. */
    private static float d(float y) {
        return -y;
    }

    // --- The screen ------------------------------------------------------------------------------

    @Override
    public void backdrop(Batch batch, float x, float y, float width, float height, float alpha) {
        YardPaint p = p(batch, alpha);
        p.rect(x, d(y + height), width, height, BACK);
        float tx = x + insetL + 8f, ty = y + insetB + 8f, tw = width - insetL - insetR - 16f, th = height - insetT - insetB - 16f;
        if (tw < 20f || th < 20f) return;
        p.tube(tx, d(ty + th), tw, th, 10f, 6f, false);
        // The phosphor's faint green in the middle of the tube, falling off to its edge
        p.glow(tx + tw / 2f, d(ty + th / 2f), Math.max(tw, th) * 0.7f, GDIM, 0.09f);
        for (float yy = ty + 1.5f; yy < ty + th; yy += 3f) p.rect(tx, d(yy + 0.6f), tw, 0.6f, Color.BLACK, 0.25f);
    }

    @Override
    public void noOrbit(Batch batch, float x, float y, float alpha) {
        text(batch, "No orbit to show", x, y, ADIM, alpha, Align.center);
    }

    @Override
    public void title(Batch batch, String body, String counts, float x, float top, float alpha) {
        if (!drawsTitle) return;
        String line = "MAP  " + body + (counts.isEmpty() ? "" : "   " + counts);
        text(batch, line, x + 22f, top - 22f, AMBER, alpha, Align.left);
    }

    @Override
    public void instructions(Batch batch, String text, float x, float top, float alpha) {
        if (!drawsInstructions) return;
        text(batch, text, x + 22f, top - 40f, ADIM, alpha, Align.left);
    }

    @Override
    public float scaleBar(Batch batch, float x, float y, float length, String distance, float alpha) {
        YardPaint p = p(batch, alpha);
        float bx = x + 8f, by = y + 8f;
        p.line(bx, d(by), bx + length, d(by), 1.4f, GDIM);
        p.line(bx, d(by - 1f), bx, d(by + 7f), 1.4f, GDIM);
        p.line(bx + length, d(by - 1f), bx + length, d(by + 7f), 1.4f, GDIM);
        for (int i = 1; i < 4; i++) p.line(bx + length * i / 4f, d(by - 1f), bx + length * i / 4f, d(by + 4f), 1f, GDIM);
        text(batch, distance, bx, by + 10f + LABEL, ADIM, alpha, Align.left);
        return by + 44f;
    }

    // --- Orbits ---------------------------------------------------------------------------------

    @Override
    public void path(Batch batch, Role role, float x0, float y0, float x1, float y1, float alpha) {
        YardPaint p = p(batch, alpha);
        Color c = color(role);
        boolean dashed = role == Role.PLANNED || role == Role.PLANNED_LEG || role == Role.PROPOSED;
        boolean glow = role == Role.CURRENT || role == Role.PLANNED;
        float w = role == Role.CURRENT || role == Role.PLANNED ? 1.8f : role == Role.MOON_ORBIT || role == Role.TARGET_ORBIT ? 1.2f : 1.5f;
        if (glow) p.line(x0, d(y0), x1, d(y1), 5f, c, 0.1f);
        if (!dashed) {
            p.line(x0, d(y0), x1, d(y1), w, c);
            dashRole = null;
            return;
        }
        // The dashes run on from the piece before when this one starts where it ended
        if (role != dashRole || Math.abs(x0 - dashX) + Math.abs(y0 - dashY) > 0.75f) dashAlong = 0f;
        float dash = role == Role.PROPOSED ? 4f : 7f, gap = role == Role.PROPOSED ? 4f : 5f, period = dash + gap;
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        if (len > 1e-3f) {
            float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
            float s = -(dashAlong % period);
            for (; s < len; s += period) {
                float a = Math.max(0f, s), b = Math.min(len, s + dash);
                if (b > a) p.line(x0 + ux * a, d(y0 + uy * a), x0 + ux * b, d(y0 + uy * b), w, c);
            }
        }
        dashRole = role;
        dashX = x1;
        dashY = y1;
        dashAlong += len;
    }

    @Override
    public void body(Batch batch, double x, double y, double radius, float left, float bottom, float right, float top,
                     float alpha) {
        super.body(batch, x, y, radius, left, bottom, right, top, alpha);
        if (radius < 3) {
            YardPaint p = p(batch, alpha);
            p.circle((float) x, d((float) y), 4f, GREEN);
            return;
        }
        // The limb: a ring of phosphor, only the arcs on the canvas
        YardPaint p = p(batch, alpha);
        int n = MathUtils.clamp((int) (radius * 0.5), 48, 1440);
        double px = 0, py = 0;
        boolean have = false;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double cx = x + Math.cos(a) * radius, cy = y + Math.sin(a) * radius;
            boolean on = cx > left - 4 && cx < right + 4 && cy > bottom - 4 && cy < top + 4;
            if (have && on) {
                p.line((float) px, d((float) py), (float) cx, d((float) cy), 6f, GREEN, 0.09f);
                p.line((float) px, d((float) py), (float) cx, d((float) cy), 2f, GREEN);
            }
            px = cx;
            py = cy;
            have = on;
        }
    }

    @Override
    public void ghost(Batch batch, float x, float y, float radius, float soiRadius, float alpha) {
        YardPaint p = p(batch, alpha);
        if (soiRadius > 0) dashedCircle(p, x, y, soiRadius, 1f, GHOST_SOI, 4f, 6f);
        if (radius > 0) ring(p, x, y, radius, 1.4f, GDIM, 1f);
    }

    @Override
    public void approach(Batch batch, float shipX, float shipY, float targetX, float targetY, float alpha) {
        YardPaint p = p(batch, alpha);
        p.dashed(shipX, d(shipY), targetX, d(targetY), 1.3f, 4f, 4f, AMBER, 0.9f);
        ring(p, shipX, shipY, 4f, 1.4f, GREEN, 1f);
        ring(p, targetX, targetY, 4f, 1.4f, AMBER, 1f);
    }

    @Override
    public void burn(Batch batch, Role role, float x, float y, float alpha) {
        YardPaint p = p(batch, alpha);
        Color c = color(role);
        boolean sel = role == Role.SELECTED;
        if (sel) p.glow(x, d(y), 14f, c, 0.35f);
        p.circle(x, d(y), sel ? 6.5f : 5f, Yard.CRT_BLACK);
        ring(p, x, y, sel ? 6f : 4.5f, 1.6f, c, 1f);
        p.circle(x, d(y), 1.6f, c);
    }

    @Override
    public void ship(Batch batch, float x, float y, float alpha) {
        YardPaint p = p(batch, alpha);
        // The own-ship cross on a dark knockout, so the orbit under it never hides it
        p.circle(x, d(y), 11f, Yard.CRT_BLACK, 0.92f);
        p.glow(x, d(y), 12f, GREEN, 0.25f);
        p.line(x - 7f, d(y), x + 7f, d(y), 2f, GREEN);
        p.line(x, d(y - 7f), x, d(y + 7f), 2f, GREEN);
    }

    @Override
    public void moon(Batch batch, float x, float y, float size, float alpha) {
        YardPaint p = p(batch, alpha);
        float r = Math.max(2.5f, size / 2f);
        p.circle(x, d(y), r, DISC);
        ring(p, x, y, r, 1.4f, GDIM, 1f);
    }

    @Override
    public void marker(Batch batch, float x, float y, boolean target, float alpha) {
        YardPaint p = p(batch, alpha);
        if (target) YardSymbols.navMark(p, x, d(y), AMBER, 1.6f);
        else YardSymbols.station(p, x, d(y), 4f, GREEN, 1.4f);
    }

    @Override
    public void label(Batch batch, Role role, String text, float x, float top, float width, int align, float alpha) {
        Color c = role == Role.IMPACT || role == Role.HOSTILE ? RED : role == Role.CURRENT || role == Role.CURRENT_LEG ? GREEN : AMBER;
        BitmapFont f = font();
        float base = top - f.getCapHeight();
        float tx = (align & Align.right) != 0 ? x + width : x;
        text(batch, text, tx, base, c, alpha, (align & Align.right) != 0 ? Align.right : Align.left);
    }

    // --- Traffic --------------------------------------------------------------------------------

    @Override
    public void contact(Batch batch, ContactKind kind, boolean stale, float x, float y, float alpha) {
        YardPaint p = p(batch, alpha);
        Color c = contactColor(kind, stale);
        float a = stale ? 0.6f : 1f;
        switch (kind) {
            case STATION:
                YardSymbols.station(p, x, d(y), 4.5f, c, 1.5f);
                break;
            case HAULER: {
                // A chevron, nose to the right, as the plot draws a craft that keeps to its lane
                float[] v = {x - 5f, d(y + 5f), x + 5f, d(y), x - 5f, d(y - 5f), x - 2f, d(y)};
                if (!stale) p.fillConvex(new float[]{v[0], v[1], v[2], v[3], v[6], v[7]}, c, a);
                if (!stale) p.fillConvex(new float[]{v[6], v[7], v[2], v[3], v[4], v[5]}, c, a);
                p.polyline(v, true, 1.3f, c, a);
                break;
            }
            case HOSTILE: {
                float[] v = {x, d(y + 6f), x + 5.5f, d(y), x, d(y - 6f), x - 5.5f, d(y)};
                if (!stale) {
                    p.glow(x, d(y), 10f, RED, 0.3f);
                    p.fillConvex(v, c, a);
                }
                p.polyline(v, true, 1.4f, c, a);
                break;
            }
            default: {
                float[] v = {x, d(y + 4f), x + 4f, d(y), x, d(y - 4f), x - 4f, d(y)};
                p.polyline(v, true, 1.2f, c, a);
            }
        }
    }

    @Override
    public void objective(Batch batch, float x, float y, float alpha) {
        YardPaint p = p(batch, alpha);
        dashedCircle(p, x, y, 13f, 1.6f, AMBER, 5f, 3.5f);
    }

    @Override
    public void targetBrackets(Batch batch, float x, float y, float alpha) {
        YardPaint p = p(batch, alpha);
        YardSymbols.bracket(p, x, d(y), 9f, AMBER, 1.6f);
    }

    @Override
    public void leader(Batch batch, ContactKind kind, boolean stale, float x0, float y0, float x1, float y1, float alpha) {
        YardPaint p = p(batch, alpha);
        p.line(x0, d(y0), x1, d(y1), 1f, ADIM, 0.6f);
    }

    @Override
    public void contactName(Batch batch, ContactKind kind, boolean stale, boolean target, String name, float x, float y,
                            float alpha) {
        Color c = kind == ContactKind.HOSTILE ? RED : AMBER;
        if (stale) c = tmp.set(c).mul(0.65f, 0.65f, 0.65f, 1f);
        text(batch, name, x, y - font().getCapHeight(), c, alpha, Align.left);
    }

    @Override
    public void contactDetail(Batch batch, String detail, float x, float y, float alpha) {
        text(batch, detail, x, y - font().getCapHeight(), ADIM, alpha, Align.left);
    }

    @Override
    public void regions(Batch batch, Array<Region> regions, float x, float top, float alpha) {
        if (!drawsRegions || regions.size == 0) return;
        float cap = font().getCapHeight();
        text(batch, "TENSION", x, top - cap, ADIM, alpha, Align.left);
        float y = top - lineHeight();
        for (int i = 0; i < regions.size; i++) {
            Region r = regions.get(i);
            text(batch, r.line, x, y - cap, r.high ? RED : ADIM, alpha, Align.left);
            y -= lineHeight();
        }
    }

    // --- Planning -------------------------------------------------------------------------------

    @Override
    public void handle(Batch batch, int handle, float x, float y, float dx, float dy, float reach, boolean hot, float alpha) {
        YardPaint p = p(batch, alpha);
        Color c = color(handle < 2 ? Role.PROGRADE : Role.RADIAL);
        float a = hot ? 1f : 0.8f;
        float hx = x + dx * reach, hy = y + dy * reach;
        p.line(x + dx * 9f, d(y + dy * 9f), hx - dx * 6f, d(hy - dy * 6f), 1.2f, c, 0.35f * a);
        if (hot) p.glow(hx, d(hy), 14f, c, 0.3f);
        p.circle(hx, d(hy), 9f, Yard.CRT_BLACK, 0.85f);
        // A double chevron pointing the way the handle pulls the burn
        float nx = -dy, ny = dx;
        for (int k = 0; k < 2; k++) {
            float tip = 4.5f - k * 5f, back = tip - 4.5f;
            float tx = hx + dx * tip, ty = hy + dy * tip;
            float bx = hx + dx * back, by = hy + dy * back;
            p.line(bx + nx * 4.5f, d(by + ny * 4.5f), tx, d(ty), 1.8f, c, a);
            p.line(bx - nx * 4.5f, d(by - ny * 4.5f), tx, d(ty), 1.8f, c, a);
        }
    }

    // --- Shapes ---------------------------------------------------------------------------------

    /** A ring in stage coordinates. */
    private static void ring(YardPaint p, float x, float y, float r, float w, Color c, float a) {
        int n = MathUtils.clamp((int) (r * 0.9f), 16, 360);
        float px = x + r, py = y;
        for (int i = 1; i <= n; i++) {
            double ang = Math.PI * 2 * i / n;
            float cx = x + (float) Math.cos(ang) * r, cy = y + (float) Math.sin(ang) * r;
            p.line(px, d(py), cx, d(cy), w, c, a);
            px = cx;
            py = cy;
        }
    }

    /** A dashed ring in stage coordinates. */
    private static void dashedCircle(YardPaint p, float x, float y, float r, float w, Color c, float dash, float gap) {
        double circ = Math.PI * 2 * r;
        int n = Math.max(6, (int) (circ / (dash + gap)));
        double step = Math.PI * 2 / n, on = step * dash / (dash + gap);
        for (int i = 0; i < n; i++) {
            double a0 = step * i, a1 = a0 + on;
            int seg = Math.max(1, (int) (r * on / 6));
            for (int s = 0; s < seg; s++) {
                double b0 = a0 + (a1 - a0) * s / seg, b1 = a0 + (a1 - a0) * (s + 1) / seg;
                p.line(x + (float) Math.cos(b0) * r, d(y + (float) Math.sin(b0) * r), x + (float) Math.cos(b1) * r,
                        d(y + (float) Math.sin(b1) * r), w, c, 1f);
            }
        }
    }

    /** Tube capitals with the beam's bloom: its baseline at {@code baseline} (stage y), from or to {@code x}. */
    private void text(Batch batch, String s, float x, float baseline, Color c, float alpha, int align) {
        if (s == null || s.isEmpty()) return;
        BitmapFont f = font();
        float y = baseline + f.getCapHeight();
        // A layout keeps the colour it was set with, so each takes its own
        ink.set(c.r, c.g, c.b, c.a * alpha * 0.2f);
        halo.setText(f, s, ink, 0f, Align.left, false);
        ink.set(c.r, c.g, c.b, c.a * alpha);
        layout.setText(f, s, ink, 0f, Align.left, false);
        float w = layout.width;
        float lx = align == Align.right ? x - w : align == Align.center ? x - w / 2f : x;
        // The phosphor's faint haze round the line, then the beam's halo hugging each letter
        p(batch, alpha).glowRect(lx, d(y), w, f.getCapHeight(), 9f, c, 0.03f);
        f.draw(batch, halo, lx - 1f, y);
        f.draw(batch, halo, lx + 1f, y);
        f.draw(batch, halo, lx, y + 1f);
        f.draw(batch, halo, lx, y - 1f);
        f.draw(batch, layout, lx, y);
    }

    @Override
    public void dispose() {
        if (labels != null) labels.dispose();
        labels = null;
        white.getTexture().dispose();
        if (ownPaint) paint.dispose();
    }
}
