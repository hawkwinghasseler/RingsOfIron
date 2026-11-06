package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.widgets.MapPainter.Role;

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
 *
 * <p>It works out where everything goes; a {@link MapPainter} draws each piece there, the classic look
 * unless a HUD variant gives the full-screen map its own ({@link #setPainter}).
 */
public class OrbitMap extends Widget {
    /** Wide enough for a transfer to Earth's Moon, about 60 Earth radii out. */
    private static final float MAX_BODY_RADII = 80f;
    private static final float EDGE = 8f;
    private static final float LABEL_WIDTH = 110f;

    private final MapPainter classic;
    private MapPainter painter;
    private final OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
    private OrbitPathProvider provider;
    private Label caption;
    /** Names drawn so far this frame, so the next one keeps clear. */
    private final Array<Rectangle> placed = new Array<>();
    private final GlyphLayout layout = new GlyphLayout();
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
    /** Where the path's origin is drawn this frame: its points are drawn at (pathX + x * drawScale, ...). */
    private double pathX, pathY;
    /** The map's middle in the frame last drawn, meters from the body: where the path is sampled finely. */
    private double lastViewX, lastViewY;

    public OrbitMap(TextureRegion white) {
        classic = new MapPainter(white, null);
        painter = classic;
    }

    public void setProvider(OrbitPathProvider provider) {
        this.provider = provider;
    }

    /** Font for other bodies' names, or null to draw them unlabelled; the classic look's. */
    public void setFont(BitmapFont font) {
        classic.setFont(font);
    }

    /** Draws the map's pieces with {@code painter}, a HUD variant's look; null goes back to the classic look. */
    public void setPainter(MapPainter painter) {
        this.painter = painter == null ? classic : painter;
    }

    public MapPainter getPainter() {
        return painter;
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

    /** Where the path's point x (one of its float arrays' x values) was drawn last frame. */
    public float pathScreenX(float x) {
        return (float) (originX + (path.originX + x) * drawScale);
    }

    /** Where the path's point y was drawn last frame. */
    public float pathScreenY(float y) {
        return (float) (originY + (path.originY + y) * drawScale);
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
        // What it shows, so the provider can sample the curves finely there: as set, or as last drawn
        if (viewScale > 0) {
            path.viewScale = viewScale;
            path.viewX = viewX;
            path.viewY = viewY;
        } else {
            path.viewScale = drawScale > 0 ? 1.0 / drawScale : 0;
            path.viewX = lastViewX;
            path.viewY = lastViewY;
        }
        path.viewReach = Math.hypot(getWidth(), getHeight()) / 2;
        // A hidden map (the small one under the full-screen map, or in the deck view) asks for nothing
        hasPath = provider != null && ascendantsVisible() && provider.getPlayerPath(path);
        if (caption != null) caption.setText(hasPath ? path.caption : "No orbit");
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!hasPath) return;
        Color old = batch.getColor().cpy();
        place();
        batch.flush();
        if (!clipBegin()) return;
        drawPieces(batch, parentAlpha * getColor().a);
        batch.flush();
        clipEnd();
        batch.setColor(old);
    }

    /**
     * Works out this frame's scale and where the body's centre goes. Positions are worked out in doubles
     * and only the pixel they land on is a float: zoomed in on a far orbit the body's centre is hundreds
     * of millions of pixels off the map.
     */
    void place() {
        // vx, vy: the map's middle; the body's centre is drawn at (originX, originY), the same unless a view is set
        float vx = getX() + getWidth() / 2f;
        float vy = getY() + getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f;
        if (viewScale > 0) {
            drawScale = 1.0 / viewScale;
            originX = vx - viewX * drawScale;
            originY = vy - viewY * drawScale;
            lastViewX = viewX;
            lastViewY = viewY;
        } else {
            float extent = Math.max(Math.max(extent(path.current), extent(path.planned)), extent(path.proposed));
            extent = Math.max((float) path.bodyRadius * 1.1f, Math.min(extent, (float) path.bodyRadius * MAX_BODY_RADII));
            drawScale = (half - fitMargin) / extent;
            originX = vx;
            originY = vy;
            lastViewX = lastViewY = 0;
        }
        pathX = originX + path.originX * drawScale;
        pathY = originY + path.originY * drawScale;
    }

    /** Every piece, where {@link #place} put this frame's frame, through the painter. */
    void drawPieces(Batch batch, float a) {
        float vx = getX() + getWidth() / 2f;
        float vy = getY() + getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f;
        float scale = (float) drawScale;
        BitmapFont font = painter.font();

        for (OrbitPathProvider.MapBody b : path.bodies) polyline(batch, b.orbit, Role.MOON_ORBIT, a);
        // Where a moon the path meets will be by then, under everything else
        float largest = 20f * Math.max(getWidth(), getHeight());
        for (OrbitPathProvider.PathMark m : path.marks) {
            if (m.kind != OrbitPathProvider.MarkKind.GHOST) continue;
            float soi = (float) m.soiRadius * scale, outline = Math.max(3f, (float) m.radius * scale);
            // Too small to see, or far bigger than the map: not drawn
            painter.ghost(batch, screenX(m.x), screenY(m.y), outline > largest ? 0f : outline,
                    soi < 2f || soi > largest ? 0f : soi, a);
        }

        painter.body(batch, originX, originY, path.bodyRadius * drawScale, getX(), getY(), getX() + getWidth(),
                getY() + getHeight(), a);

        polyline(batch, path.targetOrbit, Role.TARGET_ORBIT, a);
        polyline(batch, path.current, Role.CURRENT, a);
        polyline(batch, path.planned, Role.PLANNED, a);
        for (OrbitPathProvider.PathLeg l : path.legs) polyline(batch, l.points, l.planned ? Role.PLANNED_LEG : Role.CURRENT_LEG, a);
        polyline(batch, path.proposed, Role.PROPOSED, a);
        for (int i = 0; i + 1 < path.proposedNodes.size; i += 2) {
            painter.burn(batch, Role.PROPOSED, pathScreenX(path.proposedNodes.get(i)), pathScreenY(path.proposedNodes.get(i + 1)), a);
        }
        if (path.approach.size >= 4) {
            painter.approach(batch, pathScreenX(path.approach.get(0)), pathScreenY(path.approach.get(1)),
                    pathScreenX(path.approach.get(2)), pathScreenY(path.approach.get(3)), a);
        }
        placed.clear();
        for (OrbitPathProvider.MapMarker m : path.markers) marker(batch, m, vx, vy, half, a);
        for (int i = 0; i + 1 < path.nodes.size; i += 2) {
            boolean selected = i / 2 == path.selectedNode;
            Role role = selected ? Role.SELECTED : Role.PLANNED;
            float nx = pathScreenX(path.nodes.get(i)), ny = pathScreenY(path.nodes.get(i + 1));
            painter.burn(batch, role, nx, ny, a);
            // Every burn's countdown beside it, not only the next one's
            if (font != null && i / 2 < path.nodeLabels.size) {
                float ly = ny - 4f - 14f * ((i / 2) % 2);
                String text = path.nodeLabels.get(i / 2);
                layout.setText(font, text);
                placed.add(new Rectangle(nx + 6f, ly - layout.height, layout.width, layout.height));
                painter.label(batch, role, text, nx + 6f, ly, 0f, Align.left, a);
            }
        }
        painter.ship(batch, screenX(path.shipX), screenY(path.shipY), a);
        for (OrbitPathProvider.MapBody b : path.bodies) otherBody(batch, b, vx, vy, half, scale, a);
        for (OrbitPathProvider.PathMark m : path.marks) mark(batch, m, a);
    }

    private void otherBody(Batch batch, OrbitPathProvider.MapBody b, float vx, float vy, float half, float scale,
                           float alpha) {
        // From the map's middle, which is where it is pulled in towards
        float x = (float) (originX - vx + b.x * drawScale), y = (float) (originY - vy + b.y * drawScale);
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        boolean inside = far <= reach;
        if (!inside) {
            // Pull it in to the edge of the map along its direction.
            x *= reach / far;
            y *= reach / far;
        }
        float size = inside ? Math.max(5f, 2f * (float) b.radius * scale) : 5f;
        painter.moon(batch, vx + x, vy + y, size, alpha);
        if (painter.font() == null || b.name.isEmpty()) return;
        // Label toward the map's middle so it stays on the map.
        float lx = x > 0 ? vx + x - size / 2f - 4f - LABEL_WIDTH : vx + x + size / 2f + 4f;
        int align = x > 0 ? Align.right : Align.left;
        label(batch, Role.MOON, inside ? b.name : b.name + " (off map)", lx, vy + y + (y > 0 ? -6f : 14f), align,
                y > 0 ? -1f : 1f, alpha);
    }

    /**
     * Draws a name, moved a line at a time toward the map's middle ({@code dir} down or up) until it
     * clears the names already drawn: a target on a body that is off the map sits on the body's own
     * marker at the edge.
     */
    private void label(Batch batch, Role role, String text, float lx, float ly, int align, float dir, float alpha) {
        BitmapFont font = painter.font();
        layout.setText(font, text);
        float x = align == Align.right ? lx + LABEL_WIDTH - layout.width : lx;
        Rectangle box = new Rectangle(x, ly - layout.height, layout.width, layout.height);
        float step = font.getLineHeight();
        for (int tries = 0; tries < 4; tries++) {
            boolean hit = false;
            for (Rectangle r : placed) hit |= r.overlaps(box);
            if (!hit) break;
            box.y += dir * step;
        }
        placed.add(box);
        painter.label(batch, role, text, lx, box.y + box.height, LABEL_WIDTH, align, alpha);
    }

    /**
     * An apsis, a change of sphere of influence or an impact, each with its label when
     * {@link #setShowMarks} is on; impacts always.
     */
    private void mark(Batch batch, OrbitPathProvider.PathMark m, float alpha) {
        if (m.kind == OrbitPathProvider.MarkKind.GHOST) return;
        boolean impact = m.kind == OrbitPathProvider.MarkKind.IMPACT;
        if (!showMarks && !impact) return;
        float x = screenX(m.x), y = screenY(m.y);
        if (x < getX() || x > getX() + getWidth() || y < getY() || y > getY() + getHeight()) return;
        Role role = impact ? Role.IMPACT : m.planned ? Role.PLANNED : Role.CURRENT;
        painter.mark(batch, m.kind, role, x, y, alpha);
        if (painter.font() == null || m.text.isEmpty() || !showMarks && !impact) return;
        boolean right = x > getX() + getWidth() - LABEL_WIDTH - 20f;
        label(batch, role, m.text, right ? x - 8f - LABEL_WIDTH : x + 8f, y + 6f, right ? Align.right : Align.left, -1f, alpha);
    }

    /** A station or the target, pulled to the map's edge when off it (the target only). */
    private void marker(Batch batch, OrbitPathProvider.MapMarker m, float vx, float vy, float half, float alpha) {
        float x = (float) (originX - vx + m.x * drawScale), y = (float) (originY - vy + m.y * drawScale);
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        if (far > reach) {
            if (!m.target) return;
            x *= reach / far;
            y *= reach / far;
        } else if (!showStations) {
            return;
        }
        painter.marker(batch, vx + x, vy + y, m.target, alpha);
        if (!m.target || painter.font() == null || m.name.isEmpty()) return;
        float size = 9f;
        float lx = x > 0 ? vx + x - size / 2f - 4f - LABEL_WIDTH : vx + x + size / 2f + 4f;
        int align = x > 0 ? Align.right : Align.left;
        label(batch, Role.TARGET, m.name, lx, vy + y + (y > 0 ? -6f : 14f), align, y > 0 ? -1f : 1f, alpha);
    }

    private static float extent(FloatArray points) {
        float m = 0f;
        for (int i = 0; i < points.size; i++) m = Math.max(m, Math.abs(points.get(i)));
        return m;
    }

    /**
     * The path's points as pieces of line, each segment worked out in doubles and cut to the map before
     * it is drawn: zoomed in, one segment of a far orbit runs tens of thousands of pixels, and drawn whole
     * its far end strays from where the next one starts.
     */
    private void polyline(Batch batch, FloatArray points, Role role, float alpha) {
        double left = getX() - 2, right = getX() + getWidth() + 2, bottom = getY() - 2, top = getY() + getHeight() + 2;
        for (int i = 2; i + 1 < points.size; i += 2) {
            double x0 = pathX + points.get(i - 2) * drawScale, y0 = pathY + points.get(i - 1) * drawScale;
            double x1 = pathX + points.get(i) * drawScale, y1 = pathY + points.get(i + 1) * drawScale;
            // Far-off paths: drop segments wholly to one side of the map
            if ((x0 < left && x1 < left) || (x0 > right && x1 > right)
                    || (y0 < bottom && y1 < bottom) || (y0 > top && y1 > top)) continue;
            // Liang-Barsky: the part of the segment inside the map, as fractions along it
            double dx = x1 - x0, dy = y1 - y0;
            double t0 = 0, t1 = 1;
            double[] p = {-dx, dx, -dy, dy};
            double[] q = {x0 - left, right - x0, y0 - bottom, top - y0};
            boolean outside = false;
            for (int k = 0; k < 4 && !outside; k++) {
                if (p[k] == 0) {
                    outside = q[k] < 0;
                } else {
                    double t = q[k] / p[k];
                    if (p[k] < 0) t0 = Math.max(t0, t);
                    else t1 = Math.min(t1, t);
                    outside = t0 > t1;
                }
            }
            if (outside) continue;
            painter.path(batch, role, (float) (x0 + dx * t0), (float) (y0 + dy * t0), (float) (x0 + dx * t1),
                    (float) (y0 + dy * t1), alpha);
        }
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
