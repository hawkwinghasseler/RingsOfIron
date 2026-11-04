package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;

/**
 * The two multi-function displays at a command seat (functional-stations 9.1): which page each shows,
 * folded or not, pinned or not. When the player sits down the seat and the condition pick the pages;
 * once the player picks or folds one, the choice holds until they stand up. A caution or warning
 * turns the right MFD to its home page, or the left one when the right is pinned, and gives it back
 * 10 s after it clears. Pure state: the band draws it, the keys and soft keys change it.
 */
final class Mfds {

    /** Every page, with its full name as its soft key carries it (9.2). */
    enum Page {
        HELM, DOCK, NAV, PLAN, SENSORS, WEAPONS, DEFENSE, DAMAGE, POWER, ATMOS, COMMS, CREW, CONTRACTS;

        /** The station that owns it, 0 to 5 as the board numbers them. */
        int station() {
            for (int s = 0; s < STATIONS.length; s++) for (Page p : STATIONS[s]) if (p == this) return s;
            return 0;
        }
    }

    /** Each station's pages, by the board's numbering: HELM, NAV, TAC, ENG, COMMS, CMD. */
    static final Page[][] STATIONS = {
            {Page.HELM, Page.DOCK},
            {Page.NAV, Page.PLAN},
            {Page.SENSORS, Page.WEAPONS, Page.DEFENSE},
            {Page.DAMAGE, Page.POWER, Page.ATMOS},
            // CARGO waits for a provider of the hold's contents: a page with nothing true to show is left out
            {Page.COMMS},
            {Page.CREW, Page.CONTRACTS},
    };
    /** The stations' tags, as the board and the ticker print them. */
    static final String[] TAGS = {"HELM", "NAV", "TAC", "ENG", "COMMS", "CMD"};

    /** Seconds an auto-paged MFD keeps the alert's page after it clears. */
    static final float GIVE_BACK = 10f;

    static final class Mfd {
        /** What it shows now. */
        Page page = Page.SENSORS;
        boolean folded = true;
        boolean pinned;
        /** The player's own pick, held until they stand up. */
        boolean chosen;
        Page chosenPage = Page.SENSORS;
        boolean chosenFolded;
        /** The alert it was turned to, and seconds since that alert cleared (NaN while it holds). */
        String auto;
        Page autoPage;
        float autoCleared = Float.NaN;

        /** The station on show, whose pages its soft keys name. */
        int station() {
            return page.station();
        }
    }

    final Mfd left = new Mfd(), right = new Mfd();
    /** The order alerts take them in: the right first. */
    private final Mfd[] byPreference = {right, left};
    private Presence seat = Presence.NONE;
    /** Alerts that have had an MFD while they hold, so one the player paged away from does not take it again. */
    private final ObjectSet<String> paged = new ObjectSet<>();
    private final ObjectSet<String> holding = new ObjectSet<>();

    /**
     * Once a frame: the seat (HELM or WEAPONS; anything else stands the player up), the condition,
     * docked, whether the hull has a shield emitter, time warp above 1x, and the alerts that hold
     * (worst first); {@code delta} real seconds.
     */
    void update(Presence presence, Condition condition, boolean docked, boolean shields, boolean warping,
                Array<Alert> alerts, float delta) {
        boolean command = presence == Presence.HELM || presence == Presence.WEAPONS;
        if (presence != seat) {
            // Standing up, or another seat: the player's picks and the alerts' pages go
            seat = presence;
            reset(left);
            reset(right);
            paged.clear();
        }
        if (!command) return;
        defaults(presence, condition, docked, shields);
        if (!left.chosen) {
            left.page = defLeft;
            left.folded = defLeftFolded;
        } else {
            left.page = left.chosenPage;
            left.folded = left.chosenFolded;
        }
        if (!right.chosen) {
            right.page = defRight;
            right.folded = defRightFolded;
        } else {
            right.page = right.chosenPage;
            right.folded = right.chosenFolded;
        }
        autoPage(alerts, shields, delta);
        apply(left);
        apply(right);
        // In warp nothing on them changes fast enough to read: they fold, unless a caution or warning stands
        if (warping && !alerting(alerts)) {
            left.folded = true;
            right.folded = true;
        }
    }

    private static void apply(Mfd m) {
        if (m.auto != null && m.autoPage != null) {
            m.page = m.autoPage;
            m.folded = false;
        }
    }

    private static void reset(Mfd m) {
        m.chosen = false;
        m.pinned = false;
        m.auto = null;
        m.autoPage = null;
        m.autoCleared = Float.NaN;
    }

    private Page defLeft, defRight;
    private boolean defLeftFolded, defRightFolded;

    /** The seat's and the condition's pages (9.1's table). */
    private void defaults(Presence seat, Condition c, boolean docked, boolean shields) {
        if (seat == Presence.HELM) {
            if (docked) set(Page.DOCK, false, Page.COMMS, true);
            else if (c == Condition.RED) set(Page.SENSORS, false, Page.DAMAGE, false);
            else if (c == Condition.YELLOW) set(Page.SENSORS, false, Page.NAV, true);
            else set(Page.NAV, true, Page.SENSORS, true);
        } else {
            if (c == Condition.RED) set(Page.SENSORS, false, shields ? Page.DEFENSE : Page.DAMAGE, false);
            else if (c == Condition.YELLOW) set(Page.SENSORS, false, Page.WEAPONS, true);
            else set(Page.SENSORS, true, Page.WEAPONS, true);
        }
    }

