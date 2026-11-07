package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.Input;
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
import com.ringsofiron.uihud.variants.nasapunk.screens.Fmt;
import com.ringsofiron.uihud.variants.nasapunk.screens.Hits;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import com.ringsofiron.uihud.variants.nasapunk.screens.Row;
import com.ringsofiron.uihud.variants.nasapunk.screens.ScreenModes;
import com.ringsofiron.uihud.variants.nasapunk.screens.Sect;
import com.ringsofiron.uihud.variants.nasapunk.screens.Tables;
import com.ringsofiron.uihud.variants.nasapunk.screens.Terms;
import com.ringsofiron.uihud.variants.nasapunk.screens.Ui;

import java.util.Locale;

/**
 * The stations spec's displays in place of the game's windows (documentation/ui-overhaul/variations/
 * nasapunk-stations, S5 to S8): the station link (DU-6: the market, the yard, the contract board, the
 * hiring hall and the shipyard) full screen under the master lamps (S6); the slate (SLATE-1: the
 * field refit away from a yard, the crew, boarding, the contracts held and the log) down the right
 * side, so the deck stays in sight, stopping above the band at a seat (S5, S7, S8); the controls on
 * the station link's frame; and the ship-lost card over everything.
 *
 * <p>Both are MFDs. Their tabs are soft keys along the top of the glass with their keys on the bezel
 * over them; the orders are soft keys along its foot with theirs under it, ENT on the key of the one
 * Enter gives and ESC on CLOSE's. A line under the tabs says where you are, and a status line over
 * the foot keys carries the last order's words, else what Enter gives. On the station link the list
 * and the terms of the row under the cursor sit side by side, and every order is a foot key, the
 * parts' and the cursor's row's in the parts' order and then the page's (S6); on the slate the terms
 * go under the list, and a part's orders stay on its title row so the foot keeps room for the row's
 * (S5, S7). The slate leaves the number keys to the seat's own orders; its tabs turn on the windows'
 * own keys or a click.
 */
final class StationScreen extends ScreenModes<StationScreen.Mode> {
    /** The spec's displays that stand in for the game's windows, with the name on the bezel and the plate. */
    enum Mode {
        LINK("STATION LINK", "DU-6  STATION LINK"),
        SLATE("SLATE", "SLATE-1  CMD"),
        CONTROLS("CONTROLS", "DU-6  REFERENCE");

        final String title, plate;

        Mode(String title, String plate) {
            this.title = title;
            this.plate = plate;
        }
    }

    /** Where the station link starts: under the master lamps (S6). */
    static final float LINK_TOP = 84f;
    /** The slate down the right side and its margin from the edge (S5, S7). */
    static final float SLATE_W = 478f, SLATE_EDGE = 16f;
    private static final float SLATE_TOP = 16f, LINK_EDGE = 8f, LINK_FOOT = 8f, SLATE_FOOT = 18f;
    /** The bezel round the glass: its sides, its top with the tab keys, its foot with the order keys and the plate. */
    private static final float SIDE = 14f, HEAD = 30f, FOOT = 40f;
    /** Inside the glass: the tab legends, the line under them, the status line and the foot legends. */
    private static final float TABS_H = 26f, INFO_H = 34f, STATUS_H = 24f, KEYS_H = 28f;
    /** The list's share of the station link's width beside the terms, and of the slate's height over them. */
    private static final float LIST_SHARE = 0.6f, SLATE_LIST = 0.55f;
    /** The fewest slots the foot keys share: the station link's six (S6), the slate's four (S5, S7). */
    private static final int LINK_SLOTS = 6, SLATE_SLOTS = 4;
    /** A dark order's legend: dimmer than one that can be given. */
    private static final Color BARRED = NpColors.rgb(0x4A5058);

    /** Where the slate stops, y down: above the band at a seat; NaN runs it near the bottom edge. */
    float slateFoot = Float.NaN;

    /** The station link's list: no buttons on its rows or its parts' titles, whose orders are foot keys. */
    private final Tables.Style list = new Tables.Style();
    /** The slate's list: its parts' orders on their title rows. */
    private final Tables.Style slate = new Tables.Style();
    private final Tables.Style terms = new Tables.Style();
    /** The controls: keys on their caps, tight rows, no cursor. */
    private final Tables.Style keys = new Tables.Style();

    private final IntArray all = new IntArray();
    private final Terms termsOf = new Terms();
    /** The foot keys as drawn: orders, then CLOSE. */
    private final Array<Object> foot = new Array<>();

