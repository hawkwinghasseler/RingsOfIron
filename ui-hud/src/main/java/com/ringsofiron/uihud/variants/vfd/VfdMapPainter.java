package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Region;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.Locale;

/**
 * The map under the FILAMENT HUDs: the plot (style-vfd.md, section 18.3). The orbit canvas is a
 * graphic dot panel at pitch 3, every dot of it ghosting through the glass, and every piece the map
 * draws lights the panel's own dots: the orbit now every second dot at L2, a planned leg solid at L3,
 * a proposed transfer every third at L3, a moon's orbit every third at L1 and a sphere of influence
 * every second, a leader every second. Marks are sprites on the same dots; labels are 5x7 text at
 * pitch 1.5 on a ground that keeps the dots under them dark, so no line crosses text.
 *
 * <p>The map keeps where everything goes, the zoom, the panning, the handles and the picks
 * ({@link MapPainter}). This lights dots as it is handed each piece, and draws the frame through the
 * kit, glow and all, once the map has handed it the last (its instructions, or that there is no orbit
 * to show). Only the backdrop is drawn as it comes, since the map's own side panel goes over it.
 */
public class VfdMapPainter extends MapPainter implements Disposable {
    /** The plot's dot pitch, and its labels' (style-vfd.md, 18.3). */
    static final float PITCH = 3f, TEXT = 1.5f;
    /** From one line of a label to the next: nine dots of the text's pitch. */
    static final float LINE = 13.5f;

    // The plot's sprites, on its own dots (the mockups' PS)
    static final String[] SHIP = {".###.", "#...#", "#...#", "#...#", ".###."};
    static final String[] STATION = {"#####", "#...#", "#.#.#", "#...#", "#####"};
    static final String[] NEUTRAL = {"..#..", ".#.#.", "#...#", ".#.#.", "..#.."};
    static final String[] HOSTILE = {"#...#", "##.##", ".....", "##.##", "#...#"};
    static final String[] NODE = {".###.", "#...#", "#...#", "#...#", ".###."};
    static final String[] NODE_SELECTED = {".###.", "#####", "#####", "#####", ".###."};
    static final String[] APOAPSIS = {"..#..", ".###.", "#####"};
    static final String[] PERIAPSIS = {"#####", ".###.", "..#.."};
    static final String[] IMPACT = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
    static final String[] FAR = {"##", "##"};
    static final String[] WAYPOINT = {"..#..", ".#.#.", "#...#", ".#.#.", "..#.."};
    static final String[] ARROW_UP = {"..#..", ".###.", "#.#.#", "..#..", "..#.."};
    static final String[] ARROW_DOWN = {"..#..", "..#..", "#.#.#", ".###.", "..#.."};
    static final String[] ARROW_LEFT = {"..#..", ".#...", "#####", ".#...", "..#.."};
    static final String[] ARROW_RIGHT = {"..#..", "...#.", "#####", "...#.", "..#.."};

    private final Plot plot = new Plot(PITCH);
    private final RomFont rom;
    private final BitmapFont labelFont;
    /** Made on the first frame drawn: it holds GL resources. */
    private Filament filament;
    private Texture blank;
    /** This frame's text: x, y (y down) and level each, with the strings in {@link #strings}. */
    private final Array<String> strings = new Array<>();
    private final FloatArray textAt = new FloatArray();
    /** The stage this frame is drawn on, UI units, and its screen pixels per unit. */
    private float stageW = 1280f, stageH = 800f, unitsPx = 1f;
    /** The canvas the orbits are drawn on, y down; none until the map gives it. */
    private float canvasX, canvasY, canvasW, canvasH;
    private boolean hasCanvas;
    /** Where the map's heading starts in this frame's text: what came before it is the plot's own. */
    private int heading = -1;
    /** The path being chained: its role, so its pieces keep one rhythm of dots. */
    private Role chaining;
    private final Filament.Painter frame = this::paintFrame;

