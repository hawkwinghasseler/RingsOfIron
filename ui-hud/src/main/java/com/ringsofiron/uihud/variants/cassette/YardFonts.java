package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;

/**
 * The Yard Standard's typefaces (assets/fonts/cassette), rasterised by FreeType at the UI scale and drawn
 * at {@code 1 / scale}, so every size is in UI units, as {@link com.ringsofiron.uihud.HudFonts} does for
 * the classic HUD. A font is made the first time a face and size is asked for, and all are made again
 * when the scale changes. Without the files or FreeType, each face falls back to libGDX's own font.
 *
 * <p>Every font's glyphs go on the pages of one {@link #packer()}, which {@link YardPaint} also packs its
 * shapes into, so a frame of the HUD draws from one texture and the batch seldom has to flush.
 */
public final class YardFonts implements Disposable {

    /** One face per job (style guide, typography). */
    public enum Face {
        /** Engraved legends: Barlow Semi Condensed SemiBold, tracked 0.05 em, capitals. */
        LEGEND("fonts/cassette/BarlowSemiCondensed-SemiBold.ttf", 0.05f),
        /** Split flaps and drum figures: Barlow Condensed Medium. */
        FLAP("fonts/cassette/BarlowCondensed-Medium.ttf", 0f),
        /** Dymo tape, for names a refit changes: Barlow Condensed SemiBold, tracked 0.08 em. */
        DYMO("fonts/cassette/BarlowCondensed-SemiBold.ttf", 0.08f),
        /** Tube text, amber words on the beam tubes: IBM Plex Mono. */
        TUBE("fonts/cassette/IBMPlexMono-Regular.ttf", 0f),
        /** A tube's codes and keys: IBM Plex Mono SemiBold. */
        TUBE_BOLD("fonts/cassette/IBMPlexMono-SemiBold.ttf", 0f),
        /** The warning row's title: VT323. */
        TITLE("fonts/cassette/VT323-Regular.ttf", 0f),
        /** Typed paper: Special Elite. */
        PAPER("fonts/cassette/SpecialElite-Regular.ttf", 0f),
        /** Out-of-fiction text (tooltips, settings): Inter, the classic HUD's face. */
        PLAIN("fonts/Inter-SemiBold.otf", 0f);

        final String file;
        /** Extra space between letters, in ems. */
        final float tracking;

        Face(String file, float tracking) {
            this.file = file;
            this.tracking = tracking;
        }
    }

    /** Every character the HUD draws: Latin, figures and the few symbols the faces carry. */
    static final String CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "Δ°²³·–—…×−½’‘“”•";

    private final IntMap<BitmapFont> fonts = new IntMap<>();
    private float scale = 1f;
    private PixmapPacker packer;

    /** The pages the glyphs (and the painter's shapes) are packed on; made anew after {@link #dispose}. */
    public PixmapPacker packer() {
        if (packer == null) {
            packer = new PixmapPacker(2048, 2048, Pixmap.Format.RGBA8888, 2, false, new PixmapPacker.SkylineStrategy());
            // Glyphs are white: padding of transparent white keeps their filtered edges from darkening
            packer.setTransparentColor(new Color(1f, 1f, 1f, 0f));
        }
        return packer;
    }

    /** The scale the fonts are rasterised for; a change drops every font, to be made again on use. */
    public void setScale(float scale) {
        if (Math.abs(scale - this.scale) < 1e-3f) return;
        dispose();
        this.scale = scale;
    }

    public float scale() {
        return scale;
    }

    /** {@code face} at {@code size} UI units (to the tenth). */
    public BitmapFont get(Face face, float size) {
        int key = face.ordinal() * 10000 + Math.round(size * 10f);
        BitmapFont f = fonts.get(key);
        if (f == null) {
            f = create(face, size, scale, packer());
            fonts.put(key, f);
        }
        return f;
    }

    /** A font {@code size} UI units tall for {@code scale}, its glyphs on {@code packer}'s pages (null: its own); the caller disposes it. */
    static BitmapFont create(Face face, float size, float scale, PixmapPacker packer) {
        FileHandle handle = Gdx.files == null ? null : Gdx.files.internal(face.file);
        if (handle == null || !handle.exists()) return fallback(size);
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(handle);
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = Math.max(6, Math.round(size * scale));
            p.characters = CHARS;
            p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.incremental = false;
            p.packer = packer;
            BitmapFont font = generator.generateFont(p);
            font.getData().markupEnabled = false;
            if (face.tracking > 0f) {
                // Tracking: each glyph advances a little further, in the font's own pixels
                int extra = Math.max(1, Math.round(face.tracking * p.size));
                for (BitmapFont.Glyph[] page : font.getData().glyphs) {
                    if (page == null) continue;
                    for (BitmapFont.Glyph g : page) if (g != null) g.xadvance += extra;
                }
            }
            font.getData().setScale(size / (float) p.size);
            font.setUseIntegerPositions(false);
            return font;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            if (Gdx.app != null) Gdx.app.error("YardFonts", "Could not load " + face.file + ", using the default font", e);
            return fallback(size);
        } finally {
            if (generator != null) generator.dispose();
        }
    }

    private static BitmapFont fallback(float size) {
        BitmapFont f = new BitmapFont();
        f.getData().setScale(size / 15f);
        f.setUseIntegerPositions(false);
        return f;
    }

    @Override
    public void dispose() {
        for (BitmapFont f : fonts.values()) f.dispose();
        fonts.clear();
        if (packer != null) {
            packer.dispose();
            packer = null;
        }
    }
}
