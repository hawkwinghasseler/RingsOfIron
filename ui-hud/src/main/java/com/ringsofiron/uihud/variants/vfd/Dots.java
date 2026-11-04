package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/**
 * The one texture FILAMENT draws everything but text from: a soft round-cornered dot for the dot
 * matrix, a whole unlit 5x7 cell for a character's ghost, and the opaque middle of the dot, which
 * {@link Shapes} uses as a white texel. It is mipmapped, so dots a pixel or two across keep their
 * true coverage instead of flickering, and drawing all of it from one texture keeps the batch from
 * flushing between shapes and dots.
 */
final class Dots implements Disposable {
    static final int WIDTH = 256, HEIGHT = 128;
    /** The dot cell is 128 texels with the dot 64 across in its middle, so a quad twice the dot's size draws it. */
    static final float DOT_QUAD = 2f;
    /** The ghost cell's dot pitch in texels, and where its 5x7 block starts. */
    private static final int GHOST_PITCH = 12, GHOST_X = 128 + (128 - 5 * GHOST_PITCH) / 2, GHOST_Y = (128 - 7 * GHOST_PITCH) / 2;

    final Texture texture;
    /** The white texel, at the dot's opaque middle. */
    final float whiteU, whiteV;
    final float dotU0, dotV0, dotU1, dotV1;
    /** The ghost cell's texture box: the 5x7 block with half a pitch round it. */
    final float ghostU0, ghostV0, ghostU1, ghostV1;

    Dots() {
        Pixmap pixmap = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float d = 64f, rr = d * 0.28f;
        float gd = GHOST_PITCH * 0.8f, grr = gd * 0.28f, goff = (GHOST_PITCH - gd) / 2f;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                float cover;
                if (x < 128) {
                    cover = coverage(x, y, 64f - d / 2f, 64f - d / 2f, d, rr);
                } else {
                    // which dot of the ghost block this texel is nearest
                    int col = (int) Math.floor((x - GHOST_X) / (float) GHOST_PITCH);
                    int row = (int) Math.floor((y - GHOST_Y) / (float) GHOST_PITCH);
                    cover = 0f;
                    if (col >= 0 && col < Rom.COLS && row >= 0 && row < Rom.ROWS) {
                        cover = coverage(x, y, GHOST_X + col * GHOST_PITCH + goff, GHOST_Y + row * GHOST_PITCH + goff, gd, grr);
                    }
                }
                int a = Math.round(cover * 255f);
                // white everywhere, so the mipmaps average coverage and nothing else
                pixmap.drawPixel(x, y, 0xffffff00 | a);
            }
        }
        texture = new Texture(pixmap, true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        whiteU = 64.5f / WIDTH;
        whiteV = 64.5f / HEIGHT;
        dotU0 = 0f;
        dotV0 = 0f;
        dotU1 = 128f / WIDTH;
        dotV1 = 1f;
        float pad = GHOST_PITCH / 2f;
        ghostU0 = (GHOST_X - pad) / WIDTH;
        ghostV0 = (GHOST_Y - pad) / HEIGHT;
        ghostU1 = (GHOST_X + 5 * GHOST_PITCH + pad) / WIDTH;
        ghostV1 = (GHOST_Y + 7 * GHOST_PITCH + pad) / HEIGHT;
    }

    /** The share of the texel at {@code px, py} inside a round-cornered square, by 4x4 samples. */
    private static float coverage(int px, int py, float x0, float y0, float size, float radius) {
        int inside = 0;
        float half = size / 2f, cx = x0 + half, cy = y0 + half, core = half - radius;
        for (int sy = 0; sy < 4; sy++) {
            for (int sx = 0; sx < 4; sx++) {
                float x = px + (sx + 0.5f) / 4f, y = py + (sy + 0.5f) / 4f;
                float dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                if (dx > half || dy > half) continue;
                float ex = Math.max(dx - core, 0f), ey = Math.max(dy - core, 0f);
                if (ex * ex + ey * ey <= radius * radius) inside++;
            }
        }
        return inside / 16f;
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
