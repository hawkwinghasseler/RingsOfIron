package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;

/**
 * The faces FILAMENT prints with: Inter SemiBold, condensed and tracked, for the legends laid in
 * phosphor, the silk printed on the bezels and the personal kit's LCD (style-vfd.md, section 5.4).
 * Each size and tracking is rasterised by FreeType at the UI scale and kept until the scale changes;
 * condensing is a horizontal scale at draw time, so one face serves every width.
 *
 * <p>Text is placed by its baseline in the y-down frame, as the mockups' canvas does it.
 */
final class Type implements Disposable {
    static final String FILE = "fonts/Inter-SemiBold.otf";
    /** The glyphs beyond Latin-1 that legends use. */
    static final String EXTRA = "←↑→↓Δ·°±×…▲▼◀▶•−";

    static final class Face {
        final BitmapFont font;
        /** The font's own scale that makes its size UI units. */
        final float unitScale;

        Face(BitmapFont font, float unitScale) {
            this.font = font;
            this.unitScale = unitScale;
        }
    }

    private final LongMap<Face> faces = new LongMap<>();
    private final GlyphLayout layout = new GlyphLayout();
    /** Where the drawing's origin sits on the stage, y down. */
    private float ox, oy;
    private final StringBuilder upper = new StringBuilder();
    private final Color color = new Color();
    private float scale = 1f;
    private FreeTypeFontGenerator generator;
    private boolean generatorFailed;

    /** Pixels per UI unit; a change rasterises every face again as it is next used. */
    void setScale(float scale) {
        if (Math.abs(scale - this.scale) < 1e-3f) return;
        this.scale = scale;
        clear();
    }

    /** The face {@code size} units tall, tracked by {@code track} of its size between letters. */
    Face face(float size, float track) {
        long key = ((long) Math.round(size * 100f) << 32) | (Math.round(track * 10000f) & 0xffffffffL);
        Face f = faces.get(key);
        if (f == null) {
            f = create(size, track);
            faces.put(key, f);
        }
        return f;
    }

    private Face create(float size, float track) {
        FreeTypeFontGenerator gen = generator();
        int px = Math.max(6, Math.round(size * scale));
        if (gen != null) {
            try {
                FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
                p.size = px;
                p.spaceX = Math.round(track * size * scale);
                p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
                p.minFilter = Texture.TextureFilter.Linear;
                p.magFilter = Texture.TextureFilter.Linear;
                p.incremental = false;
                p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA;
                BitmapFont font = gen.generateFont(p);
                font.getData().markupEnabled = false;
                font.setUseIntegerPositions(false);
                return new Face(font, size / px);
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                if (Gdx.app != null) Gdx.app.error("Filament", "Could not rasterise " + FILE + " at " + px + " px", e);
            }
        }
        BitmapFont font = new BitmapFont();
        font.getData().markupEnabled = false;
        font.setUseIntegerPositions(false);
        return new Face(font, size / 15f);
    }

    private FreeTypeFontGenerator generator() {
        if (generator != null || generatorFailed) return generator;
        try {
            FileHandle handle = Gdx.files == null ? null : Gdx.files.internal(FILE);
            if (handle != null && handle.exists()) generator = new FreeTypeFontGenerator(handle);
            else generatorFailed = true;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            generatorFailed = true;
            if (Gdx.app != null) Gdx.app.error("Filament", "No FreeType for " + FILE + "; legends use the default font", e);
        }
        return generator;
    }

    /** {@code text} folded to upper case, in a buffer reused by the next call. */
    CharSequence upper(CharSequence text) {
        upper.setLength(0);
        for (int i = 0; i < text.length(); i++) upper.append(Character.toUpperCase(text.charAt(i)));
        return upper;
    }

    /** How wide {@code text} sets in {@code face}, condensed to {@code condense} of its width. */
    float width(Face face, CharSequence text, float condense) {
        if (text.length() == 0) return 0f;
        BitmapFont.BitmapFontData data = face.font.getData();
        data.setScale(face.unitScale * condense, face.unitScale);
        layout.setText(face.font, text);
        return layout.width;
    }

    /**
     * Draws {@code text} with its baseline at {@code baseline} (y down, in a frame {@code frameHeight}
     * tall) starting at {@code x}, in the packed colour {@code packed}; returns its width.
     */
    float draw(Batch batch, Face face, CharSequence text, float x, float baseline, float condense, float packed, float frameHeight) {
        if (text.length() == 0) return 0f;
        BitmapFont font = face.font;
        font.getData().setScale(face.unitScale * condense, face.unitScale);
        Color.abgr8888ToColor(color, packed);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, x + ox, frameHeight - oy - baseline + font.getCapHeight());
        return layout.width;
    }

    /** Moves the origin of what is drawn next to {@code x, y} on the stage (y down), as {@link Shapes#origin} does. */
    void origin(float x, float y) {
        ox = x;
        oy = y;
    }

    private void clear() {
        for (LongMap.Entry<Face> e : faces) e.value.font.dispose();
        faces.clear();
    }

    @Override
    public void dispose() {
        clear();
        if (generator != null) generator.dispose();
        generator = null;
    }
}
