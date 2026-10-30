package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.function.ToDoubleFunction;

/**
 * Settles carried cargo for salvage around the market's own settlement: goods carried between two
 * ships (off a taken craft) just move from hold to hold, and salvaged parts ({@code part:<id>})
 * carried off to a station are sold for what {@code salePrice} says. Everything else is the
 * market's.
 */
public class SalvageSettlement implements CargoLoading.Settlement {
    private final CargoLoading.Settlement market;
    private final CommodityCatalog catalog;
    private final ToDoubleFunction<String> salePrice;

    public SalvageSettlement(CargoLoading.Settlement market, CommodityCatalog catalog, ToDoubleFunction<String> salePrice) {
        this.market = market;
        this.catalog = catalog;
        this.salePrice = salePrice;
    }

    public static boolean isPart(String commodity) {
        return commodity != null && commodity.startsWith(Salvage.PART);
    }

    private static boolean isStation(Entity craft) {
        return craft != null && craft.getComponent(StationComponent.class) != null;
    }

    @Override
    public double unitVolume(String commodity) {
        if (isPart(commodity)) return Salvage.CRATE_VOLUME;
        return market.unitVolume(commodity);
    }

    @Override
    public double available(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order) {
        if (!isStation(station)) {
            CargoHoldComponent from = hold(order.toShip ? station : ship), to = hold(order.toShip ? ship : station);
            if (from == null || to == null) return 0;
            return Math.min(from.amount(order.commodity), to.room(order.commodity, volumeOf(from, order.commodity)));
        }
        if (isPart(order.commodity)) {
            CargoHoldComponent hold = hold(ship);
            return order.toShip || hold == null || salePrice.applyAsDouble(order.commodity) <= 0 ? 0 : hold.amount(order.commodity);
        }
        return market.available(ship, station, order);
    }

    @Override
    public double settle(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order, double units) {
        if (!isStation(station)) {
            CargoHoldComponent from = hold(order.toShip ? station : ship), to = hold(order.toShip ? ship : station);
            double qty = Math.min(units, available(ship, station, order));
            if (from == null || to == null || qty <= 0) return 0;
            double volume = volumeOf(from, order.commodity), mass = massOf(from, order.commodity);
            qty = from.unload(order.commodity, qty);
            double stowed = to.load(order.commodity, qty, volume, mass);
            if (stowed < qty) from.load(order.commodity, qty - stowed, volume, mass);
            return stowed;
        }
        if (isPart(order.commodity)) return sellParts(ship, order.commodity, units);
        return market.settle(ship, station, order, units);
    }

    /** Sells up to {@code units} of a salvaged part out of {@code ship}'s hold; returns the units sold. */
    public double sellParts(Entity ship, String part, double units) {
        CargoHoldComponent hold = hold(ship);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        double price = salePrice.applyAsDouble(part);
        if (hold == null || trader == null || price <= 0) return 0;
        double sold = hold.unload(part, Math.min(units, hold.amount(part)));
        trader.credits += sold * price;
        return sold;
    }

    /** m³ a unit of {@code commodity} takes, from the catalog, else from how it is stowed in {@code hold}. */
    private double volumeOf(CargoHoldComponent hold, String commodity) {
        if (isPart(commodity)) return Salvage.CRATE_VOLUME;
        CommodityType c = catalog.commodity(commodity);
        if (c != null) return c.unitVolume;
        CargoHoldComponent.CargoStack s = stackOf(hold, commodity);
        return s == null ? 0 : s.unitVolume;
    }

    private double massOf(CargoHoldComponent hold, String commodity) {
        if (isPart(commodity)) return Salvage.CRATE_MASS;
        CommodityType c = catalog.commodity(commodity);
        if (c != null) return c.unitMass;
        CargoHoldComponent.CargoStack s = stackOf(hold, commodity);
        return s == null ? 0 : s.unitMass;
    }

    private static CargoHoldComponent.CargoStack stackOf(CargoHoldComponent hold, String commodity) {
        for (CargoHoldComponent.CargoStack s : hold.stacks) {
            if (s != null && commodity.equals(s.commodity)) return s;
        }
        return null;
    }

    private static CargoHoldComponent hold(Entity e) {
        return e == null ? null : e.getComponent(CargoHoldComponent.class);
    }
}
