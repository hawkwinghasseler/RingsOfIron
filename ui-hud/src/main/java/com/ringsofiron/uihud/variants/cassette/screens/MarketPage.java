package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The station's market (Context's M3 Market, the classic {@code TradeWindow}): a row a commodity with
 * the stock, both prices, what the hold carries and what is on its way, and the four trade keys on
 * the selected row. Each trade's result prints on the trade printer's roll, this visit's trades
 * with the newest at the tear bar.
 *
 * <p>In a side panel (the Stations spec's Station link, 10.3) it is the station's terminal: the rows
 * with the stock, both prices and what is held, then "Not traded here", the hold's cargo this station
 * neither makes nor uses; under the tube one quantity control (1, 10, all) as interlocked keys, the
 * trade's total on drums before it is made, and BUY and SELL push-lights, Enter on the one lit.
 */
final class MarketPage extends ListPage {
    static final int BULK = 10;
    /** The quantity control's keys and the trade's: hit codes from {@code OWN}. */
    static final int Q1 = 0, Q10 = 1, QALL = 2, BUY = 3, SELL = 4;
    private static final String[] QTY = {"1", "10", "ALL"};
    private Boards b;
    private final Printer printer = new Printer("TRADE PRINTER", 7);
    private String visit = "";
    /** Whether it drew in a side panel last frame, so its keys are the terminal's. */
    private boolean narrow;
    /** The terminal's quantity ({@code Q1}, {@code Q10} or {@code QALL}) and the control its keys are on. */
    int qty = Q1, focus = -1;
    /** The hold's goods this station doesn't quote, for "Not traded here". */
    final Array<CargoPage.Line> notTraded = new Array<>();
    private final Array<CargoPage.Line> held = new Array<>(), heldPool = new Array<>();

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
    boolean fitsSide() {
        return true;
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
            qty = Q1;
            focus = -1;
        }
        readNotTraded();
        keepSelection();
    }

    /** The hold's goods whose commodity the station's board doesn't list: it neither makes nor uses them. */
    private void readNotTraded() {
        notTraded.clear();
        Entity ship = b.data == null ? null : b.data.ctx.ship;
        CargoHoldComponent hold = ship == null ? null : ship.getComponent(CargoHoldComponent.class);
        CargoPage.read(hold, board(), held, heldPool);
        for (int i = 0; i < held.size; i++) {
            CargoPage.Line l = held.get(i);
            if (!l.part && !quoted(l.id)) notTraded.add(l);
        }
    }

    private boolean quoted(String id) {
        Array<TradeLine> lines = board().lines;
        for (int i = 0; i < lines.size; i++) if (lines.get(i).commodityId.equals(id)) return true;
        return false;
    }

    @Override
    int count() {
        if (b == null) return 0;
        int n = board().lines.size;
        return narrow && notTraded.size > 0 ? n + 1 + notTraded.size : n;
    }

    @Override
    boolean selectable(int i) {
        return b != null && i < board().lines.size;
    }

    private TradeLine line() {
        return sel >= 0 && sel < board().lines.size ? board().lines.get(sel) : null;
    }

    @Override
    String empty() {
        return board().hasHold ? "NOTHING ON THE BOARD" : "THIS SHIP HAS NO CARGO HOLD";
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        if (narrow) {
            rowNarrow(p, i, r, s);
            return;
        }
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

    /** The terminal's columns, from the tube's right: what is held, sell, buy and the stock. */
    private int cHeld() {
        return L.cols - 1;
    }

    private int cSell() {
        return L.cols - 9;
    }

    private int cBuy() {
        return L.cols - 16;
    }

    private int cStock() {
        return L.cols - 23;
    }

    /** A row of the terminal: a commodity, the heading of the hold's goods not traded here, or one of them. */
    private void rowNarrow(YardPaint p, int i, int r, boolean s) {
        int n = board().lines.size, nameRoom = cStock() - 7;
        if (i >= n) {
            if (i == n) {
                L.at(p, 1, r, "NOT TRADED HERE", DIM);
                L.right(p, cHeld(), r, "HELD", DIM);
                return;
            }
            CargoPage.Line c = notTraded.get(i - n - 1);
            L.at(p, 2, r, Ledger.cut(Fmt.upper(c.name), nameRoom - 1), DIM);
            L.right(p, cHeld(), r, Fmt.units(c.units), DIM);
            return;
        }
        TradeLine l = board().lines.get(i);
        // The stock in whole units, as the narrow column has room for: what a buy of 1 or 10 draws on
        String name = Ledger.cut(Fmt.upper(l.name), nameRoom), stock = l.available > 1e-6 ? wholeUnits(l.available) : "--";
        String buy = l.available > 1e-6 ? Fmt.price(l.buyPrice) : "--", sell = l.stationBuys ? Fmt.price(l.sellPrice) : "--";
        String hold = heldText(l);
        if (s) {
            L.rev(p, 1, r, name);
            L.revRight(p, cStock(), r, stock);
            L.revRight(p, cBuy(), r, buy);
            L.revRight(p, cSell(), r, sell);
            L.revRight(p, cHeld(), r, hold);
        } else {
            L.at(p, 1, r, name, AMBER);
            L.right(p, cStock(), r, stock, AMBER);
            L.right(p, cBuy(), r, buy, AMBER);
            L.right(p, cSell(), r, sell, AMBER);
            L.right(p, cHeld(), r, hold, AMBER);
        }
    }

    /** Units to the whole unit, a part of one as "<1": "935" for 935.6. */
    static String wholeUnits(double u) {
        return u < 0.95 ? "<1" : Fmt.thousands(Math.floor(u + 0.05));
    }

    /** What is held, and what is on its way aboard after it: "4+6". */
    private static String heldText(TradeLine l) {
        String h = Fmt.units(l.held);
        if (Math.abs(l.onOrder) < 0.05) return h;
        return h + (l.onOrder > 0 ? "+" : "-") + Fmt.units(Math.abs(l.onOrder));
    }

    private static String onOrder(TradeLine l) {
        if (Math.abs(l.onOrder) < 0.05) return "--";
        return (l.onOrder > 0 ? "+" : "-") + Fmt.units(Math.abs(l.onOrder));
    }

    @Override
    void keys() {
        if (narrow) return;
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
        boolean buying = k < 2;
        trade(buying, k == 0 || k == 2 ? 1 : k == 1 ? BULK : Double.MAX_VALUE);
    }

    /** Buys or sells up to {@code units} of the selected row, and says what came of it on the roll and in the feed. */
    private void trade(boolean buying, double units) {
        TradeLine l = line();
        TradeProvider provider = b.data == null ? null : b.data.trade;
        if (l == null || provider == null) return;
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
        if (console != null) console.refreshNow();
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

    // --- The terminal's quantity control and its trade keys ------------------------------------

    /** The units the terminal's quantity gives for a buy (as many as there are and can be paid for, for all) or a sale. */
    double units(TradeLine l, boolean buying) {
        if (l == null) return 0;
        if (buying) {
            double most = Math.max(0, l.available);
            if (l.buyPrice > 1e-9) most = Math.min(most, Math.floor(board().credits / l.buyPrice));
            return qty == QALL ? most : Math.min(qty == Q10 ? BULK : 1, most);
        }
        double most = Math.max(0, l.held + Math.min(0, l.onOrder));
        return qty == QALL ? most : Math.min(qty == Q10 ? BULK : 1, most);
    }

    /** The trade key Enter gives: the one the keys are on, else BUY where it can, else SELL. */
    int action() {
        if (focus == BUY || focus == SELL) return focus;
        TradeLine l = line();
        if (l != null && !canBuy(l) && canSell(l)) return SELL;
        return BUY;
    }

    @Override
    boolean key(int keycode) {
        if (!narrow) return super.key(keycode);
        switch (keycode) {
            case Input.Keys.LEFT:
                focus = (focus < 0 ? action() : focus) - 1;
                if (focus < Q1) focus = SELL;
                return true;
            case Input.Keys.RIGHT:
                focus = (focus < 0 ? action() : focus) + 1;
                if (focus > SELL) focus = Q1;
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
                control(focus < 0 ? action() : focus);
                return true;
            default:
                return super.key(keycode);
        }
    }

    /** A press on one of the terminal's controls: a quantity sets it, and the keys go on to the trade; a trade key trades. */
    void control(int c) {
        if (c <= QALL) {
            qty = c;
            focus = -1;
            focus = action();
            return;
        }
        focus = c;
        TradeLine l = line();
        if (l == null) return;
        boolean buying = c == BUY;
        if (buying ? !canBuy(l) : !canSell(l)) {
            say(buying ? buyRefusal(l) : Fmt.capital(sellRefusal(l).toLowerCase(Locale.ROOT)));
            return;
        }
        trade(buying, qty == QALL ? Double.MAX_VALUE : qty == Q10 ? BULK : 1);
    }

    @Override
    void select(int i) {
        super.select(i);
        if (narrow && focus >= BUY) focus = -1;
    }

    @Override
    public void hit(int code) {
        if (code >= OWN) {
            control(code - OWN);
            return;
        }
        super.hit(code);
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        narrow = w < 700f;
        if (narrow) {
            drawNarrow(p, hits, x, y, w, h);
            return;
        }
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

    /** The control row's height under the tube: the quantity keys, the total and the trade keys, with the ENTER cap under them. */
    static final float CONTROLS_H = 66f;

    /**
     * The Station link's market: the rows on the tube and the trade on its last two rows, what the
     * trade comes to; under it the quantity keys, the total on drums and BUY and SELL, the one Enter
     * gives lit with the ENTER cap under it, a key that can't act with its reason under it.
     */
    private void drawNarrow(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, true);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(8, Ledger.rowsIn(bh - 24f - CONTROLS_H));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, "MARKET", "CR A UNIT");
        L.at(p, 1, 1, "COMMODITY", DIM);
        L.right(p, cStock(), 1, "STOCK", DIM);
        L.right(p, cBuy(), 1, "BUY", DIM);
        L.right(p, cSell(), 1, "SELL", DIM);
        L.right(p, cHeld(), 1, "HELD", DIM);
        L.rule(p, 1);
        drawList(p, hits, 2, rows - 4);
        L.rule(p, rows - 3);
        preview(p, rows);
        L.over(p);
        controls(p, hits, L.x, L.y + L.h + 10f, L.w);
    }

    /** The tube's last two rows: what the trade under the keys comes to, before it is made. */
    private void preview(YardPaint p, int rows) {
        TradeLine l = line();
        if (l == null) return;
        boolean buying = action() == BUY;
        double u = units(l, buying), price = buying ? l.buyPrice : l.sellPrice, total = u * price;
        String name = Fmt.upper(l.name);
        String what = (buying ? "BUY " : "SELL ") + (qty == QALL ? "ALL " : "") + Fmt.units(u) + " " + name;
        L.at(p, 1, rows - 2, Ledger.cut(what, L.cols - 12), AMBER);
        L.right(p, L.cols - 1, rows - 2, Fmt.thousands(total) + " CR", AMBER);
        double after = board().credits + (buying ? -total : total);
        String then = "CREDITS AFTER " + Fmt.thousands(Math.max(0, after));
        if (Math.abs(l.onOrder) >= 0.05) then += (l.onOrder > 0 ? "   COMING ABOARD " : "   GOING OFF ") + Fmt.units(Math.abs(l.onOrder));
        L.at(p, 1, rows - 1, Ledger.cut(then, L.cols - 2), DIM);
    }

    /** The control row: QUANTITY on three interlocked keys, TOTAL on drums, then BUY and SELL. */
    private void controls(YardPaint p, Hits hits, float x, float y, float w) {
        Color ink = BigConsole.LIV_INK;
        TradeLine l = line();
        int act = action();
        // The quantity keys
        p.text(YardFonts.Face.LEGEND, 11f, "QUANTITY", x, y + 10f, ink);
        float kx = x, ky = y + 16f, kw = 34f, kh = 22f;
        p.rrect(kx - 3f, ky - 3f, 3 * kw + 4f + 6f, kh + 6f, 2.5f, Yard.BAKELITE);
        for (int i = 0; i < 3; i++) {
            float qx = kx + i * (kw + 2f);
            boolean dn = qty == i;
            float qy = ky + (dn ? 2f : 0f);
            if (!dn) p.rrect(qx + 0.5f, qy + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
            p.rrect(qx, qy, kw, kh - 2f, 2f, dn ? BigConsole.KEY_DOWN : BigConsole.KEY_UP);
            p.rect(qx + 2f, qy + 0.8f, kw - 4f, 1f, Color.WHITE, 0.5f);
            if (dn) p.rect(qx, ky + kh - 4f, kw, 3f, Yard.ORANGE);
            p.textC(YardFonts.Face.LEGEND, 12f, QTY[i], qx + kw / 2f, ky + kh / 2f + 4.3f + (dn ? 1f : -1f), dn ? Yard.ORANGE : Yard.INK);
            if (focus == i) p.frame(qx - 1.5f, qy - 1.5f, kw + 3f, kh + 1f, 1.2f, Yard.ORANGE, 0.95f);
            hits.add(p, qx, ky, kw, kh, this, OWN + i);
        }
        // The total, for the trade Enter gives
        float tx = kx + 3 * kw + 4f + 22f;
        p.text(YardFonts.Face.LEGEND, 11f, "TOTAL", tx, y + 10f, ink);
        double total = l == null ? 0 : units(l, act == BUY) * (act == BUY ? l.buyPrice : l.sellPrice);
        String t = String.valueOf(Math.max(0L, Math.round(total)));
        float dw = p.drums(tx, ky + 1f, t, 11f, 19f, 14f, false);
        p.text(YardFonts.Face.LEGEND, 11f, "CR", tx + dw + 6f, ky + 15f, ink);
        // BUY and SELL: the one Enter gives lit, a refused one barred with its reason under it
        float pw = 64f, ph = 22f, sx = x + w - pw, bxx = sx - 10f - pw;
        trade(p, hits, bxx, ky, pw, ph, "BUY", BUY, l == null || !canBuy(l), act == BUY,
                l == null ? "" : canBuy(l) ? "" : shortReason(buyRefusal(l)), ink);
        trade(p, hits, sx, ky, pw, ph, "SELL", SELL, l == null || !canSell(l), act == SELL,
                l == null ? "" : canSell(l) ? "" : shortReason(sellRefusal(l)), ink);
    }

    /** A trade push-light, lit while Enter gives it; ENTER on a cap under it then, or why it can't act. */
    private void trade(YardPaint p, Hits hits, float x, float y, float w, float h, String name, int code, boolean refused, boolean entered,
                       String reason, Color ink) {
        boolean lit = entered && !refused;
        p.push(x, y, w, h, new String[]{name}, Yard.GREEN_LAMP, lit, YardPaint.Kind.ORDER, refused, false);
        if (focus == code) p.frame(x - 4.5f, y - 4.5f, w + 9f, h + 9f, 1.2f, Yard.ORANGE, 0.95f);
        hits.add(p, x, y, w, h, this, OWN + code);
        float cy = y + h + 7f;
        if (lit) {
            float kw = p.keyWidth("ENTER");
            p.key(x + (w - kw) / 2f, cy, "ENTER");
        } else if (!reason.isEmpty()) {
            p.textC(YardFonts.Face.LEGEND, 9.5f, reason, x + w / 2f, cy + 11f, ink);
        }
    }

    /** A refusal short enough to engrave under its key: "NONE HELD", "NO CREDITS", "HOLD FULL". */
    static String shortReason(String refusal) {
        String r = Fmt.upper(refusal);
        switch (r) {
            case "THE STATION HAS NONE TO SELL":
                return "NONE FOR SALE";
            case "THE HOLD IS FULL":
                return "HOLD FULL";
            case "THE STATION DOESN'T BUY IT":
                return "NOT BOUGHT HERE";
            case "IT IS ALL ON ITS WAY OFF":
                return "ALL GOING OFF";
            case "ALL THAT FITS IS ON ORDER ALREADY":
                return "ON ORDER";
            case "NO FREE CARGO BAY FOR IT":
                return "NO FREE BAY";
            case "THIS SHIP HAS NO CARGO HOLD":
                return "NO HOLD";
            default:
                return r.length() > 14 ? Ledger.cut(r, 14) : r;
        }
    }

    @Override
    boolean jewel() {
        return false;
    }
}
