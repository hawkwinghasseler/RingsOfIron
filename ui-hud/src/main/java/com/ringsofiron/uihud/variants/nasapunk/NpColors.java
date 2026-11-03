package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;

/**
 * The Nasa-punk palette (documentation/ui-overhaul/nasapunk/style-nasapunk.md, section 3). On the
 * glass each colour has one meaning: white normal, cyan commanded or your own, green engaged,
 * magenta guidance, amber caution, red warning; dim is for labels and units. The hardware colours
 * are the bezels, key caps and lenses the glass sits in. Shared, so never modify one in place.
 */
public final class NpColors {
    public static final Color GLASS = rgb(0x05070A);
    public static final Color GRATICULE = rgb(0x16202A);
    public static final Color DIM = rgb(0x7E8792);
    public static final Color WHITE = rgb(0xEEF1F3);
    public static final Color CYAN = rgb(0x3CCBE6);
    public static final Color GREEN = rgb(0x3DDC7A);
    public static final Color MAGENTA = rgb(0xF052D2);
    public static final Color AMBER = rgb(0xFFAE1F);
    public static final Color RED = rgb(0xFF3B30);
    /** Under symbology laid over the world, so it reads on stars and planets alike. */
    public static final Color HALO = new Color(0f, 0f, 0f, 0.7f);
    public static final Color BEZEL = rgb(0x2B2E33);
    public static final Color BEZEL_TOP = rgb(0x2F3238);
    public static final Color EDGE_HI = rgb(0x40444B);
    public static final Color EDGE_LO = rgb(0x1A1C1F);
    /** The bezel's shadow line, a shade under the bezel. */
    public static final Color BEZEL_SHADOW = rgb(0x151719);
    public static final Color STENCIL = rgb(0xD6D8D2);
    public static final Color KEY = rgb(0x383C42);
    public static final Color KEY_FACE = rgb(0x1F2226);
    public static final Color KEY_EDGE = rgb(0x3A434D);
    public static final Color LENS_OFF = rgb(0x1C1E21);
    public static final Color LENS_LEGEND = rgb(0x5A5E63);
    public static final Color KAPTON = rgb(0xC8962E);
    public static final Color ORANGE = rgb(0xE5531A);
    /** The empty part of a bar's track. */
    public static final Color TRACK = rgb(0x26303A);
    /** Ink on a lit lens or a filled box. */
    public static final Color INK = rgb(0x0A0A0A);
    /** The suit display's grey body (the DCM). */
    public static final Color SUIT = rgb(0xB9BDB8);
    public static final Color SUIT_EDGE = rgb(0x8E928D);
    public static final Color SUIT_INK = rgb(0x26282A);

    private NpColors() {}

    public static Color rgb(int v) {
        return new Color(((v >> 16) & 255) / 255f, ((v >> 8) & 255) / 255f, (v & 255) / 255f, 1f);
    }

    /** The colour of a severity on the glass. */
    public static Color of(Severity s) {
        switch (s) {
            case WARNING: return RED;
            case CAUTION: return AMBER;
            case ENGAGED: return GREEN;
            case COMMANDED: return CYAN;
            case GUIDANCE: return MAGENTA;
            case DIM: return DIM;
            default: return WHITE;
        }
    }

    /** What a box, a lens or a line of text is saying, which picks its colour. */
    public enum Severity { NORMAL, DIM, ENGAGED, COMMANDED, GUIDANCE, CAUTION, WARNING }
}
