package com.ringsofiron.uihud.variants.cassette.task;

/**
 * Where each unit of the Task-driven Cassette HUD goes, from what is up and how big it is: pure, so the
 * layout test can try every post at every window size without a GL context.
 *
 * <p>The zones of spec section 5 anchor to the screen's edges and corners, so on a wider or taller window
 * the extra space goes to the clear zone: the master unit and the schematic to the left edge, the visor,
 * the flight pod and the crisis placard centred, the rack, the target plate, the scope and the weapons to
 * the right edge, the key sill along the bottom. Nothing is stretched. Where two units share a column the
 * lower one keeps its place and the upper one gives way: the target plate rises clear of the scope and the
 * weapons, the rack drops news and then slips to stop short of the plate, and the weapons panel shows no
 * more rows than keep it out of the clear zone. The console panel alone goes where its console is, which
 * on deck is about the middle of the screen: off the ship by it where there is room, else beside it, and
 * clear of every other unit ({@link #placeConsole}).
 */
final class TaskLayout {
    /** The key sill along the bottom edge, and the gap between units. */
    static final float SILL = 38f, GAP = 8f;
    /** The clear zone: an ellipse 600 x 400 units about the centre, where only the crisis placard may go. */
    static final float CLEAR_RX = 300f, CLEAR_RY = 200f;
    /** Where the target plate hangs when nothing below pushes it up. */
    static final float TGT_Y = 332f;
    /** Units from the console panel's near edge to its console: beside it, never over it. */
    static final float CON_LEAD = 56f;
    /** Units the console panel stands off its ship's deck, which the hull's art overhangs a little. */
    static final float HULL_GAP = 16f;

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

        boolean overlapsX(Rect o) {
            return on && o.on && x < o.right() && o.x < right();
        }

        boolean overlaps(Rect o) {
            return overlapsX(o) && y < o.bottom() && o.y < bottom();
        }

