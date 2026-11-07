package com.ringsofiron.uihud.variants.cassette.stations;

/**
 * Where each unit of the Ship Stations Cassette HUD goes (spec 6.2), from what is up: pure, so the layout
 * test can try every presence at every window size without a GL context.
 *
 * <p>The zones anchor to the screen's edges and centre, so a wider window gives the extra width to the
 * world. The top column hangs from the top edge, centred, each unit at its fixed height whatever else is
 * up ("slots hold still"): the status strip at 16, the annunciator at 44, the warp strip at 84, the ticker
 * at 108. The seated band stands flush with the bottom edge, 1088 wide and centred: the two MFDs either side
 * of the cluster, the station board under them on its sill. The guide card keeps the top left corner, and
 * over a screen across the screen the top right, clear of the screen's title; the suit readout the bottom
 * left; the prompt line the bottom centre, over the band when seated.
 */
final class StationsLayout {
    /** The top column's width and its units' heights (6.2). */
    static final float COLUMN_W = 720f;
    static final float STRIP_Y = 16f, STRIP_H = 24f, ANN_Y = 44f, ANN_H = 36f, WARP_Y = 84f, WARP_H = 20f, TICKER_Y = 108f, TICKER_H = 60f;
    /** The band (Z3): its width and height, an MFD's and the cluster's size, the board's strip. */
    static final float BAND_W = 1088f, BAND_H = 184f, MFD_W = 288f, CLUSTER_W = 480f, UNIT_H = 160f, FOLDED_H = 32f;
    /**
     * The board's strip under the band, and its sill: the sill is drawn 8 units taller than the strip, its
     * back edge under the units' feet, so the units stand on it.
     */
    static final float BOARD_H = 24f, SILL_H = 32f, TILE_W = 146f, TILE_H = 22f;
    /** The gunner sight's pod. */
    static final float SIGHT_W = 290f, SIGHT_H = 96f;
    static final float SUIT_W = 200f, SUIT_H = 80f, PROMPT_W = 720f, PROMPT_H = 26f;
    static final float GUIDE_W = 248f, BERTH_W = 420f, BERTH_H = 40f;
    static final float MARGIN = 16f, GAP = 8f;

    /** A unit's place, in design units, y down from the top left. */
    static final class Rect {
        float x, y, w, h;
        boolean on;

        Rect set(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            on = true;
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
            return on ? String.format(java.util.Locale.ROOT, "(%.0f, %.0f, %.0f x %.0f)", x, y, w, h) : "(off)";
        }
    }

    // Inputs
    float width = 1280f, height = 800f;
    boolean strip, ann, warpStrip, ticker;
    StationsFrame.Seat seat = StationsFrame.Seat.DECK;
    /** The band is hidden under a screen across the screen: the map, or a panel too wide for the side. */
    boolean map;
    boolean leftFolded, rightFolded;
    boolean suit, prompt, berth;
    /** The guide card's height, 0 with no guide running. */
    float guideH;
    /**
     * The left edge of a side panel hanging at the right (10.1), or a negative for none: the top column,
     * the berth card and the prompt line recentre in the world left of it, and the guide card drops under
     * the ticker (6.3, rule 2).
     */
    float panelLeft = -1f;

    // Outputs
    final Rect column = new Rect(), mfdL = new Rect(), mfdR = new Rect(), cluster = new Rect(), board = new Rect();
    final Rect sight = new Rect(), suitR = new Rect(), promptR = new Rect(), guideR = new Rect(), berthR = new Rect();
    /** The band's left edge: the MFDs, the cluster and the board tiles are placed from it. */
    float bandX;

    /** The width the top column, the berth card and the prompt line centre in: the world left of a side panel, or the screen. */
    float centreWidth() {
        return panelLeft >= 0f ? Math.min(width, panelLeft) : width;
    }

    /**
     * Where a side panel stops, as solved: 8 above the seated band or the gunner's sight, else 0 for
     * the screen's foot less its margin, as on deck (6.2).
     */
    float panelBottom() {
        if (cluster.on) return height - BAND_H - GAP;
        if (sight.on) return sight.y - GAP;
        return 0f;
    }

