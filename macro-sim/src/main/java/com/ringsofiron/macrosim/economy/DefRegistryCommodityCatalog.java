package com.ringsofiron.macrosim.economy;

import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.CommodityDef;
import com.ringsofiron.moddingdata.defs.StationTypeDef;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Commodity and station definitions from modding-data ({@code assets/data/economy.json} plus mods).
 * Defs are copied when the catalog is built, so build a new one after mods reload.
 *
 * <p>{@code StationTypeDef.consumes} are the inputs of one production cycle and {@code produces} its
 * outputs; defs have no separate upkeep yet. Station storage is not used: the economy caps output at
 * four times target stock.
 */
public class DefRegistryCommodityCatalog implements CommodityCatalog {
    /** Production cycles per sim day when the defs do not say. */
    public static final double DEFAULT_CYCLES_PER_DAY = 4;
    public static final double DEFAULT_TARGET_STOCK_DAYS = 10;

    private final Map<String, CommodityType> commodities = new LinkedHashMap<>();
    private final Map<String, StationType> stationTypes = new LinkedHashMap<>();

    public DefRegistryCommodityCatalog(DefRegistry defs) {
        this(defs, DEFAULT_CYCLES_PER_DAY, DEFAULT_TARGET_STOCK_DAYS);
    }

    public DefRegistryCommodityCatalog(DefRegistry defs, double cyclesPerDay, double targetStockDays) {
        for (CommodityDef d : defs.all(CommodityDef.class)) {
            commodities.put(d.id, new CommodityType(d.id, d.name != null ? d.name : d.id, d.basePrice, d.volumePerUnit));
        }
        for (StationTypeDef d : defs.all(StationTypeDef.class)) {
            stationTypes.put(d.id, new StationType(d.id, toDoubles(d.consumes), toDoubles(d.produces), Map.of(),
                    cyclesPerDay, targetStockDays));
        }
    }

    private static Map<String, Double> toDoubles(Map<String, Float> in) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (in != null) in.forEach((k, v) -> out.put(k, v.doubleValue()));
        return out;
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
