package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent.CargoStack;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The terminal's Market (functional-task 7, TRM): the station's commodities in catalog order, each
 * with its stock, the prices to buy and sell at and what the hold carries; under them, "NOT TRADED
 * HERE", the cargo this station neither sells nor buys, so the hold adds up. Beside the list, the trade
 * as it stands for the row in focus: one quantity (1, 10, all) that ← and → change, what it costs
 * before Enter buys it, the credits and the hold after, what selling it would bring (Delete sells), what
 * is on order and crossing the dock, and the held deliveries this market supplies. The game keeps no
 * price paid for cargo, so there is no column for it.
 */
final class MarketTab extends ListTab {
    static final int BUY = 0, SELL = 1;
    static final String[] QTY = {"1", "10", "ALL"};
    static final double[] QTY_UNITS = {1, 10, Double.MAX_VALUE};
    /** Click ids, from the page's: a quantity. */
    static final int QTY_IDS = Windows.TAB_IDS + 120;
    /** The list's parts below the page's top: the heads' row, the first row. */
    static final float HEADS = 26f, LIST = 42f;

    /** A row: a commodity the station trades (its line), the head over the rest, or cargo it doesn't trade. */
    static final int LINE = 0, HEAD = 1, HELD = 2;

    private final Glass.Cols cols = new Glass.Cols();
    final Array<String> rows = new Array<>();
    private final Array<String> ids = new Array<>();
    final Array<Integer> kinds = new Array<>();
    private final Array<Integer> lineOf = new Array<>();
    private final Array<String> deliveries = new Array<>();
    private TradeBoard board;
    private String note = "";
    /** The quantity a press trades: 1, 10 or all. */
    int qty;
    private int nameCells = 12;
    private double used, capacity;
    private final float[] qtyX = new float[QTY.length], qtyW = new float[QTY.length];
    private float qtyY;
    private final String[] lines = new String[2];

    MarketTab() {
        super("MARKET", ClassicWindow.TRADE, "T");
        keys.define(2, 2);
        keys.set(BUY, "BUY", false).cap(BUY, "ENTER").set(SELL, "SELL", false).cap(SELL, "DEL");
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.trade.isDocked() && b.market;
    }

