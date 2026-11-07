package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Region;

import java.util.Locale;

/**
 * How the map's pieces look. This is the classic look; a HUD variant that restyles the map
 * ({@code HudVariant#mapPainter}) extends it. The map ({@link ManeuverMap}, its {@link OrbitMap} and
 * {@link TrafficLayer}) keeps where everything goes and when it shows: the zoom and panning, what is
 * on the map and what is pulled to its edge, labels kept clear of each other, the burn handles and
 * the picks. It calls here to draw each piece where it has put it, so a variant can recolour the lot
 * ({@link #color}), give the labels its own font ({@link #font}) or draw any piece its own way.
 *
 * <p>Coordinates are stage coordinates (UI units, y up). {@code alpha} is the map's, to multiply in.
 * Each call sets the batch's colour, or the font's, itself.
 */
public class MapPainter {
    /** What a piece is, for its colour ({@link #color}). */
    public enum Role {
        /** Under everything, the whole map screen. */
        BACKDROP,
        /** The body the map is drawn around. */
        BODY,
        /** The dot it shrinks to zoomed far out. */
        BODY_FAR,
        /** Moons, and the body the map's body orbits: their dots and names. */
        MOON,
        /** Their orbits. */
        MOON_ORBIT,
        /** Where a moon the path meets will be by then: its outline. */
        GHOST,
        /** And its sphere of influence. */
        GHOST_SOI,
        /** The orbit now, its marks and their labels. */
        CURRENT,
        /** The path after the planned burns, the burns on it and their countdowns. */
        PLANNED,
        /** A leg of the orbit now around a moon. */
        CURRENT_LEG,
        /** A leg of the planned path around a moon. */
        PLANNED_LEG,
        /** A transfer to the target proposed but not accepted yet, and its burns. */
        PROPOSED,
        /** The navigation target, its name, its brackets and the closest approach to it. */
        TARGET,
        /** The target's orbit. */
        TARGET_ORBIT,
        /** The player's ship. */
        SHIP,
        /** The selected burn and its countdown. */
        SELECTED,
        /** Where the path meets a surface. */
        IMPACT,
        /** Stations, as the traffic layer or the orbit map marks them. */
        STATION,
        /** Haulers. */
        HAULER,
        /** Hostiles, and a region whose tension runs high. */
        HOSTILE,
        /** Other craft: derelicts and the like. */
        OTHER,
        /** A contract's waypoint. */
        OBJECTIVE,
        /** The selected burn's prograde and retrograde handles. */
        PROGRADE,
        /** Its radial out and in handles. */
        RADIAL,
        /** The scale bar. */
        SCALE,
        /** The title line. */
        TITLE,
        /** Dim text: the instructions, a contact's detail, the tension readout. */
        DIM
    }

    private static final Color BACKDROP = new Color(0.01f, 0.02f, 0.04f, 1f);
    private static final Color BODY = new Color(0.12f, 0.25f, 0.42f, 1f);
    private static final Color PLANNED = new Color(1f, 0.7f, 0.2f, 1f);
    private static final Color OTHER_BODY = new Color(0.55f, 0.6f, 0.65f, 1f);
    private static final Color OTHER_ORBIT = new Color(0.55f, 0.6f, 0.65f, 0.45f);
    private static final Color STATION = new Color(0.4f, 0.75f, 1f, 1f);
    private static final Color TARGET = new Color(1f, 0.35f, 0.9f, 1f);
    private static final Color TARGET_ORBIT = new Color(1f, 0.35f, 0.9f, 0.5f);
    private static final Color PROPOSED = new Color(0.35f, 1f, 0.45f, 1f);
    private static final Color CURRENT_LEG = new Color(0.45f, 0.85f, 1f, 0.6f);
    private static final Color PLANNED_LEG = new Color(1f, 0.82f, 0.45f, 0.85f);
    private static final Color GHOST = new Color(0.55f, 0.6f, 0.65f, 0.55f);
    private static final Color GHOST_SOI = new Color(0.55f, 0.6f, 0.65f, 0.25f);
    private static final Color IMPACT = new Color(1f, 0.3f, 0.25f, 1f);
    private static final Color HAULER = new Color(1f, 0.85f, 0.25f, 1f);
    private static final Color OBJECTIVE = new Color(1f, 0.7f, 0.1f, 1f);
    private static final Color HOSTILE = new Color(1f, 0.25f, 0.2f, 1f);
    private static final Color PROGRADE = new Color(0.75f, 1f, 0.3f, 1f);
    private static final Color RADIAL = new Color(0.4f, 0.8f, 1f, 1f);
    private static final Color SCALE = new Color(0.55f, 0.65f, 0.75f, 0.8f);

