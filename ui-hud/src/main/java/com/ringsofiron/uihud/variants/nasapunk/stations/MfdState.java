package com.ringsofiron.uihud.variants.nasapunk.stations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the two MFDs show (functional-stations.md section 9.1). Sitting down sets each from the seat
 * and the condition; a page the player picks, a fold or a pin holds until they stand up, and a
 * change of condition leaves that MFD alone. A caution or warning newly raised turns the right MFD
 * to its home page, open, unless it is pinned, else the left; 10 s after that alert clears the MFD
 * goes back to what it showed. With several alerts the worst, then the newest, has the MFD.
 * Under warp both fold unless a caution or warning is lit.
 */
final class MfdState {
    /** The seats with MFDs. */
    enum Seat { NONE, HELM, WEAPONS }

    /** What the defaults go by: the condition, or docked below Red. */
    enum Mode { GREEN, YELLOW, RED, DOCKED }

    /** After its alert clears, an MFD waits this long before going back, s. */
    static final float RELEASE = 10f;

    static final class Side {
        Page page = Page.NAV;
        boolean folded = true, pinned;
        /** The player picked a page, folded it or pinned it: the defaults leave it alone. */
        boolean chosen;
        /** The tile whose home page it shows, or null. */
        String auto;
        /** What it showed before that tile took it. */
        Page before = Page.NAV;
        boolean foldedBefore = true;
        /** Seconds left before it goes back, once its tile has cleared; NaN while the tile holds. */
        float release = Float.NaN;
        /** Opened by the player under warp, which otherwise folds it. */
        boolean warpOpen;

        void reset() {
            pinned = chosen = warpOpen = false;
            auto = null;
            release = Float.NaN;
        }
    }

    final Side left = new Side(), right = new Side();
    Seat seat = Seat.NONE;
    Mode mode = Mode.GREEN;
    private boolean emitter, warp, lit;
    private final Set<String> seen = new HashSet<>();
    private final Set<String> now = new HashSet<>();

    /**
     * One frame: where the captain sits, the condition, whether the hull has a shield emitter, warp,
     * and the annunciator's tiles, worst then newest first.
     */
    void update(Seat seat, Mode mode, boolean emitter, boolean warp, List<Annunciation.Tile> tiles, float dt) {
        now.clear();
        for (int i = 0; i < tiles.size(); i++) now.add(tiles.get(i).key);
        boolean sat = seat != this.seat;
        boolean changed = mode != this.mode || emitter != this.emitter;
        this.seat = seat;
        this.mode = mode;
        this.emitter = emitter;
        this.warp = warp;
        lit = !tiles.isEmpty();
        if (!warp) left.warpOpen = right.warpOpen = false;
        if (sat) {
            // a new seat starts from its defaults; what is lit already doesn't page
            left.reset();
            right.reset();
            defaults(left);
            defaults(right);
            seen.clear();
            seen.addAll(now);
            return;
        }
        if (changed) {
            if (!left.chosen && left.auto == null) defaults(left);
            if (!right.chosen && right.auto == null) defaults(right);
        }
        if (seat != Seat.NONE && !tiles.isEmpty()) {
            Annunciation.Tile best = tiles.get(0);
            if (!seen.contains(best.key)) page(best);
        }
        seen.clear();
        seen.addAll(now);
        release(left, dt);
        release(right, dt);
    }

    /** The tile's home page on the right MFD, or the left if the right is pinned. */
    private void page(Annunciation.Tile t) {
        // already open on either, it has its page
        if (shows(left, t.home) && !left.pinned) {
            take(left, t);
            return;
        }
        if (shows(right, t.home) && !right.pinned) {
            take(right, t);
            return;
        }
        Side s = !right.pinned ? right : !left.pinned ? left : null;
        if (s != null) take(s, t);
    }

    private void take(Side s, Annunciation.Tile t) {
        if (s.auto == null) {
            s.before = s.page;
            s.foldedBefore = s.folded;
        }
        s.auto = t.key;
        s.release = Float.NaN;
        s.page = t.home;
        s.folded = false;
    }

    private boolean shows(Side s, Page p) {
        return s.page == p && !folded(s);
    }

    private void release(Side s, float dt) {
        if (s.auto == null) return;
        if (now.contains(s.auto)) {
            s.release = Float.NaN;
            return;
        }
        if (Float.isNaN(s.release)) s.release = RELEASE;
        s.release -= dt;
        if (s.release > 0f) return;
        s.auto = null;
        s.release = Float.NaN;
        if (s.chosen) {
            s.page = s.before;
            s.folded = s.foldedBefore;
        } else {
            defaults(s);
        }
    }

    /** The seat's and condition's page for {@code s}, and whether it starts folded (section 9.1's table). */
    private void defaults(Side s) {
        boolean l = s == left;
        Page p;
        boolean f;
        if (seat == Seat.WEAPONS) {
            switch (mode) {
                case YELLOW: p = l ? Page.SENSORS : Page.WEAPONS; f = !l; break;
                case RED: p = l ? Page.SENSORS : emitter ? Page.DEFENSE : Page.DAMAGE; f = false; break;
                default: p = l ? Page.SENSORS : Page.WEAPONS; f = true; break;
            }
        } else {
            switch (mode) {
                case YELLOW: p = l ? Page.SENSORS : Page.NAV; f = !l; break;
                case RED: p = l ? Page.SENSORS : Page.DAMAGE; f = false; break;
                // CARGO has no data to show, so the right MFD takes COMMS, the berth's other page
                case DOCKED: p = l ? Page.DOCK : Page.COMMS; f = !l; break;
                default: p = l ? Page.NAV : Page.SENSORS; f = true; break;
            }
        }
        s.page = p;
        s.folded = f;
    }

    Side side(boolean leftSide) {
        return leftSide ? left : right;
    }

    /** Whether {@code s} shows only its rail: folded, or under warp with nothing lit. */
    boolean folded(Side s) {
        return s.folded || warp && !lit && !s.warpOpen;
    }

    /** The player picks a page: it opens there, and holds until they stand up. */
    void choose(boolean leftSide, Page p) {
        Side s = side(leftSide);
        s.page = p;
        s.folded = false;
        s.chosen = true;
        s.auto = null;
        s.release = Float.NaN;
        if (warp) s.warpOpen = true;
    }

    /** A station's key: its first page, or its next page while it shows already. */
    void station(boolean leftSide, Station st) {
        Side s = side(leftSide);
        choose(leftSide, s.page.station == st && !folded(s) ? s.page.next() : st.pages()[0]);
    }

    /** FOLD: folds an open MFD, or opens a folded one on its page. */
    void fold(boolean leftSide) {
        Side s = side(leftSide);
        boolean open = folded(s);
        s.folded = !open;
        s.chosen = true;
        s.auto = null;
        s.release = Float.NaN;
        s.warpOpen = open && warp;
    }

    /** PIN: an alert can't take a pinned MFD. */
    void pin(boolean leftSide) {
        Side s = side(leftSide);
        s.pinned = !s.pinned;
        s.chosen = true;
    }
}
