package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Region;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The map under the Nasa-punk HUDs, the same under all three: the system plot the mockups draw
 * (documentation/ui-overhaul/nasapunk/mockups/screens.js {@code systemPlot}; the map screens C5, T4
 * and S4). The plot is glass. The orbit now is white, the planned path magenta (guidance), a
 * transfer proposed but not accepted white and dashed, as a flight computer shows a modified route
 * until EXEC; the selected burn and its handles are cyan, the other burns hollow magenta diamonds.
 * The Earth is a dark disc ringed blue, a moon grey, a moon where the path meets it a dashed outline
 * in its dashed sphere of influence. Contacts take their affiliation's shape (style section 7): a
 * station a square with a centre dot, a hauler a square, a hostile a red diamond, the rest a dim
 * dashed square; the navigation target a magenta ring. Labels are B612 in capitals.
 *
 * <p>The map keeps where everything goes, the zoom, the panning, the handles and the picks
 * ({@link MapPainter}); this draws each piece where the map puts it.
 */
public final class NpMapPainter extends MapPainter implements Disposable {
    /** The labels' size and spacing (S_LABEL), and the pitch between a contact's lines. */
    static final float LABEL = 12f, LABEL_TRACK = 0.5f, LINE = 15f;

    // The plot's own shades (screens.js systemPlot)
    static final Color EARTH = NpColors.rgb(0x0B1622), EARTH_RIM = NpColors.rgb(0x2C6A96);
    static final Color MOON = NpColors.rgb(0x141A20), MOON_RIM = NpColors.rgb(0x6E7C88);
    static final Color GHOST = NpColors.rgb(0x4A6276), GHOST_SOI = NpColors.rgb(0x2A3B4C);

    private final NpKit kit;
    private BitmapFont label;
    private float scale = 1f;
    /** The body the map was last drawn round, from its title: the Earth is blue, the rest grey. */
    private boolean earth = true;
    /** The canvas as the body last gave it, stage units, y up, so far-off circles draw only what shows. */
    private float clipL = Float.NaN, clipB, clipR, clipT;
    /** How far the map's title and its row of hints come down over the canvas's top. */
    static final float HEADER = 60f;
    /** How far in from the canvas's edge the map pulls a body or the target that is off it (OrbitMap's EDGE). */
    private static final float EDGE = 8f;
    /** How far the last body or target marker came down clear of the heading, for its name to follow. */
    private float drop;
    /** The path being drawn: its role and where its last piece ended, so a dashed path keeps its rhythm. */
    private Role chain;
    private float chainX, chainY, phase;

    /** Needs GL: makes the kit's white and the labels' font. */
    public static NpMapPainter create() {
        return new NpMapPainter(new NpKit());
    }

    private NpMapPainter(NpKit kit) {
        super(kit.whiteRegion(), null);
        this.kit = kit;
        label = NpFonts.create(NpFonts.BOLD, LABEL, scale, LABEL_TRACK, true);
    }

    /** Rasterises the labels again at the UI scale {@code uiScale}. */
    public void rescale(float uiScale) {
        if (uiScale <= 0f || Math.abs(uiScale - scale) < 1e-3f) return;
        scale = uiScale;
        kit.setScale(uiScale);
        BitmapFont was = label;
        label = NpFonts.create(NpFonts.BOLD, LABEL, scale, LABEL_TRACK, true);
        was.dispose();
    }

    @Override
    public BitmapFont font() {
        return label;
    }

    /** The map's labels are the style's own font: the game's font is not taken. */
    @Override
    public void setFont(BitmapFont font) {
    }

    @Override
    public float lineHeight() {
        return LINE;
    }

    @Override
    public Color color(Role role) {
        switch (role) {
            case BACKDROP: return NpColors.GLASS;
            case BODY: return earth ? EARTH : MOON;
            case BODY_FAR: return earth ? EARTH_RIM : MOON_RIM;
            case MOON: return MOON_RIM;
            case MOON_ORBIT: return NpColors.GRATICULE;
            case GHOST: return GHOST;
            case GHOST_SOI: return GHOST_SOI;
            case CURRENT:
            case CURRENT_LEG:
            case PROPOSED:
            case SHIP:
            case STATION:
            case HAULER:
            case TITLE: return NpColors.WHITE;
            case PLANNED:
            case PLANNED_LEG:
            case TARGET:
            case TARGET_ORBIT:
            case OBJECTIVE: return NpColors.MAGENTA;
            case SELECTED:
            case PROGRADE:
            case RADIAL: return NpColors.CYAN;
            case IMPACT:
            case HOSTILE: return NpColors.RED;
            default: return NpColors.DIM;
        }
    }