    void solve() {
        float span = centreWidth();
        float cx = Math.max(MARGIN, Math.round((span - COLUMN_W) / 2f));
        float bottom = ticker ? TICKER_Y + TICKER_H : warpStrip ? WARP_Y + WARP_H : ann ? ANN_Y + ANN_H : strip ? STRIP_Y + STRIP_H : 0f;
        if (bottom > 0f) column.set(cx, 0f, COLUMN_W, bottom);
        else column.off();

        bandX = Math.round((width - BAND_W) / 2f);
        float y0 = height - BAND_H;
        boolean band = !map && (seat == StationsFrame.Seat.HELM || seat == StationsFrame.Seat.WEAPONS);
        if (band) {
            place(mfdL, bandX, y0, leftFolded);
            cluster.set(bandX + MFD_W + 16f, y0, CLUSTER_W, UNIT_H);
            place(mfdR, bandX + MFD_W + 16f + CLUSTER_W + 16f, y0, rightFolded);
            board.set(bandX - 8f, height - BOARD_H, BAND_W + 16f, BOARD_H);
        } else {
            mfdL.off();
            mfdR.off();
            cluster.off();
            board.off();
        }

        boolean gun = !map && seat == StationsFrame.Seat.GUN;
        if (gun) {
            float gx = Math.round((width - SIGHT_W) / 2f);
            // The gun's own tile on a short sill, the sight standing on it
            board.set(Math.round(width / 2f - TILE_W / 2f - 8f), height - BOARD_H, TILE_W + 16f, BOARD_H);
            sight.set(gx, height - BOARD_H - SIGHT_H, SIGHT_W, SIGHT_H);
        } else sight.off();

        boolean deck = seat == StationsFrame.Seat.DECK;
        if (suit && deck && !map) suitR.set(MARGIN, height - MARGIN - SUIT_H, SUIT_W, SUIT_H);
        else suitR.off();

        if (prompt && !map) {
            float px = Math.max(MARGIN, Math.round((span - PROMPT_W) / 2f)), pw = PROMPT_W;
            float py = band ? y0 - GAP - PROMPT_H : gun ? sight.y - GAP - PROMPT_H : height - 40f;
            // On deck with a side panel up the world is too narrow for it beside the suit readout: it narrows between them
            if (panelLeft >= 0f && suitR.on && py + PROMPT_H > suitR.y && px < suitR.right() + GAP) {
                float left = suitR.right() + GAP, right = span - GAP;
                pw = Math.min(PROMPT_W, right - left);
                px = Math.round(left + (right - left - pw) / 2f);
            }
            promptR.set(px, py, pw, PROMPT_H);
        } else promptR.off();

        // With a side panel up the column takes the top left: the guide card drops under the ticker. Over a
        // screen across the screen, whose title is at its top left, it goes to the top right beside the column
        boolean across = map && panelLeft < 0f;
        float guideX = across ? width - MARGIN - GUIDE_W : MARGIN;
        float guideY = panelLeft >= 0f ? TICKER_Y + TICKER_H + GAP : MARGIN;
        if (guideH > 0f) guideR.set(guideX, guideY, GUIDE_W, guideH);
        else guideR.off();

        if (berth) berthR.set(Math.max(MARGIN, Math.round((span - BERTH_W) / 2f)), TICKER_Y + TICKER_H + GAP, BERTH_W, BERTH_H);
        else berthR.off();
        if (berth && guideR.on && guideR.overlaps(berthR)) guideR.y = berthR.bottom() + GAP;
    }

    /** An MFD raised, or folded down to its soft-key rail, standing on the band's foot either way. */
    private static void place(Rect r, float x, float y0, boolean folded) {
        if (folded) r.set(x, y0 + UNIT_H - FOLDED_H, MFD_W, FOLDED_H);
        else r.set(x, y0, MFD_W, UNIT_H);
    }

    /** Where the board's tile {@code i} (0 to 5) is: its left edge. */
    float tileX(int i) {
        return bandX + 2f + i * 150f;
    }
}
