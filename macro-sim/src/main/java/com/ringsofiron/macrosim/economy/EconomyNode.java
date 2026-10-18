package com.ringsofiron.macrosim.economy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inventory and prices of one station. Plain fields so it serializes to flat JSON; the owning
 * entity is referenced by {@code stationUuid}.
 */
public class EconomyNode {
    /** Price never falls below or rises above these multiples of the base price. */
    public static final double MIN_PRICE_FACTOR = 0.25;
    public static final double MAX_PRICE_FACTOR = 4.0;
    /** How strongly price reacts to stock deviating from target. */
    public static final double PRICE_ELASTICITY = 0.7;
    /** Floor for a target stock, so goods a station only stores still get a sane price curve. */
    public static final double MIN_TARGET_STOCK = 50;

    public String stationUuid;
    public String stationTypeId;
    public String displayName;
    public final Map<String, Double> inventory = new LinkedHashMap<>();
    public final Map<String, Double> prices = new LinkedHashMap<>();
    /** Units already promised to freighters (outgoing) or on their way in (incoming). */
    public final Map<String, Double> reservedOut = new LinkedHashMap<>();
    public final Map<String, Double> incoming = new LinkedHashMap<>();
    /** Fraction of the last pass's production that inputs allowed, 0..1. Shown on HUD/debug. */
    public double lastProductionRatio;
    /** Days of upkeep that went unmet in total (starvation pressure). */
    public double shortfallDays;

    public EconomyNode() {
    }

    public EconomyNode(String stationUuid, String stationTypeId, String displayName) {
        this.stationUuid = stationUuid;
        this.stationTypeId = stationTypeId;
        this.displayName = displayName;
    }

    public double stock(String commodity) {
        return inventory.getOrDefault(commodity, 0.0);
    }

    /** Stock not yet promised to a freighter. */
    public double available(String commodity) {
        return Math.max(0, stock(commodity) - reservedOut.getOrDefault(commodity, 0.0));
    }

    /** Stock plus what is already on its way here. */
    public double projectedStock(String commodity) {
        return stock(commodity) + incoming.getOrDefault(commodity, 0.0);
    }

    public void add(String commodity, double amount) {
        inventory.merge(commodity, amount, Double::sum);
    }

    /** Removes up to {@code amount} and returns how much was actually removed. */
    public double remove(String commodity, double amount) {
        double taken = Math.min(stock(commodity), amount);
        inventory.put(commodity, stock(commodity) - taken);
        return taken;
    }

    public double price(String commodity) {
        return prices.getOrDefault(commodity, 0.0);
    }

    static void adjust(Map<String, Double> map, String commodity, double delta) {
        double v = map.getOrDefault(commodity, 0.0) + delta;
        if (v <= 1e-9) map.remove(commodity);
        else map.put(commodity, v);
    }

    public void reserveOut(String commodity, double amount) {
        adjust(reservedOut, commodity, amount);
    }

    public void expectIncoming(String commodity, double amount) {
        adjust(incoming, commodity, amount);
    }
}