    /** How wide a path is drawn, by its role. */
    static float width(Role role) {
        switch (role) {
            case PLANNED: return 2.5f;
            case PLANNED_LEG:
            case PROPOSED: return 2f;
            case MOON_ORBIT:
            case TARGET_ORBIT: return 1.2f;
            default: return 1.5f;
        }
    }

    /** How much of its colour a path shows: a leg round a moon, and the target's orbit, are quieter. */
    static float opacity(Role role) {
        switch (role) {
            case CURRENT_LEG: return 0.6f;
            case PLANNED_LEG: return 0.85f;
            case TARGET_ORBIT: return 0.5f;
            default: return 1f;
        }
    }

    /** The canvas, local (0, 0) at the stage's origin: y is drawn at {@code -y}. */
    private NpCanvas cv(Batch batch, float alpha) {
        return kit.canvas().begin(batch, 0f, 0f, alpha);
    }

    // ------------------------------------------------------------------ the screen

    @Override
    public void backdrop(Batch batch, float x, float y, float width, float height, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        cv.fill(x, -y - height, width, height, NpColors.GLASS);
        cv.end();
    }

    @Override
    public void noOrbit(Batch batch, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        cv.text(x, -y, "NO ORBIT TO SHOW", LABEL, true, NpColors.DIM, 0.6f, NpCanvas.MIDDLE);
        cv.end();
    }

    @Override
    public void title(Batch batch, String body, String counts, float x, float top, float alpha) {
        earth = body == null || body.isEmpty() || body.equalsIgnoreCase("earth");
        NpCanvas cv = cv(batch, alpha);
        String name = (body == null || body.isEmpty() ? "MAP" : body.toUpperCase(Locale.ROOT) + " SYSTEM");
        float base = -top + 26f;
        float w = cv.text(x + 16f, base, name, 15f, true, NpColors.WHITE, 0.8f, NpCanvas.START);
        float mx = x + 16f + w + 10f;
        mx += cv.text(mx, base, "MAP", LABEL, true, NpColors.DIM, 0.8f, NpCanvas.START);
        String c = counts(counts);
        if (!c.isEmpty()) cv.text(mx + 16f, base, c, LABEL, true, NpColors.DIM, LABEL_TRACK, NpCanvas.START);
        cv.end();
    }

    /** "5 stations   12 haulers" as the plot's title row gives it: "5 STATIONS · 12 HAULERS". */
    static String counts(String counts) {
        if (counts == null || counts.trim().isEmpty()) return "";
        return String.join(" · ", counts.trim().split("\\s{2,}")).toUpperCase(Locale.ROOT);
    }

    private static final Pattern HINT = Pattern.compile("\\[([^\\]]+)\\]\\s*([^\\[]*)");

    /**
     * The map's keys and clicks as a row of hints under the title: a key on its cap, a click or a drag
     * in white, each with what it does in dim.
     */
    @Override
    public void instructions(Batch batch, String text, float x, float top, float alpha) {
        if (text == null || text.isEmpty()) return;
        NpCanvas cv = cv(batch, alpha);
        float px = x + 16f, base = -top + 48f;
        for (String[] h : hints(text)) {
            if (isKey(h[0])) px += NpHardware.keyCap(cv, px, base - 12.5f, h[0], 10.5f, 16f) + 6f;
            else px += cv.text(px, base, h[0], LABEL, true, NpColors.WHITE, LABEL_TRACK, NpCanvas.START) + 6f;
            px += cv.text(px, base, h[1], LABEL, true, NpColors.DIM, LABEL_TRACK, NpCanvas.START) + 16f;
        }
        cv.end();
    }

