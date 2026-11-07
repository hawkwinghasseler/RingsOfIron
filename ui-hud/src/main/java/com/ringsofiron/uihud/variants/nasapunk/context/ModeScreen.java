package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.screens.Fmt;
import com.ringsofiron.uihud.variants.nasapunk.screens.Hits;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import com.ringsofiron.uihud.variants.nasapunk.screens.ScreenModes;
import com.ringsofiron.uihud.variants.nasapunk.screens.Tables;
import com.ringsofiron.uihud.variants.nasapunk.screens.Ui;

/**
 * The context spec's modes in place of the game's windows (documentation/ui-overhaul/variations/
 * nasapunk-context, C6 to C10): the ship (M2: crew, and the field refit away from a yard, I), the
 * station (M3: market, yard, shipyard, missions and hire, T and Y), boarding (M4, K), the captain's
 * record (M5: the contracts and the log, J and L) and the controls (M6, F1), and the ship-lost card
 * over everything.
 *
 * <p>Each mode has the frame C8 and C9 draw: a console body under P1, a title row of glass with the
 * mode's name and its tabs on their number caps, the page's display units, and a foot row of glass
 * with the one-line feed and the keys that close it. M3 is the one that doesn't take the whole
 * screen: a panel 1 120 wide and as tall as its tab needs over the docked view, with the rail every
 * tab shares (credits, standing, the hold, the tanks).
 */
final class ModeScreen extends ScreenModes<ModeScreen.Mode> {
    /** The spec's modes that stand in for the game's windows, with their plates and title words. */
    enum Mode {
        SHIP("M2  SHIP", "SHIP"),
        STATION("M3  STATION", "STATION"),
        BOARDING("M4  BOARDING", "BOARDING"),
        RECORD("M5  RECORD", "CAPTAIN'S RECORD"),
        CONTROLS("M6  CONTROLS", "");

        final String plate, sub;

        Mode(String plate, String sub) {
            this.plate = plate;
            this.sub = sub;
        }
    }

    private static final float ROW_H = 24f, EDGE = 8f, GAP = 6f, DU_B = 8f;
    /** M3's panel and its rail (C7). */
    private static final float STATION_W = 1120f, RAIL_W = 276f;
    /** A page's own orders at its first unit's foot. */
    private static final float ACTS_H = 34f;

    private final Tables.Style style = new Tables.Style();
    private final Tables.Style keys = new Tables.Style();

    /** One display unit of a page: its box, and its parts stacked or in columns. */
    private static final class Unit {
        float x, y, w, h;
        final IntArray sects = new IntArray();
        int columns = 1;
        boolean acts;
    }

    private final Array<Unit> units = new Array<>();
    private final Array<Unit> unitPool = new Array<>();

    ModeScreen(Game game, Group layer, NpKit kit) {
        super(game, layer, kit);
        // the glass cockpit's tables: rows tall enough for its lit push buttons (C7)
        style.rowH = 30f;
        style.actW = 68f;
        style.actH = 24f;
        style.gap = 10f;
        style.titleH = 34f;
        style.nameSize = 14f;
        // the controls list keys, not records: tight rows, no rules and no cursor (C10)
        keys.rowH = 21f;
        keys.titleH = 26f;
        keys.rules = false;
        keys.cursor = false;
        keys.gap = 8f;
    }

    // ---- which mode, which tabs ------------------------------------------------------------------

    /**
     * The market opens M3 on its Market tab; the yard M3 at a berth and M2's field refit away from
     * one; the contracts M3's Missions tab while M3 shows and else M5's Contracts tab.
     */
    @Override
    protected boolean route(ClassicWindow w) {
        switch (w) {
            case TRADE:
                return go(Mode.STATION, market);
            case YARD:
                return yard.offered(boards) ? go(Mode.STATION, yard) : go(Mode.SHIP, loadout);
            case SHIPYARD:
                return go(Mode.STATION, shipyard);
            case MISSIONS:
                return mode == Mode.STATION && offers.offered(boards) ? go(Mode.STATION, offers) : go(Mode.RECORD, held);
            case BOARDING:
                return go(Mode.BOARDING, boarding);
            case CREW:
                return go(Mode.SHIP, crew);
            case LOG:
                return go(Mode.RECORD, log);
            case HELP:
                return go(Mode.CONTROLS, help);
            default:
                return false;
        }
    }

