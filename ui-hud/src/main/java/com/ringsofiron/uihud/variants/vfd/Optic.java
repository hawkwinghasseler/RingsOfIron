package com.ringsofiron.uihud.variants.vfd;

import com.ringsofiron.uihud.data.Insets;

/**
 * The game's magnified insets (its picture-in-picture bubbles) as FILAMENT's projector draws them
 * (style-vfd 8, "Picture-in-picture ring"). The game lays the magnified picture under the HUD; over
 * it go a circle of dots every 4 units just outside the picture, the craft's lock or nav mark round
 * the circle, its caption in 5x7 under it, and towards the craft a dot chain (beside it in view) or a
 * dot chevron on the circle (out of view). Dots only, no strokes; zinc, red for a threat.
 *
 * <p>Each HUD writes the caption by its own rule for a marker's name and number ({@link Look}), and
 * leaves out its own mark for the craft, which the inset carries, and the marks under the inset,
 * which the caption counts ({@link #covers}).
 *
 * <p>On the stage, y down; the inset itself is in the UI camera's units, y up, so its y is turned
 * over the stage's height.
 */
public final class Optic {
    /** The ring: this far outside the picture's edge, a dot about every {@link #STEP} units. */
    public static final float RING_GAP = 3f, STEP = 4f, DOT = 1.6f;
    /** The caption: 5x7 at pitch 2, at most 12 cells a line (style-vfd 8). */
    public static final float PITCH = 2f;
    public static final int CELLS = 12;
    /** From one caption line's top to the next's. */
    public static final float LINE = 7f * PITCH * 1.35f;
    /**
     * How far the ring, marks and caption reach past the picture's edge: either side (a 12-cell
     * caption, the lock's ticks), above (the ticks) and below (the ticks and a two-line caption). The
     * room a HUD gives the game for each inset ({@code HudVariant#insetRoom}).
     */
    public static final float SIDE = 16f, ABOVE = 16f, BELOW = 56f;
    /** The lock's brackets stand this far past the ring, its ticks 3 to 5 dots further; the nav's diamond points this far. */
    static final float LOCK_OUT = 2f, TICKS = 5f * 2f, NAV_OUT = 9f;
    /** The chevron's tip past the ring and any mark there; the caption's gap under them. */
    static final float CHEVRON_OUT = 11f, CAPTION_GAP = 5f;

    private Optic() {
    }

    /** What an inset shows, by the HUD's own rules: its caption, colour and mark. */
    public static final class Look {
        /** The caption's first line, at {@link #level}: the craft's name; "" for none (the player's ship). */
        public String name = "";
        /** Its second line, at L1: a number where the HUD gives the craft's marker one, or "". */
        public String sub = "";
        /** The ring's and the caption's colour: zinc, red for a threat. */
        public Phosphor color = Phosphor.ZINC;
        public float level = Drive.NORM;
        /** The lock's full corner brackets with four ticks; else the nav target's diamond brackets. */
        public boolean lock, nav;
        /** The mark's colour where the HUD draws it apart from the ring's (a zinc lock on a hostile), else null. */
        public Phosphor mark;
        /** The caption's colour where the HUD writes names apart from the ring's (zinc names on red marks), else null. */
        public Phosphor text;
        /** The HUD's marks under the inset, left out there and counted on the caption ("+3"). */
        public int others;

        public Look clear() {
            name = "";
            sub = "";
            color = Phosphor.ZINC;
            level = Drive.NORM;
            lock = nav = false;
            mark = text = null;
            others = 0;
            return this;
        }
    }

    /** Where one inset's parts go on the stage, y down. */
    public static final class Layout {
        /** The ring's middle and radius. */
        public float cx, cy, ring;
        /** A dot chain from the ring to the craft beside it, from ({@link #x0}, {@link #y0}) to ({@link #x1}, {@link #y1}). */
        public boolean chain;
        public float x0, y0, x1, y1;
        /** A chevron on the ring pointing at the craft out of view, along ({@link #dx}, {@link #dy}), its tip {@link #tip} from the middle. */
        public boolean chevron;
        public float dx, dy, tip;
        /** The caption's first line's top. */
        public float captionTop;
    }

    /** How far {@code look}'s mark stands past the ring: the lock's ticks, the nav's diamond points; none without one. */
    public static float markOut(Look look) {
        return look.lock ? LOCK_OUT + TICKS + DOT / 2f : look.nav ? NAV_OUT + DOT / 2f : 0f;
    }

