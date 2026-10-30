package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

/** The white, tintable textures {@link ShieldParticles} draws the shield with, made from pixmaps. */
final class ShieldTextures {
    private ShieldTextures() {
    }

    /** A round fleck, white, for tinting: solid at its heart and fading to nothing at its rim. */
    static Texture dotTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, dotAlpha((float) Math.sqrt(dx * dx + dy * dy))));
            }
        }
        return texture(pm);
    }

    /** A fleck's alpha {@code r} out from its center (0 to 1 at its rim): a smooth fall from 1 to 0. */
    static float dotAlpha(float r) {
        float t = MathUtils.clamp(1f - r, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Plain white, for tinting. */
    static Texture whiteTexture() {
        Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        return texture(pm);
    }

    /** A soft round glow, white, for tinting. */
    static Texture glowTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                float f = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy));
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, f * f));
            }
        }
        return texture(pm);
    }

    /** A band, soft across and even along, white, for tinting and stretching along a curve. */
    static Texture bandTexture(int n) {
        Pixmap pm = new Pixmap(4, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            float v = (y + 0.5f) / n * 2f - 1f;
            float a = bandAlpha(v);
            for (int x = 0; x < 4; x++) pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
        }
        return texture(pm);
    }

    /** The band's alpha {@code v} across it (-1 to 1): a bright thin line at the middle over a soft glow. */
    static float bandAlpha(float v) {
        float line = v / 0.12f, soft = v / 0.5f;
        return Math.min(1f, 0.8f * (float) Math.exp(-line * line) + 0.35f * (float) Math.exp(-soft * soft));
    }

    private static Texture texture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }
}
