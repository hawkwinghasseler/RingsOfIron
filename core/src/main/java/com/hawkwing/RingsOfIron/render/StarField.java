package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * The distant stars behind everything in either view, behind the {@link SpaceSnow}. They are
 * as good as infinitely far: they neither move with the ship nor change with zoom, so they sit
 * still in screen pixels while the snow and the ships slide over them. Most are faint points; a few
 * are brighter and larger, with a soft halo, tinted from blue-white to orange.
 */
public final class StarField {
    /** As a developer overlay ({@code overlay("stars")} and the debug menu); shown by default. */
    public static final String NAME = "stars";
    public static final String DESCRIPTION = "the distant starfield behind the exterior and deck views (on in play)";
    /** Spacing of the grid the stars are scattered on, pixels: one cell holds at most one star. */
    static final float CELL = 26f;
    /** Of the cells, the share that hold a star. */
    static final float FILL = 0.5f;
    /** Stars brighter than this get a halo and are a pixel larger. */
    private static final float BRIGHT = 0.85f;
    /** Star colors, the bluest first; whites are the most common. */
    private static final Color[] TINTS = {
            new Color(0.70f, 0.80f, 1.00f, 1f),
            new Color(0.85f, 0.90f, 1.00f, 1f),
            new Color(1.00f, 1.00f, 1.00f, 1f),
            new Color(1.00f, 0.96f, 0.86f, 1f),
            new Color(1.00f, 0.85f, 0.68f, 1f),
            new Color(1.00f, 0.72f, 0.58f, 1f),
    };
    private static final int[] TINT_WEIGHTS = {2, 5, 8, 5, 3, 1};

    private boolean shown = true;
    private final Color color = new Color();

    public boolean isShown() {
        return shown;
    }

    public StarField setShown(boolean shown) {
        this.shown = shown;
        return this;
    }

    /**
     * How bright a star at cell ({@code i}, {@code j}) is, 0 (none) to 1. Skewed so most stars are
     * faint and only a few are bright.
     */
    static float brightness(int i, int j) {
        int h = hash(i, j);
        if ((h & 0xffff) / 65536f >= FILL) return 0f;
        float u = ((h >>> 16) & 0xff) / 256f;
        return 0.25f + 0.75f * u * u * u;
    }

    /**
     * Draws the stars in the view of {@code camera} (world meters, not turned),
     * {@code metersPerPixel} to a screen pixel. Call before anything else outside, and on deck behind the decks.
     */
    public void draw(ShapeRenderer shapes, OrthographicCamera camera, float metersPerPixel) {
        if (!shown || !(metersPerPixel > 0f)) return;
        float halfW = camera.viewportWidth * camera.zoom / metersPerPixel / 2f;
        float halfH = camera.viewportHeight * camera.zoom / metersPerPixel / 2f;
        int i0 = (int) Math.floor(-halfW / CELL) - 1, i1 = (int) Math.ceil(halfW / CELL);
        int j0 = (int) Math.floor(-halfH / CELL) - 1, j1 = (int) Math.ceil(halfH / CELL);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                float b = brightness(i, j);
                if (b <= 0f) continue;
                int h = hash(j, i);
                // Snapped to whole pixels so a still star stays crisp
                float x = (float) Math.floor((i + (h & 0xff) / 256f) * CELL);
                float y = (float) Math.floor((j + ((h >>> 8) & 0xff) / 256f) * CELL);
                Color tint = tint((h >>> 16) & 0xff);
                float wx = camera.position.x + x * metersPerPixel;
                float wy = camera.position.y + y * metersPerPixel;
                if (b > BRIGHT) {
                    // A soft halo two pixels out, then a 2-pixel core
                    color.set(tint).a = 0.12f * b;
                    shapes.setColor(color);
                    shapes.rect(wx - 1.5f * metersPerPixel, wy - 1.5f * metersPerPixel,
                            5f * metersPerPixel, 5f * metersPerPixel);
                    color.a = b;
                    shapes.setColor(color);
                    shapes.rect(wx, wy, 2f * metersPerPixel, 2f * metersPerPixel);
                } else {
                    color.set(tint).a = 0.8f * b;
                    shapes.setColor(color);
                    shapes.rect(wx, wy, metersPerPixel, metersPerPixel);
                }
            }
        }
        shapes.end();
    }

    /** A star's color from a byte of its hash, weighted by {@link #TINT_WEIGHTS}. */
    private static Color tint(int b) {
        int total = 0;
        for (int w : TINT_WEIGHTS) total += w;
        int pick = b * total / 256;
        for (int t = 0; t < TINTS.length; t++) {
            pick -= TINT_WEIGHTS[t];
            if (pick < 0) return TINTS[t];
        }
        return TINTS[TINTS.length - 1];
    }

    /** A well-mixed hash of a cell, so the stars look scattered. */
    private static int hash(int i, int j) {
        int h = i * 0x3c6ef372 ^ j * 0x1b873593 ^ 0x5bd1e995;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h;
    }
}
