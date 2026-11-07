package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * The character ROM as Scene2D fonts, for what lays text out with a {@link BitmapFont}: the game's
 * menus and the map, which measure their labels to place them. Every glyph is one cell wide, six dots
 * across, so a word of n letters measures n cells and lands on the cells FILAMENT draws it in
 * ({@link Filament#dm}); the drawing itself is the kit's, with its ghosts and glow.
 *
 * <p>The glyphs are the ROM's own dots on a texture, so text set in these fonts still reads where
 * nothing paints over it. One texture serves every size: a font is that texture at a scale.
 */
final class RomFont implements Disposable {
    /** Texels from one dot to the next on the texture, and the room round each glyph's cell there. */
    static final int DOT = 4, PAD = 2;
    /** A glyph's cell on the texture: five dots by seven, with its padding. */
    static final int CELL_W = Rom.COLS * DOT + 2 * PAD, CELL_H = Rom.ROWS * DOT + 2 * PAD;
    private static final int PER_ROW = 16;

    private final Texture texture;
    private final TextureRegion region;
    private final Array<BitmapFont> fonts = new Array<>();
    /** The characters on the texture, in cell order. */
    private final String chars;

    /** Draws the ROM onto a texture; needs GL. */
    RomFont() {
        chars = characters();
        int rows = (chars.length() + PER_ROW - 1) / PER_ROW;
        Pixmap pixmap = new Pixmap(pow2(PER_ROW * CELL_W), pow2(rows * CELL_H), Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        for (int k = 0; k < chars.length(); k++) {
            long g = Rom.glyph(chars.charAt(k));
            int x0 = (k % PER_ROW) * CELL_W + PAD, y0 = (k / PER_ROW) * CELL_H + PAD;
            for (int r = 0; r < Rom.ROWS; r++) {
                for (int c = 0; c < Rom.COLS; c++) {
                    if (!Rom.lit(g, r, c)) continue;
                    // A dot three texels of the four, as the kit's dots are 0.8 of their pitch
                    for (int dy = 0; dy < DOT - 1; dy++) {
                        for (int dx = 0; dx < DOT - 1; dx++) pixmap.drawPixel(x0 + c * DOT + dx, y0 + r * DOT + dy, 0xffffffff);
                    }
                }
            }
        }
        texture = new Texture(pixmap, true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        region = new TextureRegion(texture);
    }

    /**
     * A font whose cells are {@code advance} UI units apart, its capitals seven dots of that cell tall
     * ({@code advance} is six dots), {@code lineHeight} from one line to the next and {@code room} above
     * and below a line in a label's height. Kept until {@link #dispose}.
     */
    BitmapFont font(float advance, float lineHeight, float room) {
        float scale = advance / (6f * DOT);
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.flipped = false;
        data.markupEnabled = false;
        // Every metric in texels; the scale makes them UI units
        data.capHeight = Rom.ROWS * DOT;
        data.xHeight = 5 * DOT;
        data.ascent = 0f;
        data.lineHeight = lineHeight / scale;
        data.down = -data.lineHeight;
        data.descent = -room / scale;
        data.spaceXadvance = 6f * DOT;
        for (int k = 0; k < chars.length(); k++) {
            char ch = chars.charAt(k);
            BitmapFont.Glyph g = glyph(ch, k);
            data.setGlyph(ch, g);
            // The ROM is upper case only: a lower case letter is its capital
            if (ch >= 'A' && ch <= 'Z') data.setGlyph(Character.toLowerCase(ch), glyph(Character.toLowerCase(ch), k));
        }
        data.missingGlyph = data.getGlyph('?');
        data.setScale(scale);
        BitmapFont font = new BitmapFont(data, region, false);
        font.setUseIntegerPositions(false);
        fonts.add(font);
        return font;
    }

    private BitmapFont.Glyph glyph(char ch, int cell) {
        BitmapFont.Glyph g = new BitmapFont.Glyph();
        g.id = ch;
        g.srcX = (cell % PER_ROW) * CELL_W + PAD;
        g.srcY = (cell / PER_ROW) * CELL_H + PAD;
        // Five dots wide: the cell's sixth is the gap to the next letter
        g.width = ch == ' ' ? 0 : Rom.COLS * DOT;
        g.height = ch == ' ' ? 0 : Rom.ROWS * DOT;
        g.xoffset = 0;
        // From the baseline, as a font's glyphs hang: the cell's top sits on the cap height
        g.yoffset = -Rom.ROWS * DOT;
        g.xadvance = 6 * DOT;
        return g;
    }

    /** The printable ASCII characters and the ROM's own glyphs. */
    static String characters() {
        StringBuilder b = new StringBuilder();
        for (char c = 32; c < 127; c++) if (c < 'a' || c > 'z') b.append(c);
        b.append(Rom.extras());
        return b.toString();
    }

    private static int pow2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    @Override
    public void dispose() {
        for (BitmapFont f : fonts) f.dispose();
        fonts.clear();
        texture.dispose();
    }
}
