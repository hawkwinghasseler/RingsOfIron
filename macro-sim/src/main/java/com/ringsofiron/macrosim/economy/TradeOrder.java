package com.ringsofiron.macrosim.economy;

/** A haul a freighter can take: buy {@code quantity} at {@code fromUuid}, sell at {@code toUuid}. */
public class TradeOrder {
    public long id;
    public String commodity;
    public String fromUuid;
    public String toUuid;
    public double quantity;
    public double buyPrice;
    public double sellPrice;
    /** UUID of the freighter that took the order, or null while it is open. */
    public String claimedBy;

    public TradeOrder() {
    }

    public TradeOrder(long id, String commodity, String fromUuid, String toUuid,
                      double quantity, double buyPrice, double sellPrice) {
        this.id = id;
        this.commodity = commodity;
        this.fromUuid = fromUuid;
        this.toUuid = toUuid;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public double unitMargin() {
        return sellPrice - buyPrice;
    }

    @Override
    public String toString() {
        return String.format("#%d %.0f %s %s->%s (%.1f->%.1f)", id, quantity, commodity, fromUuid, toUuid, buyPrice, sellPrice);
    }
}
