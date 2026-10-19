package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.CommodityType;

/**
 * Settles carried cargo by moving it from one craft's hold to the other's, with no money changing
 * hands: for a station with no market (the docking demo's outpost), or goods the ship owns.
 */
public class HoldTransfer implements CargoLoading.Settlement {
    private final CommodityCatalog catalog;

    public HoldTransfer(CommodityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public double unitVolume(String commodity) {
        CommodityType c = catalog.commodity(commodity);
        return c == null ? 0 : c.unitVolume;
    }

    @Override
    public double available(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order) {
        CargoHoldComponent from = hold(order.toShip ? station : ship), to = hold(order.toShip ? ship : station);
        CommodityType c = catalog.commodity(order.commodity);
        if (from == null || to == null || c == null) return 0;
        return Math.min(from.amount(order.commodity), to.room(order.commodity, c.unitVolume));
    }

    @Override
    public double settle(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order, double units) {
        CargoHoldComponent from = hold(order.toShip ? station : ship), to = hold(order.toShip ? ship : station);
        CommodityType c = catalog.commodity(order.commodity);
        if (from == null || to == null || c == null) return 0;
        double qty = Math.min(units, available(ship, station, order));
        if (qty <= 0) return 0;
        qty = from.unload(order.commodity, qty);
        double stowed = to.load(order.commodity, qty, c.unitVolume, c.unitMass);
        // Whatever would not fit goes back where it came from
        if (stowed < qty) from.load(order.commodity, qty - stowed, c.unitVolume, c.unitMass);
        return stowed;
    }

    private static CargoHoldComponent hold(Entity e) {
        return e == null ? null : e.getComponent(CargoHoldComponent.class);
    }
}
