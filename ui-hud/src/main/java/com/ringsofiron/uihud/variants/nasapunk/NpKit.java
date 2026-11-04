package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/**
 * What a Nasa-punk variant draws with: the B612 fonts, a white pixel, and the canvas over them.
 * One per variant; {@link #setScale} follows the UI scale, and {@link #dispose} frees the lot.
 */
public final class NpKit implements Disposable {
    private final Texture white;
    private final NpFonts fonts = new NpFonts();
    private final NpCanvas canvas;

    public NpKit() {
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        p.setColor(1f, 1f, 1f, 1f);
        p.fill();
        white = new Texture(p);
        p.dispose();
        // the region's middle texel, so filtering never reaches an edge
        canvas = new NpCanvas(new TextureRegion(white, 1, 1, 2, 2), fonts);
    }

    public NpCanvas canvas() {
        return canvas;
    }

    public NpFonts fonts() {
        return fonts;
    }

    /** Rasterises the fonts again at a new UI scale. */
    public void setScale(float uiScale) {
        fonts.setScale(uiScale);
    }

    @Override
    public void dispose() {
        fonts.dispose();
        white.dispose();
    }
}