    /** Where inset {@code in}'s parts go on a stage {@code height} tall, with {@code look}'s mark round it, into {@code out}. */
    public static Layout layout(Insets.Inset in, float height, Look look, Layout out) {
        float mark = markOut(look);
        out.cx = in.x;
        out.cy = height - in.y;
        out.ring = in.radius + RING_GAP;
        out.chain = out.chevron = false;
        float tx = in.craftX - in.x, ty = (height - in.craftY) - out.cy;
        float len = (float) Math.sqrt(tx * tx + ty * ty);
        float under = mark;
        if (!in.centered && len > 1f) {
            out.dx = tx / len;
            out.dy = ty / len;
            if (in.onScreen) {
                // From past the ring and its mark to just short of the craft as the main view draws it
                float near = Math.max(4f, in.reach + 3f), from = out.ring + mark + 4f;
                if (len > from + near) {
                    out.chain = true;
                    out.x0 = out.cx + out.dx * from;
                    out.y0 = out.cy + out.dy * from;
                    out.x1 = in.craftX - out.dx * near;
                    out.y1 = (height - in.craftY) - out.dy * near;
                }
            } else {
                // Past the mark, so the two never fall on each other; the caption keeps under it
                out.chevron = true;
                out.tip = out.ring + mark + CHEVRON_OUT;
                if (out.dy > 0.5f) under = mark + CHEVRON_OUT;
            }
        }
        out.captionTop = out.cy + out.ring + under + CAPTION_GAP;
        return out;
    }

    /** The caption's second line: the HUD's number, and the marks it hides ("12.4 KM +3"). */
    public static String sub(Look look) {
        if (look.others <= 0) return look.sub;
        String more = "+" + Math.min(99, look.others);
        return look.sub.isEmpty() ? more : look.sub + " " + more;
    }

    /** Inset {@code in} on a stage {@code height} tall, as {@code look} has it; {@code scratch} for its layout. */
    public static void paint(Filament f, Insets.Inset in, float height, Look look, Layout scratch) {
        Layout l = layout(in, height, look, scratch);
        Kit.ringDots(f, l.cx, l.cy, l.ring, STEP, DOT, look.color, look.level);
        Phosphor mark = look.mark != null ? look.mark : look.color;
        if (look.lock) {
            // Full corner brackets at L3 with four ticks (style-vfd 8), round the ring
            Kit.brackets(f, l.cx, l.cy, l.ring + LOCK_OUT, Kit.Bracket.SQUARE, 2f, 5, mark, Drive.FULL, true);
        } else if (look.nav) {
            // The diamond's points stand just outside the ring
            Kit.brackets(f, l.cx, l.cy, (l.ring + NAV_OUT) / 1.25f, Kit.Bracket.DIAMOND, 2f, 5, mark, look.level, false);
        }
        if (l.chain) {
            f.dotLine(l.x0, l.y0, l.x1, l.y1, f.o().proj().step(5f).size(1.3f).color(look.color).level(Drive.DIM));
        }
        if (l.chevron) chevron(f, l, look);
        String name = look.name.length() > CELLS ? look.name.substring(0, CELLS) : look.name;
        String sub = sub(look);
        if (sub.length() > CELLS) sub = sub.substring(0, CELLS);
        Phosphor text = look.text != null ? look.text : look.color;
        float y = l.captionTop;
        if (!name.isEmpty()) {
            Kit.label(f, l.cx - width(name) / 2f, y, 0, name, PITCH, text, look.level);
            y += LINE;
        }
        if (!sub.isEmpty()) Kit.label(f, l.cx - width(sub) / 2f, y, 0, sub, PITCH, text, Drive.DIM);
    }

    /** A dot chevron on the ring pointing at the craft out of view: seven dots, a right angle, on the 2-unit pitch. */
    private static void chevron(Filament f, Layout l, Look look) {
        float tipX = l.cx + l.dx * l.tip, tipY = l.cy + l.dy * l.tip;
        Filament.Opt o = f.o().proj().size(DOT).color(look.color).level(look.level);
        f.dot(tipX, tipY, o);
        for (int i = 1; i <= 3; i++) {
            float back = 2f * i, bx = tipX - l.dx * back, by = tipY - l.dy * back;
            f.dot(bx - l.dy * back, by + l.dx * back, o);
            f.dot(bx + l.dy * back, by - l.dx * back, o);
        }
    }

    /** How wide a caption line sets: 6 dots a cell at pitch 2, less the last cell's gap. */
    public static float width(String text) {
        return text.length() * 6f * PITCH - PITCH;
    }

    /**
     * True when a box on the stage, y down, reaches into inset {@code in}: its picture and ring, or
     * the room under it where its caption goes. A HUD leaves its own mark out there and counts it on the
     * caption.
     */
    public static boolean covers(Insets.Inset in, float height, float x, float y, float w, float h) {
        float cx = in.x, cy = height - in.y, r = in.radius + RING_GAP + DOT;
        float nx = Math.max(x, Math.min(cx, x + w)) - cx, ny = Math.max(y, Math.min(cy, y + h)) - cy;
        if (nx * nx + ny * ny < r * r) return true;
        float half = CELLS * 6f * PITCH / 2f, top = cy + in.radius, bottom = cy + in.radius + BELOW;
        return x < cx + half && x + w > cx - half && y < bottom && y + h > top;
    }
}
