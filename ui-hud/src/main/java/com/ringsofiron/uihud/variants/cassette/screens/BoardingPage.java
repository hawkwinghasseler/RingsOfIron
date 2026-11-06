package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
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
 * list of the orders (board, storm, recall), the parts to strip with their value and state, the
 * cargo to take, and the prisoners to bring aboard, each row's order on its soft key.
 */
final class BoardingPage extends ListPage {
    private static final int HEAD = 0, BOARD = 1, STORM = 2, RECALL = 3, PART = 4, CARGO = 5, PRISONERS = 6;
    private static final String[] HEADS = {"ORDERS", "PARTS TO STRIP", "CARGO", "PRISONERS"};
    private static final float SIDE_W = 300f;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();
    private String name = "", nameCaps = "";
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
    void refresh(Boards boards) {
        b = boards;
        BoardingBoard bb = board();
        if (!bb.craftName.equals(name)) {
            name = bb.craftName;
            nameCaps = name.toUpperCase(Locale.ROOT);
        }
        kinds.clear();
        refs.clear();
        add(HEAD, 0);
        add(BOARD, 0);
        add(STORM, 0);
        add(RECALL, 0);
        if (bb.parts.size > 0 || bb.salvage) {
            add(HEAD, 1);
            for (int i = 0; i < bb.parts.size; i++) add(PART, i);
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
        return kinds.get(i) != HEAD;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        BoardingBoard bb = board();
        int ref = refs.get(i);
        switch (kinds.get(i)) {
            case HEAD:
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 1) {
                    if (bb.parts.size == 0) L.at(p, 18, r, "Nothing left to strip", DIM);
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
                put(p, s, 2, r, bb.boarding ? "Call the party back" : "Call the party back (none out)", bb.boarding);
                return;
            case PART: {
                Part pt = bb.parts.get(ref);
                Color ink = pt.off ? DIM : AMBER;
                // Marked to be stripped: a drawn block at the row's left
                if (pt.marked) p.rect(L.cx(2) + 1f, L.base(r) - 9.5f, 6f, 7f, s ? Yard.CRT_BLACK : ink);
                if (s) {
                    L.rev(p, 4, r, Ledger.cut(pt.name, 30));
                    L.revRight(p, 46, r, Fmt.thousands(pt.value));
                    L.rev(p, 48, r, Ledger.cut(pt.status.isEmpty() ? "--" : pt.status, L.cols - 49));
                } else {
                    L.at(p, 4, r, Ledger.cut(pt.name, 30), ink);
                    L.right(p, 46, r, Fmt.thousands(pt.value), ink);
                    L.at(p, 48, r, Ledger.cut(pt.status.isEmpty() ? "--" : pt.status, L.cols - 49), ink);
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
                softKey("RECALL", bb.boarding ? null : "NO PARTY OUT");
                return;
            case PART: {
                Part pt = bb.parts.get(refs.get(sel));
                String why = !bb.salvage && !bb.salvageRefusal.isEmpty() ? Fmt.upper(bb.salvageRefusal)
                        : pt.off ? "IT IS OFF ALREADY" : !pt.toggleable ? "OUT OF REACH FROM HERE" : null;
                softKey(pt.marked ? "UNMARK" : "MARK", why);
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

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        BoardingBoard bb = board();
        float bx = x + 8f, by = y + 6f, bh = h - 12f;
        // The other ship: its name, how it stands, the party and the prisoners
        BigConsole.bay(p, bx, by, SIDE_W, bh, "THE OTHER SHIP", false);
        float rx = bx + 16f;
        String f = nameCaps.length() > 26 ? nameCaps.substring(0, 26) : nameCaps;
        p.flaps(rx, by + 30f, f, Math.max(8, Math.min(26, f.length())), 10f, 20f, 14f, Yard.FLAP_TEXT);
        p.legend(bb.byEva ? "ALONGSIDE, NOT LATCHED" : "LATCHED", rx, by + 70f, Yard.INK);
        p.paper(rx, by + 82f, SIDE_W - 32f, 44f);
        String st = bb.state.isEmpty() ? "" : bb.state;
        p.typed(Ledger.cut(st, 34), rx + 8f, by + 99f, 13f, Paper.INK);
        if (st.length() > 34) p.typed(Ledger.cut(st.substring(Math.min(34, st.length())), 34), rx + 8f, by + 117f, 13f, Paper.INK);
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

    private void counter(YardPaint p, int i, String legend, int n, float x, float y) {
        if (n != drumAt[i]) {
            drumAt[i] = n;
            drums[i] = String.valueOf(Math.max(0, Math.min(99, n)));
        }
        p.legend(legend, x, y, Yard.INK);
        p.drums(x, y + 8f, drums[i], 15f, 22f, 16f, false);
    }
}
