package com.ringsofiron.macrosim.economy;

import java.util.Collection;

/**
 * Source of commodity and station-type definitions. {@code modding-data} will provide a JSON-backed
 * implementation; {@link DefaultCommodityCatalog} hardcodes a small starter set until then.
 */
public interface CommodityCatalog {
    CommodityType commodity(String id);

    Collection<CommodityType> commodities();

    StationType stationType(String id);

    Collection<StationType> stationTypes();
}
