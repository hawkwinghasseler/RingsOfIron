package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;

/**
 * The colours of what does not glow: smoked glass, satin black bezels, printed silk, rubber keys and
 * the reflective LCD of the personal kit (style-vfd.md, sections 2 and 3).
 */
public final class Ink {
    public static final Color GLASS = Color.valueOf("040605");
    public static final Color GLASS_TOP = Color.valueOf("08100e");
    public static final Color BEZEL = Color.valueOf("141517");
    public static final Color BEZEL_HI = Color.valueOf("26282b");
    public static final Color BEZEL_LO = Color.valueOf("08090a");
    /** The bezel plate's gradient, top to bottom. */
    public static final Color RACK_TOP = Color.valueOf("1d1e21"), RACK_MID = Color.valueOf("17181a"), RACK_BOTTOM = Color.valueOf("0f1011");
    /** A console rack's rim, along its top and down each step. */
    public static final Color RACK_RIM = Color.valueOf("2c2e32");
    /** A free-standing plate's gradient, top to bottom, and its rim. */
    public static final Color PLATE_TOP = Color.valueOf("1c1d20"), PLATE_BOTTOM = Color.valueOf("0e0f10"), PLATE_RIM = Color.valueOf("2a2c30");
    /** Printed legends on the bezel. */
    public static final Color SILK = Color.valueOf("8d949a");
    public static final Color SILK_DIM = Color.valueOf("5b6167");
    public static final Color KEY = Color.valueOf("26282c");
    public static final Color KEY_HI = Color.valueOf("3a3d42");
    public static final Color KEY_LO = Color.valueOf("111214");
    /** A key's printed legend, and a key cap's. */
    public static final Color KEY_LEGEND = Color.valueOf("c9ced2"), KEYCAP_LEGEND = Color.valueOf("e4e8ea");
    public static final Color LCD = Color.valueOf("9aa58a");
    public static final Color LCD_TOP = Color.valueOf("97a386"), LCD_BOTTOM = Color.valueOf("7f8a70");
    public static final Color LCD_LIT_TOP = Color.valueOf("68b3a4"), LCD_LIT_BOTTOM = Color.valueOf("4f978a");
    public static final Color LCD_INK = Color.valueOf("1a2016");
    /** What a letter cut out of a lit block shows: the dark behind the glass. */
    public static final Color CUT = Color.valueOf("0a0605");

    private Ink() {
    }

    /** {@code c} packed at {@code alpha} times its own. */
    static float bits(Color c, float alpha) {
        return Color.toFloatBits(c.r, c.g, c.b, c.a * alpha);
    }
}
