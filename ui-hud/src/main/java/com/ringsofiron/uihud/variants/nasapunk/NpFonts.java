package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectSet;

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

    /**
     * A font for text Scene2D lays out itself (the menus' labels and buttons, the map's labels),
     * which the canvas's letter spacing can't reach: every glyph's advance carries {@code track}
     * units of it, and with {@code caps} the lower case draws as the capitals, so the game's own
     * sentence-case words read in the style's caps and measure as they draw.
     */
    static BitmapFont create(String file, float size, float scale, float track, boolean caps) {
        BitmapFont f = create(file, size, scale);
        BitmapFont.BitmapFontData d = f.getData();
        if (caps) capitalise(d);
        if (track != 0f) track(d, Math.round(track / d.scaleX));
        return f;
    }

    /**
     * Makes every lower-case letter that has a capital in the font draw as that capital, its pairs
     * kerned as the capitals' are.
     */
    static void capitalise(BitmapFont.BitmapFontData d) {
        IntArray lower = new IntArray();
        Array<BitmapFont.Glyph> kept = new Array<>();
        for (BitmapFont.Glyph[] page : d.glyphs) {
            if (page == null) continue;
            for (BitmapFont.Glyph g : page) {
                if (g == null) continue;
                char c = (char) g.id, u = Character.toUpperCase(c);
                if (u != c && d.getGlyph(u) != null) lower.add(c);
                else kept.add(g);
            }
        }
        // Each pair with a lower-case letter takes the pair with its capital, before the letters go
        for (BitmapFont.Glyph g : kept) {
            for (int i = 0; i < lower.size; i++) {
                char c = (char) lower.get(i);
                int k = g.getKerning(Character.toUpperCase(c));
                if (k != g.getKerning(c)) g.setKerning(c, k);
            }
        }
        for (int i = 0; i < lower.size; i++) {
            char c = (char) lower.get(i);
            d.setGlyph(c, d.getGlyph(Character.toUpperCase(c)));
        }
    }

    /** Adds {@code px} font pixels to every glyph's advance, once each, however many letters share it. */
    static void track(BitmapFont.BitmapFontData d, int px) {
        if (px == 0) return;
        ObjectSet<BitmapFont.Glyph> seen = new ObjectSet<>();
        for (BitmapFont.Glyph[] page : d.glyphs) {
            if (page == null) continue;
            for (BitmapFont.Glyph g : page) if (g != null && seen.add(g)) g.xadvance += px;
        }
    }

    /**
     * Puts {@code fresh}'s glyphs, metrics and pages into {@code into}, so the styles and labels that
     * hold {@code into} draw at a new scale (as {@code HudFonts.rescale} does for the classic skin).
     * {@code into}'s old pages are freed; {@code fresh}'s now belong to it.
     */
    static void refit(BitmapFont into, BitmapFont fresh) {
        BitmapFont.BitmapFontData to = into.getData(), from = fresh.getData();
        System.arraycopy(from.glyphs, 0, to.glyphs, 0, Math.min(from.glyphs.length, to.glyphs.length));
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
        Array<TextureRegion> pages = into.getRegions();
        Array<Texture> old = new Array<>();
        for (TextureRegion r : pages) if (!old.contains(r.getTexture(), true)) old.add(r.getTexture());
        pages.clear();
        pages.addAll(fresh.getRegions());
        boolean owned = into.ownsTexture();
        into.setOwnsTexture(true);
        if (owned) for (Texture t : old) t.dispose();
        into.setUseIntegerPositions(fresh.usesIntegerPositions());
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
