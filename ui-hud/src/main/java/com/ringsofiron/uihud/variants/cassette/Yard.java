package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;

/**
 * The Yard Standard palette (documentation/ui-overhaul/style-cassette.md): enamel, chassis and bakelite
 * for the hardware, phosphor for the tubes, lamp colours lit and dark, and the finishes that tell one
 * post's fit from another's. Colours are shared; never change one in place.
 */
public final class Yard {
    private Yard() {}

    // --- Hardware ----------------------------------------------------------------------------
    public static final Color ENAMEL_TOP = hex("E3DAC1"), ENAMEL_BOTTOM = hex("CFC4A8");
    public static final Color ENAMEL_HI = hex("F4EEDD"), ENAMEL_LO = hex("7D735E");
    public static final Color CHASSIS_TOP = hex("52463C"), CHASSIS_BOTTOM = hex("3C332B"), CHASSIS_DEEP = hex("2B241F");
    public static final Color BAKELITE = hex("1E1A17"), ALU = hex("A9A69E"), RUBBER = hex("3A3734");
    public static final Color INK = hex("2A2420"), INK_LIGHT = hex("EFE8D6");
    public static final Color ORANGE = hex("D9602B"), RED = hex("B8352B"), MUSTARD = hex("D9A637"), AVOCADO = hex("6E7846");
    public static final Color SCREW = hex("C3C0B7"), SCREW_SLOT = hex("4B4842");
    public static final Color PAPER = hex("EEE6D2"), PAPER_INK = hex("2A2420"), PAPER_RED = hex("9E2A23");
    public static final Color SHADOW = new Color(0f, 0f, 0f, 0.35f);

    // --- Tubes -------------------------------------------------------------------------------
    public static final Color CRT_BLACK = hex("0E100C"), CRT_OFF = hex("22251F"), CRT_BEZEL = hex("070806");
    public static final Color P_AMBER = hex("FFB23F"), P_DIM = hex("B27B2C"), P_GREEN = hex("7CFF6A"), P_GDIM = hex("2F8A35");
    public static final Color P_RED = hex("FF5A3C"), P_WHITE = hex("F4EAD2");

    // --- Flaps and drums ---------------------------------------------------------------------
    public static final Color FLAP_FRAME = hex("0D0B0A"), FLAP_CELL = hex("24211F"), FLAP_TEXT = hex("F3EEE2");
    /** A flap word that needs a look: HAND, a room's air that isn't breathable. */
    public static final Color FLAP_AMBER = hex("FFC46B");
    public static final Color FLAP_GREEN = hex("9BE88E");
    public static final Color DRUM_TOP = hex("3A3633"), DRUM_MID = hex("1B1917"), DRUM_LEAD = hex("77716A");

    // --- Lamps: lit, dark ----------------------------------------------------------------------
    public static final Lamp RED_LAMP = new Lamp("FF4A35", "4E1B16");
    public static final Lamp AMBER_LAMP = new Lamp("FFA733", "4E3414");
    public static final Lamp GREEN_LAMP = new Lamp("8CFF5A", "203A19");
    public static final Lamp WHITE_LAMP = new Lamp("FFF3D6", "47443D");
    /** A lens's legend: dark on a lit lens, bright on an order's smoked lens, faint on a dark indicator. */
    public static final Color LEGEND_ON_LIT = hex("2A1606"), LEGEND_ORDER = hex("EFE7D3");
    public static final Color LEGEND_FAINT = new Color(240 / 255f, 230 / 255f, 210 / 255f, 0.42f);
    public static final Color SMOKED = hex("3B3834"), LENS_RIM = hex("4F4C46");

    /** A lamp colour: lit and dark. */
    public static final class Lamp {
        public final Color on, off;

        Lamp(String on, String off) {
            this.on = hex(on);
            this.off = hex(off);
        }
    }

    /**
     * A unit's finish. Each post's fit has its own: yard enamel for the repeaters and the helm,
     * gun-metal blue-grey for the weapons fit, avocado for the captain's own kit.
     */
    public enum Finish {
        YARD("E3DAC1", "CFC4A8", "F4EEDD", "7D735E", "2A2420", "857A68", "D9602B", false),
        GUN("737B81", "5B6368", "949BA0", "3B4145", "EFE8D6", "B3B8BB", "B8352B", true),
        AVOCADO("7A8450", "646D3F", "9AA46C", "454C2C", "EFE8D6", "C9CDB0", null, false);

        public final Color top, bottom, hi, lo, ink, dim, stripe;
        /** Rivets rather than screws. */
        public final boolean rivets;

        Finish(String top, String bottom, String hi, String lo, String ink, String dim, String stripe, boolean rivets) {
            this.top = hex(top);
            this.bottom = hex(bottom);
            this.hi = hex(hi);
            this.lo = hex(lo);
            this.ink = hex(ink);
            this.dim = hex(dim);
            this.stripe = stripe == null ? null : hex(stripe);
            this.rivets = rivets;
        }
    }

    public static Color hex(String rgb) {
        return Color.valueOf(rgb);
    }
}
