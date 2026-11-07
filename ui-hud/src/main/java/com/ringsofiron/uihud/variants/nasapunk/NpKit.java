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
    private final TextureRegion whiteRegion;
    private final NpFonts fonts = new NpFonts();
    private final NpCanvas canvas;

    public NpKit() {
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        p.setColor(1f, 1f, 1f, 1f);
        p.fill();
        white = new Texture(p);
        p.dispose();
        // the region's middle texel, so filtering never reaches an edge
        whiteRegion = new TextureRegion(white, 1, 1, 2, 2);
        canvas = new NpCanvas(whiteRegion, fonts);
    }

    /** The white the canvas draws its shapes with, for anything else that draws with a white region. */
    public TextureRegion whiteRegion() {
        return whiteRegion;
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
