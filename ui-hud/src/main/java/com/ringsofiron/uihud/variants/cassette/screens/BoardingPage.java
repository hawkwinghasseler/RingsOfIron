package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * Boarding and salvage (the classic {@code BoardingWindow}; Context's M4): the ship latched to on
 * flaps with how it stands, the boarding party, its defenders and the prisoners on drums; then one
 * list of the orders of the boarding's phase (board or storm before it, recall while the party is
 * across, none once it is taken, when the game refuses a recall), the parts to strip with their
 * value and state, the cargo to take, and the prisoners to bring aboard, each row's order on its
 * soft key. A part the link can't reach says so before it is marked ("EVA only", "latch on"), and
 * selected, gives the game's whole reason. A click on the part already selected marks it, or calls
 * its work off.
 *
 * <p>In a side panel (the slate's Boarding tab, Stations 10.4) the other ship's plate goes across the
 * top, its name on flaps, how it stands typed under it, and the drums and lamps in a row; the list
 * under it, a part whose state is too long for its column with the state on a second line.
 */
final class BoardingPage extends ListPage {
    /** Row kinds; in a side panel a part's long state is a row of its own under it ({@code DETAIL}). */
    private static final int HEAD = 0, BOARD = 1, STORM = 2, RECALL = 3, PART = 4, CARGO = 5, PRISONERS = 6, DETAIL = 7;
    private static final String[] HEADS = {"ORDERS", "PARTS TO STRIP", "CARGO", "PRISONERS"};
    private static final String[] KINDS = {"HEAD", "BOARD", "STORM", "RECALL", "PART", "CARGO", "PRISONERS", "DETAIL"};
    private static final float SIDE_W = 300f;
    /** In a side panel, the longest state that goes beside a part's value; a longer one takes the line under it. */
    static final int NARROW_STATE = 16;
    /** Whether it drew in a side panel last frame. */
    private boolean narrow;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();
    private final Array<Integer> byValue = new Array<>();
    private String name = "", nameCaps = "";
    /** The parts listed: those worth something, and any marked. */
    private int shownParts;
    private final String[] drums = new String[3];
    private final int[] drumAt = {-1, -1, -1};

    BoardingPage() {
        super("BOARDING", "BOARD", ClassicWindow.BOARDING);
    }

    private BoardingBoard board() {
        return b.boarding;
    }

    @Override
    boolean offered() {
        return b != null && board().isOpen();
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        BoardingBoard bb = board();
        if (!bb.craftName.equals(name)) {
            name = bb.craftName;
            nameCaps = name.toUpperCase(Locale.ROOT);
        }
        kinds.clear();
        refs.clear();
        // The orders of the boarding's phase: none once it is taken, or for a craft nobody defends,
        // which the first part marked or cargo taken takes
        if (bb.boarding) {
            add(HEAD, 0);
            add(RECALL, 0);
        } else if (!bb.taken && !bb.salvage) {
            add(HEAD, 0);
            add(BOARD, 0);
            add(STORM, 0);
        }
        shownParts = 0;
        if (bb.parts.size > 0 || bb.salvage) {
            add(HEAD, 1);
            mostValuableFirst(bb.parts, byValue);
            for (int i = 0; i < byValue.size; i++) {
                // A part worth nothing (an escape pod: the game puts no price on it) is left out, unless it is marked
                Part pt = bb.parts.get(byValue.get(i));
                if (pt.value <= 0 && !pt.marked) continue;
                add(PART, byValue.get(i));
                if (narrow && partState(pt).length() > NARROW_STATE) add(DETAIL, byValue.get(i));
                shownParts++;
            }
        }
        if (bb.cargo.size > 0) {
            add(HEAD, 2);
            for (int i = 0; i < bb.cargo.size; i++) add(CARGO, i);
        }
        if (b.crew.prisonersAlongside) {
            add(HEAD, 3);
            add(PRISONERS, 0);
        }
        keepSelection();
    }

    /** The list's rows, for tests: a heading by its title, a row by its kind ("ORDERS BOARD STORM"). */
    String outline() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kinds.size; i++) {
            if (sb.length() > 0) sb.append(' ');
            int k = kinds.get(i);
            sb.append(k == HEAD ? HEADS[refs.get(i)].replace(' ', '_') : KINDS[k]);
        }
        return sb.toString();
    }

    private void add(int kind, int ref) {
        kinds.add(kind);
        refs.add(ref);
    }

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) != HEAD && kinds.get(i) != DETAIL;
    }

    @Override
    boolean belongsAbove(int i) {
        return kinds.get(i) == DETAIL;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        if (narrow && rowNarrow(p, i, r, s)) return;
        BoardingBoard bb = board();
        int ref = refs.get(i);
        switch (kinds.get(i)) {
            case HEAD:
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 1) {
                    if (shownParts == 0) L.at(p, 18, r, "Nothing left to strip", DIM);
                    else {
                        L.right(p, 46, r, "VALUE", DIM);
                        L.at(p, 48, r, "STATE", DIM);
                    }
                } else if (ref == 2) {
                    L.right(p, 46, r, "UNITS", DIM);
                    L.at(p, 48, r, "COMING OVER", DIM);
                }
                return;
            case BOARD:
                put(p, s, 2, r, bb.byEva ? "Send the party across on EVA" : "Send the boarding party across", bb.boardRefusal.isEmpty());
                return;
            case STORM:
                put(p, s, 2, r, "Storm it, forcing every shut door", bb.stormRefusal.isEmpty());
                return;
            case RECALL:
                put(p, s, 2, r, "Call the party back", true);
                return;
            case PART: {
                Part pt = bb.parts.get(ref);
                // A part that can't be marked from here is dim, as one already off is
                Color ink = pt.off || !pt.marked && !pt.toggleable ? DIM : AMBER;
                String state = partState(pt);
                // Marked to be stripped: a drawn block at the row's left
                if (pt.marked) p.rect(L.cx(2) + 1f, L.base(r) - 9.5f, 6f, 7f, s ? Yard.CRT_BLACK : ink);
                if (s) {
                    L.rev(p, 4, r, Ledger.cut(pt.name, 30));
                    L.revRight(p, 46, r, Fmt.thousands(pt.value));
                    L.rev(p, 48, r, Ledger.cut(state, L.cols - 49));
                } else {
                    L.at(p, 4, r, Ledger.cut(pt.name, 30), ink);
                    L.right(p, 46, r, Fmt.thousands(pt.value), ink);
                    L.at(p, 48, r, Ledger.cut(state, L.cols - 49), ink);
                }
                return;
            }
            case CARGO: {
                Cargo c = bb.cargo.get(ref);
                if (s) {
                    L.rev(p, 2, r, Ledger.cut(c.commodity, 30));
                    L.revRight(p, 46, r, Fmt.units(c.units));
                    L.rev(p, 48, r, c.onOrder > 0.05 ? Fmt.units(c.onOrder) : "--");
                } else {
                    L.at(p, 2, r, Ledger.cut(c.commodity, 30), AMBER);
                    L.right(p, 46, r, Fmt.units(c.units), AMBER);
                    L.at(p, 48, r, c.onOrder > 0.05 ? Fmt.units(c.onOrder) : "--", AMBER);
                }
                return;
            }
            case PRISONERS:
                put(p, s, 2, r, "Bring its prisoners aboard", true);
                return;
            default:
        }
    }

    /**
     * A row in a side panel, 45 columns: a part's name to column 20, its value ending at 27 and its
     * state from 28, or on the line under it when longer; the cargo's units and what is coming over
     * in the same columns. False for a row that is drawn as across the screen: an order, the prisoners.
     */
    private boolean rowNarrow(YardPaint p, int i, int r, boolean s) {
        BoardingBoard bb = board();
        int ref = refs.get(i);
        switch (kinds.get(i)) {
            case HEAD:
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 1) {
                    if (shownParts == 0) L.at(p, 16, r, "Nothing left to strip", DIM);
                    else {
                        L.right(p, 27, r, "VALUE", DIM);
                        L.at(p, 28, r, "STATE", DIM);
                    }
                } else if (ref == 2) {
                    L.right(p, 27, r, "UNITS", DIM);
                    L.at(p, 28, r, "COMING OVER", DIM);
                }
                return true;
            case PART: {
                Part pt = bb.parts.get(ref);
                Color ink = pt.off || !pt.marked && !pt.toggleable ? DIM : AMBER;
                String state = partState(pt);
                if (pt.marked) p.rect(L.cx(2) + 1f, L.base(r) - 9.5f, 6f, 7f, s ? Yard.CRT_BLACK : ink);
                String name = Ledger.cut(pt.name, 16), value = Fmt.thousands(pt.value);
                String beside = state.length() > NARROW_STATE ? "" : state;
                if (s) {
                    L.rev(p, 4, r, name);
                    L.revRight(p, 27, r, value);
                    L.rev(p, 28, r, beside);
                } else {
                    L.at(p, 4, r, name, ink);
                    L.right(p, 27, r, value, ink);
                    L.at(p, 28, r, beside, ink);
                }
                return true;
            }
            case DETAIL: {
                // A part's state too long for its column, under its name
                Part pt = bb.parts.get(ref);
                String t = Ledger.cut(partState(pt), L.cols - 7);
                if (s) L.rev(p, 6, r, t);
                else L.at(p, 6, r, t, DIM);
                return true;
            }
            case CARGO: {
                Cargo c = bb.cargo.get(ref);
                String name = Ledger.cut(c.commodity, 18), units = Fmt.units(c.units), over = c.onOrder > 0.05 ? Fmt.units(c.onOrder) : "--";
                if (s) {
                    L.rev(p, 2, r, name);
                    L.revRight(p, 27, r, units);
                    L.rev(p, 28, r, over);
                } else {
                    L.at(p, 2, r, name, AMBER);
                    L.right(p, 27, r, units, AMBER);
                    L.at(p, 28, r, over, AMBER);
                }
                return true;
            }
            default:
                return false;
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, boolean live) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, live ? AMBER : DIM);
    }

    @Override
    void keys() {
        if (sel < 0) return;
        BoardingBoard bb = board();
        switch (kinds.get(sel)) {
            case BOARD:
                softKey("BOARD", bb.boardRefusal.isEmpty() ? null : Fmt.upper(bb.boardRefusal));
                return;
            case STORM:
                softKey("STORM", bb.stormRefusal.isEmpty() ? null : Fmt.upper(bb.stormRefusal));
                return;
            case RECALL:
                softKey("RECALL", null);
                return;
            case PART: {
                Part pt = bb.parts.get(refs.get(sel));
                softKey(pt.marked ? "CALL OFF" : "MARK", partRefusal(bb, pt));
                // In a side panel the strip under the keys has it whole
                if (!narrow) note(partNote(pt, L.cols - 49));
                return;
            }
            case CARGO:
                softKey("TAKE ALL", !bb.salvage && !bb.salvageRefusal.isEmpty() ? Fmt.upper(bb.salvageRefusal) : null);
                return;
            case PRISONERS:
                softKey("TAKE ABOARD", null);
                return;
            default:
        }
    }

    @Override
    void press(int k) {
        BoardingProvider provider = b.data == null ? null : b.data.boarding;
        if (provider == null || sel < 0) return;
        BoardingBoard bb = board();
        String said = null;
        switch (kinds.get(sel)) {
            case BOARD:
                said = provider.orderBoarding();
                break;
            case STORM:
                said = provider.storm();
                break;
            case RECALL:
                said = provider.recall();
                break;
            case PART: {
                Part pt = bb.parts.get(refs.get(sel));
                said = provider.toggleStrip(pt.x, pt.y);
                break;
            }
            case CARGO:
                said = provider.takeCargo(bb.cargo.get(refs.get(sel)).commodity);
                break;
            case PRISONERS:
                if (b.data.crew != null) said = b.data.crew.takePrisoners();
                break;
            default:
        }
        say(said);
    }

    /** A click on the part already selected gives its order: marks it, or calls its work off. */
    @Override
    public void hit(int code) {
        int i = code - ROW;
        if (code < KEY && i == sel && i >= 0 && i < kinds.size && kinds.get(i) == PART) {
            pressKey(0);
            return;
        }
        super.hit(code);
    }

    /**
     * A part's state in the list: before it is marked, "EVA only" or "latch on" for one the link
     * can't reach; then how it stands in the game's words ("marked", "stripping 40%").
     */
    static String partState(Part pt) {
        if (!pt.marked && !pt.reach.isEmpty()) return pt.reach;
        return pt.status.isEmpty() ? "--" : pt.status;
    }

    /**
     * What the soft row says of the selected part beside its key, in capitals, or null: before it
     * is marked, why one that can be marked will wait for an EVA; once marked, the game's word on
     * how it stands, whole, where the list's state column ({@code stateCols} wide) cuts it short.
     */
    static String partNote(Part pt, int stateCols) {
        if (!pt.marked) return pt.toggleable && !pt.reachWhy.isEmpty() ? Fmt.upper(pt.reachWhy) : null;
        return pt.status.length() > stateCols ? Fmt.upper(pt.status) : null;
    }

    /** Why a part's mark can't be set or called off now, in capitals; null when it can. */
    static String partRefusal(BoardingBoard bb, Part pt) {
        if (!bb.salvage && !bb.salvageRefusal.isEmpty()) return Fmt.upper(bb.salvageRefusal);
        if (pt.off) return "ALREADY OFF: IT IS BEING CARRIED HOME";
        if (!pt.toggleable) return Fmt.upper(pt.reachWhy.isEmpty() ? "out of reach from here" : pt.reachWhy);
        return null;
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean was = narrow;
        narrow = w < 700f;
        if (narrow != was && b != null) refresh(b);
        if (narrow) {
            drawNarrow(p, hits, x, y, w, h);
            return;
        }
        BoardingBoard bb = board();
        float bx = x + 8f, by = y + 6f, bh = h - 12f;
        // The other ship: its name, how it stands, the party and the prisoners
        BigConsole.bay(p, bx, by, SIDE_W, bh, "THE OTHER SHIP", false);
        float rx = bx + 16f;
        String f = nameCaps.length() > 26 ? nameCaps.substring(0, 26) : nameCaps;
        p.flaps(rx, by + 30f, f, Math.max(8, Math.min(26, f.length())), 10f, 20f, 14f, Yard.FLAP_TEXT);
        p.legend(bb.byEva ? "ALONGSIDE, NOT LATCHED" : "LATCHED", rx, by + 70f, Yard.INK);
        p.paper(rx, by + 82f, SIDE_W - 32f, 44f);
        String st = state(bb);
        // Two typed lines, broken at a word
        int cut = st.length() <= 34 ? st.length() : st.lastIndexOf(' ', 34);
        if (cut <= 0) cut = Math.min(34, st.length());
        p.typed(st.substring(0, cut), rx + 8f, by + 99f, 13f, Paper.INK);
        if (cut < st.length()) p.typed(Ledger.cut(st.substring(cut).trim(), 34), rx + 8f, by + 117f, 13f, Paper.INK);
        counter(p, 0, "PARTY ABOARD", bb.aboard, rx, by + 150f);
        counter(p, 1, "DEFENDERS", bb.defenders, rx, by + 200f);
        counter(p, 2, "PRISONERS", bb.prisoners, rx, by + 250f);
        p.lamp(rx + 6f, by + 312f, 4f, Yard.GREEN_LAMP, bb.taken);
        p.legend("TAKEN", rx + 18f, by + 316f, Yard.INK);
        p.lamp(rx + 106f, by + 312f, 4f, Yard.AMBER_LAMP, bb.boarding);
        p.legend("PARTY OUT", rx + 118f, by + 316f, Yard.INK);
        p.lamp(rx + 6f, by + 338f, 4f, Yard.AMBER_LAMP, bb.disabled);
        p.legend("DISABLED", rx + 18f, by + 342f, Yard.INK);
        p.lamp(rx + 106f, by + 338f, 4f, Yard.GREEN_LAMP, bb.salvage);
        p.legend("SALVAGE", rx + 118f, by + 342f, Yard.INK);
        // The list of orders, parts, cargo and prisoners
        float tx = bx + SIDE_W + 8f, tw = x + w - 8f - tx;
        BigConsole.bay(p, tx, by, tw, bh, null, false);
        int cols = Math.max(70, Ledger.colsIn(tw - 42f)), rows = Math.max(12, Ledger.rowsIn(bh - 40f));
        L.set(tx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, "BOARDING", Ledger.cut(nameCaps, 30));
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }

    /**
     * In a side panel: the other ship's plate across the top (its name on flaps, how it stands typed
     * on a strip, the party, the defenders and the prisoners on drums with the lamps beside them) and
     * the list under it, how the ship is held on its title row.
     */
    private void drawNarrow(YardPaint p, Hits hits, float x, float y, float w, float h) {
        BoardingBoard bb = board();
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        float rx = bx + 16f, rw = bw - 32f;
        int cells = Math.max(8, Math.min((int) (rw / 10f), nameCaps.length()));
        p.flaps(rx, by + 10f, nameCaps.length() > cells ? nameCaps.substring(0, cells) : nameCaps, cells, 10f, 20f, 14f, Yard.FLAP_TEXT);
        // How it stands, two typed lines broken at a word
        float py = by + 38f;
        p.paper(rx, py, rw, 42f);
        String st = state(bb);
        int per = Math.max(12, (int) ((rw - 16f) / 7.6f)), cut = CrewPage.breakAt(st, per);
        p.typed(st.substring(0, cut), rx + 8f, py + 17f, 13f, Paper.INK);
        if (cut < st.length()) p.typed(Ledger.cut(st.substring(cut).trim(), per), rx + 8f, py + 35f, 13f, Paper.INK);
        // The drums in a row, each as wide as its legend; the four lamps in two rows at the plate's right
        float cy = py + 62f, cx = rx;
        cx = counter(p, 0, "PARTY ABOARD", bb.aboard, cx, cy) + 12f;
        cx = counter(p, 1, "DEFENDERS", bb.defenders, cx, cy) + 12f;
        counter(p, 2, "PRISONERS", bb.prisoners, cx, cy);
        float col2 = rx + rw - 18f - Math.max(legendW(p, "PARTY OUT"), legendW(p, "SALVAGE"));
        float col1 = col2 - 12f - 18f - Math.max(legendW(p, "TAKEN"), legendW(p, "DISABLED"));
        lamp(p, col1, cy + 2f, Yard.GREEN_LAMP, bb.taken, "TAKEN");
        lamp(p, col2, cy + 2f, Yard.AMBER_LAMP, bb.boarding, "PARTY OUT");
        lamp(p, col1, cy + 26f, Yard.AMBER_LAMP, bb.disabled, "DISABLED");
        lamp(p, col2, cy + 26f, Yard.GREEN_LAMP, bb.salvage, "SALVAGE");
        // The list, and under its keys the selected row's whole word, typed: why a part waits, or can't be had
        float ty = cy + 42f, stripH = 44f;
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(by + bh - 34f - stripH - 6f - ty));
        L.set(bx + 12f, ty, cols, rows);
        L.under(p, false);
        title(p, "BOARDING", bb.byEva ? "ALONGSIDE, NOT LATCHED" : "LATCHED");
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        String word = word();
        if (!word.isEmpty()) {
            float sy = L.y + L.h + 30f;
            p.paper(L.x, sy, L.w, stripH);
            int per2 = Math.max(12, (int) ((L.w - 16f) / 7.6f)), at = CrewPage.breakAt(word, per2);
            p.typed(word.substring(0, at), L.x + 8f, sy + 17f, 13f, Paper.INK);
            if (at < word.length()) p.typed(Ledger.cut(word.substring(at).trim(), per2), L.x + 8f, sy + 35f, 13f, Paper.INK);
        }
    }

    private static float legendW(YardPaint p, String s) {
        return p.width(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.LEGEND, 12f, s);
    }

    /**
     * The selected row's whole word for the strip under the keys in a side panel, in the game's words:
     * why an order or a part can't be had now, why a part waits for an EVA or a latch, or how a marked
     * one's strip stands; "" for nothing more than its line says.
     */
    String word() {
        if (sel < 0 || sel >= kinds.size) return "";
        BoardingBoard bb = board();
        String w;
        switch (kinds.get(sel)) {
            case BOARD:
                w = bb.boardRefusal;
                break;
            case STORM:
                w = bb.stormRefusal;
                break;
            case PART: {
                Part pt = bb.parts.get(refs.get(sel));
                if (!bb.salvage && !bb.salvageRefusal.isEmpty()) w = bb.salvageRefusal;
                else if (pt.off && pt.status.isEmpty()) w = "Already off: it is being carried home";
                // Marked, or off: how it stands, where its line cuts it short
                else if (pt.marked || pt.off) w = pt.status.length() > L.cols - 7 ? pt.status : "";
                else w = pt.reachWhy.isEmpty() && !pt.toggleable ? "Out of reach from here" : pt.reachWhy;
                break;
            }
            case CARGO:
                w = bb.salvage ? "" : bb.salvageRefusal;
                break;
            default:
                w = "";
        }
        return w.isEmpty() ? "" : Fmt.capital(w);
    }

    /** A lamp with its legend after it, the legend's baseline at {@code y}. */
    private static void lamp(YardPaint p, float x, float y, Yard.Lamp lit, boolean on, String legend) {
        p.lamp(x + 6f, y - 4f, 4f, lit, on);
        p.legend(legend, x + 18f, y, Yard.INK);
    }

    /** A drum counter with its legend over it, its legend's baseline at {@code y}; returns where it ends. */
    private float counter(YardPaint p, int i, String legend, int n, float x, float y) {
        if (n != drumAt[i]) {
            drumAt[i] = n;
            drums[i] = String.valueOf(Math.max(0, Math.min(99, n)));
        }
        float lw = p.legend(legend, x, y, Yard.INK);
        p.drums(x, y + 8f, drums[i], 15f, 22f, 16f, false);
        return x + Math.max(lw, 32f);
    }

    /** The parts' indices, most valuable first, those still on the hull before those already off it. */
    static void mostValuableFirst(Array<Part> parts, Array<Integer> out) {
        out.clear();
        for (int i = 0; i < parts.size; i++) out.add(i);
        out.sort((x, y) -> {
            Part px = parts.get(x), py = parts.get(y);
            if (px.off != py.off) return px.off ? 1 : -1;
            return Double.compare(py.value, px.value);
        });
    }

    /**
     * How the other ship stands, in the game's words; a beaten craft nobody aboard defends needs no
     * party, since the first part marked or cargo taken takes it ({@code Boarding.takeUndefended}).
     */
    static String state(BoardingBoard bb) {
        if (bb.salvage && !bb.taken && !bb.boarding && bb.defenders == 0 && bb.disabled) {
            return "Nobody aboard: the first part marked or cargo taken takes it";
        }
        return bb.state;
    }
}