    @Override
    protected void tabsOf(Mode m) {
        switch (m) {
            case SHIP:
                tab(crew);
                tab(loadout);
                break;
            case STATION:
                tab(market);
                tab(yard);
                tab(shipyard);
                tab(offers);
                tab(hire);
                break;
            case BOARDING:
                tab(boarding);
                break;
            case RECORD:
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
        return m == Mode.STATION;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void paintMode(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        if (mode == Mode.STATION) stationPanel(cv, cam, w, h, hits);
        else fullScreen(cv, cam, w, h, hits);
    }

    /** M2, M4, M5 and M6: the console body under P1, its rows of glass and the page's units (C8 to C10). */
    private void fullScreen(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float top = StatusStrip.H + 2f, fh = h - top;
        frame.set(0f, top, w, fh);
        hits.plate(0f, top, w, fh);
        body(cv, 0f, top, w, fh);
        cv.fill(18f, top + fh - 17f, 3f, 10f, NpColors.KAPTON);
        NpHardware.stencil(cv, 27f, top + fh - 8f, mode.plate, 9f, 1.6f, NpCanvas.START);
        titleRow(cv, EDGE, top + 6f, w - 2f * EDGE, hits);
        float footY = top + fh - 26f - ROW_H;
        footRow(cv, EDGE, footY, w - 2f * EDGE, hits);
        float y = top + 6f + ROW_H + GAP;
        plan(cv, EDGE, y, w - 2f * EDGE, footY - GAP - y);
        drawUnits(cv, cam, hits);
    }

    /** M3: a panel over the docked view, as tall as its tab, with the rail every tab shares (C7). */
    private void stationPanel(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float pw = Math.min(STATION_W, w - 2f * EDGE), px = Math.round((w - pw) / 2f), py = StatusStrip.H + 8f;
        float mainW = pw - 2f * EDGE - RAIL_W - GAP;
        float most = h - py - EDGE - 74f;
        plan(cv, px + EDGE, py + 36f, mainW, most);
        float want = Math.max(railWant(), wantOf(cv, units.first()));
        float duH = Math.max(Math.min(240f, most), Math.min(most, want));
        plan(cv, px + EDGE, py + 36f, mainW, duH);
        float ph = duH + 74f;
        frame.set(px, py, pw, ph);
        hits.plate(px, py, pw, ph);
        body(cv, px, py, pw, ph);
        titleRow(cv, px + EDGE, py + 6f, pw - 2f * EDGE, hits);
        drawUnits(cv, cam, hits);
        float rx = px + EDGE + mainW + GAP;
        du(cv, rx, py + 36f, RAIL_W, duH);
        rail(cv, rx + DU_B, py + 36f + DU_B, RAIL_W - 2f * DU_B, duH - 2f * DU_B);
        footRow(cv, px + EDGE, py + 36f + duH + GAP, pw - 2f * EDGE, hits);
    }

    /** A console body: machined hardware with its fasteners at the corners. */
    private static void body(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 7f, y + 7f);
        NpHardware.fastener(cv, x + w - 7f, y + 7f);
        NpHardware.fastener(cv, x + 7f, y + h - 7f);
        NpHardware.fastener(cv, x + w - 7f, y + h - 7f);
    }

    /** A display unit set in the body: its bezel, fasteners and glass. */
    private static void du(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 6f, y + 6f);
        NpHardware.fastener(cv, x + w - 6f, y + 6f);
        NpHardware.fastener(cv, x + 6f, y + h - 6f);
        NpHardware.fastener(cv, x + w - 6f, y + h - 6f);
        NpHardware.glass(cv, x + DU_B, y + DU_B, w - 2f * DU_B, h - 2f * DU_B);
    }

