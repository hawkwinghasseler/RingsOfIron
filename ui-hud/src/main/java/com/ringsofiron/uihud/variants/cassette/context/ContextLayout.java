package com.ringsofiron.uihud.variants.cassette.context;

/**
 * Where every unit goes this frame (spec 5.1 and 5.2): the screen's fixed zones, each unit in its own,
 * stacked from the zone's outer edge inward, so that a unit coming or going in one zone never moves a
 * unit in another. The side columns stay at the screen's edges and the centre grows with a wider window;
 * a taller one gives the middle zones the room. Units are given their heights (0 when not up) and
 * the layout returns their rectangles in UI units, y down from the top left.
 */
final class ContextLayout {
    static final class Rect {
        float x, y, w, h;
        boolean on;

        Rect set(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            on = w > 0f && h > 0f;
            return this;
        }

        Rect off() {
            on = false;
            w = h = 0f;
            return this;
        }

        float bottom() {
            return y + h;
        }

        float right() {
            return x + w;
        }

        boolean overlaps(Rect o) {
            return on && o.on && x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        @Override
        public String toString() {
            return on ? String.format(java.util.Locale.ROOT, "[%.0f,%.0f %.0fx%.0f]", x, y, w, h) : "[off]";
        }
    }

    /** The screen's edge margin, the gap between stacked units, the header rail's height. */
    static final float MARGIN = 12f, GAP = 6f, RAIL_H = 26f;
    /** The side columns' width, and where the top and middle zones start (5.1). */
    static final float COL_W = 308f, TOP_Y = 38f, MID_Y = 194f;
    /** The top side zones' height, and the bottom side zones'. */
    static final float TOP_H = 150f, BOTTOM_H = 230f;
    /** The top-centre zone (the interrupts) and the bottom-centre zone (the seat's instrument). */
    static final float TC_W = 500f, TC_H = 174f, BC_W = 620f, BC_H = 190f;
    /** The centre that no fixed unit may enter, at 1280 x 800 (5.1): 360..920 by 220..590. */
    static final float CLEAR_TOP = 220f, CLEAR_FOOT_GAP = 210f, CLEAR_W = 560f;
    /** A unit cut short by its zone's end keeps at least this much, or goes: a sliver says nothing. */
    static final float MIN_H = 36f;

    float width = 1280f, height = 800f;
    ContextFrame.Seat seat = ContextFrame.Seat.HELM;

    // Heights of what is up this frame; 0 for a unit that is not
    float cautionH, threatH, placardH, intercomH;
    float clipboardH, navH, navCompactH, threatListH;
    float lockH, armamentH, stationH, boardingH;
    float conditionH, shieldH, suitH;
    float instrumentW, instrumentH, scopeW, scopeH;
    float cueW, cueH;
    /** The master cue stands alone at the bottom centre (deck, docked, latched), or on a bracket above the rack. */
    boolean cueAlone, cueBracket;
    float promptW, promptH, ordersW, ordersH;

    final Rect rail = new Rect(), caution = new Rect(), threat = new Rect(), placard = new Rect(), intercom = new Rect();
    final Rect clipboard = new Rect(), nav = new Rect(), threatList = new Rect();
    final Rect lock = new Rect(), armament = new Rect(), station = new Rect(), boarding = new Rect();
    final Rect condition = new Rect(), shield = new Rect(), suit = new Rect();
    final Rect instrument = new Rect(), scope = new Rect(), cue = new Rect(), prompt = new Rect(), orders = new Rect();
    /** H2 had to take its compact face to fit its zone. */
    boolean navCompacted;