    StationScreen(Game game, Group layer, NpKit kit) {
        super(game, layer, kit);
        for (Tables.Style s : new Tables.Style[] {list, slate}) {
            s.acts = false;
            s.rowH = 27f;
            s.titleH = 32f;
            s.gap = 12f;
            s.nameSize = 14f;
        }
        list.sectActs = false;
        slate.actW = 64f;
        terms.pairCol = 140f;
        terms.lineH = 24f;
        keys.rowH = 18f;
        keys.titleH = 26f;
        keys.rules = false;
        keys.cursor = false;
        keys.gap = 8f;
        // every key by where it works: three columns hold them all without HERE, which the prompt line says
        help.byWhereOnly();
    }

    // ---- which display, which tabs ------------------------------------------------------------------

    /**
     * The market, the yard at a berth, the shipyard and the contracts docked open the station link on
     * their tabs; the crew, boarding, the log, the contracts undocked and the yard away from one open
     * the slate.
     */
    @Override
    protected boolean route(ClassicWindow w) {
        switch (w) {
            case TRADE:
                return go(Mode.LINK, market);
            case YARD:
                return yard.offered(boards) ? go(Mode.LINK, yard) : go(Mode.SLATE, loadout);
            case SHIPYARD:
                return go(Mode.LINK, shipyard);
            case MISSIONS:
                return boards.docked() && offers.offered(boards) ? go(Mode.LINK, offers) : go(Mode.SLATE, held);
            case BOARDING:
                // with nothing latched K opens nothing, rather than the slate on another tab
                return boarding.offered(boards) && go(Mode.SLATE, boarding);
            case CREW:
                return go(Mode.SLATE, crew);
            case LOG:
                return go(Mode.SLATE, log);
            case HELP:
                return go(Mode.CONTROLS, help);
            default:
                return false;
        }
    }

