package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.MarketQuote;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.TradeProvider;

/**
 * ui-hud's market window from macro-sim: the station the player's ship is in trading range of, its
 * board, and buying and selling with the credits and hold on the ship's {@link TraderComponent}.
 */
public class MarketTrade implements TradeProvider {
    private final GameWorld world;

    public MarketTrade(GameWorld world) {
        this.world = world;
    }

    @Override
    public void collect(TradeBoard out) {
        EconomyNode station = dockedStation();
        if (station == null) return;
        out.stationName = station.displayName == null ? "Station" : station.displayName;
        TraderComponent trader = trader();
        out.hasHold = trader != null;
        if (trader != null) {
            out.credits = trader.credits;
            out.cargoUsed = trader.cargoUsed();
            out.cargoCapacity = trader.cargoCapacity;
        }
        for (MarketQuote q : world.macroSim.market().quotes(station)) {
            TradeLine l = out.addLine();
            l.commodityId = q.commodityId;
            l.name = q.displayName == null ? q.commodityId : q.displayName;
            l.available = q.available;
            l.held = trader == null ? 0 : trader.amount(q.commodityId);
            l.buyPrice = q.buyPrice;
            l.sellPrice = q.sellPrice;
            l.stationBuys = q.stationBuys;
        }
    }

    @Override
    public double buy(String commodityId, double units) {
        EconomyNode station = dockedStation();
        TraderComponent trader = trader();
        if (station == null || trader == null) return 0;
        return world.macroSim.market().buy(station, trader, commodityId, units);
    }

    @Override
    public double sell(String commodityId, double units) {
        EconomyNode station = dockedStation();
        TraderComponent trader = trader();
        if (station == null || trader == null) return 0;
        return world.macroSim.market().sell(station, trader, commodityId, units);
    }

    private EconomyNode dockedStation() {
        Entity player = world.getPlayer();
        String uuid = player == null ? null : world.universe.uuidOf(player);
        if (uuid == null) return null;
        String station = world.macroSim.dockableStation(uuid);
        return station == null ? null : world.macroSim.station(station);
    }

    private TraderComponent trader() {
        Entity player = world.getPlayer();
        return player == null ? null : player.getComponent(TraderComponent.class);
    }
}
