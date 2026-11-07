package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.ObjectFloatMap;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;

import java.util.Locale;

/**
 * The station market (T): a row a commodity with its stock, the prices, what the hold has and what
 * is on order across the dock, and the four trades the classic window has (buy 1, buy 10, sell 1,
 * sell all), each barred with the game's reason when it can't be made. Under the table, what the
 * loaders are carrying, and the held deliveries this market can supply.
 */
public final class MarketPage extends Page {
    /** The classic window's bulk buy. */
    public static final int BULK = 10;
    public static final String SOURCE = "STATION";

    /** Each commodity's biggest order seen while its loading runs, for how far along it is. */
    private final ObjectFloatMap<String> ordered = new ObjectFloatMap<>();

    public MarketPage() {
        super("MARKET", ClassicWindow.TRADE);
    }

    @Override
    public boolean offered(Boards b) {
        return b.trade.isDocked();
    }

    @Override
    protected void build(Boards b) {
        TradeBoard t = b.trade;
        Sect s = sect("MARKET").empty(t.hasHold ? "NOTHING TRADED HERE" : "THIS SHIP HAS NO CARGO HOLD");
        s.col(Col.start("COMMODITY", 150f)).col(Col.end("STOCK", 74f)).col(Col.end("BUY AT", 68f)).col(Col.end("SELL AT", 68f))
                .col(Col.end("HELD", 52f)).col(Col.end("ON ORDER", 82f));
        for (int i = 0; i < t.lines.size; i++) {
            TradeLine l = t.lines.get(i);
            Row r = s.row(l.commodityId);
            r.add(Fmt.caps(l.name), Cell.Tone.NAME);
            r.add(Fmt.units(l.available), "U", Cell.Tone.BODY);
            r.add(l.available > 1e-6 || l.buyPrice > 0 ? Fmt.price(l.buyPrice) : "—", Cell.Tone.VALUE);
            r.add(l.stationBuys ? Fmt.price(l.sellPrice) : "—", l.stationBuys ? Cell.Tone.VALUE : Cell.Tone.LABEL);
            r.add(Fmt.units(l.held), Cell.Tone.VALUE);
            boolean moving = Math.abs(l.onOrder) >= 0.05;
            r.add(moving ? (l.onOrder > 0 ? "+" : "−") + Fmt.units(Math.abs(l.onOrder)) : "", Cell.Tone.CYAN);
            String id = l.commodityId;
            boolean buy = canBuy(t, l), sell = canSell(l);
            String noBuy = buy ? "" : Fmt.caps(buyRefusal(t, l)), noSell = sell ? "" : sellRefusal(l);
            r.act("BUY 1", buy, noBuy, trade(id, true, 1));
            r.act("BUY " + BULK, buy, noBuy, trade(id, true, BULK));
            r.act("SELL 1", sell, noSell, trade(id, false, 1));
            r.act("SELL ALL", sell, noSell, trade(id, false, Double.MAX_VALUE));
            r.detail.add(Line.pair("STOCK", Fmt.units(l.available), "U", Cell.Tone.VALUE));
            r.detail.add(Line.pair("BUY AT", Fmt.price(l.buyPrice), "CR", Cell.Tone.VALUE));
            r.detail.add(Line.pair("SELL AT", l.stationBuys ? Fmt.price(l.sellPrice) : "NOT BOUGHT HERE", l.stationBuys ? "CR" : "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("HELD", Fmt.units(l.held), "U", Cell.Tone.VALUE));
            if (moving) r.detail.add(Line.pair("ON ORDER", (l.onOrder > 0 ? "+" : "−") + Fmt.units(Math.abs(l.onOrder)), "U", Cell.Tone.CYAN));
        }
        dock(s, t);
        for (int i = 0; i < b.deliveries.size; i++) {
            Boards.Delivery dl = b.deliveries.get(i);
            if (i == 0) s.line(Line.head("FOR YOUR CONTRACTS"));
            TradeLine l = dl.line;
            int aboard = (int) Math.floor(l.held + 1e-6), coming = (int) Math.max(0, Math.round(l.onOrder));
            int more = Math.max(0, dl.units - aboard - coming);
            StringBuilder sb = new StringBuilder();
            sb.append(aboard).append(" OF ").append(dl.units).append(" ABOARD");
            if (coming > 0) sb.append(", ").append(coming).append(" COMING");
            if (more > 0) {
                double free = t.freeCargo();
                sb.append(" · ").append(more <= free + 1e-6 ? more + " MORE TO BUY" : "ROOM FOR " + Fmt.units(Math.floor(free)) + " OF " + more + " MORE");
            } else {
                sb.append(" · ALL ABOARD OR COMING");
            }
            s.line(Line.text("DELIVER " + dl.units + " " + dl.commodity + " → " + dl.to, Cell.Tone.NAME).note(dl.left)).bar(more == 0 ? 1f : (float) (aboard + coming) / Math.max(1, dl.units));
            s.line(Line.text(sb.toString(), Cell.Tone.LABEL));
        }
        s.note("PRICES IN CR A UNIT");
    }

    /** What the loaders are carrying across the dock, each with how far its order has come. */
    private void dock(Sect s, TradeBoard t) {
        boolean head = false;
        for (int i = 0; i < t.lines.size; i++) {
            TradeLine l = t.lines.get(i);
            float now = (float) Math.abs(l.onOrder);
            if (now < 0.05f) {
                ordered.remove(l.commodityId, 0f);
                continue;
            }
            float peak = Math.max(now, ordered.get(l.commodityId, 0f));
            ordered.put(l.commodityId, peak);
            if (!head) s.line(Line.head("ACROSS THE DOCK"));
            head = true;
            boolean in = l.onOrder > 0;
            float done = peak - now;
            s.line(Line.pair(Fmt.units(peak) + " " + Fmt.caps(l.name), in ? "THE LOADERS ARE CARRYING IT ABOARD" : "THE LOADERS ARE CARRYING IT OFF", "", Cell.Tone.BODY)
                    .note(Fmt.units(done) + " OF " + Fmt.units(peak) + (in ? " IN" : " OFF")).bar(peak <= 0f ? 0f : done / peak));
        }
    }

    private Runnable trade(String id, boolean buying, double units) {
        return () -> {
            TradeProvider p = host == null ? null : host.data().trade;
            if (p == null) return;
            TradeBoard t = boards.trade;
            TradeLine line = find(t, id);
            if (line == null) return;
            String name = line.name;
            double credits = t.credits;
            boolean carried = t.carried;
            TradeBoard before = snapshot(t, line);
            double done = buying ? p.buy(id, units) : p.sell(id, units);
            host.reread();
            TradeLine now = find(boards.trade, id);
            String said;
            if (done <= 0) {
                said = buying ? "Can't buy " + name + ": " + (now == null ? "it is no longer listed" : buyRefusal(before, before.lines.first()))
                        : now != null && now.onOrder < -1e-6 ? "All the " + name + " is on its way off already"
                        : "The station won't take " + name;
            } else if (carried) {
                said = String.format(Locale.ROOT, "Ordered %s %s: the loaders are carrying it %s", Fmt.units(done), name, buying ? "aboard" : "off");
            } else {
                said = String.format(Locale.ROOT, "%s %s %s for %s", buying ? "Bought" : "Sold", Fmt.units(done), name,
                        Fmt.credits(Math.abs(boards.trade.credits - credits)) + " cr");
            }
            host.say(SOURCE, said);
        };
    }

    /** The board as it stood before a trade, with that commodity's line, for why a buy filled nothing. */
    private static TradeBoard snapshot(TradeBoard t, TradeLine l) {
        TradeBoard b = new TradeBoard();
        b.stationName = t.stationName;
        b.credits = t.credits;
        b.cargoUsed = t.cargoUsed;
        b.cargoCapacity = t.cargoCapacity;
        b.hasHold = t.hasHold;
        TradeLine c = b.addLine();
        c.commodityId = l.commodityId;
        c.name = l.name;
        c.available = l.available;
        c.held = l.held;
        c.onOrder = l.onOrder;
        return b;
    }

    private static TradeLine find(TradeBoard t, String id) {
        for (int i = 0; i < t.lines.size; i++) if (t.lines.get(i).commodityId.equals(id)) return t.lines.get(i);
        return null;
    }

    /** The station has stock, the hold has room and the player has credits, as the classic window asks. */
    static boolean canBuy(TradeBoard t, TradeLine l) {
        return t.hasHold && l.available > 1e-6 && t.freeCargo() > 1e-6 && t.credits > 1e-6;
    }

    static boolean canSell(TradeLine l) {
        return l.stationBuys && l.held > 1e-6;
    }

    /** Why a buy can't be made (or filled nothing), in the classic window's words. */
    static String buyRefusal(TradeBoard t, TradeLine l) {
        if (!t.hasHold) return "this ship has no cargo hold";
        if (l.available <= 1e-6) return "the station has none to sell";
        if (t.credits <= 1e-6) return "no credits";
        if (t.freeCargo() <= 1e-6) return "the hold is full";
        if (l.onOrder > 1e-6) return "all that fits is on order already";
        return "no free cargo bay for it (each bay holds one commodity)";
    }

    static String sellRefusal(TradeLine l) {
        if (!l.stationBuys) return "THE STATION DOESN'T BUY IT";
        if (l.onOrder < -1e-6) return "ALL OF IT IS ON ITS WAY OFF";
        return "NOTHING HELD TO SELL";
    }
}
