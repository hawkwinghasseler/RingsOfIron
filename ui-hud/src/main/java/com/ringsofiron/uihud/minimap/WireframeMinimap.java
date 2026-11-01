package com.ringsofiron.uihud.minimap;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Blueprint-style outline of a ship's interior grid, drawn in the lower-left of the piloting HUD.
 * Fed by {@code WireframeSyncSystem}; draws straight from the live grid every frame, so fires,
 * venting and wall damage show as they happen.
 *
 * Grid +x (the ship's nose) points right and +y up, like the grid itself.
 */
public class WireframeMinimap extends Widget {
    private static final float BLINK_PERIOD = 0.6f;
    private static final float BORDER = 1f;

    private final TextureRegion white;
    private ShipGridComponent grid;
    private final Array<SubsystemComponent> subsystems = new Array<>();
    private float prefSize = 220f;
    private float time;
    private boolean hasAvatar;
    private float avatarX, avatarY;
    private int flashX = -1, flashY = -1;
    private float flashTime;
    private final Color tmp = new Color();

    public WireframeMinimap(TextureRegion white) {
        this.white = white;
    }

    public void setGrid(ShipGridComponent grid) {
        if (this.grid == grid) return;
        this.grid = grid;
        invalidateHierarchy();
    }

    public ShipGridComponent getGrid() {
        return grid;
    }

    /** Subsystems drawn as status-colored insets over their mounts. Read every frame. */
    public Array<SubsystemComponent> getSubsystems() {
        return subsystems;
    }

    /** Draws the player avatar at a fractional tile position. */
    public void setAvatar(boolean visible, float tileX, float tileY) {
        hasAvatar = visible;
        avatarX = tileX;
        avatarY = tileY;
    }

    /** Flashes a tile white for a moment, e.g. where a shot just hit. */
    public void flashTile(int x, int y) {
        flashX = x;
        flashY = y;
        flashTime = 0.5f;
    }

    public void setPrefSize(float size) {
        prefSize = size;
        invalidateHierarchy();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
        if (flashTime > 0f) flashTime -= delta;
    }

    public boolean blinkOn() {
        return (time % BLINK_PERIOD) < BLINK_PERIOD / 2f;
    }

    /** Pixel size of one tile, fitting the grid into the widget with its aspect kept. */
    public float tilePixels() {
        if (grid == null || grid.width == 0 || grid.height == 0) return 0f;
        return Math.min((getWidth() - 2 * BORDER) / grid.width, (getHeight() - 2 * BORDER) / grid.height);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        Color old = batch.getColor().cpy();
        float a = parentAlpha * getColor().a;
        if (grid == null) return;

        float px = tilePixels();
        float ox = getX() + (getWidth() - px * grid.width) / 2f;
        float oy = getY() + (getHeight() - px * grid.height) / 2f;
        boolean blink = blinkOn();

        // Tiles
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                TileShade.fill(t, blink, tmp);
                if (tmp.a <= 0f) continue;
                rect(batch, ox + x * px, oy + y * px, px, px, tmp, a);
            }
        }

        // Thin edge walls
        float line = Math.max(1f, px * 0.18f);
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeWall w = grid.edgeH[y][x];
                if (w == null) continue;
                TileShade.edge(w, tmp);
                if (tmp.a > 0f) rect(batch, ox + x * px, oy + y * px - line / 2f, px, line, tmp, a);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall w = grid.edgeV[y][x];
                if (w == null) continue;
                TileShade.edge(w, tmp);
                if (tmp.a > 0f) rect(batch, ox + x * px - line / 2f, oy + y * px, line, px, tmp, a);
            }
        }

        // Subsystems: a small inset square in each mount, colored by status.
        for (SubsystemComponent s : subsystems) {
            MountFootprint m = s.footprint;
            if (s.isDestroyed()) tmp.set(TileShade.SYSTEM_DESTROYED);
            else if (!s.powered && s.type.needsPower()) tmp.set(TileShade.SYSTEM_UNPOWERED);
            else tmp.set(TileShade.SYSTEM_OK);
            float inset = px * 0.3f;
            outline(batch, ox + m.originX * px + inset, oy + m.originY * px + inset,
                    m.width() * px - 2 * inset, m.height() * px - 2 * inset, Math.max(1f, px * 0.12f), tmp, a);
        }

        if (flashTime > 0f && grid.inBounds(flashX, flashY)) {
            rect(batch, ox + flashX * px, oy + flashY * px, px, px, tmp.set(1f, 1f, 1f, flashTime * 2f), a);
        }

        if (hasAvatar) {
            float s = Math.max(3f, px * 0.5f);
            rect(batch, ox + avatarX * px - s / 2f, oy + avatarY * px - s / 2f, s, s, tmp.set(Color.WHITE), a);
        }

        batch.setColor(old);
    }

    private void rect(Batch batch, float x, float y, float w, float h, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
        batch.draw(white, x, y, w, h);
    }

    private void outline(Batch batch, float x, float y, float w, float h, float t, Color c, float alpha) {
        rect(batch, x, y, w, t, c, alpha);
        rect(batch, x, y + h - t, w, t, c, alpha);
        rect(batch, x, y, t, h, c, alpha);
        rect(batch, x + w - t, y, t, h, c, alpha);
    }

    @Override
    public float getPrefWidth() {
        if (grid == null || grid.height == 0) return prefSize;
        float aspect = (float) grid.width / grid.height;
        return aspect >= 1f ? prefSize : prefSize * aspect;
    }

    @Override
    public float getPrefHeight() {
        if (grid == null || grid.width == 0) return prefSize;
        float aspect = (float) grid.width / grid.height;
        return aspect >= 1f ? prefSize / aspect : prefSize;
    }

    /** Clears per-ship state, e.g. when the player changes ship. */
    public void clearShip() {
        grid = null;
        subsystems.clear();
        hasAvatar = false;
    }
}
