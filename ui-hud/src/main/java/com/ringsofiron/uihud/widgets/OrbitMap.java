package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
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
 * orbits, capped so an escape path does not shrink the body to a dot.
 */
public class OrbitMap extends Widget {
    private static final Color BODY = new Color(0.12f, 0.25f, 0.42f, 1f);
    private static final Color PLANNED = new Color(1f, 0.7f, 0.2f, 1f);
    private static final float MAX_BODY_RADII = 12f;

    private final TextureRegion white;
    private final OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
    private OrbitPathProvider provider;
    private Label caption;
    private boolean hasPath;
    private float prefSize = 220f;

    public OrbitMap(TextureRegion white) {
        this.white = white;
    }

    public void setProvider(OrbitPathProvider provider) {
        this.provider = provider;
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
        float scale = (Math.min(getWidth(), getHeight()) / 2f - 4f) / extent;

        // The body as a disc of thin horizontal slices.
        float r = (float) path.bodyRadius * scale;
        batch.setColor(BODY.r, BODY.g, BODY.b, BODY.a * a);
        for (float y = -r; y < r; y += 1f) {
            float half = (float) Math.sqrt(Math.max(0f, r * r - (y + 0.5f) * (y + 0.5f)));
            batch.draw(white, cx - half, cy + y, 2f * half, 1f);
        }

        polyline(batch, path.current, cx, cy, scale, HudSkin.HUD_CYAN, a);
        polyline(batch, path.planned, cx, cy, scale, PLANNED, a);
        for (int i = 0; i + 1 < path.nodes.size; i += 2) {
            boolean selected = i / 2 == path.selectedNode;
            dot(batch, cx + path.nodes.get(i) * scale, cy + path.nodes.get(i + 1) * scale,
                    selected ? 7f : 5f, selected ? Color.WHITE : PLANNED, a);
        }
        dot(batch, cx + (float) path.shipX * scale, cy + (float) path.shipY * scale, 5f, Color.WHITE, a);
        batch.setColor(old);
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
            // Escape paths run far off the map; drop segments that start outside it.
            if (Math.abs(x0) > limit || Math.abs(y0) > limit) continue;
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
