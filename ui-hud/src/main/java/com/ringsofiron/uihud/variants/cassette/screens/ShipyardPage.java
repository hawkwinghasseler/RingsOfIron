package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.ShipyardProvider.Ship;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * Ships for sale and a prize in tow (the classic {@code ShipyardWindow}). At a station that sells
 * ships, Context's M3 Shipyard: the ship flown and its trade-in, each ship on sale with its terms
 * (or why it can't be bought yet), and the prize in tow. Away from one, the prize alone, on the
 * ship's console. Buying a ship and handing a prize over can't be undone, so their keys are guarded.
 */
final class ShipyardPage extends ListPage {
    private static final int HEAD = 0, CURRENT = 1, SHIP = 2, PRIZE = 3;
    private static final String[] HEADS = {"YOUR SHIP", "FOR SALE", "PRIZE IN TOW"};
    /** True on the ship's console (the prize away from a shipyard), false on a station's. */
    private final boolean prizeOnly;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();

    ShipyardPage(boolean prizeOnly) {
        super(prizeOnly ? "PRIZE" : "SHIPYARD", prizeOnly ? "PRIZE" : "SHIPS", ClassicWindow.SHIPYARD);
        this.prizeOnly = prizeOnly;
    }

    private ShipyardProvider.Yard yard() {
        return b.shipyard;
    }

    @Override
    boolean offered() {
        if (b == null) return false;
        return prizeOnly ? yard().stationName.isEmpty() && !yard().prize.isEmpty() : !yard().stationName.isEmpty();
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        ShipyardProvider.Yard y = yard();
        kinds.clear();
        refs.clear();
        if (!prizeOnly) {
            if (!y.current.isEmpty()) {
                add(HEAD, 0);
                add(CURRENT, 0);
            }
            add(HEAD, 1);
            for (int i = 0; i < y.ships.size; i++) add(SHIP, i);
        }
        if (!y.prize.isEmpty()) {
            add(HEAD, 2);
            add(PRIZE, 0);
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
        int k = kinds.get(i);
        return k == SHIP || k == PRIZE;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        ShipyardProvider.Yard y = yard();
        switch (kinds.get(i)) {
            case HEAD:
                L.at(p, 1, r, HEADS[refs.get(i)], DIM);
                if (refs.get(i) == 1) {
                    if (y.ships.size == 0) L.at(p, 27, r, "Nothing on sale here", DIM);
                    else {
                        L.at(p, 27, r, "SELLER", DIM);
                        L.at(p, 52, r, "TERMS", DIM);
                    }
                }
                return;
            case CURRENT:
                L.at(p, 2, r, Ledger.cut(y.current, L.cols - 4), AMBER);
                return;
            case SHIP: {
                Ship sh = y.ships.get(refs.get(i));
                String terms = sh.locked.isEmpty() ? sh.terms : sh.locked;
                if (s) {
                    L.rev(p, 2, r, Ledger.cut(sh.name, 24));
                    L.rev(p, 27, r, Ledger.cut(sh.seller, 24));
                    L.rev(p, 52, r, Ledger.cut(terms, L.cols - 54));
                } else {
                    L.at(p, 2, r, Ledger.cut(sh.name, 24), AMBER);
                    L.at(p, 27, r, Ledger.cut(sh.seller, 24), AMBER);
                    L.at(p, 52, r, Ledger.cut(terms, L.cols - 54), sh.locked.isEmpty() ? AMBER : DIM);
                }
                return;
            }
            case PRIZE: {
                String terms = (y.sellTerms.isEmpty() ? "" : "Sell " + y.sellTerms) + (y.turnInTerms.isEmpty() ? "" : (y.sellTerms.isEmpty() ? "" : "  ") + "Turn in " + y.turnInTerms);
                if (s) {
                    L.rev(p, 2, r, Ledger.cut(y.prize, 48));
                    L.rev(p, 52, r, Ledger.cut(terms, L.cols - 54));
                } else {
                    L.at(p, 2, r, Ledger.cut(y.prize, 48), AMBER);
                    L.at(p, 52, r, Ledger.cut(terms, L.cols - 54), AMBER);
                }
                return;
            }
            default:
        }
    }

    @Override
    void keys() {
        if (sel < 0) return;
        ShipyardProvider.Yard y = yard();
        if (kinds.get(sel) == SHIP) {
            Ship sh = y.ships.get(refs.get(sel));
            guardedKey("BUY", sh.locked.isEmpty() ? null : Fmt.upper(sh.locked));
        } else if (kinds.get(sel) == PRIZE) {
            guardedKey("SELL", y.sellLocked.isEmpty() ? null : Fmt.upper(y.sellLocked));
            guardedKey("TURN IN", y.turnInLocked.isEmpty() ? null : Fmt.upper(y.turnInLocked));
        }
    }

    @Override
    void press(int k) {
        ShipyardProvider provider = b.data == null ? null : b.data.shipyard;
        if (provider == null || sel < 0) return;
        String said;
        if (kinds.get(sel) == SHIP) said = provider.buy(yard().ships.get(refs.get(sel)).id);
        else said = k == 0 ? provider.sellPrize() : provider.turnInPrize();
        say(said);
    }

    @Override
    String empty() {
        return "NOTHING TO SHOW";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean liv = !prizeOnly;
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        int cols = Math.max(90, Ledger.colsIn(bw - 42f)), rows = Math.max(12, Ledger.rowsIn(bh - 40f));
        L.set(bx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, prizeOnly ? "PRIZE IN TOW" : "SHIPYARD", null);
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