    @Override
    void refresh(Boards b, HudData d) {
        board = b.trade;
        String focusId = pages.focus() >= 0 && pages.focus() < ids.size ? ids.get(pages.focus()) : null;
        rows.clear();
        ids.clear();
        kinds.clear();
        lineOf.clear();
        for (int i = 0; i < board.lines.size; i++) {
            TradeLine l = board.lines.get(i);
            rows.add(line(l));
            ids.add(l.commodityId);
            kinds.add(LINE);
            lineOf.add(i);
        }
        // The hold's cargo this station doesn't list, so the hold adds up
        CargoHoldComponent hold = d.ctx.ship == null ? null : d.ctx.ship.getComponent(CargoHoldComponent.class);
        used = hold == null ? 0 : hold.usedVolume();
        capacity = hold == null ? 0 : hold.capacity();
        boolean headed = false;
        if (hold != null) {
            for (CargoStack s : hold.stacks) {
                if (s == null || s.commodity == null || s.units <= 1e-6 || traded(s.commodity) || ids.contains(s.commodity, false)) continue;
                if (!headed) {
                    rows.add("NOT TRADED HERE");
                    ids.add("");
                    kinds.add(HEAD);
                    lineOf.add(-1);
                    headed = true;
                }
                rows.add(cols.clear().left(name(s.commodity), nameCells).right("", 15).right(Glass.units(hold.amount(s.commodity)), 5).toString());
                ids.add(s.commodity);
                kinds.add(HELD);
                lineOf.add(-1);
            }
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

    private boolean traded(String commodity) {
        for (int i = 0; i < board.lines.size; i++) if (board.lines.get(i).commodityId.equalsIgnoreCase(commodity)) return true;
        return false;
    }

    /** "iron_ore" as "IRON ORE". */
    static String name(String commodity) {
        return commodity.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private String line(TradeLine l) {
        return cols.clear().left(l.name.toUpperCase(Locale.ROOT), nameCells).right(Glass.units(l.available), 5)
                .right(l.available > 0 ? price(l.buyPrice) : "-", 5).right(l.stationBuys ? price(l.sellPrice) : "-", 5)
                .right(Glass.units(l.held), 5).toString();
    }

    /** A price: whole credits, or one decimal under a hundred when it isn't whole. */
    static String price(double p) {
        if (p < 100 && Math.abs(p - Math.rint(p)) > 0.05) return String.format(Locale.ROOT, "%.1f", p);
        return Glass.thousands(p);
    }

    @Override
    boolean selectable(int i) {
        return i >= 0 && i < kinds.size && kinds.get(i) == LINE;
    }

    /** The line in focus, or null. */
    TradeLine focused() {
        int f = pages.focus();
        if (board == null || f < 0 || f >= lineOf.size || lineOf.get(f) < 0) return null;
        return board.lines.get(lineOf.get(f));
    }

    @Override
    void place(Tab.Place p) {
        super.place(p);
        listX = p.lx;
        listCells = Glass.cells(p.lw());
        nameCells = Math.max(8, listCells - 21);
        listY = p.ly + LIST;
        shown = fit(p.lb);
        pages.set(rows.size, shown);
    }

    // ------------------------------------------------------------------ the trade

    /** How many units a buy of the quantity chosen would fill, as the trade window limits it: stock, hold room, credits. */
    static double buyUnits(TradeBoard b, TradeLine l, double want) {
        double units = Math.min(want, l.available);
        units = Math.min(units, b.freeCargo());
        if (l.buyPrice > 0) units = Math.min(units, Math.floor(b.credits / l.buyPrice + 1e-9));
        return Math.max(0, want == Double.MAX_VALUE ? Math.floor(units + 1e-9) : units);
    }

    /** The station has stock, the hold has room and the player has credits, as the trade window has it. */
    static boolean canBuy(TradeBoard board, TradeLine line) {
        return board.hasHold && line.available > 1e-6 && board.freeCargo() > 1e-6 && board.credits > 1e-6;
    }

    /** Why a buy would fill nothing, from the board (the trade window's reasons). */
    static String buyRefusal(TradeBoard board, TradeLine line) {
        if (!board.hasHold) return "this ship has no cargo hold";
        if (line.available <= 1e-6) return "the station has none to sell";
        if (board.credits <= 1e-6) return "no credits";
        if (board.freeCargo() <= 1e-6) return "the hold is full";
        if (line.onOrder > 1e-6) return "all that fits is on order already";
        return "no free cargo bay for it (each bay holds one commodity)";
    }

    @Override
    void keysFor(int focus) {
        TradeLine l = focused();
        keys.set(BUY, "BUY", l != null && canBuy(board, l)).set(SELL, "SELL", l != null && l.stationBuys && l.held > 1e-6);
    }

    @Override
    boolean key(int keycode) {
        if (keycode == Keys.LEFT || keycode == Keys.RIGHT) {
            qty = Math.max(0, Math.min(QTY.length - 1, qty + (keycode == Keys.LEFT ? -1 : 1)));
            return true;
        }
        return super.key(keycode);
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        TradeLine l = focused();
        if (d == null || d.trade == null || board == null || l == null) return;
        String id = l.commodityId, name = l.name;
        boolean buying = k == BUY;
        double units = QTY_UNITS[qty];
        double credits = board.credits;
        boolean carried = board.carried;
        // Why a buy may fill nothing, read off the board as it is before the order
        String refusal = buying ? buyRefusal(board, l) : "";
        double done = buying ? d.trade.buy(id, units) : d.trade.sell(id, units);
        windows.refresh();
        if (done <= 0) {
            TradeLine now = windows.boards.line(id);
            windows.say(buying ? "Can't buy " + name + ": " + refusal
                    : now != null && now.onOrder < -1e-6 ? "All the " + name + " is on its way off already" : "The station won't take " + name);
        } else if (carried) {
            windows.say(String.format(Locale.ROOT, "Ordered %s %s: the loaders are carrying it %s", Glass.units(done), name, buying ? "aboard" : "off"));
        } else {
            windows.say(String.format(Locale.ROOT, "%s %s %s for %s cr", buying ? "Bought" : "Sold", Glass.units(done), name,
                    Glass.thousands(Math.abs(windows.boards.trade.credits - credits))));
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    void paint(Filament f, float time) {
        float x0 = at.lx, x1 = at.lr;
        Glass.dim(f, x0, at.ly, note);
        if (pages.pages() > 1) {
            float pw = Glass.pagerWidth(f, pages.page(), pages.pages());
            Glass.pager(f, x1 - pw, at.ly, pages.page(), pages.pages());
        }
        float hy = at.ly + HEADS + 9f;
        Glass.label(f, x0 + Glass.CW, hy, "COMMODITY");
        head(f, x0, 1 + nameCells + 5, hy, "STOCK");
        head(f, x0, 1 + nameCells + 10, hy, "BUY");
        head(f, x0, 1 + nameCells + 15, hy, "SELL");
        head(f, x0, 1 + nameCells + 20, hy, "HELD");
        int n = Math.max(1, pages.end() - pages.first());
        Glass.cells(f, x0, listY, listCells, n * 1.5f);
        if (rows.size == 0) Glass.text(f, x0 + Glass.CW, listY, "THE MARKET HAS NOTHING LISTED", Drive.DIM);
        for (int i = pages.first(); i < pages.end(); i++) {
            int kind = kinds.get(i);
            if (kind == HEAD) Glass.label(f, x0 + Glass.CW, rowY(i) + 9.5f, rows.get(i));
            else Glass.row(f, x0, rowY(i), rows.get(i), listCells, i == pages.focus(), kind == HELD ? Drive.DIM : Drive.NORM);
        }
        paintTrade(f);
    }

    /** A column's head, right-aligned to the end of cell {@code endCell - 1}. */
    private static void head(Filament f, float x0, int endCell, float y, String t) {
        Glass.labelRight(f, x0 + endCell * Glass.CW - Glass.P, y, t);
    }

    /** Beside the list: the trade for the row in focus, before Enter or Delete gives it. */
    private void paintTrade(Filament f) {
        float x0 = at.dx, x1 = at.dr, y = at.dy;
        TradeLine l = focused();
        qtyY = -1f;
        if (l == null) {
            Glass.dim(f, x0, y, rows.size == 0 ? "NOTHING TO TRADE HERE" : "↑ ↓ PICK A COMMODITY");
            return;
        }
        String nm = l.name.toUpperCase(Locale.ROOT);
        float nw = f.dm(x0, y, nm, f.o().pitch(2f).full().noGhost());
        int room = Glass.cells(x1 - x0 - nw - 12f);
        // Beside the name, where it is traded and the stock; while some is on order, the order instead
        boolean ordered = Math.abs(l.onOrder) > 0.05;
        if (room > 4) Glass.text(f, x0 + nw + 12f, y + 3f, Glass.cut(ordered ? onOrder(l) : where(l), room), ordered ? Drive.NORM : Drive.DIM);
        y += 30f;
        Glass.label(f, x0, y + 9.5f, "QTY");
        float qx = x0 + 44f;
        qtyY = y;
        for (int i = 0; i < QTY.length; i++) {
            String q = " " + QTY[i] + " ";
            Filament.Opt o = f.o().pitch(Glass.P).noGhost();
            if (i == qty) o.full().inverse(0, q.length());
            else o.norm();
            qtyX[i] = qx;
            qtyW[i] = Glass.width(q.length());
            f.dm(qx, y, q, o);
            qx += qtyW[i] + 10f;
        }
        Glass.capWord(f, qx + 10f, y - 3f, ARROWS, "CHANGES IT", Drive.DIM);
        y += 30f;
        double want = QTY_UNITS[qty];
        if (canBuy(board, l)) {
            double units = buyUnits(board, l, want), cost = units * l.buyPrice;
            String t = "BUY " + Glass.units(units) + " " + nm + " FOR";
            float tw = Glass.text(f, x0, y + 9f, Glass.cut(t, Glass.cells(x1 - x0) - 9), Drive.NORM);
            float fx = Math.max(x0 + 150f, x0 + tw + 12f);
            fx += f.seg7(fx, y, 26f, String.format(Locale.ROOT, "%d", Math.round(cost)), f.o().cells(5).norm()) + 5f;
            f.legend(fx, y + 26f, "CR", f.o().size(13f).dim().noGhost());
            y += 46f;
            Glass.dim(f, x0, y, Glass.cut("CREDITS AFTER   " + Glass.thousands(board.credits - cost), Glass.cells(x1 - x0)));
            Glass.dim(f, x0, y + 20f, Glass.cut("HOLD AFTER      " + Glass.units(used + units) + " OF " + Glass.units(capacity) + " M3",
                    Glass.cells(x1 - x0)));
            y += 40f;
        } else {
            Glass.dim(f, x0, y + 9f, Glass.cut("BUY: " + buyRefusal(board, l), Glass.cells(x1 - x0)));
            y += 30f;
        }
        if (y + Glass.RH <= at.db) Glass.dim(f, x0, y, Glass.cut(sellLine(l, want), Glass.cells(x1 - x0)));
        y += 20f;
        // The deliveries held that this market supplies, while there is room over the response line
        if (deliveries.size > 0 && y + 30f < at.db) {
            y += 8f;
            Glass.head(f, x0, y, x1, "FOR YOUR CONTRACTS");
            y += 20f;
            for (int i = 0; i < deliveries.size && y + Glass.RH <= at.db; i++) {
                int nl = Glass.wrap(deliveries.get(i), Glass.cells(x1 - x0), lines, 2);
                for (int j = 0; j < nl && y + Glass.RH <= at.db; j++) {
                    Glass.text(f, x0, y, lines[j], Drive.NORM);
                    y += 18f;
                }
            }
        }
    }

    private static final String[] ARROWS = {"←", "→"};

    /** Whether the station sells it, buys it, or both, and its stock. */
    static String where(TradeLine l) {
        boolean sells = l.available > 1e-6 || l.buyPrice > 0, buys = l.stationBuys;
        String stock = l.available > 1e-6 ? Glass.units(l.available) + " IN STOCK" : "NONE IN STOCK";
        if (sells && buys) return "BOUGHT AND SOLD HERE, " + stock;
        if (buys) return "BOUGHT HERE, " + stock;
        return "SOLD HERE, " + stock;
    }

    /** What is on order of it: "10 ON ORDER, COMING ABOARD". */
    static String onOrder(TradeLine l) {
        return Glass.units(Math.abs(l.onOrder)) + (l.onOrder > 0 ? " ON ORDER, COMING ABOARD" : " ON ORDER, GOING OFF");
    }

    /** What selling the quantity chosen would bring, or why it can't be sold. */
    static String sellLine(TradeLine l, double want) {
        if (!l.stationBuys) return "SELL: NOT BOUGHT HERE";
        if (l.held <= 1e-6) return "SELL: NONE HELD";
        double units = Math.min(want, l.held);
        return "SELL " + Glass.units(units) + " FOR " + Glass.thousands(units * l.sellPrice) + " CR";
    }

    @Override
    void boxes(FilamentActor a) {
        super.boxes(a);
        if (qtyY >= 0f) for (int i = 0; i < QTY.length; i++) a.box(QTY_IDS + i, qtyX[i] - 3f, qtyY - 3f, qtyW[i] + 6f, 7f * Glass.P + 6f);
    }

    @Override
    void clicked(int id) {
        if (id >= QTY_IDS && id < QTY_IDS + QTY.length) {
            qty = id - QTY_IDS;
            return;
        }
        super.clicked(id);
    }
}
