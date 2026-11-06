package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The station terminal's Market (functional-context 10.3; mockup 11.10): today's trade window as a
 * table on the grid, a commodity a row with its stock, the prices to buy and sell at, what the hold
 * has and what the loaders are still carrying, the trade keys under it acting on the focused row, and
 * under the table the deliveries held that this market supplies.
 */
final class MarketTab extends ListTab {
    static final int CELLS = 85;
    static final int BUY_1 = 0, BUY_BULK = 1, SELL_1 = 2, SELL_ALL = 3;
    static final int BULK = 10;
    /** The pane's parts, from its top: the units line, the heads, the list. */
    static final float HEADS = 35f, LIST = 42f;

    private final Glass.Cols cols = new Glass.Cols();
    private final Array<String> rows = new Array<>(), ids = new Array<>();
    private final Array<String> deliveries = new Array<>();
    private TradeBoard board;
    private String note = "";
    private float paneH;

    MarketTab() {
        super("MARKET", ClassicWindow.TRADE, "T");
        keys.define(4, 6);
        keys.set(BUY_1, "BUY 1", false).set(BUY_BULK, "BUY " + BULK, false).set(SELL_1, "SELL 1", false).set(SELL_ALL, "SELL ALL", false);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.trade.isDocked();
    }

    @Override
    void refresh(Boards b, HudData d) {
        board = b.trade;
        String focusId = pages.focus() >= 0 && pages.focus() < ids.size ? ids.get(pages.focus()) : null;
        rows.clear();
        ids.clear();
        for (int i = 0; i < board.lines.size; i++) {
            TradeLine l = board.lines.get(i);
            rows.add(line(l));
            ids.add(l.commodityId);
        }
        deliveries.clear();
        deliveries.addAll(b.deliveries);
        note = !board.hasHold ? "THIS SHIP HAS NO CARGO HOLD" : "PRICES IN CR, AMOUNTS IN M3";
        pages.set(rows.size, shown);
        if (focusId != null) {
            int at = ids.indexOf(focusId, false);
            if (at >= 0) pages.focus(at);
        }
        focusMoved();
    }

    private String line(TradeLine l) {
        String oo = Math.abs(l.onOrder) < 0.05 ? "-" : (l.onOrder > 0 ? "IN " : "OUT ") + Glass.units(Math.abs(l.onOrder));
        return cols.clear().left(l.name.toUpperCase(Locale.ROOT), 28).right(Glass.units(l.available), 10)
                .right(l.available > 0 ? price(l.buyPrice) : "-", 10).right(l.stationBuys ? price(l.sellPrice) : "-", 10)
                .right(Glass.units(l.held), 10).gap(4).left(oo, 12).toString();
    }

    /** A price: whole credits, or one decimal under a hundred when it isn't whole. */
    static String price(double p) {
        if (p < 100 && Math.abs(p - Math.rint(p)) > 0.05) return String.format(Locale.ROOT, "%.1f", p);
        return Glass.thousands(p);
    }

    /** The pane's height for {@code n} rows. */
    private float paneHeight(int n) {
        float body = LIST + Math.max(1, n) * Glass.ROW + 8f;
        if (deliveries.size > 0) body += 20f + 20f * deliveries.size;
        return body + 32f;
    }

