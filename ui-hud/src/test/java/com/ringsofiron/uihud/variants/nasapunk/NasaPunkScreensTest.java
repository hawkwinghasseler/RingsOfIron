package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.FocusNav;
import com.ringsofiron.uihud.widgets.MapPainter.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the Nasa-punk menus and map that need no window: the focus tint, the fonts' caps and spacing, the plot's words. */
class NasaPunkScreensTest {
    @Test
    void theDrawablesKnowTheFocusTintTheMenusUse() throws Exception {
        // The menus tint the focused control; the computer reads the tint and draws its cyan cursor instead
        Field f = FocusNav.class.getDeclaredField("FOCUS");
        f.setAccessible(true);
        Color tint = (Color) f.get(null);
        assertEquals(tint, NpDrawable.FOCUS);
        assertTrue(NpDrawable.isFocus(new Color(tint.r, tint.g, tint.b, 0.4f)), "at any alpha, as in a fade");
        assertFalse(NpDrawable.isFocus(Color.WHITE));
        assertFalse(NpDrawable.isFocus(NpColors.AMBER));
    }

    @Test
    void theLowerCaseDrawsAsTheCapitalsKernedAsTheyAre() {
        BitmapFont.BitmapFontData d = new BitmapFont.BitmapFontData();
        BitmapFont.Glyph a = glyph(d, 'A', 10), v = glyph(d, 'V', 9), one = glyph(d, '1', 5);
        glyph(d, 'a', 7);
        glyph(d, 'v', 6);
        a.setKerning('V', -2);
        v.setKerning('A', -3);
        NpFonts.capitalise(d);
        assertSame(a, d.getGlyph('a'));
        assertSame(v, d.getGlyph('v'));
        assertSame(one, d.getGlyph('1'), "a figure has no capital and stays");
        assertEquals(-2, a.getKerning('v'), "\"Av\" kerns as \"AV\"");
        assertEquals(-3, v.getKerning('a'));
        assertEquals(-2, a.getKerning('V'), "the capitals' own pairs stay");
    }

    @Test
    void theSpacingIsAddedOnceToAGlyphTwoLettersShare() {
        BitmapFont.BitmapFontData d = new BitmapFont.BitmapFontData();
        glyph(d, 'A', 10);
        glyph(d, 'a', 7);
        glyph(d, '1', 5);
        NpFonts.capitalise(d);
        NpFonts.track(d, 2);
        assertEquals(12, d.getGlyph('A').xadvance);
        assertEquals(12, d.getGlyph('a').xadvance);
        assertEquals(7, d.getGlyph('1').xadvance);
    }

    @Test
    void thePlateCarriesTheBuildAsItsPartNumber() {
        assertEquals("v1.0.0 · a1b2c3d", NpShellLook.plate("v1.0.0 (a1b2c3d)"));
        assertEquals("v1.0.0 · a1b2c3d", NpShellLook.plate("  v1.0.0 (a1b2c3d) "));
        assertEquals("dev", NpShellLook.plate("dev"));
        assertEquals("", NpShellLook.plate(null));
    }

    @Test
    void theMapsCountsAreOneRowOfCapitals() {
        assertEquals("5 STATIONS · 12 HAULERS", NpMapPainter.counts("5 stations   12 haulers"));
        assertEquals("3 CONTACTS", NpMapPainter.counts(" 3 contacts "));
        assertEquals("", NpMapPainter.counts("  "));
        assertEquals("", NpMapPainter.counts(null));
    }

    @Test
    void theMapsHintsSplitIntoWhatToPressAndWhatItDoes() {
        Array<String[]> h = NpMapPainter.hints("[M] close   [wheel] zoom   [right drag] pan   [Esc] back");
        assertEquals(4, h.size);
        assertArrayEquals(new String[] {"M", "CLOSE"}, h.get(0));
        assertArrayEquals(new String[] {"WHEEL", "ZOOM"}, h.get(1));
        assertArrayEquals(new String[] {"RIGHT DRAG", "PAN"}, h.get(2));
        assertArrayEquals(new String[] {"ESC", "BACK"}, h.get(3));
        assertTrue(NpMapPainter.isKey("M"));
        assertTrue(NpMapPainter.isKey("ESC"));
        assertFalse(NpMapPainter.isKey("WHEEL"), "done with the mouse: printed, not on a cap");
        assertFalse(NpMapPainter.isKey("RIGHT DRAG"));
        assertFalse(NpMapPainter.isKey(""));
        assertEquals(0, NpMapPainter.hints("no keys here").size);
    }

    @Test
    void aDashedLineInPiecesKeepsItsRhythm() {
        // 7 on, 4 off: a piece 5 long ends 5 in; the next, 8 long, ends 2 into the next period
        assertEquals(5f, NpMapPainter.phaseAfter(0f, 5f, 7f, 4f), 1e-4f);
        assertEquals(2f, NpMapPainter.phaseAfter(5f, 8f, 7f, 4f), 1e-4f);
        assertEquals(0f, NpMapPainter.phaseAfter(0f, 11f, 7f, 4f), 1e-4f, "a whole period ends at its start");
        assertEquals(0f, NpMapPainter.phaseAfter(3f, 29.99999f, 7f, 4f), 1e-4f, "a hair short of a period is its start");
    }

    @Test
    void thePlannedPathIsTheWidestLineOnThePlot() {
        // Guidance's path is drawn 2.5 wide (screens.js systemPlot), a proposed one 2, the orbit now 1.5
        assertEquals(2.5f, NpMapPainter.width(Role.PLANNED));
        assertEquals(2f, NpMapPainter.width(Role.PROPOSED));
        assertEquals(1.5f, NpMapPainter.width(Role.CURRENT));
        assertEquals(1f, NpMapPainter.opacity(Role.PROPOSED), "a proposal is shown at full strength until it is taken");
        assertTrue(NpMapPainter.opacity(Role.CURRENT_LEG) < 1f, "the legs ahead are fainter than the orbit now");
    }

    private static BitmapFont.Glyph glyph(BitmapFont.BitmapFontData d, char c, int advance) {
        BitmapFont.Glyph g = new BitmapFont.Glyph();
        g.id = c;
        g.xadvance = advance;
        d.setGlyph(c, g);
        return g;
    }
}