    void solve() {
        float w = width, h = height, cx = Math.round(w / 2f);
        rail.set(0f, 0f, w, RAIL_H);
        float midH = h - MARGIN - BOTTOM_H - GAP - MID_Y;
        float left = MARGIN, right = w - MARGIN - COL_W;

        // The top: the caution board, the interrupts, the intercom, each hanging from the rail
        put(caution, left, TOP_Y, COL_W, cautionH, TOP_H);
        put(threat, cx - TC_W / 2f, TOP_Y, TC_W, threatH, TC_H);
        float py = threat.on ? threat.bottom() + GAP : TOP_Y;
        put(placard, cx - TC_W / 2f, py, TC_W, placardH, TOP_Y + TC_H - py);
        put(intercom, right, TOP_Y, COL_W, intercomH, TOP_H);

        // The left middle: the objective, navigation, the threat list, from the top down
        float y = MID_Y, end = MID_Y + midH;
        float need = stack(clipboardH) + stack(navH) + stack(threatListH);
        navCompacted = navH > 0f && navCompactH > 0f && need - GAP > midH;
        y = putStack(clipboard, left, y, clipboardH, end);
        y = putStack(nav, left, y, navCompacted ? navCompactH : navH, end);
        putStack(threatList, left, y, threatListH, end);

        // The right middle: the lock card, the armament, the station or boarding panel
        y = MID_Y;
        y = putStack(lock, right, y, lockH, end);
        y = putStack(armament, right, y, armamentH, end);
        y = putStack(station, right, y, stationH, end);
        putStack(boarding, right, y, boardingH, end);

        // The left foot, from the bottom up: on deck the suit unit, then the condition unit above it;
        // at fire control the condition strip, then shield control above it
        float foot = h - MARGIN, top = h - MARGIN - BOTTOM_H;
        if (seat == ContextFrame.Seat.DECK) {
            foot = putUp(suit, left, foot, suitH, top);
            foot = putUp(condition, left, foot, conditionH, top);
            shield.off();
        } else {
            suit.off();
            foot = putUp(condition, left, foot, conditionH, top);
            foot = putUp(shield, left, foot, shieldH, top);
        }

        // The right foot: the scope, right-aligned on the coaming
        if (scopeW > 0f && scopeH > 0f) scope.set(w - MARGIN - scopeW, h - MARGIN - scopeH, scopeW, scopeH);
        else scope.off();

        // The bottom centre: the seat's instrument, its cue, the prompt over them and the orders panel
        float bcTop = h - MARGIN - BC_H;
        if (instrumentW > 0f && instrumentH > 0f) instrument.set(cx - instrumentW / 2f, h - MARGIN - instrumentH, instrumentW, instrumentH);
        else instrument.off();
        float over = instrument.on ? instrument.y : h - MARGIN;
        if (cueW > 0f && cueH > 0f && cueAlone) {
            cue.set(cx - cueW / 2f, h - MARGIN - cueH, cueW, cueH);
            over = cue.y;
        } else if (cueW > 0f && cueH > 0f && cueBracket && instrument.on) {
            cue.set(instrument.right() - 20f - cueW, instrument.y - GAP - cueH, cueW, cueH);
            over = Math.min(over, cue.y);
        } else cue.off();
        if (ordersW > 0f && ordersH > 0f) {
            // Its foot on the instrument's top edge, over the world (5.1 lets it into the centre)
            float oy = (instrument.on || cue.on ? over : h - MARGIN) - ordersH;
            orders.set(cx - ordersW / 2f, Math.max(RAIL_H + GAP, oy), ordersW, ordersH);
            prompt.off();
        } else {
            orders.off();
            if (promptW > 0f && promptH > 0f) prompt.set(cx - promptW / 2f, Math.min(over, h - MARGIN) - GAP - promptH, promptW, promptH);
            else prompt.off();
        }
        if (!instrument.on && !cue.on && prompt.on && bcTop > prompt.y) prompt.y = Math.max(prompt.y, bcTop);
    }

    private static float stack(float h) {
        return h > 0f ? h + GAP : 0f;
    }

    private static void put(Rect r, float x, float y, float w, float h, float max) {
        if (h <= 0f) r.off();
        else r.set(x, y, w, Math.min(h, max));
    }

    /** Stacks {@code r} down from {@code y} within the zone's {@code end}; returns where the next starts. */
    private static float putStack(Rect r, float x, float y, float h, float end) {
        if (h <= 0f || end - y < Math.min(h, MIN_H)) {
            r.off();
            return y;
        }
        r.set(x, y, COL_W, Math.min(h, end - y));
        return r.bottom() + GAP;
    }

    /** Stacks {@code r} up from {@code foot} within the zone's {@code top}; returns where the next ends. */
    private static float putUp(Rect r, float x, float foot, float h, float top) {
        if (h <= 0f || foot - top < Math.min(h, MIN_H)) {
            r.off();
            return foot;
        }
        float hh = Math.min(h, foot - top);
        r.set(x, foot - hh, COL_W, hh);
        return r.y - GAP;
    }

    /** The centre that stays clear (5.1): it grows with the window, its foot over the bottom-centre zone. */
    Rect clear(Rect out) {
        float cx = Math.round(width / 2f), cw = CLEAR_W + Math.max(0f, width - 1280f);
        return out.set(cx - cw / 2f, CLEAR_TOP, cw, height - CLEAR_FOOT_GAP - CLEAR_TOP);
    }
}