    private void set(Page l, boolean lf, Page r, boolean rf) {
        defLeft = l;
        defLeftFolded = lf;
        defRight = r;
        defRightFolded = rf;
    }

    /** The most severe, then newest, caution or warning with a home page takes an MFD that is not pinned. */
    private void autoPage(Array<Alert> alerts, boolean shields, float delta) {
        holding.clear();
        for (int i = 0; i < alerts.size; i++) holding.add(alerts.get(i).id);
        // Pages given back 10 s after their alert clears
        for (Mfd m : byPreference) {
            if (m.auto == null) continue;
            if (holding.contains(m.auto)) {
                m.autoCleared = Float.NaN;
                continue;
            }
            m.autoCleared = Float.isNaN(m.autoCleared) ? delta : m.autoCleared + delta;
            if (m.autoCleared >= GIVE_BACK) {
                m.auto = null;
                m.autoPage = null;
                m.autoCleared = Float.NaN;
            }
        }
        // Forget alerts that cleared, so one that comes back pages again
        Array<String> gone = null;
        for (String id : paged) {
            if (!holding.contains(id)) {
                if (gone == null) gone = new Array<>();
                gone.add(id);
            }
        }
        if (gone != null) for (String id : gone) paged.remove(id);
        Alert best = null;
        for (int i = 0; i < alerts.size; i++) {
            Alert a = alerts.get(i);
            if (a.level == Level.ADVISORY || paged.contains(a.id) || home(a, shields) == null) continue;
            if (best == null || a.level.ordinal() > best.level.ordinal() || (a.level == best.level && a.since > best.since)) best = a;
        }
        if (best == null) return;
        Page page = home(best, shields);
        paged.add(best.id);
        // Already up on an open MFD: nothing to turn
        if ((!left.folded && left.page == page) || (!right.folded && right.page == page)) return;
        Mfd m = !right.pinned ? right : !left.pinned ? left : null;
        if (m == null) return;
        m.auto = best.id;
        m.autoPage = page;
        m.autoCleared = Float.NaN;
    }

    /** Whether a caution or warning holds. */
    private static boolean alerting(Array<Alert> alerts) {
        for (int i = 0; i < alerts.size; i++) if (alerts.get(i).level != Level.ADVISORY) return true;
        return false;
    }

    /** An alert's home page (13.4), or null for one with none an MFD shows. */
    static Page home(Alert a, boolean shields) {
        if (a.id.startsWith("sys:")) return Page.POWER;
        switch (a.code) {
            case "MISSILE":
                return shields ? Page.DEFENSE : Page.SENSORS;
            case "HOSTILE":
                return Page.SENSORS;
            case "SHIELD":
                return Page.DEFENSE;
            case "FIRE":
            case "BREACH":
            case "VACUUM":
            case "PRESSURE":
            case "SMOKE":
            case "HULL":
                return Page.DAMAGE;
            case "O2":
                return Page.ATMOS;
            case "FUEL":
                return Page.PLAN;
            case "HELM":
                return Page.HELM;
            case "CASUALTY":
            case "SUIT AIR":
            case "BLEEDING":
            case "NO SUIT":
            case "ABANDON":
                return Page.CREW;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ the player's picks

    /** Alt with a station's number: its first page, or its next while it is on show already. */
    void station(Mfd m, int station) {
        Page[] pages = STATIONS[station];
        Page page = pages[0];
        if (m.station() == station && !m.folded) {
            int at = 0;
            for (int i = 0; i < pages.length; i++) if (pages[i] == m.page) at = i;
            page = pages[(at + 1) % pages.length];
        }
        choose(m, page, false);
    }

    /** A page's soft key: that page, open. */
    void softKey(Mfd m, int slot) {
        Page[] pages = STATIONS[m.station()];
        if (slot < 0 || slot >= pages.length) return;
        choose(m, pages[slot], false);
    }

    /**
     * An annunciator tile clicked: its alert's home page, open on the right MFD, or the left one when
     * the right is pinned. Held as the player's pick.
     */
    void show(Page page) {
        if (page == null) return;
        if ((!left.folded && left.page == page) || (!right.folded && right.page == page)) return;
        Mfd m = !right.pinned ? right : !left.pinned ? left : null;
        if (m != null) choose(m, page, false);
    }

    /** The fold half of the split key: folded to its rail, or open again. */
    void fold(Mfd m) {
        choose(m, m.page, !m.folded);
    }

    /** The pin half: an MFD pinned keeps its page through alerts. */
    void pin(Mfd m) {
        m.pinned = !m.pinned;
    }

    private static void choose(Mfd m, Page page, boolean folded) {
        m.chosen = true;
        m.chosenPage = page;
        m.chosenFolded = folded;
        m.page = page;
        m.folded = folded;
        // The player's pick ends an alert's hold on it
        m.auto = null;
        m.autoPage = null;
        m.autoCleared = Float.NaN;
    }
}
