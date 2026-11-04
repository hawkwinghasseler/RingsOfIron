package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;

/**
 * The hardware the glass sits in, drawn as the mockups' kit draws it (documentation/ui-overhaul/
 * nasapunk/mockups/lib.js and kit.js): bezels with fasteners, soft keys, lens annunciators, status
 * boxes, key caps, and the console prompt. Everything is in local UI units, y down.
 */
public final class NpHardware {
    private static final Color KEY_HI = NpColors.rgb(0x4A4F56);
    private static final Color KEY_DOWN = NpColors.rgb(0x24272B);
    private static final Color FASTENER = NpColors.rgb(0x24272B);
    private static final Color SLOT = NpColors.rgb(0x15171A);
    private static final Color GLASS_EDGE = NpColors.rgb(0x0E141A);
    private static final Color LENS_ADVISORY = NpColors.rgb(0x3D454E);
    private static final Color HIGHLIGHT = new Color(1f, 1f, 1f, 1f);
    private static final Color PROMPT_VERB = NpColors.rgb(0xC9CCC6);
    private static final Color SLIP = new Color(5 / 255f, 7 / 255f, 10 / 255f, 0.92f);

    private NpHardware() {}

    /** A bezel's body: shaded top to bottom, lit along its top and left edges, shadowed below. */
    public static void bezel(NpCanvas cv, float x, float y, float w, float h, float r) {
        float rr = Math.min(r, Math.min(w, h) / 2f);
        cv.fillGradient(x + rr, y, w - 2f * rr, h, NpColors.BEZEL_TOP, NpColors.BEZEL);
        cv.fillGradient(x, y + rr, rr, h - 2f * rr, NpColors.BEZEL_TOP, NpColors.BEZEL);
        cv.fillGradient(x + w - rr, y + rr, rr, h - 2f * rr, NpColors.BEZEL_TOP, NpColors.BEZEL);
        if (rr > 0.5f) {
            cv.disc(x + rr, y + rr, rr * 2f, NpColors.BEZEL_TOP);
            cv.disc(x + w - rr, y + rr, rr * 2f, NpColors.BEZEL_TOP);
            cv.disc(x + rr, y + h - rr, rr * 2f, NpColors.BEZEL);
            cv.disc(x + w - rr, y + h - rr, rr * 2f, NpColors.BEZEL);
        }
        cv.fill(x + rr, y + h - 1f, w - 2f * rr, 1f, NpColors.EDGE_LO);
        cv.fill(x + w - 1f, y + rr, 1f, h - 2f * rr, NpColors.EDGE_LO);
        cv.fill(x + rr, y, w - 2f * rr, 1f, NpColors.EDGE_HI);
        cv.fill(x, y + rr, 1f, h - 2f * rr, NpColors.EDGE_HI);
    }

    public static void bezel(NpCanvas cv, float x, float y, float w, float h) {
        bezel(cv, x, y, w, h, 4f);
    }

    /** A screw head on a bezel's corner. */
    public static void fastener(NpCanvas cv, float x, float y) {
        cv.disc(x, y, 5.2f, FASTENER);
        cv.ring(x, y, 2.6f, 0.6f, NpColors.EDGE_HI);
        cv.line(x - 1.8f, y + 1.2f, x + 1.8f, y - 1.2f, 0.8f, SLOT);
    }

    /** A soft key's cap beside the glass, raised or pressed. */
    public static void softKey(NpCanvas cv, float x, float y, float w, float h, boolean pressed) {
        cv.fillRound(x, y, w, h, 2f, pressed ? KEY_DOWN : NpColors.KEY, 1f);
        cv.fill(x + 1f, y + h - 1f, w - 2f, 1f, pressed ? NpColors.EDGE_HI : NpColors.EDGE_LO);
        cv.fill(x + 1f, y, w - 2f, 1f, pressed ? NpColors.EDGE_LO : KEY_HI);
    }

