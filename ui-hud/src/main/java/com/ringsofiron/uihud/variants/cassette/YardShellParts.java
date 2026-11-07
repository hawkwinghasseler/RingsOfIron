package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;

/**
 * The hardware the menus wear (style guide, section 9; documentation/ui-overhaul/cassette/shell),
 * each drawn in its own coordinates, (0, 0) its top left, {@code w} by {@code h} units, as
 * {@link YardDrawable} asks. {@code focus} is the keyboard's focus, shown by the orange pointer tab.
 */
public final class YardShellParts {
    private YardShellParts() {}

    static final Color LIP_TOP = Yard.hex("F4EEDD"), LIP_FOOT = Yard.hex("B3A88E");
    static final Color CAST_TOP = Yard.hex("E4E1DA"), CAST_FOOT = Yard.hex("9C9890");
    static final Color GAP = Yard.hex("1F1B18"), GROUND = Yard.hex("1D1916");
    static final Color WELL = Yard.hex("B4A98F"), RACK = Yard.hex("2B2622");
    static final Color SHELL = Yard.hex("3B3835"), LABEL_TOP = Yard.hex("F3ECDA"), LABEL_FOOT = Yard.hex("E6DDC6");
    static final Color GRILLE = Yard.hex("C8BEA6"), HOLE = Yard.hex("3A332D");
    static final Color HATCH = Yard.hex("4A3F36"), HATCH_TOP = Yard.hex("CDC2A7"), HATCH_FOOT = Yard.hex("B5AA8F");
    static final Color DOOR_HINGE = Yard.hex("B7AD95"), DOOR_FREE = Yard.hex("8E846E"), DOOR_EDGE = Yard.hex("5D5444");
    static final Color DOOR_GROOVE = Yard.hex("6F6553");

    /** The orange pointer tab: its tip at ({@code x}, {@code cy}), pointing right at the control. */
    public static void focusTab(YardPaint p, float x, float cy) {
        p.fillConvex(new float[]{x - 10.5f, cy - 7.5f, x + 0.8f, cy, x - 10.5f, cy + 7.5f}, Yard.hex("7A3514"), 1f);
        p.fillConvex(new float[]{x - 9.5f, cy - 6.5f, x, cy, x - 9.5f, cy + 6.5f}, Yard.ORANGE, 1f);
    }

    // --- The faceplate and its plates --------------------------------------------------------

    /** The desk console's faceplate: enamel, a rolled lip along its top with the orange stripe under it, screws. */
    static void faceplate(YardPaint p, float w, float h, boolean focus) {
        p.rrect(3f, 6f, w, h, 6f, Yard.SHADOW, 0.7f);
        p.shade(0f, 0f, w, h, 6f, Yard.ENAMEL_TOP, Yard.ENAMEL_BOTTOM);
        // A pool of lamplight on the upper half
        p.glow(w / 2f, h * 0.3f, Math.max(w, h) * 0.6f, Color.WHITE, 0.06f);
        p.rect(1f, 16f, 1.2f, h - 22f, Yard.ENAMEL_HI, 0.8f);
        p.rect(w - 2.4f, 16f, 1.4f, h - 22f, Yard.ENAMEL_LO, 0.8f);
        p.rect(4f, h - 1.4f, w - 8f, 1.2f, Yard.ENAMEL_LO, 0.8f);
        p.shade(-7f, -5f, w + 14f, 11f, 5f, LIP_TOP, LIP_FOOT);
        p.rect(-4f, -4.4f, w + 8f, 1f, Color.WHITE, 0.6f);
        p.rect(0f, 7f, w, 6f, Yard.ORANGE);
        p.rect(0f, 13f, w, 1f, Yard.hex("6E3216"), 0.6f);
        p.screw(9f, 24f);
        p.screw(w - 9f, 24f);
        p.screw(9f, h - 10f);
        p.screw(w - 9f, h - 10f);
    }