    /** Needs GL: the labels' font is the ROM on a texture. */
    public VfdMapPainter() {
        super(null, null);
        rom = new RomFont();
        labelFont = rom.font(6f * TEXT, LINE, TEXT);
    }

    /** The UI is drawn {@code uiScale} times its units now. */
    public void rescale(float uiScale) {
        if (uiScale > 0f) unitsPx = uiScale;
    }

    @Override
    public BitmapFont font() {
        return labelFont;
    }

    @Override
    public void setFont(BitmapFont font) {
        // The plot's labels are the ROM's cells, whatever font the map is given
    }

    @Override
    public float lineHeight() {
        return LINE;
    }

    @Override
    public Color color(Role role) {
        // Everything on the plot is the bare phosphor; how hard it is lit carries the meaning
        return role == Role.BACKDROP ? Ink.GLASS : Color.WHITE;
    }

    // ------------------------------------------------------------------ the screen

    @Override
    public void backdrop(Batch batch, float x, float y, float width, float height, float alpha) {
        stage(batch);
        plot.clear();
        strings.clear();
        textAt.clear();
        heading = -1;
        hasCanvas = false;
        chaining = null;
        // Under the map's side panel too, so drawn now: the dark behind the glass
        if (blank == null) blank = white();
        batch.setColor(Ink.BEZEL_LO.r, Ink.BEZEL_LO.g, Ink.BEZEL_LO.b, alpha);
        batch.draw(blank, x, y, width, height);
        batch.setColor(Color.WHITE);
    }

    @Override
    public void noOrbit(Batch batch, float x, float y, float alpha) {
        text("NO ORBIT TO SHOW", x - width(16) / 2f, stageH - y - 5f, Plot.DIM);
        flush(batch);
    }

    @Override
    public void title(Batch batch, String body, String counts, float x, float top, float alpha) {
        String t = "MAP  " + body.toUpperCase(Locale.ROOT) + (counts.isEmpty() ? "" : "   " + counts);
        float y = stageH - top + 12f;
        heading = strings.size;
        ground(x + 16f, y, width(t.length()), 1);
        text(t, x + 16f, y, Plot.NORM);
    }

    @Override
    public void instructions(Batch batch, String text, float x, float top, float alpha) {
        float y = stageH - top + 12f + LINE + 2f;
        ground(x + 16f, y, width(text.length()), 1);
        text(text, x + 16f, y, Plot.DIM);
        clearHeading();
        // The map's last piece of the frame
        flush(batch);
    }

    @Override
    public float scaleBar(Batch batch, float x, float y, float length, String distance, float alpha) {
        float yd = stageH - y;
        plot.chain(x, yd, x + length, yd, Plot.DIM, 1, true);
        plot.chain(x, yd - 4f, x, yd + 3f, Plot.DIM, 1, true);
        plot.chain(x + length, yd - 4f, x + length, yd + 3f, Plot.DIM, 1, true);
        chaining = null;
        text(distance, x + length + 8f, yd - 5f, Plot.DIM);
        return y + 16f;
    }

    // ------------------------------------------------------------------ orbits

    @Override
    public void path(Batch batch, Role role, float x0, float y0, float x1, float y1, float alpha) {
        int level, every;
        switch (role) {
            case MOON_ORBIT: level = Plot.DIM; every = 3; break;
            case TARGET_ORBIT: level = Plot.DIM; every = 2; break;
            case CURRENT:
            case CURRENT_LEG: level = Plot.NORM; every = 2; break;
            case PLANNED:
            case PLANNED_LEG: level = Plot.FULL; every = 1; break;
            case PROPOSED: level = Plot.FULL; every = 3; break;
            default: level = Plot.DIM; every = 2;
        }
        plot.chain(x0, stageH - y0, x1, stageH - y1, level, every, role != chaining);
        chaining = role;
    }

