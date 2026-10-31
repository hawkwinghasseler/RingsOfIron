package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.OrbitPathProvider;

/**
 * Small top-down map of the player's orbit around its parent body: the body, the orbit now, the
 * orbit after planned burns, the burn points and the ship. Scaled to fit the larger of the two
 * orbits, capped so an escape path does not shrink the body to a dot. Other bodies (moons, with their
 * orbits, and the body the parent orbits) are drawn where they fit, or as a labelled marker on the
 * edge of the map in their direction. Stations show as small squares; the navigation target as a
 * larger magenta one with its orbit, and the closest approach to it along the planned path as a
 * line between where each will be. A transfer proposed to the target but not accepted yet is drawn
 * in green.
 *
 * <p>A path that meets a moon goes on around it, drawn where the moon will be then (the moon's outline
 * and sphere of influence there, faint), and back out; one that meets the body's surface stops there.
 * {@link #setShowMarks} labels the apsides, the changes of sphere of influence and impacts as well,
 * for a map large enough to hold them.
 *
 * <p>{@link #setView} fixes the scale and centre instead of fitting the orbit, for a map that fills a
 * screen and zooms; {@link #screenX} and {@link #screenY} then place anything else in the same frame.
 */
public class OrbitMap extends Widget {
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
    /** Wide enough for a transfer to Earth's Moon, about 60 Earth radii out. */
    private static final float MAX_BODY_RADII = 80f;
    private static final float EDGE = 8f;
    private static final float LABEL_WIDTH = 110f;

    private final TextureRegion white;
    private final OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
    private OrbitPathProvider provider;
    private Label caption;
    /** Names drawn so far this frame, so the next one keeps clear. */
    private final Array<Rectangle> placed = new Array<>();
    private final GlyphLayout layout = new GlyphLayout();
    private BitmapFont font;
    private boolean hasPath;
    private boolean showMarks;
    /** Stations and an on-map target drawn here; off where another layer draws the traffic. */
    private boolean showStations = true;
    /** Pixels left clear around a fitted orbit. */
    private float fitMargin = 4f;
    private float prefSize = 220f;
    /** Meters per pixel and the point at the map's centre, meters from the body; 0 fits the orbit instead. */
    private double viewScale, viewX, viewY;
    /** Where the body's centre was drawn last frame, and pixels per meter. */
    private double originX, originY, drawScale;

    public OrbitMap(TextureRegion white) {
        this.white = white;
    }

    public void setProvider(OrbitPathProvider provider) {
        this.provider = provider;
    }

    /** Font for other bodies' names, or null to draw them unlabelled. */
    public void setFont(BitmapFont font) {
        this.font = font;
    }

    /** Label to show the provider's caption in, or null. */
    public void setCaption(Label caption) {
        this.caption = caption;
    }

    /** Labels the apsides, changes of sphere of influence and impacts on the path; off for the small map. */
    public void setShowMarks(boolean show) {
        showMarks = show;
    }

    /**
     * Whether to draw the stations and the target where they are; off, only a target off the map is
     * marked, on the edge in its direction, for a map that draws the traffic itself.
     */
    public void setShowStations(boolean show) {
        showStations = show;
    }

    /** Pixels kept clear between a fitted orbit and the map's edge, for its labels; 4 by default. */
    public void setFitMargin(float margin) {
        fitMargin = margin;
    }

    public void setPrefSize(float size) {
        prefSize = size;
        invalidateHierarchy();
    }

    /**
     * Draws at {@code metersPerPixel} with the point ({@code x}, {@code y}), meters from the body's
     * centre, in the middle of the map; {@code metersPerPixel} 0 goes back to fitting the orbit.
     */
    public void setView(double metersPerPixel, double x, double y) {
        viewScale = metersPerPixel;
        viewX = x;
        viewY = y;
    }

    /** Where a point {@code x} meters from the body's centre was drawn last frame. */
    public float screenX(double x) {
        return (float) (originX + x * drawScale);
    }

    /** Where a point {@code y} meters from the body's centre was drawn last frame. */
    public float screenY(double y) {
        return (float) (originY + y * drawScale);
    }

    /** Pixels per meter last frame. */
    public double getDrawScale() {
        return drawScale;
    }

    /** What was drawn last frame; for tests. */
    public OrbitPathProvider.OrbitPath getPath() {
        return path;
    }

    /** The labels drawn last frame, stage coordinates, for another layer to keep its own clear of. */
    public Array<Rectangle> getPlacedLabels() {
        return placed;
    }