    /** The builder's plate: cast aluminium round a painted ground, a screw at each end. */
    static void builderPlate(YardPaint p, float w, float h, boolean focus) {
        p.rrect(2f, 4f, w, h, 9f, Yard.SHADOW, 0.8f);
        p.shade(0f, 0f, w, h, 9f, CAST_TOP, CAST_FOOT);
        p.rect(9f, 2f, w - 18f, 1f, Color.WHITE, 0.55f);
        p.rrect(9f, 9f, w - 18f, h - 18f, 4f, GROUND);
        p.frame(9f, 9f, w - 18f, h - 18f, 1.5f, Color.BLACK, 0.45f);
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? 24f : w - 24f, cy = h / 2f;
            p.circle(cx, cy, 6.3f, Yard.hex("4D4A44"));
            p.circle(cx, cy, 5.5f, Yard.hex("C9C6BE"));
            p.circle(cx - 1.5f, cy - 1.8f, 1.6f, Color.WHITE, 0.6f);
        }
    }

    /** A recessed well in the faceplate, for a list. */
    public static void well(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 3f, WELL);
        p.rect(2f, 0f, w - 4f, 3f, Color.BLACK, 0.16f);
        p.rect(0f, 2f, 2f, h - 4f, Color.BLACK, 0.1f);
        p.rect(2f, h - 1.2f, w - 4f, 1.2f, Yard.ENAMEL_HI, 0.7f);
    }

    /** The tape deck the newest save sits in: a dark housing, a cassette in its well, the reels under its label. */
    static void tapeDeck(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 4f, Yard.hex("48403A"));
        p.rect(2f, 1f, w - 4f, 1f, Color.WHITE, 0.12f);
        float wx = 10f, wy = 10f, ww = w - 20f, wh = h - 38f;
        p.rrect(wx, wy, ww, wh, 3f, GAP);
        // The cassette: its shell, the label, the window with the reels
        float cx = wx + 8f, cy = wy + 7f, cw = ww - 16f, ch = wh - 14f;
        p.rrect(cx, cy, cw, ch, 4f, SHELL);
        p.screw(cx + 6f, cy + 6f);
        p.screw(cx + cw - 6f, cy + 6f);
        p.vgrad(cx + 12f, cy + 5f, cw - 24f, ch - 30f, LABEL_TOP, LABEL_FOOT);
        p.rect(cx + 12f, cy + ch - 28f, cw - 24f, 2.5f, Yard.ORANGE);
        float ry = cy + ch - 15f;
        p.rrect(cx + cw / 2f - 52f, ry - 7f, 104f, 14f, 7f, Yard.hex("161311"));
        for (int i = 0; i < 2; i++) {
            float hx = cx + cw / 2f + (i == 0 ? -40f : 40f);
            p.circle(hx, ry, 5f, Yard.hex("D8D2C4"));
            p.circle(hx, ry, 2.4f, Yard.hex("2A2622"));
        }
        p.legend("NEWEST SAVE", 12f, h - 10f, Yard.INK_LIGHT);
    }

    /** The deck with no cassette in it: the empty well. */
    static void emptyDeck(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 4f, Yard.hex("48403A"));
        p.rect(2f, 1f, w - 4f, 1f, Color.WHITE, 0.12f);
        p.rrect(10f, 10f, w - 20f, h - 38f, 3f, GAP);
        p.rect(18f, 18f, w - 36f, 2f, Color.WHITE, 0.04f);
        p.legend("NEWEST SAVE", 12f, h - 10f, Yard.INK_LIGHT);
    }

    /** A bay of the maintenance panel: a plate let into the faceplate, a screw at each corner. */
    public static void bay(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 3f, Yard.hex("A79C84"));
        p.shade(1.5f, 1.5f, w - 3f, h - 3f, 2.5f, Yard.hex("DDD3B9"), Yard.hex("CBC0A4"));
        p.rect(3f, 2f, w - 6f, 1f, Yard.ENAMEL_HI, 0.8f);
        p.screw(7f, 7f);
        p.screw(w - 7f, 7f);
        p.screw(7f, h - 7f);
        p.screw(w - 7f, h - 7f);
    }

    /** The ship console's opening the pause hatch swings out of: a dark frame round the faceplate. */
    static void hatchFrame(YardPaint p, float w, float h, boolean focus) {
        p.rrect(3f, 6f, w, h, 5f, Yard.SHADOW, 0.8f);
        p.rrect(0f, 0f, w, h, 5f, HATCH);
        p.rect(4f, 1f, w - 8f, 1f, Color.WHITE, 0.1f);
        p.rrect(9f, 9f, w - 18f, h - 18f, 3f, Color.BLACK, 0.5f);
        // The piano hinge's knuckles down its left edge, the faceplate swung out on them
        for (float y = 14f; y + 18f <= h - 6f; y += 26f) p.rrect(-3f, y, 6f, 18f, 1f, Yard.ALU);
    }

    /** The speaker grille: holes punched in a grid. */
    static void grille(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 3f, GRILLE);
        p.rect(2f, 0f, w - 4f, 2f, Color.BLACK, 0.12f);
        for (float y = 10f; y < h - 6f; y += 7.5f) {
            for (float x = 10f; x < w - 6f; x += 7.5f) p.circle(x, y, 1.35f, HOLE);
        }
    }

    /** An engraved rule across the faceplate, or the painted red line before the quits. */
    public static void rule(YardPaint p, float w, float h, boolean red) {
        float y = h / 2f;
        if (red) {
            p.rect(-8f, y - 1.5f, w + 16f, 3f, Yard.RED);
            return;
        }
        p.rect(0f, y - 0.6f, w, 1f, Yard.INK, 0.3f);
        p.rect(0f, y + 0.4f, w, 1f, Yard.ENAMEL_HI, 0.8f);
    }

    /** The open hatch's door, swung out on its hinge to the left of the panel and seen in perspective. */
    static void hinge(YardPaint p, float w, float h, boolean focus) {
        // In perspective: the hinge edge 4 in from the surround's corners, the free edge, nearer the eye, 44
        float[] door = {w, 4f, 0f, 44f, 0f, h - 44f, w, h - 4f};
        p.quad(door, DOOR_HINGE, DOOR_FREE, DOOR_FREE, DOOR_HINGE, 1f);
        p.line(w, 4f, 0f, 44f, 1.2f, DOOR_EDGE, 1f);
        p.rect(0f, 44f, 1.2f, h - 88f, DOOR_EDGE);
        p.line(0f, h - 44f, w, h - 4f, 1.2f, DOOR_EDGE, 1f);
        // The stiffening groove pressed down its length
        if (h > 160f) p.rect(11.5f, 74f, 5f, h - 148f, DOOR_GROOVE, 0.5f);
    }

    /** The technician's panel behind the swung-out faceplate: plain painted steel, screwed at its corners. */
    static void hatchPanel(YardPaint p, float w, float h, boolean focus) {
        p.shade(0f, 0f, w, h, 3f, HATCH_TOP, HATCH_FOOT);
        p.rect(3f, 1f, w - 6f, 1f, Color.WHITE, 0.35f);
        p.screw(8f, 8f);
        p.screw(w - 8f, 8f);
        p.screw(8f, h - 8f);
        p.screw(w - 8f, h - 8f);
    }

    /** A riveted plate: the answers at the foot of the caution tag. */
    public static void rivetedPlate(YardPaint p, float w, float h, boolean focus) {
        p.rrect(2f, 4f, w, h, 3f, Yard.SHADOW, 0.7f);
        p.shade(0f, 0f, w, h, 3f, Yard.hex("E6E4DE"), Yard.hex("A7A49C"));
        p.rect(3f, 1f, w - 6f, 1f, Color.WHITE, 0.6f);
        p.fixing(8f, 8f, Yard.Finish.GUN);
        p.fixing(w - 8f, 8f, Yard.Finish.GUN);
        p.fixing(8f, h - 8f, Yard.Finish.GUN);
        p.fixing(w - 8f, h - 8f, Yard.Finish.GUN);
    }

    /** Under a bay's engraved name: a short rule. */
    static void bayLabel(YardPaint p, float w, float h, boolean focus) {
        p.rect(0f, h - 2.4f, w, 1f, Yard.INK, 0.3f);
        p.rect(0f, h - 1.4f, w, 1f, Yard.ENAMEL_HI, 0.8f);
    }

    /** A typed card under two clips. */
    public static void card(YardPaint p, float w, float h, boolean focus) {
        p.paper(0f, 0f, w, h);
        p.shade(16f, -4f, 18f, 9f, 1.5f, CAST_TOP, CAST_FOOT);
        p.shade(w - 34f, -4f, 18f, 9f, 1.5f, CAST_TOP, CAST_FOOT);
    }

    /** The serial plate the build's version is stamped on. */
    static void serialPlate(YardPaint p, float w, float h, boolean focus) {
        p.rrect(1.5f, 3f, w, h, 3f, Yard.SHADOW, 0.7f);
        p.shade(0f, 0f, w, h, 3f, CAST_TOP, CAST_FOOT);
        p.rect(3f, 1f, w - 6f, 1f, Color.WHITE, 0.6f);
        p.circle(8f, h / 2f, 2.8f, Yard.SCREW_SLOT);
        p.circle(8f, h / 2f, 2.3f, Yard.SCREW);
        p.circle(w - 8f, h / 2f, 2.8f, Yard.SCREW_SLOT);
        p.circle(w - 8f, h / 2f, 2.3f, Yard.SCREW);
        p.legend("SERIAL", 18f, 13f, Yard.INK);
    }

    /** Dymo tape: black, a faint shine along its top. */
    public static void dymoTape(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 1.5f, Yard.hex("1C1B1A"));
        p.rect(0f, 0.6f, w, 1f, Color.WHITE, 0.12f);
        p.rect(0f, h - 2f, w, 2f, Color.BLACK, 0.35f);
    }

    /** A rubber stamp's frame in blue ink. */
    public static void stamp(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 3f, YardSkin.STAMP, 0.08f);
        p.frame(0f, 0f, w, h, 1.4f, YardSkin.STAMP, 0.8f);
    }

    /** A cream lens, lit: the PAUSED lamp. */
    public static void creamLens(YardPaint p, float w, float h, boolean focus) {
        p.rrect(-2f, -2f, w + 4f, h + 4f, 2f, Yard.ALU);
        p.glowRect(0f, 0f, w, h, 7f, Yard.WHITE_LAMP.on, 0.4f);
        p.rrect(0f, 0f, w, h, 1.2f, Yard.hex("FFF6DC"));
        p.glass(0f, 0f, w, h);
    }

    /** A split flap's window, for a word that changes. */
    public static void flapWindow(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 1.5f, Yard.FLAP_FRAME);
        p.rect(2f, 2f, w - 4f, h - 4f, Yard.FLAP_CELL);
        p.rect(2f, 2f, w - 4f, (h - 4f) / 2f, Color.WHITE, 0.025f);
        p.rect(2f, h / 2f - 0.5f, w - 4f, 1f, Color.BLACK, 0.7f);
    }

    /** A drum counter's window, for an exact figure. */
    public static void drumWindow(YardPaint p, float w, float h, boolean focus) {
        p.rrect(-2f, -2f, w + 4f, h + 4f, 2.5f, Yard.BAKELITE);
        p.vgrad(0f, 0f, w, h / 2f, Yard.hex("050505"), Yard.hex("34302D"));
        p.vgrad(0f, h / 2f, w, h / 2f, Yard.hex("34302D"), Yard.hex("050505"));
        p.glass(-2f, -2f, w + 4f, h + 4f);
    }

    // --- Buttons -----------------------------------------------------------------------------

    /**
     * A large momentary push button: an aluminium bezel, a dark gap and the cap with its lip. Pressed,
     * the cap sinks; barred (it can't be pressed now), the barber strip runs along its foot.
     */
    public static void push(YardPaint p, float w, float h, YardSkin.Cap cap, boolean down, boolean over, boolean barred, boolean focus) {
        p.rrect(0f, 0f, w, h, 4.5f, Yard.ALU);
        p.glass(0f, 0f, w, h);
        p.rrect(2.2f, 2.2f, w - 4.4f, h - 4.4f, 3f, GAP);
        float x = 4f, y = 4f + (down ? 1.5f : 0f), cw = w - 8f, ch = h - 8f - 3f - (down ? 1.5f : 0f);
        if (!down) p.rrect(x + 0.5f, y + 1.8f, cw, ch + 1f, 3f, Color.BLACK, 0.35f);
        Color top = cap.top;
        if (over && !barred) top = Yard.hex(cap == YardSkin.Cap.CREAM ? "F7F2E4" : cap == YardSkin.Cap.GREEN ? "B9EE98" : cap == YardSkin.Cap.AMBER ? "FFD285" : "D65A4D");
        if (down) top = cap.lip.cpy().lerp(cap.top, 0.65f);
        p.rrect(x, y, cw, ch, 3f, top);
        p.rect(x, y + ch - 4f, cw, 4f, cap.lip);
        p.rect(x + 3f, y + 1f, cw - 6f, 1f, Color.WHITE, down ? 0.3f : 0.55f);
        if (cap == YardSkin.Cap.GREEN && !barred) p.glowRect(x, y, cw, ch, 5f, Yard.GREEN_LAMP.on, 0.18f);
        if (barred) p.barber(x, y + ch - 11f, cw, 7f);
        if (focus) focusTab(p, -4f, h / 2f);
    }

    /**
     * A push button under a red flip guard (style guide 9.7): shut, the order's name on the guard; lifted
     * ({@code open}), the cream cap under it and the lamp beside it lit, and the next press gives it.
     */
    public static void guarded(YardPaint p, float w, float h, boolean over, boolean open, boolean focus) {
        push(p, w, h, YardSkin.Cap.CREAM, false, over && open, false, false);
        float lx = -14f;
        p.lamp(lx, h / 2f, 4f, Yard.RED_LAMP, open);
        if (open) {
            p.rrect(-3f, -26f, w + 6f, 22f, 3f, Yard.RED);
            p.rect(-3f, -8f, w + 6f, 4f, Yard.hex("7A221B"));
            p.glass(-3f, -26f, w + 6f, 22f);
        } else {
            p.rrect(-4f, -4f, w + 8f, h + 8f, 4f, over ? Yard.hex("C9463A") : Yard.RED, 0.9f);
            p.glass(-4f, -4f, w + 8f, h + 8f);
            p.rect(-4f, -4f, w + 8f, 5f, Yard.hex("7A221B"));
        }
        if (focus) focusTab(p, lx - 8f, h / 2f);
    }

    /** Interlocked keys: one key, raised, or pressed in with its foot safety orange. */
    public static void pianoKey(YardPaint p, float w, float h, boolean down, boolean over, boolean focus) {
        p.rrect(0f, 0f, w, h, 2.5f, Yard.BAKELITE);
        float x = 3f, y = 3f + (down ? 2f : 0f), kw = w - 6f, kh = h - 8f;
        if (!down) p.rrect(x + 0.5f, y + 1.5f, kw, kh, 2f, Color.BLACK, 0.4f);
        p.rrect(x, y, kw, kh, 2f, down ? Yard.hex("CFC4A8") : over ? Yard.hex("F5EEDB") : Yard.hex("ECE4CE"));
        p.rect(x + 2f, y + 0.8f, kw - 4f, 1f, Color.WHITE, 0.5f);
        if (down) p.rect(x, y + kh - 3f, kw, 3f, Yard.ORANGE);
        if (focus) focusTab(p, -4f, h / 2f);
    }

    /** A keycap on the patch panel: rubber, a lighter top. */
    public static void keycap(YardPaint p, float w, float h, boolean over, boolean focus) {
        p.rrect(0.5f, 1.5f, w, h, 2.5f, Color.BLACK, 0.35f);
        p.rrect(0f, 0f, w, h, 2.5f, Yard.RUBBER);
        p.rrect(1.2f, 0.8f, w - 2.4f, h - 4f, 2f, over ? Yard.hex("5A5550") : Yard.hex("4D4945"));
        if (focus) focusTab(p, -4f, h / 2f);
    }

    /** A cassette's spine in the rack: its shell and its typed label with the orange stripe. */
    static void spine(YardPaint p, float w, float h, boolean over, boolean focus) {
        p.rrect(0f, 0f, w, h, 3f, RACK);
        p.rrect(3f, 3f, w - 6f, h - 6f, 2f, SHELL);
        p.vgrad(9f, 5f, w - 18f, h - 10f, over ? Yard.hex("FBF6E8") : LABEL_TOP, LABEL_FOOT);
        p.rect(9f, h - 8f, w - 18f, 2f, Yard.ORANGE);
        if (focus) focusTab(p, -4f, h / 2f);
    }

    // --- Switches and fields -----------------------------------------------------------------

    /** A bat toggle, up for on, with ON and OFF engraved beside it. */
    public static void bat(YardPaint p, float w, float h, boolean on) {
        float cx = 12f, cy = h / 2f;
        p.toggle(cx, cy, on, false, false);
        p.legend("ON", 27f, cy - 5f, on ? Yard.INK : YardSkin.INK_DIM);
        p.legend("OFF", 27f, cy + 13f, on ? YardSkin.INK_DIM : Yard.INK);
    }

    /** A label holder: a card held in a cast frame, under glass. */
    static void holder(YardPaint p, float w, float h, boolean focus) {
        p.rrect(1.5f, 3f, w, h, 2.5f, Yard.SHADOW, 0.7f);
        p.shade(0f, 0f, w, h, 2.5f, CAST_TOP, CAST_FOOT);
        p.rect(5f, 5f, w - 10f, h - 10f, Yard.hex("F5EFE0"));
        p.shade(0f, 0f, w, 6f, 2f, CAST_TOP, Yard.hex("BEBAB2"));
        p.shade(0f, h - 6f, w, 6f, 2f, Yard.hex("BEBAB2"), CAST_FOOT);
        p.glass(5f, 5f, w - 10f, h - 10f);
        if (focus) focusTab(p, -4f, h / 2f);
    }

    static void scrollSlot(YardPaint p, float w, float h, boolean focus) {
        p.rrect(1f, 0f, w - 2f, h, 3f, Yard.BAKELITE);
    }

    static void scrollKnob(YardPaint p, float w, float h, boolean focus) {
        p.shade(1.5f, 1f, w - 3f, h - 2f, 2f, Yard.hex("E9E6DF"), Yard.ALU);
    }

    /** The list a flap selector drops open: dark, framed. */
    static void flapList(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0f, 0f, w, h, 2f, Yard.FLAP_FRAME);
        p.rect(2f, 2f, w - 4f, h - 4f, Yard.FLAP_CELL);
    }

    /** A flap selector: a flap window between two arrow keys (Left and Right step it). */
    static void flapSelector(YardPaint p, float w, float h, boolean over, boolean focus) {
        float k = Math.min(22f, h);
        arrowKey(p, 0f, (h - k) / 2f, k, k, false);
        arrowKey(p, w - k, (h - k) / 2f, k, k, true);
        float x = k + 4f, fw = w - 2f * k - 8f;
        p.rrect(x, 0f, fw, h, 1.5f, Yard.FLAP_FRAME);
        p.rect(x + 2f, 2f, fw - 4f, h - 4f, over ? Yard.hex("2D2926") : Yard.FLAP_CELL);
        p.rect(x + 2f, 2f, fw - 4f, (h - 4f) / 2f, Color.WHITE, 0.025f);
        p.rect(x + 2f, h / 2f - 0.5f, fw - 4f, 1f, Color.BLACK, 0.6f);
        if (focus) focusTab(p, -4f, h / 2f);
    }

    /** A bakelite key with a drawn arrow, pointing right or left. */
    static void arrowKey(YardPaint p, float x, float y, float w, float h, boolean right) {
        p.rrect(x + 0.5f, y + 1.5f, w, h, 3f, Color.BLACK, 0.35f);
        p.rrect(x, y, w, h, 3f, Yard.BAKELITE);
        p.rrect(x + 2f, y + 1.5f, w - 4f, h - 6f, 2f, Yard.hex("3A3430"));
        float cx = x + w / 2f, cy = y + (h - 4f) / 2f + 0.5f, d = right ? 1f : -1f;
        p.fillConvex(new float[]{cx - 4f * d, cy - 5f, cx + 4f * d, cy, cx - 4f * d, cy + 5f}, Yard.INK_LIGHT, 1f);
    }

    /** A slide pot's slot, across: ticks along it, a dark groove down its middle. */
    static void potSlot(YardPaint p, float w, float h, boolean focus) {
        float cy = h / 2f;
        for (int i = 0; i <= 10; i++) {
            float x = 8f + (w - 16f) * i / 10f;
            float t = i % 5 == 0 ? 6f : 3.5f;
            p.rect(x - 0.5f, 0f, 1f, t, Yard.INK, 0.8f);
            p.rect(x - 0.5f, h - t, 1f, t, Yard.INK, 0.8f);
        }
        p.rrect(4f, cy - 2.5f, w - 8f, 5f, 2.5f, Yard.BAKELITE);
        p.rect(6f, cy + 1.2f, w - 12f, 1f, Color.WHITE, 0.15f);
        if (focus) focusTab(p, -4f, cy);
    }

    /** A slide pot's cap: black, an index line across its middle. */
    static void potKnob(YardPaint p, float w, float h, boolean focus) {
        p.rrect(0.8f, 2f, w, h, 2f, Color.BLACK, 0.4f);
        p.shade(0f, 0f, w, h, 2f, Yard.hex("3A3633"), Yard.hex("141210"));
        p.rect(1.5f, 0.8f, w - 3f, 1f, Color.WHITE, 0.25f);
        p.rect(w / 2f - 0.8f, 3f, 1.6f, h - 6f, Yard.hex("F3EEE2"));
    }

    // --- The caution tag ---------------------------------------------------------------------

    /**
     * The caution tag hung on a wire over the menu: yellow card with its top corners cut, a grommet, the
     * black band with CAUTION in it. The question's title is typed under the band.
     */
    static void cautionTag(YardPaint p, float w, float h, boolean focus) {
        float cut = 26f;
        p.line(w / 2f, -2000f, w / 2f, 18f, 1.2f, Yard.hex("C9C4BA"));
        float[] shape = {cut, 0f, w - cut, 0f, w, cut, w, h, 0f, h, 0f, cut};
        float[] shadow = new float[shape.length];
        for (int i = 0; i < shape.length; i += 2) {
            shadow[i] = shape[i] + 4f;
            shadow[i + 1] = shape[i + 1] + 6f;
        }
        p.fillConvex(shadow, Color.BLACK, 0.4f);
        p.fillConvex(shape, YardSkin.TAG, 1f);
        p.vgrad(0f, h * 0.55f, w, h * 0.45f, new Color(YardSkin.TAG_FOOT.r, YardSkin.TAG_FOOT.g, YardSkin.TAG_FOOT.b, 0f), YardSkin.TAG_FOOT);
        p.circle(w / 2f, 18f, 8f, Yard.hex("8D8A84"));
        p.circle(w / 2f, 18f, 6.2f, Yard.hex("E8E6E0"));
        p.circle(w / 2f, 18f, 3.6f, Yard.hex("2A2622"));
        float by = 34f, bh = 46f;
        p.rect(18f, by, w - 36f, bh, Yard.hex("1E1A17"));
        float size = 38f;
        p.textC(YardFonts.Face.DYMO, size, "CAUTION", w / 2f, by + bh / 2f + size * 0.36f, YardSkin.TAG);
    }
}