    /** The glass of a display unit, with its dark inner edge. */
    public static void glass(NpCanvas cv, float x, float y, float w, float h) {
        cv.fill(x, y, w, h, NpColors.GLASS);
        cv.stroke(x + 0.5f, y + 0.5f, w - 1f, h - 1f, 1f, GLASS_EDGE);
    }

    /** A printed legend on the hardware: bezel titles, plates, key letters. */
    public static void stencil(NpCanvas cv, float x, float y, String s, float size, float track, int anchor) {
        cv.text(x, y, s, size, true, NpColors.STENCIL, track, anchor);
    }

    /** A part plate: a kapton tab and the unit's name, bottom left on the bezel. */
    public static void plate(NpCanvas cv, float x, float y, String name) {
        cv.fill(x, y, 3f, 8f, NpColors.KAPTON);
        stencil(cv, x + 7f, y + 7f, name, 7.5f, 1f, NpCanvas.START);
    }

    /**
     * A lens annunciator: dark with a grey legend when unlit, filled with {@code lit} and a dark
     * legend when lit. {@code legend} may hold a line break.
     */
    public static void lens(NpCanvas cv, float x, float y, float w, float h, String legend, Color lit) {
        lens(cv, x, y, w, h, legend, lit, false, 0f);
    }

    /**
     * A lens; {@code advisory} lights it softly (a light legend on grey) instead of hard, and
     * {@code floor} is the smallest legend size the page allows (0 for the kit's own).
     */
    public static void lens(NpCanvas cv, float x, float y, float w, float h, String legend, Color lit, boolean advisory, float floor) {
        boolean on = lit != null || advisory;
        Color fill = advisory ? LENS_ADVISORY : lit != null ? lit : NpColors.LENS_OFF;
        Color ink = advisory ? NpColors.WHITE : lit != null ? NpColors.INK : NpColors.LENS_LEGEND;
        cv.fillRound(x - 1f, y - 1f, w + 2f, h + 2f, 2f, NpColors.BEZEL_SHADOW, 1f);
        cv.fillRound(x, y, w, h, 1.5f, fill, 1f);
        if (on) cv.fillRound(x, y, w, h * 0.35f, 1.5f, HIGHLIGHT, advisory ? 0.06f : 0.12f);
        String[] lines = legend.split("\n");
        float size = Math.max(lines.length > 1 ? 8.5f : 10f, floor), pitch = Math.max(10f, size + 1f);
        float track = floor > 0f ? 0.2f : 0.5f;
        for (int i = 0; i < lines.length; i++) {
            float ly = y + h / 2f + size * 0.36f + (i - (lines.length - 1) / 2f) * pitch;
            cv.text(x + w / 2f, ly, lines[i], size, true, ink, track, NpCanvas.MIDDLE);
        }
    }

    /** What a status box says: its outline colour, and dashed when off. */
    public enum BoxState { NORMAL, ENGAGED, CAUTION, WARNING, OFF, SELECTED, GUIDANCE }

    public static Color boxColor(BoxState s) {
        switch (s) {
            case ENGAGED: return NpColors.GREEN;
            case CAUTION: return NpColors.AMBER;
            case WARNING: return NpColors.RED;
            case OFF: return NpColors.DIM;
            case SELECTED: return NpColors.CYAN;
            case GUIDANCE: return NpColors.MAGENTA;
            default: return NpColors.WHITE;
        }
    }

    /** A status box (an EICAS or FMA box): its label inside an outline in its state's colour. */
    public static void box(NpCanvas cv, float x, float y, float w, float h, String t, BoxState state, float size, boolean filled) {
        Color c = boxColor(state);
        if (filled) cv.fill(x, y, w, h, c);
        if (state == BoxState.OFF) cv.strokeDashed(x, y, w, h, 1.2f, c, 3f, 2f);
        else cv.stroke(x, y, w, h, state == BoxState.SELECTED ? 1.8f : 1.2f, c);
        if (t != null && !t.isEmpty()) cv.text(x + w / 2f, y + h / 2f + size * 0.38f, t, size, true, filled ? NpColors.GLASS : c, 0.5f, NpCanvas.MIDDLE);
    }

