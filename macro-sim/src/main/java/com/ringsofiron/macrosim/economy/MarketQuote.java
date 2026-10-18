package com.ringsofiron.macrosim.economy;

/** One line of a station's market board, as a trader sees it. Prices are for the next unit. */
public final class MarketQuote {
    public final String commodityId;
    public final String displayName;
    /** Units the station will sell now (stock not promised to freighters). */
    public final double available;
    /** Credits the trader pays for one unit; prices rise as the trader buys more. */
    public final double buyPrice;
    /** Credits the trader gets for one unit, or 0 if the station does not take this commodity. */
    public final double sellPrice;
    public final boolean stationBuys;

    public MarketQuote(String commodityId, String displayName, double available, double buyPrice,
                       double sellPrice, boolean stationBuys) {
        this.commodityId = commodityId;
        this.displayName = displayName;
        this.available = available;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stationBuys = stationBuys;
    }
}
