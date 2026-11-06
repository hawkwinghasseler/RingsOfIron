package com.ringsofiron.uihud.variants.cassette.screens;

import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The station's market (Context's M3 Market, the classic {@code TradeWindow}): a row a commodity with
 * the stock, both prices, what the hold carries and what is on its way, and the four trade keys on
 * the selected row. Each trade's result prints on the trade printer's roll, this visit's trades
 * with the newest at the tear bar.
 */
final class MarketPage extends ListPage {
    static final int BULK = 10;
    private Boards b;
    private final Printer printer = new Printer("TRADE PRINTER", 7);
    private String visit = "";

    MarketPage() {
        super("MARKET", "TRADE", ClassicWindow.TRADE);
    }

    private TradeBoard board() {
        return b.trade;
    }

    @Override
    boolean offered() {
        return b != null && board().isDocked();
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        if (!board().isDocked()) {
            visit = "";
            printer.clear();
        } else if (!board().stationName.equals(visit)) {
            visit = board().stationName;
            printer.clear();
        }
        keepSelection();
    }

    @Override
    int count() {
        return b == null ? 0 : board().lines.size;
    }

    private TradeLine line() {
        return sel >= 0 && sel < count() ? board().lines.get(sel) : null;
    }

    @Override
    String empty() {
        return board().hasHold ? "NOTHING ON THE BOARD" : "THIS SHIP HAS NO CARGO HOLD";
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        TradeLine l = board().lines.get(i);
        if (s) {
            L.rev(p, 1, r, Ledger.cut(l.name, 26));
            L.revRight(p, 38, r, l.available > 1e-6 ? Fmt.units(l.available) : "--");
            L.revRight(p, 50, r, l.available > 1e-6 ? Fmt.price(l.buyPrice) : "--");
            L.revRight(p, 62, r, l.stationBuys ? Fmt.price(l.sellPrice) : "--");
            L.revRight(p, 72, r, Fmt.units(l.held));
            L.revRight(p, 84, r, onOrder(l));
        } else {
            L.at(p, 1, r, Ledger.cut(l.name, 26), AMBER);
            L.right(p, 38, r, l.available > 1e-6 ? Fmt.units(l.available) : "--", AMBER);
            L.right(p, 50, r, l.available > 1e-6 ? Fmt.price(l.buyPrice) : "--", AMBER);
            L.right(p, 62, r, l.stationBuys ? Fmt.price(l.sellPrice) : "--", AMBER);
            L.right(p, 72, r, Fmt.units(l.held), AMBER);
            L.right(p, 84, r, onOrder(l), AMBER);
        }
    }

    private static String onOrder(TradeLine l) {
        if (Math.abs(l.onOrder) < 0.05) return "--";
        return (l.onOrder > 0 ? "+" : "-") + Fmt.units(Math.abs(l.onOrder));
    }

    @Override
    void keys() {
        TradeLine l = line();
        String buy = l == null ? "NOTHING SELECTED" : canBuy(l) ? null : Fmt.upper(buyRefusal(l));
        String sell = l == null ? "NOTHING SELECTED" : canSell(l) ? null : sellRefusal(l);
        softKey("BUY 1", buy);
        softKey("BUY " + BULK, buy);
        softKey("SELL 1", sell);
        softKey("SELL ALL", sell);
    }

    @Override
    void press(int k) {
        TradeLine l = line();
        TradeProvider provider = b.data == null ? null : b.data.trade;
        if (l == null || provider == null) return;
        boolean buying = k < 2;
        double units = k == 0 || k == 2 ? 1 : k == 1 ? BULK : Double.MAX_VALUE;
        String id = l.commodityId, name = l.name, refusal = buyRefusal(l);
        double creditsWas = board().credits;
        boolean carried = board().carried, offAlready = l.onOrder < -1e-6;
        double done = buying ? provider.buy(id, units) : provider.sell(id, units);
        String said;
        if (done <= 0) {
            said = buying ? "Can't buy " + name.toLowerCase(Locale.ROOT) + ": " + refusal.toLowerCase(Locale.ROOT)
                    : offAlready ? "All the " + name.toLowerCase(Locale.ROOT) + " is on its way off already"
                    : "The station won't take " + name.toLowerCase(Locale.ROOT);
        } else if (carried) {
            said = String.format(Locale.ROOT, "Ordered %s %s: the loaders are carrying it %s", Fmt.units(done),
                    name.toLowerCase(Locale.ROOT), buying ? "aboard" : "off");
        } else {
            // The market's board again, for what the trade came to
            TradeBoard t = board();
            t.clear();
            provider.collect(t);
            said = String.format(Locale.ROOT, "%s %s %s for %s", buying ? "Bought" : "Sold", Fmt.units(done),
                    name.toLowerCase(Locale.ROOT), Fmt.credits(Math.abs(t.credits - creditsWas)));
        }
        if (!printer.started()) printer.start(Fmt.upper(visit) + " MARKET  " + Fmt.stamp(b.data.ctx.simSeconds));
        printer.print(said);
        say(said);
    }

    private boolean canBuy(TradeLine l) {
        TradeBoard t = board();
        return t.hasHold && l.available > 1e-6 && t.freeCargo() > 1e-6 && t.credits > 1e-6;
    }

    /**
     * Why a buy fills nothing, as the classic market says it. With room left over, the hold had no bay
     * free for this commodity: each cargo bay carries one.
     */
    private String buyRefusal(TradeLine l) {
        TradeBoard t = board();
        if (!t.hasHold) return "This ship has no cargo hold";
        if (l.available <= 1e-6) return "The station has none to sell";
        if (t.credits <= 1e-6) return "No credits";
        if (t.freeCargo() <= 1e-6) return "The hold is full";
        if (l.onOrder > 1e-6) return "All that fits is on order already";
        return "No free cargo bay for it";
    }

    private static boolean canSell(TradeLine l) {
        return l.stationBuys && l.held > 1e-6;
    }

    private static String sellRefusal(TradeLine l) {
        if (!l.stationBuys) return "THE STATION DOESN'T BUY IT";
        if (l.onOrder < -1e-6) return "IT IS ALL ON ITS WAY OFF";
        return "NONE HELD";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, null, true);
        int cols = Math.max(90, Ledger.colsIn(bw - 42f)), rows = Math.max(12, Ledger.rowsIn(bh - 322f));
        L.set(bx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, "MARKET", "PRICES IN CR A UNIT");
        L.at(p, 1, 2, "COMMODITY", DIM);
        L.right(p, 38, 2, "STOCK", DIM);
        L.right(p, 50, 2, "BUY AT", DIM);
        L.right(p, 62, 2, "SELL AT", DIM);
        L.right(p, 72, 2, "HELD", DIM);
        L.right(p, 84, 2, "ON ORDER", DIM);
        L.rule(p, 2);
        drawList(p, hits, 3, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        // The roll prints under the keys, at the bay's right
        float pw = Math.min(470f, bw - 480f);
        printer.draw(p, bx + bw - pw - 15f, L.y + L.h + 40f, pw, BigConsole.LIV_INK2);
    }

    @Override
    boolean jewel() {
        return false;
    }
}