    /** "[M] close   [wheel] zoom" as its hints: what to press or do, upper case, and what it does. */
    static Array<String[]> hints(String text) {
        Array<String[]> out = new Array<>();
        Matcher m = HINT.matcher(text);
        while (m.find()) out.add(new String[] {m.group(1).trim().toUpperCase(Locale.ROOT), m.group(2).trim().toUpperCase(Locale.ROOT)});
        return out;
    }

    /** A key, printed on a cap, rather than something done with the mouse: a short word with no space. */
    static boolean isKey(String s) {
        return !s.isEmpty() && s.length() <= 3 && s.indexOf(' ') < 0;
    }

    @Override
    public float scaleBar(Batch batch, float x, float y, float length, String distance, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        float yy = -y - 3f;
        cv.line(x, yy, x + length, yy, 1.5f, NpColors.DIM);
        cv.line(x, yy - 4f, x, yy + 0.75f, 1.5f, NpColors.DIM);
        cv.line(x + length, yy - 4f, x + length, yy + 0.75f, 1.5f, NpColors.DIM);
        cv.text(x + length / 2f, yy - 7f, distance.toUpperCase(Locale.ROOT), LABEL, true, NpColors.DIM, LABEL_TRACK, NpCanvas.MIDDLE);
        cv.end();
        return y + 3f + 7f + LABEL + 8f;
    }

    // ------------------------------------------------------------------ orbits

    @Override
    public void path(Batch batch, Role role, float x0, float y0, float x1, float y1, float alpha) {
        Color c = color(role);
        batch.setColor(c.r, c.g, c.b, c.a * alpha * opacity(role));
        float w = width(role);
        boolean joined = role == chain && Math.abs(x0 - chainX) < 0.5f && Math.abs(y0 - chainY) < 0.5f;
        if (!joined) phase = 0f;
        if (role == Role.PROPOSED) phase = dashes(batch, x0, y0, x1, y1, w, 7f, 4f, phase);
        else line(batch, x0, y0, x1, y1, w);
        chain = role;
        chainX = x1;
        chainY = y1;
    }

