package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.screens.Act;
import com.ringsofiron.uihud.variants.nasapunk.screens.Cell;
import com.ringsofiron.uihud.variants.nasapunk.screens.Fmt;
import com.ringsofiron.uihud.variants.nasapunk.screens.Hits;
import com.ringsofiron.uihud.variants.nasapunk.screens.Line;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import com.ringsofiron.uihud.variants.nasapunk.screens.Row;
import com.ringsofiron.uihud.variants.nasapunk.screens.ScreenModes;
import com.ringsofiron.uihud.variants.nasapunk.screens.Sect;
import com.ringsofiron.uihud.variants.nasapunk.screens.Tables;
import com.ringsofiron.uihud.variants.nasapunk.screens.Ui;

import java.util.Locale;

/**
 * The task spec's panels in place of the game's windows (documentation/ui-overhaul/variations/
 * nasapunk-task, T5 to T9): the station terminal (TRM: the contracts, market, yard, shipyard and
 * hiring hall, on J, T and Y), the ship's book (BOOK: the field refit away from a yard, the crew, the
 * contracts held and the log, on Y, I, J undocked and L), boarding (BRD, K) and the controls (F1),
 * and the ship-lost card over everything.
 *
 * <p>The terminal and the book are T6's frame: a console under CHR and ALR with its plate, a header
 * of glass that says where you are, the tabs under it on their keys, and the page's display units.
 * A page whose rows have terms puts its list on the left and, on the right, the terms of the row
 * under the cursor with its orders as the hard keys under the glass; the page's own orders are soft
 * keys along the list's foot. A page whose rows have none (the log, the contracts held) takes one
 * unit the whole width, its orders on its rows. The controls take the book's frame without tabs,
 * every key in three columns (T9). Boarding is a panel down the right side, so the deck stays in
 * sight (T5).
 */
final class TaskScreen extends ScreenModes<TaskScreen.Mode> {
    /** The spec's panels that stand in for the game's windows, with the console's name and its units' plate. */
    enum Mode {
        TERMINAL("STATION TERMINAL", "TRM"),
        BOOK("SHIP'S BOOK", "BOOK"),
        BOARDING("BOARDING", "DU-7"),
        CONTROLS("CONTROLS", "CTL");

        final String title, unit;

        Mode(String title, String unit) {
            this.title = title;
            this.unit = unit;
        }
    }

    /** Where the console starts: under CHR and ALR (T6). */
    static final float TOP = 96f;
    private static final float EDGE = 8f, INSET = 16f, HEAD_H = 48f, TAB_H = 26f, GAP = 10f, DU_B = 7f;
    /** A unit's foot under its glass: the plate alone, or the hard keys over it. */
    private static final float FOOT = 24f, KEYS_FOOT = 50f;
    /** The soft keys' row along the foot of a list's glass. */
    private static final float SOFT_H = 26f;
    /** The terms' foot line over the hard keys. */
    private static final float SAY_H = 24f;
    /** The list's share of the width beside the terms (T6, T7). */
    private static final float LIST_SHARE = 0.62f;
    /** Boarding's panel down the right side (T5), clear of the hint line under it. */
    private static final float BOARD_W = 412f, BOARD_FOOT = HintStrip.H + 14f;

    /** The list beside the terms: no buttons on the rows, whose orders are the terms' hard keys. */
    private final Tables.Style list = new Tables.Style();
    /** A page the whole width, its orders on its rows. */
    private final Tables.Style whole = new Tables.Style();
    /** The terms of the row under the cursor. */
    private final Tables.Style terms = new Tables.Style();
    /** The controls: keys on their caps, tight rows, no cursor (T9). */
    private final Tables.Style keys = new Tables.Style();
    /** Boarding's narrow panel. */
    private final Tables.Style panel = new Tables.Style();

    private final IntArray all = new IntArray();
    /** The terms made from a row's cells under their heads, for a row that has none of its own. */
    private final Array<Line> made = new Array<>();
    private final Array<Line> linePool = new Array<>();

