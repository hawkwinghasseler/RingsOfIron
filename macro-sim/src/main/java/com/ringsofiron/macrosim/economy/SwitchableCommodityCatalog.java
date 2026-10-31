package com.ringsofiron.macrosim.economy;

import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.StationType;

import java.util.Collection;

/**
 * The economy's definitions: macro-sim's built-in set until game data loads, then the loaded one.
 * The economy is built with the world, before content, so it reads through this.
 */
public class SwitchableCommodityCatalog implements CommodityCatalog {
    private CommodityCatalog current;

    public SwitchableCommodityCatalog(CommodityCatalog initial) {
        current = initial;
    }

    public void set(CommodityCatalog catalog) {
        current = catalog;
    }

    @Override
    public CommodityType commodity(String id) {
        return current.commodity(id);
    }

    @Override
    public Collection<CommodityType> commodities() {
        return current.commodities();
    }

    @Override
    public StationType stationType(String id) {
        return current.stationType(id);
    }

    @Override
    public Collection<StationType> stationTypes() {
        return current.stationTypes();
    }
}
