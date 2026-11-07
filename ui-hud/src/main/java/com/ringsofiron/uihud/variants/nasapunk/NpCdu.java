package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;

/**
 * CDU-1, the flight computer every menu is a page of (documentation/ui-overhaul/variations/
 * nasapunk-shell: shell.js and screens.js {@code cdu()}), drawn as the mockups draw it: a bezel
 * with FLIGHT OPERATIONS stencilled across its top, a strip each side for the line-select keys, the
 * glass, and the unit's plate along its foot with the build as its part number. Out of a game it
 * sits in a console with the game's name engraved on it. Local units, y down.
 */
public final class NpCdu {
    /** The bezel round the glass, the strip each side that holds the line-select keys, and the foot that holds the plate. */
    public static final float BEZEL = 12f, KEY_ROW = 14f, FOOT = 22f;
    /** The glass's left edge, from the unit's. */
    public static final float GLASS_X = BEZEL + KEY_ROW;
    /** A line-select key: in the strip beside the glass, level with its row. */
    public static final float LSK_X = BEZEL - 1f, LSK_W = 12f, LSK_H = 16f;
    /** How far the page's content sits in from the unit's edges: a prompt's cursor box starts 5 in from the glass, its caret 10. */
    public static final float PAD_SIDE = GLASS_X + 5f, PAD_TOP = 15f, PAD_BOTTOM = 24f;
    /** The console round the unit on the main menu: its margin, and where its fasteners sit. */
    public static final float CONSOLE_MARGIN = 14f, CONSOLE_FASTENER = 8f;

    /** The page title's size and spacing (screens.js {@code cduHead}), and a prompt's (a data line). */
    public static final float TITLE = 15f, TITLE_TRACK = 0.8f, DATA = 15f, DATA_TRACK = 0.5f;
    /** A label line's, and a cell's (body text, a list's rows). */
    public static final float LABEL = 12f, LABEL_TRACK = 0.6f, CELL = 13f, CELL_TRACK = 0.3f;

    /** The shade behind a pressed prompt: the computer's reverse video (screens.js {@code modShade}). */
    public static final Color SHADE = NpColors.rgb(0x3B4858);
    /** An entry field's ground, a shade over the glass. */
    public static final Color FIELD = NpColors.rgb(0x0B1016);
    /** The console's engraving: the rule under the game's name, and its subtitle. */
    public static final Color RULE = NpColors.rgb(0x55595F), SUBTITLE = NpColors.rgb(0x9EA29C);

    private NpCdu() {}

    /** The unit, {@code w} by {@code h} at ({@code x}, {@code y}): bezel, fasteners, legend, glass and plate. */
    public static void unit(NpCanvas cv, float x, float y, float w, float h, String plate) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 6f, y + 6f);
        NpHardware.fastener(cv, x + w - 6f, y + 6f);
        NpHardware.fastener(cv, x + 6f, y + h - 6f);
        NpHardware.fastener(cv, x + w - 6f, y + h - 6f);
        NpHardware.stencil(cv, x + w / 2f, y + 9f, "FLIGHT OPERATIONS", 8.5f, 1.4f, NpCanvas.MIDDLE);
        NpHardware.glass(cv, x + GLASS_X, y + BEZEL, w - 2f * GLASS_X, h - BEZEL - FOOT);
        NpHardware.plate(cv, x + 18f, y + h - 13f, plate);
    }

    /** The console the unit sits in on the main menu: a bezel body with a fastener in each corner. */
    public static void console(NpCanvas cv, float w, float h) {
        NpHardware.bezel(cv, 0f, 0f, w, h, 4f);
        float f = CONSOLE_FASTENER;
        NpHardware.fastener(cv, f, f);
        NpHardware.fastener(cv, w - f, f);
        NpHardware.fastener(cv, f, h - f);
        NpHardware.fastener(cv, w - f, h - f);
    }

    /** A line-select key's cap at ({@code x}, {@code y}), raised or held down. */
    public static void lsk(NpCanvas cv, float x, float y, boolean pressed) {
        NpHardware.softKey(cv, x, y, LSK_W, LSK_H, pressed);
    }

    /** The cursor: a cyan box on the field the keyboard or the pad has reached (screens.js {@code cursor}). */
    public static void cursor(NpCanvas cv, float x, float y, float w, float h) {
        cv.stroke(x + 0.75f, y + 0.75f, w - 1.5f, h - 1.5f, 1.5f, NpColors.CYAN);
    }

    /** The baseline that puts text of {@code size} with its capitals centred on a box {@code h} tall. */
    public static float baseline(NpCanvas cv, float h, float size) {
        return h / 2f + cv.capHeight(size, true) / 2f;
    }

    /** A divider: a dashed rule each side of a word, as the computer heads a part of a page (screens.js {@code divider}). */
    public static void divider(NpCanvas cv, float x0, float x1, float wordX0, float wordX1, float y) {
        if (wordX0 - 10f > x0) cv.dashed(x0, y, wordX0 - 10f, y, 1f, NpColors.DIM, 6f, 4f);
        if (x1 > wordX1 + 10f) cv.dashed(wordX1 + 10f, y, x1, y, 1f, NpColors.DIM, 6f, 4f);
    }
}