    /** A white pixel, for drawing shapes in any colour. */
    protected final TextureRegion white;
    private BitmapFont font;
    private final float[] quad = new float[20];
    private final Color dimmed = new Color();

    /** The classic look: shapes drawn with {@code white}, labels in {@code font} (null for none). */
    public MapPainter(TextureRegion white, BitmapFont font) {
        this.white = white;
        this.font = font;
    }

    /** Each piece's colour. Override to recolour the map and keep its shapes. */
    public Color color(Role role) {
        switch (role) {
            case BACKDROP: return BACKDROP;
            case BODY: return BODY;
            case BODY_FAR:
            case MOON: return OTHER_BODY;
            case MOON_ORBIT: return OTHER_ORBIT;
            case GHOST: return GHOST;
            case GHOST_SOI: return GHOST_SOI;
            case CURRENT: return HudSkin.HUD_CYAN;
            case PLANNED: return PLANNED;
            case CURRENT_LEG: return CURRENT_LEG;
            case PLANNED_LEG: return PLANNED_LEG;
            case PROPOSED: return PROPOSED;
            case TARGET: return TARGET;
            case TARGET_ORBIT: return TARGET_ORBIT;
            case SHIP:
            case SELECTED: return Color.WHITE;
            case IMPACT: return IMPACT;
            case STATION: return STATION;
            case HAULER: return HAULER;
            case HOSTILE: return HOSTILE;
            case OBJECTIVE: return OBJECTIVE;
            case PROGRADE: return PROGRADE;
            case RADIAL: return RADIAL;
            case SCALE: return SCALE;
            case TITLE: return HudSkin.HUD_CYAN;
            case DIM: return HudSkin.HUD_DIM;
            default: return Color.LIGHT_GRAY;
        }
    }

    /** The labels' font, which the map measures them with to keep them clear of each other; null draws none. */
    public BitmapFont font() {
        return font;
    }

    /** Sets the labels' font. */
    public void setFont(BitmapFont font) {
        this.font = font;
    }

    /** Pixels between the lines of a contact's label, and of the tension readout. */
    public float lineHeight() {
        return 14f;
    }

    // ------------------------------------------------------------------ the screen