        @Override
        public String toString() {
            return on ? String.format(java.util.Locale.ROOT, "(%.0f,%.0f %.0fx%.0f)", x, y, w, h) : "off";
        }
    }

    // --- What is up, and the sizes that vary --------------------------------------------------
    float width = 1280f, height = 800f;
    boolean annOn = true;
    boolean visor, per, sch, flt, scope, tgt, wpn, wpnCompact, crs, prompt, sill = true;
    /** The decoy and shields pod at gunnery, standing on the helm line's zone. */
    boolean defence;
    float defenceH = DefencePod.H_SHIELDS;
    /** The helm pod: at gunnery between the schematic's zone and the weapons, else centred where FLT goes. */
    boolean hlm, hlmAtGunnery;
    boolean rack;
    float fltH = FlightPod.H, tgtH = TargetPlate.H_FULL, crsH = CrisisPlacard.H_FULL, promptW = 200f;
    /** The weapons panel without rows, and how many mounts it would show. */
    float wpnBaseH = 75f;
    int wpnMounts;

    // --- Where it all goes -------------------------------------------------------------------
    final Rect master = new Rect(), visorR = new Rect(), rackR = new Rect(), perR = new Rect(), schR = new Rect();
    final Rect fltR = new Rect(), hlmR = new Rect(), scopeR = new Rect(), tgtR = new Rect(), wpnR = new Rect();
    final Rect sillR = new Rect(), crsR = new Rect(), promptR = new Rect(), defR = new Rect();
    /** The weapons rows that fit, and the most the rack may stand. */
    int wpnRows;
    float rackLimit;

    final Rect[] all = {master, visorR, rackR, perR, schR, fltR, hlmR, scopeR, tgtR, wpnR, sillR, crsR, promptR, defR};
    /** The console panel (spec CON): not among {@link #all}, since it stands where its console is. */
    final Rect conR = new Rect();

    void solve() {
        float w = width, h = height, base = h - SILL;
        master.set(0f, 0f, MasterUnit.W, annOn ? MasterUnit.H : MasterUnit.H_CLOCK);
        if (visor) visorR.set((w - Visor.W) / 2f, 0f, Visor.W, Visor.H);
        else visorR.off();
        if (per) perR.set(0f, master.bottom(), SuitUnit.W, SuitUnit.H);
        else perR.off();
        if (crs) crsR.set((w - CrisisPlacard.W) / 2f, Visor.H + GAP, CrisisPlacard.W, crsH);
        else crsR.off();
        if (sill) sillR.set(0f, base, w, SILL);
        else sillR.off();

        // Bottom left and bottom centre
        if (sch) schR.set(0f, base - Schematic.H, Schematic.W, Schematic.H);
        else schR.off();
        if (flt) fltR.set((w - FlightPod.W) / 2f, base - fltH, FlightPod.W, fltH);
        else fltR.off();

        // Bottom right: the scope in the corner, the weapons beside it
        float scopeX = w - Scope.W;
        if (scope) scopeR.set(scopeX, base - Scope.H, Scope.W, Scope.H);
        else scopeR.off();
        if (wpn) {
            float ww = wpnCompact ? GunPanel.W_COMPACT : GunPanel.W, wx = scopeX - 6f - ww;
            if (wpnCompact) {
                wpnRows = 0;
                wpnR.set(wx, base - GunPanel.H_COMPACT, ww, GunPanel.H_COMPACT);
            } else {
                float top = clearTop(wx, wx + ww, base);
                int rows = Math.max(0, Math.min(wpnMounts, (int) Math.floor((base - top - wpnBaseH) / 23f)));
                wpnRows = Math.min(rows, 8);
                float wh = wpnBaseH + wpnRows * 23f;
                wpnR.set(wx, base - wh, ww, wh);
            }
        } else {
            wpnRows = 0;
            wpnR.off();
        }
        if (hlm) {
            if (hlmAtGunnery) {
                float hx = Schematic.W + 12f, right = (wpn ? wpnR.x : scopeX - 6f - GunPanel.W) - GAP;
                hlmR.set(hx, base - HelmPod.H, Math.min(FlightPod.W, right - hx), HelmPod.H);
            } else {
                hlmR.set((w - FlightPod.W) / 2f, base - HelmPod.H, FlightPod.W, HelmPod.H);
            }
        } else hlmR.off();
        if (defence) defR.set(Schematic.W + 12f, base - HelmPod.H - GAP - defenceH, DefencePod.W, defenceH);
        else defR.off();
        if (prompt) promptR.set((w - promptW) / 2f, base - HelmPod.H - GAP - PromptStrip.H, promptW, PromptStrip.H);
        else promptR.off();

        // The right column, bottom up: the plate clears what is under it, the rack stops short of the plate
        float floor = base;
        if (scope) floor = Math.min(floor, scopeR.y - GAP);
        if (wpnR.on && wpnR.right() > w - TargetPlate.W) floor = Math.min(floor, wpnR.y - GAP);
        if (tgt) {
            tgtR.set(w - TargetPlate.W, Math.min(TGT_Y, floor - tgtH), TargetPlate.W, tgtH);
            rackLimit = tgtR.y - GAP;
        } else {
            tgtR.off();
            rackLimit = floor;
        }
        rackR.off();
    }

    /** Puts the rack at the top right, as tall as it measured (never past {@link #rackLimit}). */
    void placeRack(float rackH) {
        if (rack && rackH > 0f) rackR.set(width - Rack.W, 0f, Rack.W, Math.min(rackH, rackLimit));
        else rackR.off();
    }

    /**
     * Puts the console panel, {@code w} x {@code h}, for its console at design ({@code cx}, {@code cy}) on
     * a ship whose deck spans {@code hull} (off where the game can't say). Off the ship first, so the rooms
     * round the console stay in sight, its leader run back across the deck: under the deck, over it, past
     * its right end or its left. Where the ship leaves no room for it, beside the console: to its right,
     * or to its left where the right has none. Each place is as near centred on the console as keeps it
     * clear of every unit up, on the screen and above the sill. Call after {@link #placeRack}. Where none
     * is clear, it stands to the right of the console at its height regardless; off while no console is
     * open ({@code on} false).
     */
    void placeConsole(boolean on, float cx, float cy, float w, float h, Rect hull) {
        if (!on) {
            conR.off();
            return;
        }
        float top = GAP, bottom = height - SILL - GAP;
        h = Math.min(h, bottom - top);
        if (hull != null && hull.on) {
            for (int side = 0; side < 2; side++) {
                float y = side == 0 ? hull.bottom() + HULL_GAP : hull.y - HULL_GAP - h;
                if (y < top || y + h > bottom) continue;
                float x = clearX(w, h, y, cx);
                if (!Float.isNaN(x)) {
                    conR.set(x, y, w, h);
                    return;
                }
            }
            for (int side = 0; side < 2; side++) {
                float x = side == 0 ? hull.right() + HULL_GAP : hull.x - HULL_GAP - w;
                if (beside(x, w, h, cy, top, bottom)) return;
            }
        }
        for (int side = 0; side < 2; side++) {
            if (beside(side == 0 ? cx + CON_LEAD : cx - CON_LEAD - w, w, h, cy, top, bottom)) return;
        }
        float x = cx + CON_LEAD + w <= width - GAP ? cx + CON_LEAD : Math.max(GAP, cx - CON_LEAD - w);
        conR.set(x, Math.max(top, Math.min(bottom - h, cy - h / 2f)), w, h);
    }

    /** Puts the console panel with its left edge at {@code x}, if that is on the screen and some height there is clear. */
    private boolean beside(float x, float w, float h, float cy, float top, float bottom) {
        if (x < GAP || x + w > width - GAP) return false;
        float y = clearY(x, w, h, cy, top, bottom);
        if (Float.isNaN(y)) return false;
        conR.set(x, y, w, h);
        return true;
    }

    /**
     * The left edge nearest centring a {@code w} x {@code h} panel at {@code y} on {@code cx} that overlaps
     * no unit, on the screen: centred, or just past a unit in its way. NaN when none is clear.
     */
    private float clearX(float w, float h, float y, float cx) {
        float best = Float.NaN, bestOff = Float.MAX_VALUE;
        for (int i = -1; i < all.length * 2; i++) {
            float x;
            if (i < 0) {
                x = cx - w / 2f;
            } else {
                Rect r = all[i / 2];
                if (!r.on || r.y >= y + h || r.bottom() <= y) continue;
                x = i % 2 == 0 ? r.right() + GAP : r.x - GAP - w;
            }
            x = Math.max(GAP, Math.min(width - GAP - w, x));
            if (blocked(x, y, w, h)) continue;
            float off = Math.abs(x + w / 2f - cx);
            if (off < bestOff) {
                bestOff = off;
                best = x;
            }
        }
        return best;
    }

    /**
     * The top nearest centring a {@code w} x {@code h} panel at {@code x} on {@code cy} that overlaps no
     * unit, between {@code top} and {@code bottom}: centred, or just under or just over a unit in its way.
     * NaN when none is clear.
     */
    private float clearY(float x, float w, float h, float cy, float top, float bottom) {
        float best = Float.NaN, bestOff = Float.MAX_VALUE;
        for (int i = -1; i < all.length * 2; i++) {
            float y;
            if (i < 0) {
                y = cy - h / 2f;
            } else {
                Rect r = all[i / 2];
                if (!r.on || r.x >= x + w || r.right() <= x) continue;
                y = i % 2 == 0 ? r.bottom() + GAP : r.y - GAP - h;
            }
            y = Math.max(top, Math.min(bottom - h, y));
            if (blocked(x, y, w, h)) continue;
            float off = Math.abs(y + h / 2f - cy);
            if (off < bestOff) {
                bestOff = off;
                best = y;
            }
        }
        return best;
    }

    private boolean blocked(float x, float y, float w, float h) {
        for (Rect r : all) {
            if (r.on && r.x < x + w && x < r.right() && r.y < y + h && y < r.bottom()) return true;
        }
        return false;
    }

    /** The highest a unit spanning {@code x0} to {@code x1} and standing on {@code base} may reach and stay out of the clear zone. */
    float clearTop(float x0, float x1, float base) {
        float cx = width / 2f, cy = height / 2f;
        float nx = Math.max(x0, Math.min(cx, x1));
        float k = (nx - cx) / CLEAR_RX;
        if (k * k >= 1f) return 0f;
        return Math.min(base, cy + CLEAR_RY * (float) Math.sqrt(1f - k * k));
    }

    /** Whether {@code r} reaches into the clear zone. */
    boolean inClearZone(Rect r) {
        if (!r.on) return false;
        float cx = width / 2f, cy = height / 2f;
        float nx = Math.max(r.x, Math.min(cx, r.right())), ny = Math.max(r.y, Math.min(cy, r.bottom()));
        float dx = (nx - cx) / CLEAR_RX, dy = (ny - cy) / CLEAR_RY;
        return dx * dx + dy * dy < 1f - 1e-4f;
    }
}