    public static void box(NpCanvas cv, float x, float y, float w, float h, String t, BoxState state) {
        box(cv, x, y, w, h, t, state, 10.5f, false);
    }

    /** How wide a box fits {@code t} with {@code pad} each side. */
    public static float boxWidth(NpCanvas cv, String t, float size, float pad) {
        return cv.width(t, size, true, 0.5f) + 2f * pad;
    }

    /** How wide a key cap printed with {@code k} is. */
    public static float capWidth(NpCanvas cv, String k, float size) {
        return cv.width(k, size, true, 0f) + 10f;
    }

    /** A keyboard key's cap with its letter printed on it; returns its width. */
    public static float keyCap(NpCanvas cv, float x, float y, String k, float size, float h) {
        float w = capWidth(cv, k, size);
        softKey(cv, x, y, w, h, false);
        cv.text(x + w / 2f, y + h / 2f + size * 0.36f, k, size, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
        return w;
    }

    /**
     * A key hint on the glass: a cap and what it does, in a row starting at {@code x}; returns the
     * width drawn.
     */
    public static float keyHint(NpCanvas cv, float x, float y, String k, String does, float size, Color doesColor) {
        float h = size + 6f, w = keyCap(cv, x, y, k, size, h);
        float tw = cv.text(x + w + 6f, y + h / 2f + size * 0.38f, does, size, true, doesColor, 0.5f, NpCanvas.START);
        return w + 6f + tw;
    }

    public static float keyHintWidth(NpCanvas cv, String k, String does, float size) {
        return capWidth(cv, k, size) + 6f + cv.width(does, size, true, 0.5f);
    }

    /**
     * A console prompt: a small plate with the console's name and its key caps, centred on
     * {@code cx}. {@code verbs} alternates keys and what they do.
     */
    public static void prompt(NpCanvas cv, float cx, float y, String name, String... verbs) {
        float nameW = cv.width(name, 12f, true, 1f) + 16f, w = nameW + 6f;
        for (int i = 0; i + 1 < verbs.length; i += 2) w += capWidth(cv, verbs[i], 10.5f) + 5f + cv.width(verbs[i + 1], 11.5f, false, 0f) + 2f + 10f;
        float x = cx - w / 2f;
        bezel(cv, x, y, w, 26f, 3f);
        cv.fill(x + 3f, y + 3f, 2f, 20f, NpColors.KAPTON);
        cv.text(x + 11f, y + 17.5f, name, 12f, true, NpColors.STENCIL, 1f, NpCanvas.START);
        float px = x + nameW + 4f;
        for (int i = 0; i + 1 < verbs.length; i += 2) {
            float kw = capWidth(cv, verbs[i], 10.5f);
            softKey(cv, px, y + 5f, kw, 16f, false);
            cv.text(px + kw / 2f, y + 17f, verbs[i], 10.5f, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
            float vw = cv.text(px + kw + 5f, y + 17.5f, verbs[i + 1], 11.5f, false, PROMPT_VERB, 0f, NpCanvas.START);
            px += kw + 5f + vw + 2f + 10f;
        }
    }

    /**
     * An annunciator tile, lit amber or red with dark ink, or (clearing) dark with its outline and
     * text in the colour; returns its width.
     */
    public static float tile(NpCanvas cv, float x, float y, String tag, String value, boolean warning, boolean clearing, float h) {
        float size = 12f, tw = tileWidth(cv, tag, value);
        Color fill = warning ? NpColors.RED : NpColors.AMBER;
        cv.fillRound(x - 1f, y - 1f, tw + 2f, h + 2f, 2f, NpColors.BEZEL_SHADOW, 1f);
        if (clearing) {
            cv.fill(x, y, tw, h, NpColors.GLASS);
            cv.stroke(x, y, tw, h, 1.5f, fill);
        } else {
            cv.fillRound(x, y, tw, h, 1.5f, fill, 1f);
            cv.fillRound(x, y, tw, h * 0.38f, 1.5f, HIGHLIGHT, 0.13f);
        }
        Color ink = clearing ? fill : NpColors.INK;
        float base = y + h / 2f + 4.5f;
        float tagW = cv.text(x + 8f, base, tag, size, true, ink, 0.5f, NpCanvas.START);
        if (value != null && !value.isEmpty()) cv.text(x + 8f + tagW + 8f, base, value, size, false, ink, 0f, NpCanvas.START);
        return tw;
    }

    public static float tileWidth(NpCanvas cv, String tag, String value) {
        float w = cv.width(tag, 12f, true, 0.5f) + 16f;
        if (value != null && !value.isEmpty()) w += cv.width(value, 12f, false, 0f) + 8f;
        return w;
    }

    /**
     * A message on its own glass slip: a loop tag in a box, then the words in their severity's
     * colour; {@code w} 0 fits the text. Returns the slip's width.
     */
    public static float callout(NpCanvas cv, float x, float y, float w, String tag, String msg, Color col, boolean advisory, float size, String count) {
        float tagSize = Math.max(10f, size - 2f);
        float tagW = tag == null || tag.isEmpty() ? 0f : cv.width(tag, tagSize, true, 0.6f) + 10f;
        if (w <= 0f) w = tagW + 8f + cv.width(msg, size, false, 0f) + 14f + (count == null ? 0f : cv.width(count, 11f, true, 0f) + 8f);
        float th = Math.max(14f, tagSize + 4f), sh = Math.max(20f, size + 8f);
        cv.fill(x, y, w, sh, SLIP);
        Color tagC = advisory ? NpColors.DIM : col;
        if (tagW > 0f) {
            cv.stroke(x + 4f, y + sh / 2f - th / 2f, tagW, th, 1f, tagC);
            cv.text(x + 4f + tagW / 2f, y + sh / 2f + tagSize * 0.36f, tag, tagSize, true, tagC, 0.6f, NpCanvas.MIDDLE);
        }
        cv.text(x + tagW + (tagW > 0f ? 12f : 6f), y + sh / 2f + size * 0.38f, msg, size, false, col, 0f, NpCanvas.START);
        if (count != null) cv.text(x + w - 6f, y + sh / 2f + size * 0.38f, count, 11f, true, NpColors.DIM, 0f, NpCanvas.END);
        return w;
    }

    /** The glass slip a line of text sits on over the world. */
    public static void slip(NpCanvas cv, float x, float y, float w, float h) {
        cv.fill(x, y, w, h, SLIP);
    }

    /**
     * The suit display on the captain's chest (an EVA suit's DCM): a light grey body with a small
     * glass, rows of label and value. {@code rows} holds label, value pairs; {@code colors} each
     * value's colour.
     */
    public static float dcm(NpCanvas cv, float x, float y, float w, String[] rows, Color[] colors, float floor) {
        float pitch = Math.max(18f, Math.max(10f, floor) + 7f), h = 34f + rows.length / 2 * pitch;
        cv.fillRound(x, y, w, h, 6f, NpColors.SUIT, 1f);
        cv.stroke(x, y, w, h, 1f, NpColors.SUIT_EDGE);
        cv.text(x + 10f, y + 13f, "DCM  SUIT", 8.5f, true, NpColors.SUIT_INK, 1.4f, NpCanvas.START);
        cv.fillRound(x + w - 30f, y + 6f, 20f, 8f, 1f, NpColors.ORANGE, 1f);
        cv.fill(x + 8f, y + 20f, w - 16f, h - 28f, NpColors.GLASS);
        for (int i = 0; i + 1 < rows.length; i += 2) {
            float yy = y + 36f + i / 2 * pitch;
            cv.text(x + 16f, yy, rows[i], Math.max(10f, floor), true, NpColors.DIM, 0.6f, NpCanvas.START);
            cv.text(x + w - 16f, yy, rows[i + 1], Math.max(13f, floor), true, colors[i / 2], 0f, NpCanvas.END);
        }
        return h;
    }
}
