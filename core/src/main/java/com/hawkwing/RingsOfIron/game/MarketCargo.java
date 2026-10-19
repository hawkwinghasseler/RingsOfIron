package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.StationMarket;
import com.ringsofiron.macrosim.economy.TraderComponent;

/**
 * Settles carried trades with macro-sim's market: a load carried aboard is bought from the station
 * at the price when it lands, and one carried off is sold. The station's stock stays macro-sim's;
 * its cargo bays are only where the loaders pick up and set down.
 */
public class MarketCargo implements CargoLoading.Settlement {
    private final MacroSimulation macroSim;

    public MarketCargo(MacroSimulation macroSim) {
        this.macroSim = macroSim;
    }

    @Override
    public double unitVolume(String commodity) {
        CommodityType c = macroSim.economy().catalog().commodity(commodity);
        return c == null ? 0 : c.unitVolume;
    }

    @Override
    public double available(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order) {
        EconomyNode node = node(station);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        CommodityType c = macroSim.economy().catalog().commodity(order.commodity);
        if (node == null || trader == null || c == null) return 0;
        StationMarket market = macroSim.market();
        if (!order.toShip) return market.stationBuys(node, order.commodity) ? trader.amount(order.commodity) : 0;
        double units = Math.min(node.available(order.commodity), trader.room(c));
        return affordable(market, node, order.commodity, units, trader.credits);
    }

    @Override
    public double settle(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order, double units) {
        EconomyNode node = node(station);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (node == null || trader == null) return 0;
        return order.toShip ? macroSim.market().buy(node, trader, order.commodity, units)
                : macroSim.market().sell(node, trader, order.commodity, units);
    }

    /** The market of the station entity, or null if it has none. */
    EconomyNode node(Entity station) {
        UuidComponent id = station == null ? null : station.getComponent(UuidComponent.class);
        return id == null ? null : macroSim.station(id.id);
    }

    /** Most of {@code units} that {@code credits} pays for at today's prices. */
    static double affordable(StationMarket market, EconomyNode node, String commodity, double units, double credits) {
        if (units <= 0 || market.buyCost(node, commodity, units) <= credits) return Math.max(0, units);
        double lo = 0, hi = units;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (market.buyCost(node, commodity, mid) <= credits) lo = mid;
            else hi = mid;
        }
        return lo;
    }
}
