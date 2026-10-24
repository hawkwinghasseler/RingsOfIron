package com.ringsofiron.uihud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Array;

/**
 * The HUD's typefaces, rasterised from Inter (assets/fonts, SIL Open Font License) by FreeType at the
 * UI scale, so text stays sharp on a large screen instead of being the 15 px bitmap font blown up.
 * Each font is drawn at {@code 1 / scale} so that its sizes are in UI units: a 15 unit font is 15
 * pixels tall at 1x and 30 at 2x, and every layout written in UI units keeps its proportions.
 *
 * Without the font files, or without FreeType's natives, each falls back to libGDX's own font.
 */
public final class HudFonts {
    /** Body text: panels, readouts, labels and menus. */
    public static final int BODY = 15;
    /** Panel titles. */
    public static final int TITLE = 15;
    /** Banners: the missile warning, the dock hint, a menu's heading. */
    public static final int LARGE = 22;
    /** The main menu's title and the end of a career. */
    public static final int DISPLAY = 44;

    static final String REGULAR = "fonts/Inter-Regular.otf";
    static final String SEMIBOLD = "fonts/Inter-SemiBold.otf";

    private HudFonts() {}

    /** A font {@code size} UI units tall, rasterised for {@code scale}; the caller disposes it. */
    public static BitmapFont create(String file, int size, float scale) {
        FileHandle handle = Gdx.files == null ? null : Gdx.files.internal(file);
        if (handle == null || !handle.exists()) return fallback(size);
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(handle);
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = Math.max(6, Math.round(size * scale));
            p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            // Linear: between a rescale and the glyphs being drawn again, they stretch smoothly
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.incremental = false;
            BitmapFont font = generator.generateFont(p);
            font.getData().markupEnabled = false;
            font.getData().setScale(size / (float) p.size);
            font.setUseIntegerPositions(false);
            return font;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            if (Gdx.app != null) Gdx.app.error("HudFonts", "Could not load " + file + ", using the default font", e);
            return fallback(size);
        } finally {
            if (generator != null) generator.dispose();
        }
    }

    /** Body text at scale 1, for the standalone demos and tools that draw without a skin. */
    public static BitmapFont body() {
        return create(REGULAR, BODY, 1f);
    }

    /**
     * Rasterises {@code font} again for {@code scale}, in place: every label and style holding it
     * picks up the new glyphs. The caller lays the UI out again after.
     */
    public static void rescale(BitmapFont font, String file, int size, float scale) {
        BitmapFont fresh = create(file, size, scale);
        BitmapFont.BitmapFontData to = font.getData(), from = fresh.getData();
        if (to.glyphs.length != from.glyphs.length) {
            fresh.dispose();
            return;
        }
        System.arraycopy(from.glyphs, 0, to.glyphs, 0, from.glyphs.length);
        to.imagePaths = from.imagePaths;
        to.fontFile = from.fontFile;
        to.padTop = from.padTop;
        to.padRight = from.padRight;
        to.padBottom = from.padBottom;
        to.padLeft = from.padLeft;
        to.lineHeight = from.lineHeight;
        to.capHeight = from.capHeight;
        to.ascent = from.ascent;
        to.descent = from.descent;
        to.down = from.down;
        to.blankLineScale = from.blankLineScale;
        to.scaleX = from.scaleX;
        to.scaleY = from.scaleY;
        to.cursorX = from.cursorX;
        to.spaceXadvance = from.spaceXadvance;
        to.xHeight = from.xHeight;
        to.missingGlyph = from.missingGlyph;
        // The old pages go; the new ones now belong to the font that was there
        Array<TextureRegion> pages = font.getRegions();
        Array<Texture> old = new Array<>();
        for (TextureRegion r : pages) if (!old.contains(r.getTexture(), true)) old.add(r.getTexture());
        pages.clear();
        pages.addAll(fresh.getRegions());
        boolean owned = font.ownsTexture();
        font.setOwnsTexture(true);
        if (owned) for (Texture t : old) t.dispose();
        font.setUseIntegerPositions(fresh.usesIntegerPositions());
        // Its textures now live on in font, so fresh is left for the collector rather than disposed
    }

    private static BitmapFont fallback(int size) {
        BitmapFont font = new BitmapFont();
        font.getData().markupEnabled = false;
        // libGDX's font is 15 px; others are its size blown up, as before
        if (size != BODY) font.getData().setScale(size / (float) BODY);
        return font;
    }
}