    @Override
    public void body(Batch batch, double x, double y, double radius, float left, float bottom, float right, float top,
                     float alpha) {
        canvasX = left;
        canvasY = stageH - top;
        canvasW = right - left;
        canvasH = top - bottom;
        hasCanvas = true;
        chaining = null;
        if (radius < 3) {
            plot.sprite(FAR, (float) x, stageH - (float) y, Plot.NORM);
            return;
        }
        plot.disc(x, stageH - y, radius, canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
    }

    @Override
    public void ghost(Batch batch, float x, float y, float radius, float soiRadius, float alpha) {
        float yd = stageH - y;
        if (soiRadius > 0) plot.circle(x, yd, soiRadius, Plot.DIM, 2);
        if (radius > 0) plot.circle(x, yd, radius, Plot.DIM, 1);
        chaining = null;
    }

    @Override
    public void approach(Batch batch, float shipX, float shipY, float targetX, float targetY, float alpha) {
        plot.chain(shipX, stageH - shipY, targetX, stageH - targetY, Plot.DIM, 2, true);
        chaining = null;
        plot.sprite(FAR, shipX, stageH - shipY, Plot.NORM);
        plot.sprite(FAR, targetX, stageH - targetY, Plot.FULL);
    }

    @Override
    public void burn(Batch batch, Role role, float x, float y, float alpha) {
        // The selected burn solid at L3, the rest rings; a proposal's at L1 until it is accepted
        if (role == Role.SELECTED) plot.sprite(NODE_SELECTED, x, stageH - y, Plot.FULL);
        else plot.sprite(NODE, x, stageH - y, role == Role.PROPOSED ? Plot.DIM : Plot.NORM);
    }

    @Override
    public void ship(Batch batch, float x, float y, float alpha) {
        plot.sprite(SHIP, x, stageH - y, Plot.FULL);
    }

    @Override
    public void moon(Batch batch, float x, float y, float size, float alpha) {
        float r = size / 2f, yd = stageH - y;
        if (r < 3f) plot.sprite(FAR, x, yd, Plot.NORM);
        else plot.disc(x, yd, r, x - r - PITCH, yd - r - PITCH, x + r + PITCH, yd + r + PITCH);
    }

    @Override
    public void marker(Batch batch, float x, float y, boolean target, float alpha) {
        plot.sprite(STATION, x, stageH - y, target ? Plot.FULL : Plot.NORM);
        if (target) brackets(x, stageH - y, 4);
    }

    @Override
    public void mark(Batch batch, MarkKind kind, Role role, float x, float y, float alpha) {
        float yd = stageH - y;
        switch (kind) {
            case APOAPSIS: plot.sprite(APOAPSIS, x, yd - 7f, Plot.NORM); break;
            case PERIAPSIS: plot.sprite(PERIAPSIS, x, yd + 7f, Plot.NORM); break;
            case SOI_ENTRY:
            case SOI_EXIT: plot.sprite(WAYPOINT, x, yd, Plot.NORM); break;
            case IMPACT: plot.sprite(IMPACT, x, yd, Plot.FULL); break;
            default:
        }
    }

    @Override
    public void label(Batch batch, Role role, String text, float x, float top, float width, int align, float alpha) {
        float w = measure(text);
        float lx = (align & Align.right) != 0 ? x + width - w : (align & Align.center) != 0 ? x + (width - w) / 2f : x;
        float y = stageH - top;
        ground(lx, y, w, 1);
        text(text, lx, y, labelLevel(role));
    }

    // ------------------------------------------------------------------ traffic

    @Override
    public void contact(Batch batch, ContactKind kind, boolean stale, float x, float y, float alpha) {
        float yd = stageH - y;
        switch (kind) {
            case STATION: plot.sprite(STATION, x, yd, stale ? Plot.DIM : Plot.NORM); break;
            case HOSTILE: plot.sprite(HOSTILE, x, yd, stale ? Plot.DIM : Plot.NORM); break;
            case PLAYER: plot.sprite(SHIP, x, yd, Plot.FULL); break;
            default: plot.sprite(NEUTRAL, x, yd, Plot.DIM);
        }
    }

    @Override
    public void objective(Batch batch, float x, float y, float alpha) {
        plot.circle(x, stageH - y, 14f, Plot.NORM, 2);
        chaining = null;
    }

    @Override
    public void targetBrackets(Batch batch, float x, float y, float alpha) {
        brackets(x, stageH - y, 4);
    }

    @Override
    public void leader(Batch batch, ContactKind kind, boolean stale, float x0, float y0, float x1, float y1, float alpha) {
        plot.chain(x0, stageH - y0, x1, stageH - y1, Plot.DIM, 2, true);
        chaining = null;
    }

    @Override
    public void contactName(Batch batch, ContactKind kind, boolean stale, boolean target, String name, float x, float y,
                            float alpha) {
        float yd = stageH - y;
        ground(x, yd, measure(name), 1);
        text(name, x, yd, target ? Plot.FULL : stale ? Plot.DIM : Plot.NORM);
    }

    @Override
    public void contactDetail(Batch batch, String detail, float x, float y, float alpha) {
        float yd = stageH - y;
        ground(x, yd, measure(detail), 1);
        text(detail, x, yd, Plot.DIM);
    }

    @Override
    public void regions(Batch batch, Array<Region> regions, float x, float top, float alpha) {
        if (regions.size == 0) return;
        float y = stageH - top;
        text("TENSION", x, y, Plot.DIM);
        for (Region r : regions) {
            y += LINE;
            ground(x, y, measure(r.line), 1);
            text(r.line, x, y, r.high ? Plot.FULL : Plot.DIM);
        }
    }

    // ------------------------------------------------------------------ planning

    @Override
    public void handle(Batch batch, int handle, float x, float y, float dx, float dy, float reach, boolean hot, float alpha) {
        // An arrow where the handle sits, pointing the way it pushes, on a dotted stem from the burn
        float bx = x, by = stageH - y, hx = x + dx * reach, hy = stageH - (y + dy * reach);
        int level = hot ? Plot.FULL : handle == 0 ? Plot.NORM : Plot.DIM;
        plot.chain(bx + dx * 12f, by - dy * 12f, hx - dx * 9f, hy + dy * 9f, Plot.DIM, 3, true);
        chaining = null;
        String[] arrow = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? ARROW_RIGHT : ARROW_LEFT) : (dy > 0 ? ARROW_UP : ARROW_DOWN);
        plot.sprite(arrow, hx, hy, level);
    }

