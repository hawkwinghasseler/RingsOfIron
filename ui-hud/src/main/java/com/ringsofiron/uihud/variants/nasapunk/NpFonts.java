package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;

/**
 * B612, the typeface Airbus and ENAC drew for cockpit displays (assets/fonts, SIL Open Font
 * License), rasterised by FreeType at the UI scale and drawn at {@code 1 / scale}, so its sizes are
 * in UI units as the HUD's own fonts are ({@code HudFonts}). Fonts are made the first time a size is
 * asked for and made again when the scale changes.
 *
 * <p>Without the font files or FreeType's natives each size falls back to libGDX's own font.
 */
public final class NpFonts implements Disposable {
    public static final String REGULAR = "fonts/B612-Regular.ttf";
    public static final String BOLD = "fonts/B612-Bold.ttf";
    /** What the HUD writes beyond FreeType's default set: minus, arrows, delta, prime, dashes. */
    private static final String EXTRA = "−→←↑↓Δ′…—–·×°";

    private final IntMap<BitmapFont> regular = new IntMap<>(), bold = new IntMap<>();
    private float scale = 1f;

    /** The font {@code size} UI units tall. */
    public BitmapFont get(float size, boolean isBold) {
        // Sizes are whole units or halves (8.5, 10.5): the key keeps the half
        int key = Math.round(size * 2f);
        IntMap<BitmapFont> map = isBold ? bold : regular;
        BitmapFont f = map.get(key);
        if (f == null) {
            f = create(isBold ? BOLD : REGULAR, key / 2f, scale);
            map.put(key, f);
        }
        return f;
    }

    /** Rasterises every font again on its next use, for a new UI scale. */
    public void setScale(float uiScale) {
        if (uiScale <= 0f || Math.abs(uiScale - scale) < 1e-4f) return;
        scale = uiScale;
        dispose();
    }

    public float getScale() {
        return scale;
    }

    @Override
    public void dispose() {
        for (BitmapFont f : regular.values()) f.dispose();
        for (BitmapFont f : bold.values()) f.dispose();
        regular.clear();
        bold.clear();
    }

    static BitmapFont create(String file, float size, float scale) {
        FileHandle handle = Gdx.files == null ? null : Gdx.files.internal(file);
        if (handle == null || !handle.exists()) return fallback(size);
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(handle);
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = Math.max(6, Math.round(size * scale));
            p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA;
            p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.incremental = false;
            BitmapFont font = generator.generateFont(p);
            font.getData().markupEnabled = false;
            font.getData().setScale(size / p.size);
            font.setUseIntegerPositions(false);
            return font;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            if (Gdx.app != null) Gdx.app.error("NpFonts", "Could not load " + file + ", using the default font", e);
            return fallback(size);
        } finally {
            if (generator != null) generator.dispose();
        }
    }

    private static BitmapFont fallback(float size) {
        BitmapFont font = new BitmapFont();
        font.getData().markupEnabled = false;
        font.getData().setScale(size / 15f);
        font.setUseIntegerPositions(false);
        return font;
    }
}
