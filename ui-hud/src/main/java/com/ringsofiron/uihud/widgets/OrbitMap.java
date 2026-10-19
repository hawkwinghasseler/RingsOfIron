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
    private float prefSize = 220f;

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

    public void setPrefSize(float size) {
        prefSize = size;
        invalidateHierarchy();
    }

    /** What was drawn last frame; for tests. */
    public OrbitPathProvider.OrbitPath getPath() {
        return path;
    }

    public boolean hasPath() {
        return hasPath;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        path.clear();
        hasPath = provider != null && provider.getPlayerPath(path);
        if (caption != null) caption.setText(hasPath ? path.caption : "No orbit");
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!hasPath) return;
        Color old = batch.getColor().cpy();
        float a = parentAlpha * getColor().a;

        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float extent = Math.max(Math.max(extent(path.current), extent(path.planned)), extent(path.proposed));
        extent = Math.max((float) path.bodyRadius * 1.1f, Math.min(extent, (float) path.bodyRadius * MAX_BODY_RADII));
        float half = Math.min(getWidth(), getHeight()) / 2f;
        float scale = (half - 4f) / extent;
        batch.flush();
        if (!clipBegin()) return;

        for (OrbitPathProvider.MapBody b : path.bodies) polyline(batch, b.orbit, cx, cy, scale, OTHER_ORBIT, a);

        // The body as a disc of thin horizontal slices.
        float r = (float) path.bodyRadius * scale;
        batch.setColor(BODY.r, BODY.g, BODY.b, BODY.a * a);
        for (float y = -r; y < r; y += 1f) {
            float w = (float) Math.sqrt(Math.max(0f, r * r - (y + 0.5f) * (y + 0.5f)));
            batch.draw(white, cx - w, cy + y, 2f * w, 1f);
        }

        polyline(batch, path.targetOrbit, cx, cy, scale, TARGET_ORBIT, a);
        polyline(batch, path.current, cx, cy, scale, HudSkin.HUD_CYAN, a);
        polyline(batch, path.planned, cx, cy, scale, PLANNED, a);
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
        for (OrbitPathProvider.MapMarker m : path.markers) marker(batch, m, cx, cy, half, scale, a);
        for (int i = 0; i + 1 < path.nodes.size; i += 2) {
            boolean selected = i / 2 == path.selectedNode;
            float nx = cx + path.nodes.get(i) * scale, ny = cy + path.nodes.get(i + 1) * scale;
            dot(batch, nx, ny, selected ? 7f : 5f, selected ? Color.WHITE : PLANNED, a);
            // Every burn's countdown beside it, not only the next one's
            if (font != null && i / 2 < path.nodeLabels.size) {
                Color c = selected ? Color.WHITE : PLANNED;
                font.setColor(c.r, c.g, c.b, a);
                font.draw(batch, path.nodeLabels.get(i / 2), nx + 6f, ny - 4f - 14f * ((i / 2) % 2));
            }
        }
        dot(batch, cx + (float) path.shipX * scale, cy + (float) path.shipY * scale, 5f, Color.WHITE, a);
        for (OrbitPathProvider.MapBody b : path.bodies) otherBody(batch, b, cx, cy, half, scale, a);
        batch.flush();
        clipEnd();
        batch.setColor(old);
    }

    private void otherBody(Batch batch, OrbitPathProvider.MapBody b, float cx, float cy, float half, float scale, float alpha) {
        float x = (float) b.x * scale, y = (float) b.y * scale;
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        boolean inside = far <= reach;
        if (!inside) {
            // Pull it in to the edge of the map along its direction.
            x *= reach / far;
            y *= reach / far;
        }
        float size = inside ? Math.max(5f, 2f * (float) b.radius * scale) : 5f;
        dot(batch, cx + x, cy + y, size, OTHER_BODY, alpha);
        if (font == null || b.name.isEmpty()) return;
        font.setColor(OTHER_BODY.r, OTHER_BODY.g, OTHER_BODY.b, alpha);
        // Label toward the map's middle so it stays on the map.
        float lx = x > 0 ? cx + x - size / 2f - 4f - LABEL_WIDTH : cx + x + size / 2f + 4f;
        int align = x > 0 ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left;
        label(batch, inside ? b.name : b.name + " (off map)", lx, cy + y + (y > 0 ? -6f : 14f), align, y > 0 ? -1f : 1f);
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

    /** A station or the target as a square outline, pulled to the map's edge when off it. */
    private void marker(Batch batch, OrbitPathProvider.MapMarker m, float cx, float cy, float half, float scale, float alpha) {
        float x = (float) m.x * scale, y = (float) m.y * scale;
        float reach = half - EDGE;
        float far = Math.max(Math.abs(x), Math.abs(y));
        if (far > reach) {
            if (!m.target) return;
            x *= reach / far;
            y *= reach / far;
        }
        Color c = m.target ? TARGET : STATION;
        float size = m.target ? 9f : 5f;
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float x0 = cx + x - size / 2f, y0 = cy + y - size / 2f;
        batch.draw(white, x0, y0, size, 1.5f);
        batch.draw(white, x0, y0 + size - 1.5f, size, 1.5f);
        batch.draw(white, x0, y0, 1.5f, size);
        batch.draw(white, x0 + size - 1.5f, y0, 1.5f, size);
        if (!m.target || font == null || m.name.isEmpty()) return;
        font.setColor(c.r, c.g, c.b, alpha);
        float lx = x > 0 ? cx + x - size / 2f - 4f - LABEL_WIDTH : cx + x + size / 2f + 4f;
        int align = x > 0 ? com.badlogic.gdx.utils.Align.right : com.badlogic.gdx.utils.Align.left;
        label(batch, m.name, lx, cy + y + (y > 0 ? -6f : 14f), align, y > 0 ? -1f : 1f);
    }

    private static float extent(FloatArray points) {
        float m = 0f;
        for (int i = 0; i < points.size; i++) m = Math.max(m, Math.abs(points.get(i)));
        return m;
    }

    private void polyline(Batch batch, FloatArray points, float cx, float cy, float scale, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        float limit = Math.max(getWidth(), getHeight());
        for (int i = 2; i + 1 < points.size; i += 2) {
            float x0 = points.get(i - 2) * scale, y0 = points.get(i - 1) * scale;
            float x1 = points.get(i) * scale, y1 = points.get(i + 1) * scale;
            // Far-off paths: drop segments that are wholly off the map; clipping trims the rest.
            if (Math.max(Math.abs(x0), Math.abs(y0)) > limit && Math.max(Math.abs(x1), Math.abs(y1)) > limit) continue;
            line(batch, cx + x0, cy + y0, cx + x1, cy + y1);
        }
    }

    private void line(Batch batch, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - 0.75f, 0f, 0.75f, len, 1.5f, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
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
