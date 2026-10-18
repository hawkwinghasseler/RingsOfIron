package com.ringsofiron.macrosim.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Buying and selling at a station by hand. Trades walk the station's price curve, so a big purchase
 * gets dearer unit by unit and a big sale cheaper, and the station's prices update at once. A small
 * spread on each side keeps buying and selling back at the same station from paying. Goods the
 * station has promised to freighters are never sold. Main thread only.
 */
public class StationMarket {
    /** Fraction added to buy prices and taken off sell prices. */
    public static final double SPREAD = 0.05;
    /** Units per integration step along the price curve. */
    private static final double STEP_UNITS = 5;
    private static final int MAX_STEPS = 200;

    private final EconomySimulation economy;

    public StationMarket(EconomySimulation economy) {
        this.economy = economy;
    }

    /** The station's board: everything it sells or buys, in catalog order. */
    public List<MarketQuote> quotes(EconomyNode node) {
        List<MarketQuote> out = new ArrayList<>();
        for (CommodityType c : economy.catalog().commodities()) {
            boolean buys = stationBuys(node, c.id);
            double available = node.available(c.id);
            if (!buys && available <= 0) continue;
            double mid = node.price(c.id) > 0 ? node.price(c.id) : economy.priceAt(node, c, node.projectedStock(c.id));
            out.add(new MarketQuote(c.id, c.displayName, available, mid * (1 + SPREAD),
                    buys ? mid * (1 - SPREAD) : 0, buys));
        }
        return out;
    }

    /** True if the station takes this commodity from traders: it makes or uses it, or already lists it. */
    public boolean stationBuys(EconomyNode node, String commodity) {
        return economy.touches(node, commodity) || node.prices.containsKey(commodity);
    }

    /** Credits {@code units} would cost the trader right now. */
    public double buyCost(EconomyNode node, String commodity, double units) {
        return walk(node, commodity, units, -1) * (1 + SPREAD);
    }

    /** Credits the trader would get for {@code units} right now. */
    public double sellValue(EconomyNode node, String commodity, double units) {
        return walk(node, commodity, units, +1) * (1 - SPREAD);
    }

    /**
     * Buys up to {@code units}, limited by what the station can sell, the hold's free space and the
     * trader's credits. Returns the units bought.
     */
    public double buy(EconomyNode node, TraderComponent trader, String commodity, double units) {
        double qty = Math.min(units, Math.min(node.available(commodity), trader.freeCargo()));
        if (qty <= 0) return 0;
        if (buyCost(node, commodity, qty) > trader.credits) qty = affordable(node, commodity, qty, trader.credits);
        if (qty <= 1e-9) return 0;
        double cost = buyCost(node, commodity, qty);
        node.remove(commodity, qty);
        trader.credits -= cost;
        trader.add(commodity, qty);
        economy.updatePrices(node);
        return qty;
    }

    /** Sells up to {@code units} from the hold. Returns the units sold (0 if the station does not buy it). */
    public double sell(EconomyNode node, TraderComponent trader, String commodity, double units) {
        double qty = Math.min(units, trader.amount(commodity));
        if (qty <= 0 || !stationBuys(node, commodity)) return 0;
        double value = sellValue(node, commodity, qty);
        trader.add(commodity, -qty);
        trader.credits += value;
        node.add(commodity, qty);
        economy.updatePrices(node);
        return qty;
    }

    /** Most units of {@code max} that {@code credits} pays for. */
    private double affordable(EconomyNode node, String commodity, double max, double credits) {
        double lo = 0, hi = max;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (buyCost(node, commodity, mid) <= credits) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /** Sum of curve prices over {@code units} taken from (dir -1) or added to (dir +1) the stock. */
    private double walk(EconomyNode node, String commodity, double units, int dir) {
        if (units <= 0) return 0;
        CommodityType c = economy.catalog().commodity(commodity);
        if (c == null) throw new IllegalArgumentException("Unknown commodity " + commodity);
        int steps = (int) Math.min(MAX_STEPS, Math.max(1, Math.ceil(units / STEP_UNITS)));
        double du = units / steps;
        double stock = node.projectedStock(commodity);
        double total = 0;
        for (int i = 0; i < steps; i++) {
            total += economy.priceAt(node, c, stock + dir * (i + 0.5) * du) * du;
        }
        return total;
    }
}