    public boolean hasPath() {
        return hasPath;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        path.clear();
        // A hidden map (the small one under the full-screen map, or in the deck view) asks for nothing
        hasPath = provider != null && ascendantsVisible() && provider.getPlayerPath(path);
        if (caption != null) caption.setText(hasPath ? path.caption : "No orbit");
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!hasPath) return;
        Color old = batch.getColor().cpy();
        float a = parentAlpha * getColor().a;

        // vx, vy: the map's middle; cx, cy: the body's centre, the same unless a view is set
        float vx = getX() + getWidth() / 2f;
        float vy = getY() + getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f;
        float scale;
        if (viewScale > 0) {
            drawScale = 1.0 / viewScale;
            originX = vx - viewX * drawScale;
            originY = vy - viewY * drawScale;
            scale = (float) drawScale;
        } else {
            float extent = Math.max(Math.max(extent(path.current), extent(path.planned)), extent(path.proposed));
            extent = Math.max((float) path.bodyRadius * 1.1f, Math.min(extent, (float) path.bodyRadius * MAX_BODY_RADII));
            scale = (half - fitMargin) / extent;
            drawScale = scale;
            originX = vx;
            originY = vy;
        }
        float cx = (float) originX, cy = (float) originY;
        batch.flush();
        if (!clipBegin()) return;

        for (OrbitPathProvider.MapBody b : path.bodies) polyline(batch, b.orbit, cx, cy, scale, OTHER_ORBIT, a);
        // Where a moon the path meets will be by then, under everything else
        for (OrbitPathProvider.PathMark m : path.marks) {
            if (m.kind != OrbitPathProvider.MarkKind.GHOST) continue;
            float gx = cx + (float) m.x * scale, gy = cy + (float) m.y * scale;
            circle(batch, gx, gy, (float) m.soiRadius * scale, 1f, GHOST_SOI, a);
            circle(batch, gx, gy, Math.max(3f, (float) m.radius * scale), 1.5f, GHOST, a);
        }

        // The body as a disc of thin horizontal slices, only those on the map (zoomed in it is huge).
        float r = (float) path.bodyRadius * scale;
        batch.setColor(BODY.r, BODY.g, BODY.b, BODY.a * a);
        // Zoomed far out it stays a dot rather than vanishing
        if (r < 3f) dot(batch, cx, cy, 6f, OTHER_BODY, a);
        float left = getX(), right = getX() + getWidth();
        for (float y = Math.max(-r, (float) Math.floor(getY() - cy)); y < Math.min(r, getY() + getHeight() - cy); y += 1f) {
            float w = (float) Math.sqrt(Math.max(0f, r * r - (y + 0.5f) * (y + 0.5f)));
            float x0 = Math.max(left, cx - w), x1 = Math.min(right, cx + w);
            if (x1 > x0) batch.draw(white, x0, cy + y, x1 - x0, 1f);
        }