    /** Under everything, over the whole map screen (x, y, width, height). */
    public void backdrop(Batch batch, float x, float y, float width, float height, float alpha) {
        Color c = color(Role.BACKDROP);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x, y, width, height);
    }

    /** In place of the map, around the canvas's middle (x, y), when there is no orbit to draw. */
    public void noOrbit(Batch batch, float x, float y, float alpha) {
        if (font == null) return;
        font.setColor(color(Role.DIM));
        font.draw(batch, "No orbit to show", x - 50f, y);
    }

    /** The title line along the canvas's top, its top left corner at (x, top): the body and the traffic's counts ("5 stations   12 haulers", or ""). */
    public void title(Batch batch, String body, String counts, float x, float top, float alpha) {
        if (font == null) return;
        Color c = color(Role.TITLE);
        font.setColor(c.r, c.g, c.b, alpha);
        font.draw(batch, "MAP  " + body.toUpperCase(Locale.ROOT) + (counts.isEmpty() ? "" : "   " + counts), x + 16f, top - 14f);
    }

    /** The keys and clicks the map takes, under the title. */
    public void instructions(Batch batch, String text, float x, float top, float alpha) {
        if (font == null) return;
        Color c = color(Role.DIM);
        font.setColor(c.r, c.g, c.b, alpha);
        font.draw(batch, text, x + 16f, top - 32f);
    }

    /** The scale bar, {@code length} pixels long from (x, y), with the distance it stands for; returns the top of what it drew. */
    public float scaleBar(Batch batch, float x, float y, float length, String distance, float alpha) {
        Color c = color(Role.SCALE);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x, y, length, 1.5f);
        batch.draw(white, x, y, 1.5f, 6f);
        batch.draw(white, x + length - 1.5f, y, 1.5f, 6f);
        if (font != null) {
            font.setColor(c.r, c.g, c.b, alpha);
            font.draw(batch, distance, x, y + 22f);
        }
        return y + 44f;
    }

    // ------------------------------------------------------------------ orbits

    /**
     * A piece of a path, already cut to the canvas: a moon's orbit ({@link Role#MOON_ORBIT}), the
     * target's ({@link Role#TARGET_ORBIT}), the orbit now ({@link Role#CURRENT}), the planned path
     * ({@link Role#PLANNED}), a leg of either around a moon, or a proposed transfer
     * ({@link Role#PROPOSED}). A path comes as its pieces in order, each starting where the last ended
     * unless the path left the canvas between them.
     */
    public void path(Batch batch, Role role, float x0, float y0, float x1, float y1, float alpha) {
        Color c = color(role);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        line(batch, x0, y0, x1, y1, 1.5f);
    }

    /**
     * The body the map is drawn around: a disc of {@code radius} pixels centred at (x, y), which zoomed
     * in can be far bigger than the canvas, so only the part between left and right, bottom and top.
     */
    public void body(Batch batch, double x, double y, double radius, float left, float bottom, float right, float top,
                     float alpha) {
        Color c = color(Role.BODY);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        // Zoomed far out it stays a dot rather than vanishing
        if (radius < 3) dot(batch, (float) x, (float) y, 6f, color(Role.BODY_FAR), alpha);
        // A disc of thin horizontal slices, only those on the canvas
        for (double dy = Math.max(-radius, Math.floor(bottom - y)); dy < Math.min(radius, top - y); dy += 1) {
            double w = Math.sqrt(Math.max(0, radius * radius - (dy + 0.5) * (dy + 0.5)));
            float x0 = (float) Math.max(left, x - w), x1 = (float) Math.min(right, x + w);
            if (x1 > x0) batch.draw(white, x0, (float) (y + dy), x1 - x0, 1f);
        }
    }

    /**
     * Where a moon the path meets will be by then, at (x, y): its outline and its sphere of influence,
     * radii in pixels, each 0 when it is too small or far too big to draw.
     */
    public void ghost(Batch batch, float x, float y, float radius, float soiRadius, float alpha) {
        if (soiRadius > 0) circle(batch, x, y, soiRadius, 1f, color(Role.GHOST_SOI), alpha);
        if (radius > 0) circle(batch, x, y, radius, 1.5f, color(Role.GHOST), alpha);
    }

    /** The closest approach to the target along the planned path: where the ship and the target will be. */
    public void approach(Batch batch, float shipX, float shipY, float targetX, float targetY, float alpha) {
        Color t = color(Role.TARGET);
        batch.setColor(t.r, t.g, t.b, alpha);
        line(batch, shipX, shipY, targetX, targetY, 1.5f);
        dot(batch, shipX, shipY, 4f, color(Role.SHIP), alpha);
        dot(batch, targetX, targetY, 4f, t, alpha);
    }

    /** A burn on the path: a planned one ({@link Role#PLANNED}), the selected one ({@link Role#SELECTED}) or one of a proposed transfer ({@link Role#PROPOSED}). */
    public void burn(Batch batch, Role role, float x, float y, float alpha) {
        dot(batch, x, y, role == Role.SELECTED ? 7f : 5f, color(role), alpha);
    }

    /** The player's ship on its orbit. */
    public void ship(Batch batch, float x, float y, float alpha) {
        dot(batch, x, y, 5f, color(Role.SHIP), alpha);
    }

    /** A moon, or the body the map's body orbits, {@code size} pixels across; one off the canvas is pulled to its edge. */
    public void moon(Batch batch, float x, float y, float size, float alpha) {
        dot(batch, x, y, size, color(Role.MOON), alpha);
    }

    /** A station, or the navigation target, where the orbit map marks them itself (no traffic layer over it). */
    public void marker(Batch batch, float x, float y, boolean target, float alpha) {
        Color c = color(target ? Role.TARGET : Role.STATION);
        float size = target ? 9f : 5f;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float x0 = x - size / 2f, y0 = y - size / 2f;
        batch.draw(white, x0, y0, size, 1.5f);
        batch.draw(white, x0, y0 + size - 1.5f, size, 1.5f);
        batch.draw(white, x0, y0, 1.5f, size);
        batch.draw(white, x0 + size - 1.5f, y0, 1.5f, size);
    }

    /**
     * A mark on the path in its role's colour ({@link Role#CURRENT}, {@link Role#PLANNED} or
     * {@link Role#IMPACT}): an apsis as a hollow square, a change of sphere of influence as a diamond,
     * an impact as a cross.
     */
    public void mark(Batch batch, MarkKind kind, Role role, float x, float y, float alpha) {
        Color c = color(role);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        switch (kind) {
            case IMPACT:
                batch.draw(white, x - 6f, y - 1f, 6f, 1f, 12f, 2f, 1f, 1f, 45f);
                batch.draw(white, x - 6f, y - 1f, 6f, 1f, 12f, 2f, 1f, 1f, -45f);
                break;
            case SOI_ENTRY:
            case SOI_EXIT:
                batch.draw(white, x - 4f, y - 4f, 4f, 4f, 8f, 8f, 1f, 1f, 45f);
                break;
            default:
                // Small enough not to hide the path under it
                batch.draw(white, x - 4f, y - 4f, 8f, 1.5f);
                batch.draw(white, x - 4f, y + 2.5f, 8f, 1.5f);
                batch.draw(white, x - 4f, y - 4f, 1.5f, 8f);
                batch.draw(white, x + 2.5f, y - 4f, 1.5f, 8f);
        }
    }

    /**
     * A label on the orbit map, in its role's colour: a moon's or the target's name, a mark's text, a
     * burn's countdown. Its top at {@code top}; left-aligned from x, or right-aligned to x + width. The
     * map has already measured it in {@link #font} and kept it clear of the others.
     */
    public void label(Batch batch, Role role, String text, float x, float top, float width, int align, float alpha) {
        if (font == null) return;
        Color c = color(role);
        font.setColor(c.r, c.g, c.b, alpha);
        font.draw(batch, text, x, top, width, align, false);
    }

    // ------------------------------------------------------------------ traffic

    /** A contact: a station as a square, a hauler as a diamond, a hostile as a triangle (hollow for a last known position), the rest as grey diamonds. */
    public void contact(Batch batch, ContactKind kind, boolean stale, float x, float y, float alpha) {
        Color col = contactColor(kind, stale);
        batch.setColor(col.r, col.g, col.b, col.a * alpha);
        if (kind == ContactKind.STATION) {
            outline(batch, x - 5f, y - 5f, 10f, 10f, 2f);
        } else if (kind == ContactKind.HAULER) {
            batch.draw(white, x - 3.5f, y - 3.5f, 3.5f, 3.5f, 7f, 7f, 1f, 1f, 45f);
        } else if (kind == ContactKind.HOSTILE) {
            triangle(batch, x, y, 6f, stale);
        } else {
            batch.draw(white, x - 4f, y - 4f, 4f, 4f, 8f, 8f, 1f, 1f, 45f);
        }
    }

    /** A contract's waypoint, ringed. */
    public void objective(Batch batch, float x, float y, float alpha) {
        Color c = color(Role.OBJECTIVE);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        int n = MathUtils.clamp((int) (14f * 0.6f), 12, 96);
        float r = 14f, t = 2f, seg = MathUtils.PI2 * r / n + 0.5f;
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float px = x + MathUtils.cos(a) * r, py = y + MathUtils.sin(a) * r;
            batch.draw(white, px, py - t / 2f, 0f, t / 2f, seg, t, 1f, 1f, a * MathUtils.radiansToDegrees + 90f);
        }
    }

    /** The navigation target among the traffic, in four corner brackets. */
    public void targetBrackets(Batch batch, float x, float y, float alpha) {
        Color c = color(Role.TARGET);
        batch.setColor(c.r, c.g, c.b, alpha);
        float h = 9f, arm = 4.5f, t = 2f;
        batch.draw(white, x - h, y + h - t, arm, t);
        batch.draw(white, x - h, y + h - arm, t, arm);
        batch.draw(white, x + h - arm, y + h - t, arm, t);
        batch.draw(white, x + h - t, y + h - arm, t, arm);
        batch.draw(white, x - h, y - h, arm, t);
        batch.draw(white, x - h, y - h, t, arm);
        batch.draw(white, x + h - arm, y - h, arm, t);
        batch.draw(white, x + h - t, y - h, t, arm);
    }

    /** A line from a contact out to its label, where the label moved clear of another. */
    public void leader(Batch batch, ContactKind kind, boolean stale, float x0, float y0, float x1, float y1, float alpha) {
        Color c = contactColor(kind, stale);
        batch.setColor(c.r, c.g, c.b, c.a * alpha * 0.5f);
        float dx = x1 - x0, dy = y1 - y0;
        batch.draw(white, x0, y0 - 0.5f, 0f, 0.5f, (float) Math.hypot(dx, dy), 1f, 1f, 1f,
                MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    /** A contact's name, its top left at (x, y): in its kind's colour, or the target's. */
    public void contactName(Batch batch, ContactKind kind, boolean stale, boolean target, String name, float x, float y,
                            float alpha) {
        text(batch, target ? color(Role.TARGET) : contactColor(kind, stale), name, x, y, alpha);
    }

    /** A contact's detail under its name ("docked at Highport", "last seen 3 h ago"). */
    public void contactDetail(Batch batch, String detail, float x, float y, float alpha) {
        text(batch, color(Role.DIM), detail, x, y, alpha);
    }

    /** The tension readout, its heading's top left at (x, top) and a line per region under it, high ones in {@link Role#HOSTILE}. */
    public void regions(Batch batch, Array<Region> regions, float x, float top, float alpha) {
        if (regions.size == 0) return;
        text(batch, color(Role.DIM), "TENSION", x, top, alpha);
        float y = top - lineHeight();
        for (Region r : regions) {
            text(batch, color(r.high ? Role.HOSTILE : Role.DIM), r.line, x, y, alpha);
            y -= lineHeight();
        }
    }

    /** The colour a contact is drawn in: its kind's, dimmed for a last known position. */
    protected Color contactColor(ContactKind kind, boolean stale) {
        Color col = color(kind == ContactKind.STATION ? Role.STATION : kind == ContactKind.HAULER ? Role.HAULER
                : kind == ContactKind.HOSTILE ? Role.HOSTILE : Role.OTHER);
        return stale ? dimmed.set(col).mul(0.6f, 0.6f, 0.6f, 0.8f) : col;
    }

    // ------------------------------------------------------------------ planning

    /**
     * One of the selected burn's handles: 0 prograde, 1 retrograde, 2 radial out, 3 radial in. It sits
     * {@code reach} pixels out from the burn at (x, y) along (dx, dy), a unit vector on screen;
     * {@code hot} while it is dragged or under the pointer.
     */
    public void handle(Batch batch, int handle, float x, float y, float dx, float dy, float reach, boolean hot, float alpha) {
        Color c = color(handle < 2 ? Role.PROGRADE : Role.RADIAL);
        float a = alpha * (hot ? 1f : 0.75f);
        float hx = x + dx * reach, hy = y + dy * reach;
        batch.setColor(c.r, c.g, c.b, 0.35f * a);
        turnedLine(batch, x + dx * 8f, y + dy * 8f, hx, hy, 1.5f);
        batch.setColor(c.r, c.g, c.b, a);
        int n = 20;
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            turnedLine(batch, hx + MathUtils.cos(a0) * 7f, hy + MathUtils.sin(a0) * 7f, hx + MathUtils.cos(a1) * 7f,
                    hy + MathUtils.sin(a1) * 7f, 1.5f);
        }
        if (handle % 2 == 0) {
            // Prograde and radial out: a dot in the ring; retrograde and radial in: a cross
            batch.draw(white, hx - 2f, hy - 2f, 4f, 4f);
        } else {
            batch.draw(white, hx - 5f, hy - 0.75f, 5f, 0.75f, 10f, 1.5f, 1f, 1f, 45f);
            batch.draw(white, hx - 5f, hy - 0.75f, 5f, 0.75f, 10f, 1.5f, 1f, 1f, -45f);
        }
    }

    // ------------------------------------------------------------------ shapes

    /** A square dot {@code size} across. */
    protected void dot(Batch batch, float x, float y, float size, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x - size / 2f, y - size / 2f, size, size);
    }

    /** A circle outline {@code t} wide, as short straight segments. */
    protected void circle(Batch batch, float x, float y, float r, float t, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        int n = MathUtils.clamp((int) (r * 0.6f), 16, 160);
        for (int i = 0; i < n; i++) {
            // Math's sine rather than MathUtils' table: a sphere of influence can be far bigger than the map
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            line(batch, x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, x + (float) Math.cos(a1) * r,
                    y + (float) Math.sin(a1) * r, t);
        }
    }

    /**
     * A line {@code width} wide as one quad, its corners set exactly: the batch's rotated draw takes its
     * angle from a lookup table, which over a long line puts the far end a few pixels astray.
     */
    protected void line(Batch batch, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        // Half the width across the line
        float nx = -dy / len * width / 2f, ny = dx / len * width / 2f;
        float color = batch.getPackedColor();
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        corner(0, x0 - nx, y0 - ny, color, u, v);
        corner(1, x0 + nx, y0 + ny, color, u, v);
        corner(2, x1 + nx, y1 + ny, color, u, v);
        corner(3, x1 - nx, y1 - ny, color, u, v);
        batch.draw(white.getTexture(), quad, 0, quad.length);
    }

    private void corner(int i, float x, float y, float color, float u, float v) {
        int at = i * 5;
        quad[at] = x;
        quad[at + 1] = y;
        quad[at + 2] = color;
        quad[at + 3] = u;
        quad[at + 4] = v;
    }

    /** A short line as the batch's rotated draw, for the handles. */
    private void turnedLine(Batch batch, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    private void outline(Batch batch, float x, float y, float w, float h, float t) {
        batch.draw(white, x, y, w, t);
        batch.draw(white, x, y + h - t, w, t);
        batch.draw(white, x, y, t, h);
        batch.draw(white, x + w - t, y, t, h);
    }

    /** A triangle pointing up, of half-size {@code r}, outlined only when {@code hollow}. */
    private void triangle(Batch batch, float x, float y, float r, boolean hollow) {
        if (!hollow) {
            for (float dy = -r; dy < r; dy += 1f) {
                float w = r * (r - dy) / (2f * r);
                batch.draw(white, x - w, y + dy, 2f * w, 1f);
            }
            return;
        }
        float side = (float) Math.hypot(r, 2f * r);
        float angle = MathUtils.atan2(2f * r, r) * MathUtils.radiansToDegrees;
        batch.draw(white, x - r, y - r, 2f * r, 1.5f);
        batch.draw(white, x - r, y - r, 0f, 0f, side, 1.5f, 1f, 1f, angle);
        batch.draw(white, x + r, y - r, 0f, 0f, side, 1.5f, 1f, 1f, 180f - angle);
    }

    private void text(Batch batch, Color c, String s, float x, float y, float alpha) {
        if (font == null) return;
        font.setColor(c.r, c.g, c.b, c.a * alpha);
        font.draw(batch, s, x, y);
    }
}