    @Override
    float wantedHeight(float w) {
        return paneHeight(rows.size) + Glass.KEYS;
    }

    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        paneH = h - Glass.KEYS;
        // As many rows as the glass holds, the rest on further pages
        float room = paneH - (paneHeight(0) - Glass.ROW);
        shown = Math.max(1, (int) Math.floor(room / Glass.ROW + 1e-3f));
        listX = x + 12f;
        listY = y + LIST;
        listCells = CELLS;
        pages.set(rows.size, shown);
        keys.place(x + 8f, y + paneH + Glass.KEY_GAP, w - 16f, y + paneH - 7f * Glass.P - 6f);
    }

    @Override
    void keysFor(int focus) {
        TradeLine l = focus >= 0 && board != null && focus < board.lines.size ? board.lines.get(focus) : null;
        boolean buy = l != null && canBuy(board, l), sell = l != null && l.stationBuys && l.held > 1e-6;
        keys.set(BUY_1, "BUY 1", buy).set(BUY_BULK, "BUY " + BULK, buy).set(SELL_1, "SELL 1", sell).set(SELL_ALL, "SELL ALL", sell);
    }

    /** The station has stock, the hold has room and the player has credits, as the trade window has it. */
    static boolean canBuy(TradeBoard board, TradeLine line) {
        return board.hasHold && line.available > 1e-6 && board.freeCargo() > 1e-6 && board.credits > 1e-6;
    }

    @Override
    void press(int k, int focus) {
        HudData d = modes.data();
        if (d == null || d.trade == null || board == null || focus < 0 || focus >= board.lines.size) return;
        TradeLine l = board.lines.get(focus);
        String id = l.commodityId, name = l.name;
        boolean buying = k == BUY_1 || k == BUY_BULK;
        double units = k == BUY_1 || k == SELL_1 ? 1 : k == BUY_BULK ? BULK : Double.MAX_VALUE;
        double credits = board.credits;
        boolean carried = board.carried;
        // Why a buy may fill nothing, read off the board as it is before the order
        String refusal = buying ? buyRefusal(board, l) : "";
        double done = buying ? d.trade.buy(id, units) : d.trade.sell(id, units);
        modes.refresh();
        if (done <= 0) {
            TradeLine now = modes.boards.line(id);
            modes.say("STATION", buying ? "Can't buy " + name + ": " + refusal
                    : now != null && now.onOrder < -1e-6 ? "All the " + name + " is on its way off already" : "The station won't take " + name);
        } else if (carried) {
            modes.say("STATION", String.format(Locale.ROOT, "Ordered %s %s: the loaders are carrying it %s", Glass.units(done), name,
                    buying ? "aboard" : "off"));
        } else {
            modes.say("STATION", String.format(Locale.ROOT, "%s %s %s for %s cr", buying ? "Bought" : "Sold", Glass.units(done), name,
                    Glass.thousands(Math.abs(modes.boards.trade.credits - credits))));
        }
    }

    /** Why a buy filled nothing, from the board before it (the trade window's reasons). */
    static String buyRefusal(TradeBoard board, TradeLine line) {
        if (!board.hasHold) return "this ship has no cargo hold";
        if (line.available <= 1e-6) return "the station has none to sell";
        if (board.credits <= 1e-6) return "no credits";
        if (board.freeCargo() <= 1e-6) return "the hold is full";
        if (line.onOrder > 1e-6) return "all that fits is on order already";
        return "no free cargo bay for it (each bay holds one commodity)";
    }

    @Override
    void paint(Filament f, float time) {
        Glass.pane(f, x, y, w, paneH, null);
        keys.paint(f);
        float x0 = x + 12f, x1 = x + w - 12f;
        Glass.dim(f, x0, y + 8f, note);
        if (pages.pages() > 1) {
            float pw = Glass.pagerWidth(f, pages.page(), pages.pages());
            Glass.pager(f, x1 - pw, y + 8f, pages.page(), pages.pages());
        }
        float hy = y + HEADS;
        Glass.label(f, x0 + Glass.CW, hy, "COMMODITY");
        head(f, x0, 39, hy, "STOCK");
        head(f, x0, 49, hy, "BUY");
        head(f, x0, 59, hy, "SELL");
        head(f, x0, 69, hy, "HELD");
        Glass.label(f, x0 + 73 * Glass.CW, hy, "ON ORDER");
        int n = Math.max(1, pages.end() - pages.first());
        Glass.cells(f, x0, listY, CELLS, n * 1.5f);
        if (rows.size == 0) {
            Glass.text(f, x0 + Glass.CW, listY, "THE MARKET HAS NOTHING LISTED", Drive.DIM);
        }
        for (int i = pages.first(); i < pages.end(); i++) {
            Glass.row(f, x0, rowY(i), rows.get(i), CELLS, i == pages.focus(), Drive.NORM);
        }
        if (deliveries.size > 0) {
            float cy = listY + n * Glass.ROW + 8f;
            Glass.head(f, x0, cy, x1, "FOR YOUR CONTRACTS");
            for (int i = 0; i < deliveries.size; i++) {
                Glass.text(f, x0, cy + 20f + 20f * i, Glass.cut(deliveries.get(i), Glass.cells(x1 - x0)));
            }
        }
    }

    /** A column's head, right-aligned to the end of cell {@code endCell - 1}. */
    private static void head(Filament f, float x0, int endCell, float y, String t) {
        Glass.labelRight(f, x0 + endCell * Glass.CW - Glass.P, y, t);
    }
}