    /** The title row: where, or whose, then the tabs the mode offers here, each on its number. */
    private void titleRow(NpCanvas cv, float x, float y, float w, Hits hits) {
        cv.fillRound(x, y, w, ROW_H, 1f, NpColors.GLASS, 1f);
        String name = title(), sub = mode == Mode.STATION && boards.station.isEmpty() ? "" : mode.sub;
        float nx = x + 10f, base = y + 17f;
        float nw = name.isEmpty() ? 0f : cv.text(nx, base, name, Ui.V, true, NpColors.WHITE, 0.8f, NpCanvas.START);
        float sx = nx + (nw > 0f ? nw + 12f : 0f);
        float sw = sub.isEmpty() ? 0f : cv.text(sx, base, sub, Ui.LB, true, NpColors.DIM, 0.8f, NpCanvas.START);
        if (mode == Mode.BOARDING) return;
        float tx = Math.max(x + (mode == Mode.STATION ? 212f : 282f), sx + sw + 40f);
        for (int i = 0; i < tabs.size; i++) {
            Page p = tabs.get(i);
            float kx = tx;
            tx += Ui.capKey(cv, tx, y + 4f, String.valueOf(i + 1), 16f) + 7f;
            boolean on = p == page;
            float lw = cv.text(tx, base, tabName(p), Ui.LB, true, on ? NpColors.WHITE : NpColors.DIM, 0.8f, NpCanvas.START);
            if (on) cv.fill(tx, base + 3.25f, lw, 1.5f, NpColors.WHITE);
            hits.add(kx - 4f, y, tx + lw - kx + 8f, ROW_H, p, 0);
            tx += lw + 26f;
        }
    }

    private String title() {
        switch (mode) {
            case STATION:
                return boards.station.isEmpty() ? "SHIPYARD" : boards.station;
            case SHIP:
                return Fmt.caps(boards.crew.shipName);
            case BOARDING:
                return Fmt.caps(boards.boarding.craftName);
            case RECORD: {
                String r = boards.rank;
                int comma = r.indexOf(',');
                return Fmt.caps(comma > 0 ? r.substring(0, comma) : r.isEmpty() ? "Captain" : r);
            }
            case CONTROLS:
                return "CONTROLS";
            default:
                return "";
        }
    }

    private String tabName(Page p) {
        return p == help ? "KEYBOARD" : p.name;
    }