    TaskScreen(Game game, Group layer, NpKit kit) {
        super(game, layer, kit);
        for (Tables.Style s : new Tables.Style[] {list, whole, panel}) {
            s.centre = true;
            s.rowH = 28f;
            s.titleH = 34f;
            s.gap = 12f;
            s.nameSize = 14f;
        }
        list.acts = false;
        whole.actW = 70f;
        whole.actH = 22f;
        panel.acts = false;
        panel.pad = 12f;
        panel.rowH = 26f;
        panel.gap = 10f;
        panel.actW = 64f;
        terms.pairCol = 130f;
        terms.lineH = 24f;
        keys.rowH = 21f;
        keys.titleH = 26f;
        keys.rules = false;
        keys.cursor = false;
        keys.gap = 8f;
    }

    // ---- which panel, which tabs ------------------------------------------------------------------

    /**
     * The market, the yard at a berth, the shipyard and the contracts docked open the terminal on
     * their tabs; the crew, the log, the contracts undocked and the yard away from one open the book.
     */
    @Override
    protected boolean route(ClassicWindow w) {
        switch (w) {
            case TRADE:
                return go(Mode.TERMINAL, market);
            case YARD:
                return yard.offered(boards) ? go(Mode.TERMINAL, yard) : go(Mode.BOOK, loadout);
            case SHIPYARD:
                return go(Mode.TERMINAL, shipyard);
            case MISSIONS:
                return boards.docked() && offers.offered(boards) ? go(Mode.TERMINAL, offers) : go(Mode.BOOK, held);
            case BOARDING:
                return go(Mode.BOARDING, boarding);
            case CREW:
                return go(Mode.BOOK, crew);
            case LOG:
                return go(Mode.BOOK, log);
            case HELP:
                return go(Mode.CONTROLS, help);
            default:
                return false;
        }
    }