    @Override
    protected void tabsOf(Mode m) {
        switch (m) {
            case LINK:
                tab(market);
                tab(yard);
                tab(offers);
                tab(hire);
                tab(shipyard);
                break;
            case SLATE:
                tab(loadout);
                tab(crew);
                tab(boarding);
                tab(held);
                tab(log);
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
        return m == Mode.LINK;
    }

    /** A tab's legend as the spec's soft keys give it: the board, the hiring hall, the contracts held. */
    String tabName(Page p) {
        if (p == offers) return "CONTRACT BOARD";
        if (p == hire) return "HIRING";
        if (p == held) return "CONTRACTS";
        return p.name;
    }

    /** At a seat the number keys are the seat's orders, so the slate leaves them to the game. */
    @Override
    public boolean keyDown(int keycode) {
        if (mode == Mode.SLATE && digit(keycode)) return false;
        return super.keyDown(keycode);
    }

    private static boolean digit(int k) {
        return k >= Input.Keys.NUM_0 && k <= Input.Keys.NUM_9 || k >= Input.Keys.NUMPAD_0 && k <= Input.Keys.NUMPAD_9;
    }

    /** Whether the slate shows, so the top column recentres left of it. */
    boolean slateShows() {
        return mode == Mode.SLATE && page != null;
    }

    /** Whether page {@code p} shows the row under the cursor's terms beside or under its list. */
    private boolean hasTerms(Page p) {
        return p != log && p != held && p != help && p != boarding;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void paintMode(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float x, y, dw, dh;
        if (mode == Mode.SLATE) {
            dw = Math.min(SLATE_W, w - 2f * SLATE_EDGE);
            x = w - SLATE_EDGE - dw;
            y = SLATE_TOP;
            dh = (Float.isNaN(slateFoot) ? h - SLATE_FOOT : slateFoot) - y;
        } else {
            x = LINK_EDGE;
            y = LINK_TOP;
            dw = w - 2f * LINK_EDGE;
            dh = h - LINK_TOP - LINK_FOOT;
        }
        frame.set(x, y, dw, dh);
        hits.plate(x, y, dw, dh);
        display(cv, x, y, dw, dh);
        float gx = x + SIDE, gy = y + HEAD, gw = dw - 2f * SIDE, gh = dh - HEAD - FOOT;
        float top = gy;
        if (mode != Mode.CONTROLS) {
            tabKeys(cv, gx, gy, gw, hits);
            top += TABS_H;
        }
        info(cv, gx, top, gw);
        top += INFO_H;
        Ui.rule(cv, gx + 8f, top, gw - 16f);
        float sy = gy + gh - KEYS_H - STATUS_H, by = top + 6f, bh = sy - by - 4f;
        if (page == help) controls(cv, cam, gx, by, gw, bh, hits);
        else if (!hasTerms(page)) whole(cv, cam, gx, by, gw, bh, hits);
        else if (mode == Mode.SLATE) listOverTerms(cv, cam, gx, by, gw, bh, hits);
        else listBesideTerms(cv, cam, gx, by, gw, bh, hits);
        status(cv, gx, sy, gw);
        footKeys(cv, gx, gy + gh - KEYS_H, gw, hits);
    }

    /** The display's hardware: the bezel and its fasteners, its name over the glass, and its plate. */
    private void display(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 6f, y + 6f);
        NpHardware.fastener(cv, x + w - 6f, y + 6f);
        NpHardware.fastener(cv, x + 6f, y + h - 6f);
        NpHardware.fastener(cv, x + w - 6f, y + h - 6f);
        NpHardware.stencil(cv, x + w / 2f, y + 13f, mode.title, 8.5f, 1.6f, NpCanvas.MIDDLE);
        NpHardware.glass(cv, x + SIDE, y + HEAD, w - 2f * SIDE, h - HEAD - FOOT);
        NpHardware.plate(cv, x + 16f, y + h - 14f, mode.plate);
    }

    /** The tabs as soft keys along the glass's top, the one showing lit white, each key on the bezel over its legend. */
    private void tabKeys(NpCanvas cv, float gx, float gy, float gw, Hits hits) {
        int n = Math.max(1, tabs.size);
        float slot = gw / n;
        for (int i = 0; i < tabs.size; i++) {
            Page p = tabs.get(i);
            float cx = gx + slot * (i + 0.5f);
            boolean on = p == page;
            String t = Ui.fit(cv, tabName(p), slot - 8f, Ui.LB + 1f, true, 0.6f);
            cv.text(cx, gy + 18f, t, Ui.LB + 1f, true, on ? NpColors.WHITE : NpColors.DIM, 0.6f, NpCanvas.MIDDLE);
            NpHardware.softKey(cv, cx - 13f, gy - 11f, 26f, 5f, false);
            hits.add(gx + slot * i, gy - 14f, slot, TABS_H + 14f, p, 0);
        }
    }

    /** The line under the tabs: where you are and what you hold (S6), whose slate it is (S5, S7), or which reference. */
    private void info(NpCanvas cv, float gx, float y, float gw) {
        float l = gx + 14f, right = gx + gw - 14f, base = y + 22f;
        switch (mode) {
            case LINK: {
                TradeBoard t = boards.trade;
                float rx = right;
                if (t.hasHold && t.cargoCapacity > 0) {
                    rx -= factEnd(cv, rx, base, "HOLD", Fmt.units(t.cargoUsed) + " OF " + Fmt.units(t.cargoCapacity) + " M³");
                    rx -= dotEnd(cv, rx, base);
                }
                rx -= factEnd(cv, rx, base, "CREDITS", Fmt.credits(t.credits) + " CR");
                String st = boards.station.isEmpty() ? "SHIPYARD" : boards.station;
                float tx = l + cv.text(l, base, st, Ui.V + 2f, true, NpColors.WHITE, 0.8f, NpCanvas.START) + 14f;
                String owner = boards.owner;
                if (boards.sponsor) owner += (owner.isEmpty() ? "" : "  ·  ") + "YOUR SPONSOR";
                if (!boards.standing.isEmpty()) owner += (owner.isEmpty() ? "" : "  ·  ") + "STANDING " + boards.standing;
                cv.text(tx, base, Ui.fit(cv, owner, rx - 24f - tx, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                break;
            }
            case SLATE: {
                // the credits are the market's, so the slate has them docked; away, the berths
                float rx = right;
                if (boards.docked()) rx -= cv.text(right, base, Fmt.credits(boards.trade.credits) + " CR", Ui.B + 1f, true, NpColors.WHITE, 0.4f, NpCanvas.END);
                else if (boards.berths >= 0) rx -= factEnd(cv, right, base, "BERTHS", boards.berthsTaken + " OF " + boards.berths);
                String who = Fmt.caps(boards.crew.shipName);
                if (!boards.rank.isEmpty()) who = Fmt.caps(firstOf(boards.rank)) + (who.isEmpty() ? "" : "  ·  " + who);
                cv.text(l, base, Ui.fit(cv, who, rx - 20f - l, Ui.LB + 1f, true, 0.6f), Ui.LB + 1f, true, NpColors.DIM, 0.6f, NpCanvas.START);
                break;
            }
            case CONTROLS: {
                float tx = l + cv.text(l, base, "CONTROLS", Ui.V + 2f, true, NpColors.WHITE, 0.8f, NpCanvas.START) + 14f;
                cv.text(tx, base, "EVERY KEY BY WHERE IT WORKS", Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                factEnd(cv, right, base, "KEYS", "AS YOU HAVE THEM BOUND");
                break;
            }
            default:
                break;
        }
    }

    /** The rank alone from the career line ("Ensign, Terran Patrol Authority"): the slate names the ship after it. */
    private static String firstOf(String rank) {
        int comma = rank.indexOf(',');
        return comma < 0 ? rank : rank.substring(0, comma);
    }

    /** A dim label and its value ending at {@code right}; returns the width. */
    private static float factEnd(NpCanvas cv, float right, float base, String label, String value) {
        float vw = cv.text(right, base, value, Ui.B, true, NpColors.WHITE, 0.4f, NpCanvas.END);
        return vw + 7f + cv.text(right - vw - 7f, base, label, Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
    }

    /** The dot between two facts ending at {@code right}; returns its room. */
    private static float dotEnd(NpCanvas cv, float right, float base) {
        cv.text(right - 12f, base, "·", Ui.LB, true, NpColors.DIM, 0f, NpCanvas.MIDDLE);
        return 24f;
    }

    // ---- the page --------------------------------------------------------------------------------

    /** The station link: the list on the left, the terms of the row under the cursor on the right (S6). */
    private void listBesideTerms(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        float lw = Math.round(w * LIST_SHARE);
        stackAll(cv, cam, x, y, lw - 4f, h, list, hits);
        cv.fill(x + lw, y + 4f, 1f, h - 8f, Ui.RULE);
        termsOfRow(cv, cam, x + lw + 1f, y, w - lw - 1f, h);
    }

    /** The slate: the list over the terms of the row under the cursor, the list as tall as it needs up to its share (S7). */
    private void listOverTerms(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        allSects();
        float want = 0f;
        for (int i = 0; i < all.size; i++) want += Tables.want(cv, page, all.get(i), w, slate);
        float lh = Math.min(want, Math.round(h * SLATE_LIST));
        stackAll(cv, cam, x, y, w, lh, slate, hits);
        Ui.rule(cv, x + 8f, y + lh + 4f, w - 16f);
        termsOfRow(cv, cam, x, y + lh + 8f, w, h - lh - 8f);
    }

    /** A page with no terms of its rows: the log, the contracts held, boarding (S5, S8). */
    private void whole(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        stackAll(cv, cam, x, y, w, h, mode == Mode.SLATE ? slate : list, hits);
    }

    /** The controls: every part in three columns. */
    private void controls(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        allSects();
        boolean clipped = cam != null && cv.clip(cam, x, y, w, h);
        Tables.flow(cv, cam, page, all, 3, x + 4f, y, w - 8f, h, 6f, keys, hits);
        if (clipped) cv.unclip();
    }

    private void stackAll(NpCanvas cv, Camera cam, float x, float y, float w, float h, Tables.Style st, Hits hits) {
        allSects();
        boolean clipped = cam != null && cv.clip(cam, x, y, w, h);
        Tables.stack(cv, cam, page, all, x, y, w, h, st, hits);
        if (clipped) cv.unclip();
    }

    private void allSects() {
        all.clear();
        for (int i = 0; i < page.sects.size; i++) all.add(i);
    }

    /** The row under the cursor: its name and its part, then its lines (its own, or its cells under their heads). */
    private void termsOfRow(NpCanvas cv, Camera cam, float x, float y, float w, float h) {
        Row r = page.current();
        if (r == null) {
            String empty = page.sects.size > 0 && !page.sects.first().empty.isEmpty() ? page.sects.first().empty : "NOTHING HERE";
            cv.text(x + w / 2f, y + h / 2f, Ui.fit(cv, empty, w - 28f, Ui.LB, true, 0.6f), Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.MIDDLE);
            return;
        }
        Sect s = page.sectOf(r);
        float l = x + 14f, right = x + w - 14f;
        float sw = s == null || s.title.isEmpty() ? 0f : Ui.labEnd(cv, right, y + 22f, Ui.fit(cv, s.title, 140f, Ui.LB, true, 0.6f));
        String name = r.cells.size > 0 ? r.cells.first().text : "";
        cv.text(l, y + 22f, Ui.fit(cv, name, right - sw - 20f - l, Ui.V + 1f, true, 0.6f), Ui.V + 1f, true, NpColors.WHITE, 0.6f, NpCanvas.START);
        boolean clipped = cam != null && cv.clip(cam, x, y + 32f, w, h - 32f);
        Tables.lines(cv, termsOf.of(r, s), x, y + 38f, w, terms);
        if (clipped) cv.unclip();
    }

    // ---- the foot --------------------------------------------------------------------------------

    /** The status line over the foot keys: the last order's words while they stand, else what Enter gives, or the page's line. */
    private void status(NpCanvas cv, float gx, float sy, float gw) {
        Ui.rule(cv, gx + 8f, sy, gw - 16f);
        float l = gx + 14f, base = sy + 17f, room = gw - 28f;
        if (feed.showing()) {
            String s = (feed.source.isEmpty() ? "" : feed.source + "  ") + feed.text.toUpperCase(Locale.ROOT);
            cv.text(l, base, Ui.fit(cv, s, room, Ui.LB + 1f, true, 0.6f), Ui.LB + 1f, true, NpColors.WHITE, 0.6f, NpCanvas.START);
            return;
        }
        Row r = page.current();
        String say = enterLine(r);
        Color ink = NpColors.DIM;
        if (!say.isEmpty()) {
            Act enter = page.picked(r);
            if (enter == null || !enter.live) ink = NpColors.AMBER;
        } else {
            say = page.status;
        }
        if (!say.isEmpty()) cv.text(l, base, Ui.fit(cv, say, room, Ui.LB + 1f, true, 0.6f), Ui.LB + 1f, true, ink, 0.6f, NpCanvas.START);
    }

    /**
     * The orders as soft keys along the glass's foot, CLOSE at the right: on the station link every
     * part's and the cursor's row's in the parts' order, then the page's (S6); on the slate the row's
     * and the page's (S5, S7). Each key's legend is lit green while on and amber while armed, and the
     * key under it carries ENT for the order Enter gives and ESC for CLOSE.
     */
    private void footKeys(NpCanvas cv, float gx, float y, float gw, Hits hits) {
        foot.clear();
        Row r = page.current();
        Sect at = r == null ? null : page.sectOf(r);
        for (int i = 0; i < page.sects.size; i++) {
            Sect s = page.sects.get(i);
            if (mode != Mode.SLATE) for (int j = 0; j < s.acts.size; j++) foot.add(s.acts.get(j));
            if (s == at) for (int j = 0; j < r.acts.size; j++) foot.add(r.acts.get(j));
        }
        for (int i = 0; i < page.acts.size; i++) foot.add(page.acts.get(i));
        foot.add(CLOSE);
        int n = foot.size;
        float slot = gw / Math.max(n, mode == Mode.SLATE ? SLATE_SLOTS : LINK_SLOTS);
        Act enter = r == null ? null : page.picked(r);
        float base = y + KEYS_H - 9f, keyY = y + KEYS_H + 4f;
        for (int i = 0; i < n; i++) {
            Object o = foot.get(i);
            boolean close = o == CLOSE;
            float sx = close ? gx + gw - slot : gx + slot * i, cx = sx + slot / 2f;
            String label;
            Color ink;
            boolean box = false;
            if (close) {
                label = "CLOSE";
                ink = NpColors.DIM;
            } else {
                Act a = (Act) o;
                label = a.label;
                box = a.state == Act.State.ON || a.state == Act.State.ARMED;
                ink = a.state == Act.State.ON ? NpColors.GREEN : a.state == Act.State.ARMED ? NpColors.AMBER
                        : a == enter && a.live ? NpColors.WHITE : a.live ? NpColors.DIM : BARRED;
            }
            label = Ui.fit(cv, label, slot - 14f, Ui.LB + 1f, true, 0.5f);
            float tw = cv.text(cx, base, label, Ui.LB + 1f, true, ink, 0.5f, NpCanvas.MIDDLE);
            if (box) cv.stroke(cx - tw / 2f - 5f, base - 13f, tw + 10f, 17f, 1.5f, ink);
            // the key under the glass, with Enter's and Esc's legends on it
            NpHardware.softKey(cv, cx - 15f, keyY, 30f, 9f, box);
            String on = close ? "ESC" : o == enter ? "ENT" : "";
            if (!on.isEmpty()) cv.text(cx, keyY + 7f, on, 6.5f, true, NpColors.STENCIL, 0.6f, NpCanvas.MIDDLE);
            hits.add(sx, y - 2f, slot, KEYS_H + 16f, o, 0);
        }
    }
}
