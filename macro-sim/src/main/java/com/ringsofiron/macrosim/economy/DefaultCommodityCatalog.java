package com.ringsofiron.macrosim.economy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in starter economy: water, food, ore, metals, fuel and electronics, seven station types. */
public class DefaultCommodityCatalog implements CommodityCatalog {
    private final Map<String, CommodityType> commodities = new LinkedHashMap<>();
    private final Map<String, StationType> stationTypes = new LinkedHashMap<>();

    public DefaultCommodityCatalog() {
        add(new CommodityType("water", "Water", 4, 1.0));
        add(new CommodityType("food", "Food", 12, 1.0));
        add(new CommodityType("ore", "Ore", 6, 0.5));
        add(new CommodityType("metals", "Refined Metals", 25, 0.3));
        add(new CommodityType("fuel", "Fuel", 15, 1.0));
        add(new CommodityType("electronics", "Electronics", 120, 0.2));

        add(new StationType("ice_mine", Map.of(), Map.of("water", 40.0), Map.of("food", 6.0), 4, 10));
        add(new StationType("farm", Map.of("water", 20.0), Map.of("food", 15.0), Map.of(), 4, 10));
        add(new StationType("ore_mine", Map.of("fuel", 2.0), Map.of("ore", 30.0), Map.of("food", 6.0), 4, 10));
        add(new StationType("refinery", Map.of("ore", 30.0, "fuel", 3.0), Map.of("metals", 10.0), Map.of("food", 4.0), 4, 10));
        add(new StationType("fuel_depot", Map.of("water", 20.0), Map.of("fuel", 12.0), Map.of("food", 2.0), 4, 10));
        add(new StationType("factory", Map.of("metals", 10.0, "fuel", 2.0), Map.of("electronics", 2.0), Map.of("food", 6.0), 4, 10));
        add(new StationType("habitat", Map.of(), Map.of(), Map.of("food", 40.0, "water", 40.0, "electronics", 4.0), 0, 10));
    }

    private void add(CommodityType c) {
        commodities.put(c.id, c);
    }

    private void add(StationType s) {
        stationTypes.put(s.id, s);
    }

    @Override
    public CommodityType commodity(String id) {
        CommodityType c = commodities.get(id);
        if (c == null) throw new IllegalArgumentException("Unknown commodity: " + id);
        return c;
    }

    @Override
    public Collection<CommodityType> commodities() {
        return Collections.unmodifiableCollection(commodities.values());
    }

    @Override
    public StationType stationType(String id) {
        StationType s = stationTypes.get(id);
        if (s == null) throw new IllegalArgumentException("Unknown station type: " + id);
        return s;
    }

    @Override
    public Collection<StationType> stationTypes() {
        return Collections.unmodifiableCollection(stationTypes.values());
    }
}
