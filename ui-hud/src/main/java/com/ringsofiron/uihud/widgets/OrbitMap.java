package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.OrbitPathProvider;

/**
 * Small top-down map of the player's orbit around its parent body: the body, the orbit now, the
 * orbit after planned burns, the burn points and the ship. Scaled to fit the larger of the two
 * orbits, capped so an escape path does not shrink the body to a dot. Other bodies (moons, with their
 * orbits, and the body the parent orbits) are drawn where they fit, or as a labelled marker on the
 * edge of the map in their direction.
 */
public class OrbitMap extends Widget {
    private static final Color BODY = new Color(0.12f, 0.25f, 0.42f, 1f);
    private static final Color PLANNED = new Color(1f, 0.7f, 0.2f, 1f);
    private static final Color OTHER_BODY = new Color(0.55f, 0.6f, 0.65f, 1f);
    private static final Color OTHER_ORBIT = new Color(0.55f, 0.6f, 0.65f, 0.45f);
    /** Wide enough for a transfer to Earth's Moon, about 60 Earth radii out. */
    private static final float MAX_BODY_RADII = 80f;
    private static final float EDGE = 8f;
    private static final float LABEL_WIDTH = 110f;

    private final TextureRegion white;
    private final OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
    private OrbitPathProvider provider;
    private Label caption;
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
        float extent = Math.max(extent(path.current), extent(path.planned));
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

        polyline(batch, path.current, cx, cy, scale, HudSkin.HUD_CYAN, a);
        polyline(batch, path.planned, cx, cy, scale, PLANNED, a);
        for (int i = 0; i + 1 < path.nodes.size; i += 2) {
            boolean selected = i / 2 == path.selectedNode;
            dot(batch, cx + path.nodes.get(i) * scale, cy + path.nodes.get(i + 1) * scale,
                    selected ? 7f : 5f, selected ? Color.WHITE : PLANNED, a);
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
        float ly = cy + y + (y > 0 ? -6f : 14f);
        font.draw(batch, inside ? b.name : b.name + " (off map)", lx, ly, LABEL_WIDTH, align, false);
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