    /** The foot row: the feed's line under its source, and the keys that close the mode. */
    private void footRow(NpCanvas cv, float x, float y, float w, Hits hits) {
        cv.fillRound(x, y, w, ROW_H, 1f, NpColors.GLASS, 1f);
        float base = y + 17f, right = x + w - 8f;
        float closeX = right - cv.width("CLOSE", Ui.LB, true, 0.6f);
        cv.text(right, base, "CLOSE", Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
        float escX = closeX - 26f - Ui.capW(cv, "ESC");
        Ui.capKey(cv, escX, y + 4f, "ESC", 16f);
        float orW = cv.width("OR", Ui.LB, true, 0.4f), orX = escX - 6f - orW;
        cv.text(orX, base, "OR", Ui.LB, true, NpColors.DIM, 0.4f, NpCanvas.START);
        String key = closeKey();
        float kx = orX - 6f - Ui.capW(cv, key);
        Ui.capKey(cv, kx, y + 4f, key, 16f);
        hits.add(kx - 4f, y, right - kx + 8f, ROW_H, CLOSE, 0);
        if (!feed.showing()) return;
        float sx = x + 10f;
        float sw = cv.text(sx, base, Ui.fit(cv, feed.source, 140f, Ui.LB, true, 0.8f), Ui.LB, true, NpColors.DIM, 0.8f, NpCanvas.START);
        float tx = Math.max(x + 78f, sx + sw + 10f);
        cv.text(tx, base, Ui.fit(cv, feed.text, kx - 20f - tx, Ui.B, false, 0.2f), Ui.B, false, NpColors.WHITE, 0.2f, NpCanvas.START);
    }

    // ---- the page's units ------------------------------------------------------------------------

    /**
     * Lays the page out in units in the box: the log's filters over its list (C9), the crew's roster
     * beside its orders and prisoners, boarding's salvage beside the party's state and orders, the
     * controls in four columns (C10), and anything else in one unit.
     */
    private void plan(NpCanvas cv, float x, float y, float w, float h) {
        unitPool.addAll(units);
        units.clear();
        if (page == log) {
            int f = sectIndex("FILTERS");
            float ly = y;
            if (f >= 0) {
                Unit filters = unit(x, y, w, 0f);
                filters.sects.add(f);
                filters.h = Tables.want(cv, page, f, w - 2f * DU_B, style) + 2f * DU_B;
                ly = y + filters.h + GAP;
            }
            Unit list = unit(x, ly, w, y + h - ly);
            for (int i = 0; i < page.sects.size; i++) if (i != f) list.sects.add(i);
            list.acts = true;
            return;
        }
        if (page == crew || page == boarding) {
            float lw = Math.round((w - GAP) * (page == crew ? 0.66f : 0.56f));
            Unit left = unit(x, y, lw, h), right = unit(x + lw + GAP, y, w - lw - GAP, h);
            String main = page == crew ? "ABOARD" : "SALVAGE";
            for (int i = 0; i < page.sects.size; i++) (page.sects.get(i).title.equals(main) ? left : right).sects.add(i);
            left.acts = true;
            if (left.sects.size == 0 || right.sects.size == 0) {
                Unit only = left.sects.size == 0 ? right : left;
                units.removeValue(only == left ? right : left, true);
                only.x = x;
                only.w = w;
                only.acts = true;
            }
            return;
        }
        Unit u = unit(x, y, w, h);
        for (int i = 0; i < page.sects.size; i++) u.sects.add(i);
        u.columns = page == help ? 4 : 1;
        u.acts = true;
    }

    private Unit unit(float x, float y, float w, float h) {
        Unit u = unitPool.size > 0 ? unitPool.pop() : new Unit();
        u.x = x;
        u.y = y;
        u.w = w;
        u.h = h;
        u.sects.clear();
        u.columns = 1;
        u.acts = false;
        units.add(u);
        return u;
    }

    /** How tall unit {@code u} wants to be with every row of its parts showing. */
    private float wantOf(NpCanvas cv, Unit u) {
        if (u == null) return 0f;
        float gw = u.w - 2f * DU_B, h = 2f * DU_B + (u.acts && Tables.hasPageActs(page) ? ACTS_H : 0f);
        for (int i = 0; i < u.sects.size; i++) h += Tables.want(cv, page, u.sects.get(i), gw, style);
        return h;
    }

    private void drawUnits(NpCanvas cv, Camera cam, Hits hits) {
        for (int i = 0; i < units.size; i++) {
            Unit u = units.get(i);
            if (u.h < 2f * DU_B + 10f) continue;
            du(cv, u.x, u.y, u.w, u.h);
            float gx = u.x + DU_B, gy = u.y + DU_B, gw = u.w - 2f * DU_B, gh = u.h - 2f * DU_B;
            float actsH = u.acts && Tables.hasPageActs(page) ? ACTS_H : 0f;
            boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh);
            if (u.columns > 1) Tables.flow(cv, cam, page, u.sects, u.columns, gx, gy, gw, gh - actsH, 4f, keys, hits);
            else Tables.stack(cv, cam, page, u.sects, gx, gy, gw, gh - actsH, style, hits);
            if (actsH > 0f) Tables.pageActs(cv, cam, page, gx, gy + gh - actsH, gw, actsH, 132f, hits);
            if (clipped) cv.unclip();
        }
    }

    // ---- M3's rail ---------------------------------------------------------------------------------

    private float railWant() {
        float h = 2f * DU_B + 180f + Math.max(1, boards.tanks.size) * 19f;
        if (infirmaryLine()) h += 52f;
        return h;
    }

