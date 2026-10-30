package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.fleet.FreighterComponent;

/**
 * Settles the loads carried to and from a freighter's bays. macro-sim has already settled its
 * trade (bought the goods off the station, or sold them) by the time they are carried, so a load
 * only moves the goods: into the freighter's bays as it lands aboard, out as it is carried off.
 * Every other ship's loads go to {@code others}, the market.
 */
public class FreighterCargo implements CargoLoading.Settlement {
    private final CargoLoading.Settlement others;
    private final CommodityCatalog catalog;

    public FreighterCargo(CargoLoading.Settlement others, CommodityCatalog catalog) {
        this.others = others;
        this.catalog = catalog;
    }

    @Override
    public double unitVolume(String commodity) {
        return others.unitVolume(commodity);
    }

    @Override
    public double available(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order) {
        if (!isFreighter(ship)) return others.available(ship, station, order);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        CommodityType c = catalog.commodity(order.commodity);
        if (hold == null || c == null) return 0;
        return order.toShip ? Math.min(order.units, hold.room(order.commodity, c.unitVolume)) : hold.amount(order.commodity);
    }

    @Override
    public double settle(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order, double units) {
        if (!isFreighter(ship)) return others.settle(ship, station, order, units);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        CommodityType c = catalog.commodity(order.commodity);
        if (hold == null || c == null) return 0;
        return order.toShip ? hold.load(order.commodity, units, c.unitVolume, c.unitMass) : hold.unload(order.commodity, units);
    }

    private static boolean isFreighter(Entity ship) {
        return ship != null && ship.getComponent(FreighterComponent.class) != null;
    }
}