    @Override
    protected void tabsOf(Mode m) {
        switch (m) {
            case TERMINAL:
                tab(offers);
                tab(market);
                tab(yard);
                tab(shipyard);
                tab(hire);
                break;
            case BOOK:
                tab(loadout);
                tab(crew);
                tab(held);
                tab(log);
                break;
            case BOARDING:
                tab(boarding);
                break;
            case CONTROLS:
                tab(help);
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean docksOnly(Mode m) {
        return m == Mode.TERMINAL;
    }

    /** A tab's name as the spec's tab rows give it: the board and the contracts held are CONTRACTS, the hiring hall CREW. */
    String tabName(Page p) {
        if (p == offers || p == held) return "CONTRACTS";
        if (p == hire) return "CREW";
        return p.name;
    }

    /** The key on a tab's cap: the game's key for its window, J on the book's contracts only undocked; "" for none. */
    String tabKey(Page p) {
        if (p == offers) return "J";
        if (p == market) return "T";
        if (p == yard || p == loadout) return "Y";
        if (p == crew) return "I";
        if (p == held) return boards.docked() ? "" : "J";
        if (p == log) {
            String k = game.data().key("LOG");
            return k.isEmpty() ? "L" : k.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    /** Whether page {@code p} shows the row under the cursor's terms beside its list. */
    private boolean hasTerms(Page p) {
        return p != log && p != held && p != help && p != boarding;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void paintMode(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        if (mode == Mode.BOARDING) boardingPanel(cv, cam, w, h, hits);
        else console(cv, cam, w, h, hits);
    }

    /** The terminal, the book and the controls: T6's console under CHR and ALR. */
    private void console(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float x = EDGE, y = TOP, cw = w - 2f * EDGE, ch = h - TOP - 6f;
        frame.set(x, y, cw, ch);
        hits.plate(x, y, cw, ch);
        body(cv, x, y, cw, ch);
        cv.fill(x + 18f, y + 8f, 3f, 12f, NpColors.KAPTON);
        NpHardware.stencil(cv, x + 27f, y + 18f, mode.title, 11f, 1.6f, NpCanvas.START);
        String sub = plateSub();
        if (!sub.isEmpty()) {
            float tw = cv.width(mode.title, 11f, true, 1.6f);
            cv.text(x + 27f + tw + 18f, y + 18f, sub, 8.5f, true, NpColors.DIM, 1.4f, NpCanvas.START);
        }
        float ix = x + INSET, iw = cw - 2f * INSET, hy = y + 28f;
        header(cv, ix, hy, iw, hits);
        float by = hy + HEAD_H + 10f;
        if (mode != Mode.CONTROLS) {
            tabRow(cv, ix, by, iw, hits);
            by += TAB_H + 10f;
        }
        float bh = y + ch - 8f - by;
        if (page == help) controls(cv, cam, ix, by, iw, bh, hits);
        else if (hasTerms(page)) listAndTerms(cv, cam, ix, by, iw, bh, hits);
        else wholeWidth(cv, cam, ix, by, iw, bh, hits);
    }

    /** What the console's plate says after its name: the station's data link, the ship, or the reference. */
    private String plateSub() {
        switch (mode) {
            case TERMINAL:
                return (boards.station.isEmpty() ? "SHIPYARD" : boards.station) + "  ·  DATA LINK";
            case BOOK:
                return Fmt.caps(boards.crew.shipName);
            case CONTROLS:
                return "EVERY ACTION";
            default:
                return "";
        }
    }

    /** The console's body: machined hardware with its fasteners at the corners. */
    private static void body(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 7f, y + 7f);
        NpHardware.fastener(cv, x + w - 7f, y + 7f);
        NpHardware.fastener(cv, x + 7f, y + h - 7f);
        NpHardware.fastener(cv, x + w - 7f, y + h - 7f);
    }

    /** A display unit set in the console: bezel, fasteners, glass over a foot of {@code foot}, and its plate. */
    private static void unit(NpCanvas cv, float x, float y, float w, float h, float foot, String plate) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 5f, y + 5f);
        NpHardware.fastener(cv, x + w - 5f, y + 5f);
        NpHardware.fastener(cv, x + 5f, y + h - 5f);
        NpHardware.fastener(cv, x + w - 5f, y + h - 5f);
        NpHardware.glass(cv, x + DU_B, y + DU_B, w - 2f * DU_B, h - DU_B - foot);
        NpHardware.plate(cv, x + 14f, y + h - 15f, plate);
    }

    // ---- the header and the tabs -------------------------------------------------------------------

    /** The header: where you are (or whose book, or which reference) and its facts, and the way back to the deck. */
    private void header(NpCanvas cv, float x, float y, float w, Hits hits) {
        NpHardware.glass(cv, x, y, w, HEAD_H);
        float l = x + 16f, b1 = y + 20f, b2 = y + 39f, right = x + w - 14f;
        // the way back: ESC on its cap
        float bw = cv.width("BACK TO THE DECK", Ui.LB, true, 0.8f);
        cv.text(right, b1, "BACK TO THE DECK", Ui.LB, true, NpColors.WHITE, 0.8f, NpCanvas.END);
        float kx = right - bw - 10f - Ui.capW(cv, "ESC");
        Ui.capKey(cv, kx, y + 6f, "ESC", 18f);
        hits.add(kx - 4f, y, right - kx + 8f, HEAD_H, CLOSE, 0);
        float room = kx - 20f;
        switch (mode) {
            case TERMINAL: {
                float tx = l + cv.text(l, b1, boards.station.isEmpty() ? "SHIPYARD" : boards.station, Ui.V + 1f, true, NpColors.WHITE, 0.8f, NpCanvas.START) + 12f;
                if (!boards.owner.isEmpty()) tx += cv.text(tx, b1, Ui.fit(cv, boards.owner, room - tx, Ui.B, true, 0.4f), Ui.B, true, NpColors.WHITE, 0.4f, NpCanvas.START) + 10f;
                if (boards.sponsor && tx < room - 120f) tx += cv.text(tx, b1, "YOUR SPONSOR", Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START) + 4f;
                if (!boards.standing.isEmpty() && tx < room - 120f) {
                    tx = dot(cv, tx, b1);
                    fact(cv, tx, b1, "STANDING", boards.standing, NpColors.WHITE);
                }
                TradeBoard t = boards.trade;
                float fx = l + fact(cv, l, b2, "CREDITS", Fmt.credits(t.credits) + " CR", NpColors.WHITE);
                if (t.hasHold && t.cargoCapacity > 0) {
                    fx = dot(cv, fx, b2);
                    fx += fact(cv, fx, b2, "HOLD", Fmt.units(t.cargoUsed) + " OF " + Fmt.units(t.cargoCapacity) + " M³", NpColors.WHITE);
                }
                int held = boards.missions.held.size;
                fx = dot(cv, fx, b2);
                fact(cv, fx, b2, "CONTRACTS", held + " HELD", NpColors.WHITE);
                break;
            }
            case BOOK: {
                float tx = l + cv.text(l, b1, "SHIP'S BOOK", Ui.V + 1f, true, NpColors.WHITE, 0.8f, NpCanvas.START) + 12f;
                tx += cv.text(tx, b1, Ui.fit(cv, Fmt.caps(boards.crew.shipName), room - tx - 200f, Ui.B, true, 0.4f), Ui.B, true, NpColors.WHITE, 0.4f, NpCanvas.START);
                if (boards.docked()) {
                    tx = dot(cv, tx, b1);
                    cv.text(tx, b1, "DOCKED AT " + boards.station, Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                }
                float fx = l;
                if (boards.berths >= 0) fx += fact(cv, fx, b2, "BERTHS", boards.berthsTaken + " OF " + boards.berths, NpColors.WHITE);
                if (boards.wounded > 0) {
                    if (fx > l) fx = dot(cv, fx, b2);
                    fx += fact(cv, fx, b2, "HURT", Integer.toString(boards.wounded), NpColors.AMBER);
                }
                if (!boards.rank.isEmpty()) {
                    if (fx > l) fx = dot(cv, fx, b2);
                    fact(cv, fx, b2, "", Ui.fit(cv, Fmt.caps(boards.rank), room - fx, Ui.B, true, 0.4f), NpColors.WHITE);
                }
                break;
            }
            case CONTROLS: {
                float tx = l + cv.text(l, b1, "CONTROLS", Ui.V + 1f, true, NpColors.WHITE, 0.8f, NpCanvas.START) + 12f;
                cv.text(tx, b1, "EVERY KEY, FIRST WHAT WORKS WHERE YOU ARE", Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                fact(cv, l, b2, "KEYS", "AS YOU HAVE THEM BOUND", NpColors.WHITE);
                break;
            }
            default:
                break;
        }
    }

    /** A dim label and its value, as the header's facts read; returns the width. */
    private static float fact(NpCanvas cv, float x, float base, String label, String value, Color c) {
        float w = label.isEmpty() ? 0f : cv.text(x, base, label, Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START) + 7f;
        return w + cv.text(x + w, base, value, Ui.B, true, c, 0.4f, NpCanvas.START);
    }

    /** The dot between two facts; returns where the next starts. */
    private static float dot(NpCanvas cv, float x, float base) {
        cv.text(x + 10f, base, "·", Ui.LB, true, NpColors.DIM, 0f, NpCanvas.START);
        return x + 24f;
    }

    /**
     * The tabs on their keys, the one showing underlined; at the right the last order's words while
     * they stand, else why a tab is missing (no shipyard here) or where J goes.
     */
    private void tabRow(NpCanvas cv, float x, float y, float w, Hits hits) {
        cv.fillRound(x, y, w, TAB_H, 1f, NpColors.GLASS, 1f);
        float tx = x + 14f, base = y + 18f;
        for (int i = 0; i < tabs.size; i++) {
            Page p = tabs.get(i);
            float kx = tx;
            String key = tabKey(p);
            if (!key.isEmpty()) tx += Ui.capKey(cv, tx, y + 4f, key, 18f) + 8f;
            boolean on = p == page;
            float lw = cv.text(tx, base, tabName(p), Ui.LB + 1f, true, on ? NpColors.WHITE : NpColors.DIM, 0.8f, NpCanvas.START);
            if (on) cv.fill(tx, base + 4f, lw, 1.5f, NpColors.WHITE);
            hits.add(kx - 6f, y, tx + lw - kx + 12f, TAB_H, p, 0);
            tx += lw + 34f;
        }
        float right = x + w - 14f, room = right - tx - 20f;
        if (feed.showing()) {
            float tw = cv.width(feed.text, Ui.B, false, 0.2f);
            String text = Ui.fit(cv, feed.text, room - 90f, Ui.B, false, 0.2f);
            float textX = right - Math.min(tw, room - 90f);
            cv.text(textX, base, text, Ui.B, false, NpColors.WHITE, 0.2f, NpCanvas.START);
            cv.text(textX - 10f, base, Ui.fit(cv, feed.source, 80f, Ui.LB, true, 0.8f), Ui.LB, true, NpColors.DIM, 0.8f, NpCanvas.END);
            return;
        }
        String note = tabNote();
        if (!note.isEmpty()) cv.text(right, base, Ui.fit(cv, note, room, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
    }

    /** Why the tab row is as it is: no shipyard at this station, or J's way docked. */
    private String tabNote() {
        if (mode == Mode.TERMINAL && !tabs.contains(shipyard, true) && !boards.station.isEmpty())
            return "NO SHIPYARD HERE: " + boards.station + " SELLS NO SHIPS";
        if (mode == Mode.BOOK && boards.docked()) return "J OPENS CONTRACTS HERE ONLY UNDOCKED: DOCKED, IT OPENS THE TERMINAL";
        return "";
    }

    // ---- the page's units ----------------------------------------------------------------------------

    /** The list on the left, its own orders as soft keys along its foot; the cursor's row's terms on the right (T6, T7). */
    private void listAndTerms(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        float lw = Math.round((w - GAP) * LIST_SHARE), rx = x + lw + GAP, rw = w - lw - GAP;
        String n = tabName(page);
        unit(cv, x, y, lw, h, FOOT, mode.unit + "-1  " + n);
        float gx = x + DU_B, gy = y + DU_B, gw = lw - 2f * DU_B, gh = h - DU_B - FOOT;
        float soft = page.acts.size > 0 ? SOFT_H : 0f;
        allSects();
        boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh);
        Tables.stack(cv, cam, page, all, gx, gy, gw, gh - soft, list, hits);
        if (soft > 0f) softKeys(cv, gx, gy + gh - soft, gw, soft, hits);
        if (clipped) cv.unclip();
        unit(cv, rx, y, rw, h, KEYS_FOOT, mode.unit + "-2  " + (page == crew ? "PERSON" : "TERMS"));
        termsUnit(cv, cam, rx + DU_B, y + DU_B, rw - 2f * DU_B, h - DU_B - KEYS_FOOT, hits);
    }

    /** One unit the whole width: the log under its filters (T8), the contracts held with their orders on their rows. */
    private void wholeWidth(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        unit(cv, x, y, w, h, FOOT, mode.unit + "-1  " + tabName(page));
        float gx = x + DU_B, gy = y + DU_B, gw = w - 2f * DU_B, gh = h - DU_B - FOOT;
        allSects();
        boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh);
        Tables.stack(cv, cam, page, all, gx, gy, gw, gh, whole, hits);
        if (clipped) cv.unclip();
    }

    /** The controls: every part in three columns on one unit (T9). */
    private void controls(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        unit(cv, x, y, w, h, FOOT, "CTL-1  REFERENCE");
        float gx = x + DU_B, gy = y + DU_B, gw = w - 2f * DU_B, gh = h - DU_B - FOOT;
        allSects();
        boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh);
        Tables.flow(cv, cam, page, all, 3, gx + 4f, gy + 4f, gw - 8f, gh - 8f, 6f, keys, hits);
        if (clipped) cv.unclip();
    }

    private void allSects() {
        all.clear();
        for (int i = 0; i < page.sects.size; i++) all.add(i);
    }

    /** The page's own orders as soft keys along the list's foot: the words on the glass, lit green while on (T7). */
    private void softKeys(NpCanvas cv, float x, float y, float w, float h, Hits hits) {
        int n = page.acts.size;
        float slot = w / n;
        for (int i = 0; i < n; i++) {
            Act a = page.acts.get(i);
            float cx = x + slot * i + slot / 2f, base = y + h / 2f + 4f;
            float tw = cv.width(a.label, Ui.LB, true, 0.6f);
            boolean on = a.state == Act.State.ON, armed = a.state == Act.State.ARMED;
            Color ink = on ? NpColors.GREEN : armed ? NpColors.AMBER : a.live ? NpColors.WHITE : NpColors.DIM;
            cv.text(cx, base, a.label, Ui.LB, true, ink, 0.6f, NpCanvas.MIDDLE);
            if (on || armed) cv.stroke(cx - tw / 2f - 5f, base - 13f, tw + 10f, 18f, 1.5f, ink);
            // the key itself, under the glass
            NpHardware.softKey(cv, cx - 12f, y + h + DU_B - 1f, 24f, 6f, false);
            hits.add(cx - Math.max(tw / 2f + 8f, 20f), y, Math.max(tw + 16f, 40f), h + DU_B + 6f, a, 0);
        }
    }

    // ---- the terms ---------------------------------------------------------------------------------

    /**
     * The terms of the row under the cursor: whose part it is and its name over its lines (its own, or
     * its cells under their heads), what Enter will do at the foot, and its orders as hard keys under the
     * glass, the one Enter gives marked ENTER (T6).
     */
    private void termsUnit(NpCanvas cv, Camera cam, float gx, float gy, float gw, float gh, Hits hits) {
        Row r = page.current();
        float l = gx + 14f, right = gx + gw - 14f;
        if (r == null) {
            String empty = page.sects.size > 0 && !page.sects.first().empty.isEmpty() ? page.sects.first().empty : "NOTHING HERE";
            cv.text(gx + gw / 2f, gy + gh / 2f, Ui.fit(cv, empty, gw - 28f, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.MIDDLE);
            return;
        }
        Sect s = sectOf(r);
        String name = r.cells.size > 0 ? r.cells.first().text : "";
        Ui.lab(cv, l, gy + 22f, s == null || s.title.isEmpty() ? "TERMS" : Ui.fit(cv, s.title, 120f, Ui.LB, true, 0.6f));
        float mid = gx + gw / 2f + 30f;
        cv.text(mid, gy + 23f, Ui.fit(cv, name, (right - mid) * 2f - 20f, Ui.V, true, 0.6f), Ui.V, true, NpColors.WHITE, 0.6f, NpCanvas.MIDDLE);
        Array<Line> lines = r.detail.size > 0 ? r.detail : fromCells(r, s);
        boolean clipped = cam != null && cv.clip(cam, gx, gy + 34f, gw, gh - 34f - SAY_H);
        float ly = gy + 42f;
        ly += Tables.lines(cv, lines, gx, ly, gw, terms);
        if (!r.sub.isEmpty() && r.detail.size == 0) cv.text(l, ly + 14f, Ui.fit(cv, r.sub, gw - 28f, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
        if (clipped) cv.unclip();
        // what Enter does, or why it can't, over the hard keys
        Act enter = page.picked(r);
        float sy = gy + gh - SAY_H;
        Ui.rule(cv, gx + 8f, sy, gw - 16f);
        String say = say(r, enter);
        if (!say.isEmpty()) cv.text(l, sy + 16f, Ui.fit(cv, say, gw - 28f, Ui.LB, true, 0.6f), Ui.LB, true, enter != null && !enter.live ? NpColors.AMBER : NpColors.DIM, 0.6f, NpCanvas.START);
        hardKeys(cv, cam, r, enter, gx, gy + gh + 6f, gw, hits);
    }

    /** The foot line: the feed's last words while they stand, else what Enter gives on the row, or why it can't. */
    private String say(Row r, Act enter) {
        if (feed.showing()) return (feed.source.isEmpty() ? "" : feed.source + "  ") + feed.text.toUpperCase(Locale.ROOT);
        if (enter == null) return r.acts.size == 0 ? "" : "NOTHING TO DO HERE NOW";
        String pick = r.acts.size > 1 ? "  ·  ← → PICK" : "";
        if (!enter.live) return enter.label + ": " + (enter.why.isEmpty() ? "NOT NOW" : enter.why) + pick;
        if (enter.state == Act.State.ARMED) return "ENTER AGAIN TO " + enter.label;
        return "ENTER " + enter.label + pick;
    }

    /** The row's orders as push buttons under the glass, each under its key: ENTER for the one Enter gives. */
    private void hardKeys(NpCanvas cv, Camera cam, Row r, Act enter, float x, float y, float w, Hits hits) {
        float kx = x + 4f, kh = 22f;
        for (int i = 0; i < r.acts.size; i++) {
            Act a = r.acts.get(i);
            float kw = Math.min(150f, Math.max(64f, cv.width(a.label, Ui.LB, true, 0.4f) + 14f));
            if (kx + kw > x + w) break;
            Ui.korry(cv, cam, kx, y, kw, kh, a.label, a.state, a.live, false);
            hits.add(kx - 2f, y - 2f, kw + 4f, kh + 4f, a, 0);
            if (a == enter) cv.text(kx + kw / 2f, y + kh + 10f, "ENTER", 7.5f, true, NpColors.STENCIL, 1f, NpCanvas.MIDDLE);
            kx += kw + 10f;
        }
    }

    private Sect sectOf(Row r) {
        for (int i = 0; i < page.sects.size; i++) if (page.sects.get(i).rows.contains(r, true)) return page.sects.get(i);
        return null;
    }

    /** Terms for a row that has none of its own: each cell under its column's head. */
    private Array<Line> fromCells(Row r, Sect s) {
        linePool.addAll(made);
        made.clear();
        if (s == null) return made;
        for (int c = 1; c < r.cells.size && c < s.cols.size; c++) {
            Cell cell = r.cells.get(c);
            String head = s.cols.get(c).head;
            if (cell.text.isEmpty() || head.isEmpty()) continue;
            Line l = linePool.size > 0 ? linePool.pop() : new Line();
            l.kind = Line.Kind.PAIR;
            l.label = head;
            l.value = cell.text;
            l.unit = cell.unit;
            l.note = "";
            l.bar = Float.NaN;
            l.tone = cell.tone == Cell.Tone.NAME || cell.tone == Cell.Tone.BODY || cell.tone == Cell.Tone.LABEL ? Cell.Tone.VALUE : cell.tone;
            made.add(l);
        }
        return made;
    }

    // ---- boarding ----------------------------------------------------------------------------------

    /**
     * Boarding: one unit down the right side with the deck in sight (T5), the boarding's state and
     * orders, the salvage and the cargo aboard stacked in it, what Enter does on the cursor's row at
     * its foot, and the row's orders as hard keys under the glass.
     */
    private void boardingPanel(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float pw = Math.min(BOARD_W, w - 2f * EDGE), px = w - EDGE - pw, py = TOP, ph = h - TOP - BOARD_FOOT;
        frame.set(px, py, pw, ph);
        hits.plate(px, py, pw, ph);
        unit(cv, px, py, pw, ph, KEYS_FOOT, "DU-7  BOARDING");
        float gx = px + DU_B, gy = py + DU_B, gw = pw - 2f * DU_B, gh = ph - DU_B - KEYS_FOOT;
        allSects();
        boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh - SAY_H);
        Tables.stack(cv, cam, page, all, gx, gy, gw, gh - SAY_H, panel, hits);
        if (clipped) cv.unclip();
        Row r = page.current();
        Act enter = r == null ? null : page.picked(r);
        float sy = gy + gh - SAY_H;
        Ui.rule(cv, gx + 8f, sy, gw - 16f);
        String say = r == null ? (feed.showing() ? feed.text.toUpperCase(Locale.ROOT) : "") : say(r, enter);
        if (r != null && enter != null && enter.live && !feed.showing() && r.cells.size > 0) say = "ENTER " + enter.label + "  ·  " + r.cells.first().text;
        if (!say.isEmpty()) cv.text(gx + 12f, sy + 16f, Ui.fit(cv, say, gw - 24f, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
        if (r != null) hardKeys(cv, cam, r, enter, gx, gy + gh + 6f, gw, hits);
    }
}