        polyline(batch, path.targetOrbit, cx, cy, scale, TARGET_ORBIT, a);
        polyline(batch, path.current, cx, cy, scale, HudSkin.HUD_CYAN, a);
        polyline(batch, path.planned, cx, cy, scale, PLANNED, a);
        for (OrbitPathProvider.PathLeg l : path.legs) polyline(batch, l.points, cx, cy, scale, l.planned ? PLANNED_LEG : CURRENT_LEG, a);
        polyline(batch, path.proposed, cx, cy, scale, PROPOSED, a);
        for (int i = 0; i + 1 < path.proposedNodes.size; i += 2) {
            dot(batch, cx + path.proposedNodes.get(i) * scale, cy + path.proposedNodes.get(i + 1) * scale, 5f, PROPOSED, a);
        }
        if (path.approach.size >= 4) {
            float x0 = cx + path.approach.get(0) * scale, y0 = cy + path.approach.get(1) * scale;
            float x1 = cx + path.approach.get(2) * scale, y1 = cy + path.approach.get(3) * scale;
            batch.setColor(TARGET.r, TARGET.g, TARGET.b, a);
            line(batch, x0, y0, x1, y1);
            dot(batch, x0, y0, 4f, Color.WHITE, a);
            dot(batch, x1, y1, 4f, TARGET, a);
        }
        placed.clear();
        for (OrbitPathProvider.MapMarker m : path.markers) marker(batch, m, cx, cy, vx, vy, half, scale, a);
        for (int i = 0; i + 1 < path.nodes.size; i += 2) {
            boolean selected = i / 2 == path.selectedNode;
            float nx = cx + path.nodes.get(i) * scale, ny = cy + path.nodes.get(i + 1) * scale;
            dot(batch, nx, ny, selected ? 7f : 5f, selected ? Color.WHITE : PLANNED, a);
            // Every burn's countdown beside it, not only the next one's
            if (font != null && i / 2 < path.nodeLabels.size) {
                Color c = selected ? Color.WHITE : PLANNED;
                font.setColor(c.r, c.g, c.b, a);
                float ly = ny - 4f - 14f * ((i / 2) % 2);
                layout.setText(font, path.nodeLabels.get(i / 2));
                font.draw(batch, layout, nx + 6f, ly);
                placed.add(new Rectangle(nx + 6f, ly - layout.height, layout.width, layout.height));
            }
        }
        dot(batch, cx + (float) path.shipX * scale, cy + (float) path.shipY * scale, 5f, Color.WHITE, a);
        for (OrbitPathProvider.MapBody b : path.bodies) otherBody(batch, b, cx, cy, vx, vy, half, scale, a);
        for (OrbitPathProvider.PathMark m : path.marks) mark(batch, m, cx, cy, scale, a);
        batch.flush();
        clipEnd();
        batch.setColor(old);
    }

    private void otherBody(Batch batch, OrbitPathProvider.MapBody b, float cx, float cy, float vx, float vy, float half,
                           float scale, float alpha) {
        // From the map's middle, which is where it is pulled in towards
        float x = cx - vx + (float) b.x * scale, y = cy - vy + (float) b.y * scale;
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        boolean inside = far <= reach;
        if (!inside) {
            // Pull it in to the edge of the map along its direction.
            x *= reach / far;
            y *= reach / far;
        }
        float size = inside ? Math.max(5f, 2f * (float) b.radius * scale) : 5f;
        dot(batch, vx + x, vy + y, size, OTHER_BODY, alpha);
        if (font == null || b.name.isEmpty()) return;
        font.setColor(OTHER_BODY.r, OTHER_BODY.g, OTHER_BODY.b, alpha);
        // Label toward the map's middle so it stays on the map.
        float lx = x > 0 ? vx + x - size / 2f - 4f - LABEL_WIDTH : vx + x + size / 2f + 4f;
        int align = x > 0 ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left;
        label(batch, inside ? b.name : b.name + " (off map)", lx, vy + y + (y > 0 ? -6f : 14f), align, y > 0 ? -1f : 1f);
    }

    /**
     * Draws a name, moved a line at a time toward the map's middle ({@code dir} down or up) until it
     * clears the names already drawn: a target on a body that is off the map sits on the body's own
     * marker at the edge.
     */
    private void label(Batch batch, String text, float lx, float ly, int align, float dir) {
        layout.setText(font, text);
        float x = align == com.badlogic.gdx.utils.Align.right ? lx + LABEL_WIDTH - layout.width : lx;
        Rectangle box = new Rectangle(x, ly - layout.height, layout.width, layout.height);
        float step = font.getLineHeight();
        for (int tries = 0; tries < 4; tries++) {
            boolean hit = false;
            for (Rectangle r : placed) hit |= r.overlaps(box);
            if (!hit) break;
            box.y += dir * step;
        }
        placed.add(box);
        font.draw(batch, text, lx, box.y + box.height, LABEL_WIDTH, align, false);
    }

    /**
     * An apsis as a small tick across the path, a change of sphere of influence as a diamond and an
     * impact as a red cross, each with its label when {@link #setShowMarks} is on; impacts always.
     */
    private void mark(Batch batch, OrbitPathProvider.PathMark m, float cx, float cy, float scale, float alpha) {
        if (m.kind == OrbitPathProvider.MarkKind.GHOST) return;
        boolean impact = m.kind == OrbitPathProvider.MarkKind.IMPACT;
        if (!showMarks && !impact) return;
        float x = cx + (float) m.x * scale, y = cy + (float) m.y * scale;
        if (x < getX() || x > getX() + getWidth() || y < getY() || y > getY() + getHeight()) return;
        Color c = impact ? IMPACT : m.planned ? PLANNED : HudSkin.HUD_CYAN;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        switch (m.kind) {
            case IMPACT:
                batch.draw(white, x - 6f, y - 1f, 6f, 1f, 12f, 2f, 1f, 1f, 45f);
                batch.draw(white, x - 6f, y - 1f, 6f, 1f, 12f, 2f, 1f, 1f, -45f);
                break;
            case SOI_ENTRY:
            case SOI_EXIT:
                batch.draw(white, x - 4f, y - 4f, 4f, 4f, 8f, 8f, 1f, 1f, 45f);
                break;
            default:
                // Apsides: a hollow square, small enough not to hide the path under it
                batch.draw(white, x - 4f, y - 4f, 8f, 1.5f);
                batch.draw(white, x - 4f, y + 2.5f, 8f, 1.5f);
                batch.draw(white, x - 4f, y - 4f, 1.5f, 8f);
                batch.draw(white, x + 2.5f, y - 4f, 1.5f, 8f);
        }
        if (font == null || m.text.isEmpty() || !showMarks && !impact) return;
        font.setColor(c.r, c.g, c.b, alpha);
        boolean right = x > getX() + getWidth() - LABEL_WIDTH - 20f;
        label(batch, m.text, right ? x - 8f - LABEL_WIDTH : x + 8f, y + 6f,
                right ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left, -1f);
    }

    /** A circle outline as short segments; nothing when it is far bigger than the map or too small to see. */
    private void circle(Batch batch, float x, float y, float r, float t, Color c, float alpha) {
        if (r < 2f || r > 20f * Math.max(getWidth(), getHeight())) return;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        int n = MathUtils.clamp((int) (r * 0.6f), 16, 160);
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            line(batch, x + MathUtils.cos(a0) * r, y + MathUtils.sin(a0) * r, x + MathUtils.cos(a1) * r, y + MathUtils.sin(a1) * r, t);
        }
    }

    /** A station or the target as a square outline, pulled to the map's edge when off it. */
    private void marker(Batch batch, OrbitPathProvider.MapMarker m, float cx, float cy, float vx, float vy, float half,
                        float scale, float alpha) {
        float x = cx - vx + (float) m.x * scale, y = cy - vy + (float) m.y * scale;
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        if (far > reach) {
            if (!m.target) return;
            x *= reach / far;
            y *= reach / far;
        } else if (!showStations) {
            return;
        }
        Color c = m.target ? TARGET : STATION;
        float size = m.target ? 9f : 5f;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float x0 = vx + x - size / 2f, y0 = vy + y - size / 2f;
        batch.draw(white, x0, y0, size, 1.5f);
        batch.draw(white, x0, y0 + size - 1.5f, size, 1.5f);
        batch.draw(white, x0, y0, 1.5f, size);
        batch.draw(white, x0 + size - 1.5f, y0, 1.5f, size);
        if (!m.target || font == null || m.name.isEmpty()) return;
        font.setColor(c.r, c.g, c.b, alpha);
        float lx = x > 0 ? vx + x - size / 2f - 4f - LABEL_WIDTH : vx + x + size / 2f + 4f;
        int align = x > 0 ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left;
        label(batch, m.name, lx, vy + y + (y > 0 ? -6f : 14f), align, y > 0 ? -1f : 1f);
    }

    private static float extent(FloatArray points) {
        float m = 0f;
        for (int i = 0; i < points.size; i++) m = Math.max(m, Math.abs(points.get(i)));
        return m;
    }

    private void polyline(Batch batch, FloatArray points, float cx, float cy, float scale, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float left = getX() - 2f, right = getX() + getWidth() + 2f, bottom = getY() - 2f, top = getY() + getHeight() + 2f;
        for (int i = 2; i + 1 < points.size; i += 2) {
            float x0 = cx + points.get(i - 2) * scale, y0 = cy + points.get(i - 1) * scale;
            float x1 = cx + points.get(i) * scale, y1 = cy + points.get(i + 1) * scale;
            // Far-off paths: drop segments wholly to one side of the map; clipping trims the rest.
            if ((x0 < left && x1 < left) || (x0 > right && x1 > right)
                    || (y0 < bottom && y1 < bottom) || (y0 > top && y1 > top)) continue;
            line(batch, x0, y0, x1, y1);
        }
    }

    private void line(Batch batch, float x0, float y0, float x1, float y1) {
        line(batch, x0, y0, x1, y1, 1.5f);
    }

    private void line(Batch batch, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    private void dot(Batch batch, float x, float y, float size, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x - size / 2f, y - size / 2f, size, size);
    }

    @Override
    public float getPrefWidth() {
        return prefSize;
    }

    @Override
    public float getPrefHeight() {
        return prefSize;
    }
}