    /**
     * Draws the dashes of a dashed line from ({@code x0}, {@code y0}), {@code on} long with
     * {@code off} between, starting {@code phase} into the pattern; returns how far into it the line
     * ends, for the next piece to carry on from.
     */
    private float dashes(Batch batch, float x0, float y0, float x1, float y1, float w, float on, float off, float phase) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return phase;
        float ux = dx / len, uy = dy / len, t = 0f, p = phase, period = on + off;
        while (t < len) {
            float run;
            if (p < on) {
                run = Math.min(on - p, len - t);
                line(batch, x0 + ux * t, y0 + uy * t, x0 + ux * (t + run), y0 + uy * (t + run), w);
            } else {
                run = Math.min(period - p, len - t);
            }
            t += run;
            p += run;
            if (p >= period - 1e-4f) p = 0f;
        }
        return p;
    }

    /** How far into a dash pattern of {@code on} and {@code off} a line {@code len} long ends, from {@code phase}. */
    static float phaseAfter(float phase, float len, float on, float off) {
        float period = on + off;
        float p = (phase + len) % period;
        return p < 1e-4f || p > period - 1e-4f ? 0f : p;
    }

    @Override
    public void body(Batch batch, double x, double y, double radius, float left, float bottom, float right, float top,
                     float alpha) {
        clipL = left;
        clipB = bottom;
        clipR = right;
        clipT = top;
        if (radius < 3) {
            NpCanvas cv = cv(batch, alpha);
            cv.disc((float) x, (float) -y, 6f, color(Role.BODY_FAR));
            cv.end();
            return;
        }
        Color c = color(Role.BODY);
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        // A disc of thin slices, only those on the canvas: zoomed in it is far bigger than the map
        for (double dy = Math.max(-radius, Math.floor(bottom - y)); dy < Math.min(radius, top - y); dy += 1) {
            double half = Math.sqrt(Math.max(0, radius * radius - (dy + 0.5) * (dy + 0.5)));
            float sx0 = (float) Math.max(left, x - half), sx1 = (float) Math.min(right, x + half);
            if (sx1 > sx0) batch.draw(white, sx0, (float) (y + dy), sx1 - sx0, 1f);
        }
        Color rim = color(Role.BODY_FAR);
        batch.setColor(rim.r, rim.g, rim.b, rim.a * alpha);
        arc(batch, x, y, radius, 1.5f, 0f, 0f);
    }

    @Override
    public void ghost(Batch batch, float x, float y, float radius, float soiRadius, float alpha) {
        if (soiRadius > 0) {
            batch.setColor(GHOST_SOI.r, GHOST_SOI.g, GHOST_SOI.b, alpha);
            arc(batch, x, y, soiRadius, 1f, 4f, 3f);
        }
        if (radius > 0) {
            batch.setColor(GHOST.r, GHOST.g, GHOST.b, alpha);
            arc(batch, x, y, radius, 1.2f, 2f, 2f);
        }
    }

    /**
     * A circle's outline in the batch's colour, solid or dashed ({@code on} 0 for solid), drawn only
     * round the part near the canvas: a sphere of influence, or the Earth zoomed in, can be many
     * canvases across, and then only a shallow arc of it shows.
     */
    private void arc(Batch batch, double cx, double cy, double r, float w, float on, float off) {
        double from = 0, sweep = Math.PI * 2;
        if (!Float.isNaN(clipL)) {
            double mx = (clipL + clipR) / 2.0, my = (clipB + clipT) / 2.0;
            double reach = Math.hypot(clipR - clipL, clipT - clipB);
            double toMid = Math.hypot(mx - cx, my - cy);
            // Wholly off the canvas: nothing of it shows
            if (toMid > r + reach || toMid + reach < r) return;
            double span = Math.min(Math.PI, 1.2 * reach / r + 0.02);
            if (span < Math.PI) {
                double mid = Math.atan2(my - cy, mx - cx);
                from = mid - span;
                sweep = 2 * span;
            }
        }
        int n = (int) Math.max(24, Math.min(2048, Math.ceil(r * sweep / 3.0)));
        float p = 0f;
        double px = cx + r * Math.cos(from), py = cy + r * Math.sin(from);
        for (int i = 1; i <= n; i++) {
            double a = from + sweep * i / n;
            double qx = cx + r * Math.cos(a), qy = cy + r * Math.sin(a);
            float len = (float) Math.hypot(qx - px, qy - py);
            if (visible(px, py, qx, qy)) {
                if (on > 0f) p = dashes(batch, (float) px, (float) py, (float) qx, (float) qy, w, on, off, p);
                else line(batch, (float) px, (float) py, (float) qx, (float) qy, w);
            } else if (on > 0f) {
                p = phaseAfter(p, len, on, off);
            }
            px = qx;
            py = qy;
        }
    }

    /** Whether a segment may cross the canvas: false only when both its ends are off the same side. */
    private boolean visible(double x0, double y0, double x1, double y1) {
        if (Float.isNaN(clipL)) return true;
        double m = 4;
        return !(x0 < clipL - m && x1 < clipL - m || x0 > clipR + m && x1 > clipR + m
                || y0 < clipB - m && y1 < clipB - m || y0 > clipT + m && y1 > clipT + m);
    }

    @Override
    public void approach(Batch batch, float shipX, float shipY, float targetX, float targetY, float alpha) {
        batch.setColor(NpColors.MAGENTA.r, NpColors.MAGENTA.g, NpColors.MAGENTA.b, alpha);
        dashes(batch, shipX, shipY, targetX, targetY, 1.2f, 3f, 3f, 0f);
        NpCanvas cv = cv(batch, alpha);
        cv.disc(shipX, -shipY, 7f, NpColors.GLASS);
        cv.ring(shipX, -shipY, 3.2f, 1.4f, NpColors.WHITE);
        cv.disc(targetX, -targetY, 5f, NpColors.MAGENTA);
        cv.end();
    }

    @Override
    public void burn(Batch batch, Role role, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        Color c = color(role);
        float r = 7f, yy = -y;
        float[] p = {x, yy - r, x + r, yy, x, yy + r, x - r, yy};
        cv.fillPoly(p, NpColors.GLASS);
        if (role == Role.PROPOSED) {
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                cv.dashed(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], 1.8f, c, 3f, 2f);
            }
        } else {
            cv.poly(p, true, 1.8f, c);
        }
        cv.end();
    }

    @Override
    public void ship(Batch batch, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        cv.disc(x, -y, 5.2f, NpColors.WHITE);
        cv.end();
    }

    @Override
    public void moon(Batch batch, float x, float y, float size, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        float d = Math.max(size, 9f);
        drop = dropUnderHeading(x, y, d / 2f);
        y += drop;
        cv.disc(x, -y, d, MOON);
        cv.ring(x, -y, d / 2f, 1.3f, MOON_RIM);
        cv.end();
    }

    @Override
    public void marker(Batch batch, float x, float y, boolean target, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        drop = target ? dropUnderHeading(x, y, 9f) : 0f;
        y += drop;
        if (target) {
            cv.ring(x, -y, 9f, 1.5f, NpColors.MAGENTA);
            cv.disc(x, -y, 3f, NpColors.MAGENTA);
        } else {
            NpSymbols.station(cv, x, -y, 4f, NpColors.WHITE, 1.2f);
        }
        cv.end();
    }

    @Override
    public void mark(Batch batch, MarkKind kind, Role role, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        Color c = color(role);
        switch (kind) {
            case IMPACT:
                cv.line(x - 5f, -y - 5f, x + 5f, -y + 5f, 2f, NpColors.RED);
                cv.line(x - 5f, -y + 5f, x + 5f, -y - 5f, 2f, NpColors.RED);
                break;
            case SOI_ENTRY:
            case SOI_EXIT:
                // Where the path crosses a sphere's edge: a dashed ring, as the spheres are drawn
                cv.disc(x, -y, 9f, NpColors.GLASS);
                for (int i = 0; i < 8; i += 2) cv.arc(x, -y, 4.5f, i * 45f, i * 45f + 30f, 1.5f, c, 1f);
                break;
            default:
                // An apsis: a small hollow ring on the path, its height and time beside it
                cv.disc(x, -y, 8f, NpColors.GLASS);
                cv.ring(x, -y, 3.5f, 1.5f, c);
        }
        cv.end();
    }

    /**
     * How far a marker at ({@code x}, {@code y}), {@code r} round, comes down (a negative distance) to
     * clear the map's title and hints: only one the map pulled in to the edge of its view because it
     * is off the map, as the Moon is from a low orbit. One on the map stays where it is.
     */
    private float dropUnderHeading(float x, float y, float r) {
        if (Float.isNaN(clipL)) return 0f;
        float under = clipT - HEADER - r;
        if (y <= under) return 0f;
        // The map pulls a body that is off it in to a square round the middle of the view
        float mx = (clipL + clipR) / 2f, my = (clipB + clipT) / 2f, half = Math.min(clipR - clipL, clipT - clipB) / 2f;
        boolean pulledIn = Math.max(Math.abs(x - mx), Math.abs(y - my)) >= half - EDGE - 0.5f;
        return pulledIn ? under - y : 0f;
    }

    @Override
    public void label(Batch batch, Role role, String text, float x, float top, float width, int align, float alpha) {
        if (role == Role.MOON || role == Role.TARGET) {
            // A body's or the target's name follows its marker down clear of the heading
            top += drop;
            drop = 0f;
        }
        // A body's name is a label line: dim, as the plot names the Earth and the Moon
        Color c = role == Role.MOON ? NpColors.DIM : color(role);
        label.setColor(c.r, c.g, c.b, c.a * alpha);
        label.draw(batch, text, x, top, width, align, false);
    }

    // ------------------------------------------------------------------ traffic

    @Override
    public void contact(Batch batch, ContactKind kind, boolean stale, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha * (stale ? 0.6f : 1f));
        Color c = contactColor(kind, false);
        float yy = -y;
        if (stale) {
            // A last known position: the shape's outline dashed
            NpSymbols.derelict(cv, x, yy, kind == ContactKind.HOSTILE ? 4f : 3.5f, c, 1.2f);
        } else if (kind == ContactKind.STATION) {
            NpSymbols.station(cv, x, yy, 4f, c, 1.2f);
        } else if (kind == ContactKind.HAULER) {
            NpSymbols.square(cv, x, yy, 3f, c, 1.2f, false);
        } else if (kind == ContactKind.HOSTILE) {
            NpSymbols.diamond(cv, x, yy, 4f, c, 1.4f, false);
        } else {
            NpSymbols.derelict(cv, x, yy, 3.5f, c, 1.2f);
        }
        cv.end();
    }

    @Override
    public void objective(Batch batch, float x, float y, float alpha) {
        NpCanvas cv = cv(batch, alpha);
        for (int i = 0; i < 12; i++) cv.arc(x, -y, 15f, i * 30f, i * 30f + 18f, 1.2f, NpColors.MAGENTA, 1f);
        cv.end();
    }

    @Override
    public void targetBrackets(Batch batch, float x, float y, float alpha) {
        // The navigation target's magenta ring
        NpCanvas cv = cv(batch, alpha);
        cv.ring(x, -y, 12f, 1.5f, NpColors.MAGENTA);
        cv.end();
    }

    @Override
    public void leader(Batch batch, ContactKind kind, boolean stale, float x0, float y0, float x1, float y1, float alpha) {
        Color c = contactColor(kind, stale);
        batch.setColor(c.r, c.g, c.b, c.a * alpha * 0.5f);
        line(batch, x0, y0, x1, y1, 1f);
    }

    @Override
    public void contactName(Batch batch, ContactKind kind, boolean stale, boolean target, String name, float x, float y,
                            float alpha) {
        text(batch, target ? NpColors.MAGENTA : contactColor(kind, stale), name, x, y, alpha);
    }

    @Override
    public void contactDetail(Batch batch, String detail, float x, float y, float alpha) {
        text(batch, NpColors.DIM, detail, x, y, alpha);
    }

    @Override
    public void regions(Batch batch, Array<Region> regions, float x, float top, float alpha) {
        if (regions.size == 0) return;
        text(batch, NpColors.DIM, "TENSION", x, top, alpha);
        float y = top - LINE;
        for (Region r : regions) {
            // Tension running high is a caution, not yet a fight
            text(batch, r.high ? NpColors.AMBER : NpColors.DIM, r.line, x, y, alpha);
            y -= LINE;
        }
    }

    /** A contact's colour: its affiliation's, or dim for a last known position. */
    @Override
    protected Color contactColor(ContactKind kind, boolean stale) {
        if (stale) return NpColors.DIM;
        switch (kind) {
            case HOSTILE: return NpColors.RED;
            case STATION:
            case HAULER: return NpColors.WHITE;
            default: return NpColors.DIM;
        }
    }

    // ------------------------------------------------------------------ planning

    @Override
    public void handle(Batch batch, int handle, float x, float y, float dx, float dy, float reach, boolean hot, float alpha) {
        // The selected burn's handles, as the plot draws them: a cyan line out to a small square, filled while held
        float hx = x + dx * reach, hy = y + dy * reach;
        batch.setColor(NpColors.CYAN.r, NpColors.CYAN.g, NpColors.CYAN.b, alpha * (hot ? 1f : 0.85f));
        line(batch, x + dx * 9f, y + dy * 9f, hx - dx * 4f, hy - dy * 4f, 1f);
        NpCanvas cv = cv(batch, alpha);
        float yy = -hy;
        cv.fill(hx - 4f, yy - 4f, 8f, 8f, hot ? NpColors.CYAN : NpColors.GLASS);
        cv.stroke(hx - 4f, yy - 4f, 8f, 8f, 1.5f, NpColors.CYAN);
        Color ink = hot ? NpColors.GLASS : NpColors.CYAN;
        // Prograde and radial out a dot; retrograde and radial in a cross
        if (handle % 2 == 0) {
            cv.disc(hx, yy, 2.4f, ink);
        } else {
            cv.line(hx - 1.8f, yy - 1.8f, hx + 1.8f, yy + 1.8f, 1.1f, ink);
            cv.line(hx - 1.8f, yy + 1.8f, hx + 1.8f, yy - 1.8f, 1.1f, ink);
        }
        cv.end();
    }

    // ------------------------------------------------------------------ text

    private void text(Batch batch, Color c, String s, float x, float y, float alpha) {
        label.setColor(c.r, c.g, c.b, c.a * alpha);
        label.draw(batch, s, x, y);
    }

    @Override
    public void dispose() {
        label.dispose();
        kit.dispose();
    }
}