    private boolean infirmaryLine() {
        return boards.infirmary && boards.wounded > 0;
    }

    /** The rail: credits, standing with the station's owner, the hold with what is coming, the tanks (C7). */
    private void rail(NpCanvas cv, float gx, float gy, float gw, float gh) {
        TradeBoard t = boards.trade;
        float l = gx + 10f, r = gx + gw - 10f;
        Ui.lab(cv, l, gy + 24f, "CREDITS");
        Ui.valEnd(cv, r, gy + 24f, Fmt.credits(t.credits), "CR", Ui.V, NpColors.WHITE);
        sep(cv, gx, gy + 36f, gw);
        Ui.lab(cv, l, gy + 58f, "STANDING");
        cv.text(r, gy + 58f, boards.standing.isEmpty() ? "—" : boards.standing, Ui.V, true, NpColors.WHITE, 0f, NpCanvas.END);
        String who = !boards.owner.isEmpty() ? boards.owner : boards.standingWord;
        if (!who.isEmpty()) cv.text(l, gy + 76f, Ui.fit(cv, who, gw - 20f, Ui.LB, true, 0.4f), Ui.LB, true, NpColors.DIM, 0.4f, NpCanvas.START);
        sep(cv, gx, gy + 88f, gw);
        Ui.lab(cv, l, gy + 110f, "HOLD");
        if (t.hasHold && t.cargoCapacity > 0) {
            Ui.valEnd(cv, r, gy + 110f, Fmt.units(t.cargoUsed) + " OF " + Fmt.units(t.cargoCapacity), "M³", Ui.V, NpColors.WHITE);
            float bw = gw - 20f, share = (float) Math.min(1.0, t.cargoUsed / t.cargoCapacity);
            cv.fill(l, gy + 120f, bw, 8f, NpColors.TRACK);
            cv.fill(l, gy + 120f, bw * share, 8f, NpColors.WHITE);
            double coming = 0;
            for (int i = 0; i < t.lines.size; i++) coming += Math.max(0, t.lines.get(i).onOrder);
            if (coming >= 0.05) cv.text(l, gy + 146f, "+" + Fmt.units(coming) + " U ON ORDER", Ui.LB, true, NpColors.CYAN, 0.5f, NpCanvas.START);
            cv.text(r, gy + 146f, Fmt.units(Math.floor(t.freeCargo())) + " M³ FREE", Ui.LB, true, NpColors.DIM, 0.5f, NpCanvas.END);
        } else {
            cv.text(r, gy + 110f, t.hasHold || !t.isDocked() ? "—" : "NO HOLD", Ui.B, true, NpColors.DIM, 0.4f, NpCanvas.END);
        }
        sep(cv, gx, gy + 158f, gw);
        Ui.lab(cv, l, gy + 180f, "TANKS");
        float y = gy + 180f;
        if (boards.tanks.size == 0) cv.text(r, y, "—", Ui.B, true, NpColors.DIM, 0f, NpCanvas.END);
        for (int i = 0; i < boards.tanks.size && y < gy + gh - 8f; i++, y += 19f) {
            float f = boards.tankFull.get(i);
            cv.text(l + 82f, y, Ui.fit(cv, boards.tanks.get(i), r - l - 140f, Ui.B, false, 0.3f), Ui.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            cv.text(r, y, Fmt.percent(f), Ui.B, true, f < 0.25f ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.END);
        }
        if (infirmaryLine() && y + 40f < gy + gh) {
            sep(cv, gx, y - 7f, gw);
            Ui.lab(cv, l, y + 15f, "INFIRMARY");
            cv.text(r, y + 15f, boards.wounded + " HURT ABOARD", Ui.B, true, NpColors.AMBER, 0.3f, NpCanvas.END);
        }
    }

    private static void sep(NpCanvas cv, float gx, float y, float gw) {
        cv.fill(gx + 6f, y, gw - 12f, 1f, NpColors.GRATICULE);
    }
}