    // ------------------------------------------------------------------ drawing

    /** The Nav target's four corner brackets, {@code half} dots out from {@code x, y} (y down), lit full. */
    private void brackets(float x, float y, int half) {
        int ci = plot.col(x), cj = plot.row(y);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int i = ci + sx * half, j = cj + sy * half;
                plot.on(i, j, Plot.FULL);
                plot.on(i - sx, j, Plot.FULL);
                plot.on(i, j - sy, Plot.FULL);
            }
        }
    }

    private static int labelLevel(Role role) {
        switch (role) {
            case SELECTED:
            case TARGET:
            case IMPACT: return Plot.FULL;
            case PLANNED:
            case PROPOSED: return Plot.NORM;
            default: return Plot.DIM;
        }
    }

    /** {@code n} cells of label text, wide. */
    private static float width(int n) {
        return n * 6f * TEXT - TEXT;
    }

    private float measure(String s) {
        return width(s.length());
    }

    /** A label's ground: the dots under its {@code lines} of text, and a dot round them, kept dark. */
    private void ground(float x, float y, float w, int lines) {
        plot.ground(x - 3f, y - 3.5f, w + 6f, lines * LINE + 1f);
    }

    private void text(String s, float x, float y, int level) {
        strings.add(s);
        textAt.add(x);
        textAt.add(y);
        textAt.add(level);
    }

    /**
     * The heading's lines are read first: a label of the plot's under them (a body off the map, pulled
     * in to its top edge) moves down a line at a time until it is clear of them, its ground with it.
     */
    private void clearHeading() {
        if (heading < 0) return;
        float[] at = textAt.items;
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE, left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
        for (int k = heading; k < strings.size; k++) {
            top = Math.min(top, at[3 * k + 1] - 3.5f);
            bottom = Math.max(bottom, at[3 * k + 1] + LINE - 2.5f);
            left = Math.min(left, at[3 * k] - 3f);
            right = Math.max(right, at[3 * k] + measure(strings.get(k)) + 3f);
        }
        for (int k = 0; k < heading; k++) {
            float x = at[3 * k], y = at[3 * k + 1], w = measure(strings.get(k));
            if (x > right || x + w < left || y + 7f * TEXT < top || y > bottom) continue;
            while (y <= bottom) y += LINE;
            at[3 * k + 1] = y;
            ground(x, y, w, 1);
        }
    }

    /** The stage's size, from the batch's projection: the map is drawn with the stage's own. */
    private void stage(Batch batch) {
        Matrix4 m = batch.getProjectionMatrix();
        float sx = m.val[Matrix4.M00], sy = m.val[Matrix4.M11];
        if (sx > 0f && sy > 0f) {
            stageW = 2f / sx;
            stageH = 2f / sy;
        }
        if (Gdx.graphics != null && stageW > 0f) unitsPx = Math.max(0.25f, Gdx.graphics.getWidth() / stageW);
    }

    /** Draws the frame lit so far through the kit; the map has given its last piece. */
    private void flush(Batch batch) {
        if (filament == null) filament = new Filament();
        filament.render(batch, stageW, stageH, unitsPx, frame);
        batch.setColor(Color.WHITE);
    }

    private void paintFrame(Filament f) {
        if (hasCanvas) {
            f.module(canvasX, canvasY, canvasW, canvasH, f.o().filaments(Math.max(1, Math.round(canvasH / 110f))).radius(3f));
            // The panel's own dots: whole ones only, from the first column and row inside the glass
            int i0 = plot.col(canvasX) + 1, j0 = plot.row(canvasY) + 1;
            int i1 = plot.col(canvasX + canvasW) - 1, j1 = plot.row(canvasY + canvasH) - 1;
            if (i1 > i0 && j1 > j0) f.dotField(i0 * PITCH, j0 * PITCH, (i1 - i0) * PITCH, (j1 - j0) * PITCH, PITCH, f.o());
        }
        float d = PITCH * 0.8f;
        for (IntIntMap.Entry e : plot.entries()) {
            int i = Plot.colOf(e.key), j = Plot.rowOf(e.key);
            float x = plot.x(i), y = plot.y(j);
            if (hasCanvas && (x < canvasX + 1f || x > canvasX + canvasW - 1f || y < canvasY + 1f || y > canvasY + canvasH - 1f)) continue;
            if (plot.grounded(x, y)) continue;
            f.dot(x, y, f.o().size(d).level(drive(e.value)));
        }
        float[] at = textAt.items;
        for (int k = 0; k < strings.size; k++) {
            float x = at[3 * k], y = at[3 * k + 1];
            String s = strings.get(k);
            if (hasCanvas && (y < canvasY || y + 7f * TEXT > canvasY + canvasH || x > canvasX + canvasW || x + measure(s) < canvasX)) continue;
            f.dm(x, y, s, f.o().pitch(TEXT).level(drive((int) at[3 * k + 2])).noGhost());
        }
    }

    private static float drive(int level) {
        return level >= Plot.FULL ? Drive.FULL : level == Plot.NORM ? Drive.NORM : Drive.DIM;
    }

    private static Texture white() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        return t;
    }

    @Override
    public void dispose() {
        if (filament != null) filament.dispose();
        filament = null;
        if (blank != null) blank.dispose();
        blank = null;
        rom.dispose();
    }
}
