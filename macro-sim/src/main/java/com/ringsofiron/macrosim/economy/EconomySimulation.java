package com.ringsofiron.macrosim.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The slow economy pass: production, upkeep, prices and the trade order board. Runs every
 * {@link #PASS_INTERVAL} sim seconds, driven by {@code MacroSimulation}; pure logic, no ECS.
 */
public class EconomySimulation {
    /** Five minutes of sim time between passes, as in the design doc. */
    public static final double PASS_INTERVAL = 300;
    /** Stock above this multiple of target counts as surplus, below the inverse as deficit. */
    public static final double SURPLUS_FACTOR = 1.2;
    public static final double DEFICIT_FACTOR = 0.8;
    /** Smallest haul worth posting. */
    public static final double MIN_ORDER_QUANTITY = 20;
    /** Orders need at least this margin per unit as a fraction of base price. */
    public static final double MIN_MARGIN_FRACTION = 0.1;

    private final CommodityCatalog catalog;
    private final List<TradeOrder> openOrders = new ArrayList<>();
    private long nextOrderId = 1;

    public EconomySimulation(CommodityCatalog catalog) {
        this.catalog = catalog;
    }

    public CommodityCatalog catalog() {
        return catalog;
    }

    /** Gives a new node its starting stock (target levels) and prices. */
    public void initialize(EconomyNode node, double stockFactor) {
        for (CommodityType c : catalog.commodities()) {
            double target = targetStock(node, c.id);
            if (touches(node, c.id)) node.inventory.put(c.id, target * stockFactor);
        }
        updatePrices(node);
    }

    /** Advances every node by {@code dt} sim seconds, reprices them and rebuilds the order board. */
    public void pass(Collection<EconomyNode> nodes, double dt) {
        for (EconomyNode node : nodes) {
            produceAndConsume(node, dt);
            updatePrices(node);
        }
        rebuildOrders(nodes);
    }

    void produceAndConsume(EconomyNode node, double dt) {
        StationType type = catalog.stationType(node.stationTypeId);
        double days = dt / 86400.0;

        double cycles = type.cyclesPerDay * days;
        double ratio = cycles > 0 ? 1.0 : 0.0;
        for (var in : type.inputsPerCycle.entrySet()) {
            double need = in.getValue() * cycles;
            if (need > 0) ratio = Math.min(ratio, node.stock(in.getKey()) / need);
        }
        // Stop producing into a full warehouse.
        for (var out : type.outputsPerCycle.entrySet()) {
            double cap = 4 * targetStock(node, out.getKey());
            double room = Math.max(0, cap - node.stock(out.getKey()));
            double make = out.getValue() * cycles;
            if (make > 0) ratio = Math.min(ratio, room / make);
        }
        ratio = Math.max(0, Math.min(1, ratio));
        for (var in : type.inputsPerCycle.entrySet()) node.remove(in.getKey(), in.getValue() * cycles * ratio);
        for (var out : type.outputsPerCycle.entrySet()) node.add(out.getKey(), out.getValue() * cycles * ratio);
        if (cycles > 0) node.lastProductionRatio = ratio;

        for (var up : type.upkeepPerDay.entrySet()) {
            double need = up.getValue() * days;
            double got = node.remove(up.getKey(), need);
            if (got < need) node.shortfallDays += days * (1 - got / need);
        }
    }

    /** Price rises as projected stock falls below target and falls as it piles up. */
    public void updatePrices(EconomyNode node) {
        for (CommodityType c : catalog.commodities()) {
            if (!touches(node, c.id) && node.stock(c.id) <= 0) {
                node.prices.remove(c.id);
                continue;
            }
            node.prices.put(c.id, priceAt(node, c, node.projectedStock(c.id)));
        }
    }

    /** The unit price this station's curve gives at a projected stock of {@code stock}. */
    public double priceAt(EconomyNode node, CommodityType c, double stock) {
        double factor = Math.pow(targetStock(node, c.id) / Math.max(1, stock), EconomyNode.PRICE_ELASTICITY);
        factor = Math.max(EconomyNode.MIN_PRICE_FACTOR, Math.min(EconomyNode.MAX_PRICE_FACTOR, factor));
        return c.basePrice * factor;
    }

    public double targetStock(EconomyNode node, String commodity) {
        StationType type = catalog.stationType(node.stationTypeId);
        return Math.max(EconomyNode.MIN_TARGET_STOCK, type.dailyThroughput(commodity) * type.targetStockDays);
    }

    boolean touches(EconomyNode node, String commodity) {
        StationType type = catalog.stationType(node.stationTypeId);
        return type.produces(commodity) || type.consumes(commodity);
    }

    /** Pairs each station's surplus with the most profitable deficit elsewhere. Claimed orders survive. */
    void rebuildOrders(Collection<EconomyNode> nodes) {
        openOrders.removeIf(o -> o.claimedBy == null);
        for (CommodityType c : catalog.commodities()) {
            List<EconomyNode> sellers = new ArrayList<>();
            List<EconomyNode> buyers = new ArrayList<>();
            for (EconomyNode n : nodes) {
                double target = targetStock(n, c.id);
                StationType type = catalog.stationType(n.stationTypeId);
                if (!type.consumes(c.id) && n.available(c.id) > MIN_ORDER_QUANTITY) sellers.add(n);
                else if (n.available(c.id) > target * SURPLUS_FACTOR) sellers.add(n);
                else if (type.consumes(c.id) && n.projectedStock(c.id) < target * DEFICIT_FACTOR) buyers.add(n);
            }
            for (EconomyNode buyer : buyers) {
                double want = targetStock(buyer, c.id) - buyer.projectedStock(c.id);
                EconomyNode best = null;
                double bestMargin = c.basePrice * MIN_MARGIN_FRACTION;
                for (EconomyNode seller : sellers) {
                    double margin = buyer.price(c.id) - seller.price(c.id);
                    if (margin > bestMargin && sellable(seller, c.id) >= MIN_ORDER_QUANTITY) {
                        best = seller;
                        bestMargin = margin;
                    }
                }
                if (best == null) continue;
                double qty = Math.min(want, sellable(best, c.id));
                if (qty < MIN_ORDER_QUANTITY) continue;
                openOrders.add(new TradeOrder(nextOrderId++, c.id, best.stationUuid, buyer.stationUuid,
                        qty, best.price(c.id), buyer.price(c.id)));
            }
        }
    }

    /** What a seller can still offer once already-posted open orders are subtracted. */
    private double sellable(EconomyNode seller, String commodity) {
        double posted = 0;
        for (TradeOrder o : openOrders) {
            if (o.claimedBy == null && o.fromUuid.equals(seller.stationUuid) && o.commodity.equals(commodity)) posted += o.quantity;
        }
        StationType type = catalog.stationType(seller.stationTypeId);
        double keep = type.consumes(commodity) ? targetStock(seller, commodity) : 0;
        return seller.available(commodity) - keep - posted;
    }

    /** Id the next posted order gets; saved so ids stay unique across a load. */
    public long nextOrderId() {
        return nextOrderId;
    }

    /** Replaces the order board with saved orders (open and claimed). */
    public void restoreOrders(List<TradeOrder> orders, long nextOrderId) {
        openOrders.clear();
        openOrders.addAll(orders);
        this.nextOrderId = nextOrderId;
    }

    /** Open (unclaimed) and claimed orders. */
    public List<TradeOrder> orders() {
        return Collections.unmodifiableList(openOrders);
    }

    public void claim(TradeOrder order, String freighterUuid, EconomyNode from, EconomyNode to) {
        order.claimedBy = freighterUuid;
        from.reserveOut(order.commodity, order.quantity);
        to.expectIncoming(order.commodity, order.quantity);
    }

    /** Drops the unclaimed orders to or from a station that is gone. Claimed ones are the freighters'. */
    public void dropOrdersFor(String stationUuid) {
        openOrders.removeIf(o -> o.claimedBy == null && (o.fromUuid.equals(stationUuid) || o.toUuid.equals(stationUuid)));
    }

    /** Removes a finished or abandoned order and releases whatever it still holds. */
    public void close(TradeOrder order) {
        openOrders.remove(order);
    }
}
