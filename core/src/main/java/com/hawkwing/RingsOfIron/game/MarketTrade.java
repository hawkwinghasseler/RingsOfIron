package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.MarketQuote;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.shipops.CargoLoading;
import com.ringsofiron.shipops.CargoOrdersComponent;
import com.ringsofiron.shipops.SalvageSettlement;
import com.ringsofiron.uihud.providers.TradeProvider;

/**
 * ui-hud's market window from macro-sim: the station the player's ship is docked at, its board, and
 * buying and selling with the credits and hold on the ship's {@link TraderComponent}. Trading needs
 * the ship latched to one of the station's docking ports, with the station's clearance.
 *
 * <p>When the station has loaders and both craft have cargo bays ({@link CargoLoading#canCarry}),
 * a trade is an order: the loaders carry it across the dock a load at a time, and each load is
 * paid for as it lands. Otherwise the goods change hands at once.
 *
 * <p>Salvaged parts in the ship's bays are listed too: every station buys them, at
 * {@link Salvage#SALE_SHARE} of their price, and never sells them.
 */
public class MarketTrade implements TradeProvider {
    private final GameWorld world;
    private final CargoLoading.Settlement settlement;

    public MarketTrade(GameWorld world) {
        this.world = world;
        this.settlement = world.cargoSettlement;
    }

    @Override
    public void collect(TradeBoard out) {
        EconomyNode station = dockedStation();
        if (station == null) return;
        out.stationName = station.displayName == null ? "Station" : station.displayName;
        TraderComponent trader = trader();
        Entity player = world.getPlayer(), dock = stationEntity();
        out.hasHold = trader != null;
        out.carried = world.cargo.canCarry(player, dock);
        CargoOrdersComponent orders = player.getComponent(CargoOrdersComponent.class);
        EntityRef ref = dock == null ? null : EntityRef.of(dock);
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
            l.onOrder = orders == null || ref == null ? 0 : orders.pending(ref, q.commodityId);
        }
        // Salvaged parts in the hold: any station buys them, for a share of their price
        CargoHoldComponent hold = player.getComponent(CargoHoldComponent.class);
        if (hold == null) return;
        java.util.Set<String> parts = new java.util.TreeSet<>();
        for (CargoHoldComponent.CargoStack s : hold.stacks) {
            if (s != null && SalvageSettlement.isPart(s.commodity) && world.salvage.salePrice(s.commodity) > 0) parts.add(s.commodity);
        }
        for (String part : parts) {
            TradeLine l = out.addLine();
            l.commodityId = part;
            l.name = "Salvaged " + world.salvage.partName(part).toLowerCase(java.util.Locale.ROOT);
            l.held = hold.amount(part);
            l.sellPrice = world.salvage.salePrice(part);
            l.stationBuys = true;
            l.onOrder = orders == null || ref == null ? 0 : orders.pending(ref, part);
        }
    }

    @Override
    public double buy(String commodityId, double units) {
        EconomyNode station = dockedStation();
        TraderComponent trader = trader();
        if (station == null || trader == null) return 0;
        Entity player = world.getPlayer(), dock = stationEntity();
        if (world.cargo.canCarry(player, dock)) return order(player, dock, commodityId, units, true);
        return world.macroSim.market().buy(station, trader, commodityId, units);
    }

    @Override
    public double sell(String commodityId, double units) {
        EconomyNode station = dockedStation();
        TraderComponent trader = trader();
        if (station == null || trader == null) return 0;
        Entity player = world.getPlayer(), dock = stationEntity();
        boolean part = SalvageSettlement.isPart(commodityId);
        if (world.cargo.canCarry(player, dock)) {
            if (!part && !world.macroSim.market().stationBuys(station, commodityId)) return 0;
            return order(player, dock, commodityId, units, false);
        }
        if (part) return world.cargoSettlement.sellParts(player, commodityId, units);
        return world.macroSim.market().sell(station, trader, commodityId, units);
    }

    /**
     * Orders up to {@code units} carried across, no more than can still change hands once what is
     * already on order has: the stock, the room and the credits for a buy, the hold for a sale.
     */
    private double order(Entity player, Entity dock, String commodityId, double units, boolean toShip) {
        CargoOrdersComponent orders = player.getComponent(CargoOrdersComponent.class);
        CargoOrdersComponent.CargoOrder open = orders == null ? null : orders.find(EntityRef.of(dock), commodityId, toShip);
        CargoOrdersComponent.CargoOrder probe = new CargoOrdersComponent.CargoOrder(EntityRef.of(dock), commodityId, 0, toShip);
        double room = settlement.available(player, dock, probe) - (open == null ? 0 : open.units);
        double qty = Math.min(units, room);
        if (qty <= 1e-6) return 0;
        return world.cargo.order(player, dock, commodityId, qty, toShip);
    }

    private EconomyNode dockedStation() {
        Entity station = stationEntity();
        UuidComponent id = station == null ? null : station.getComponent(UuidComponent.class);
        return id == null ? null : world.macroSim.station(id.id);
    }

    private Entity stationEntity() {
        Entity player = world.getPlayer();
        // A station that refused clearance does not trade with a ship that latched on anyway.
        return player == null || !world.docking.dockedWithClearance(player) ? null : world.docking.dockedTo(player);
    }

    private TraderComponent trader() {
        Entity player = world.getPlayer();
        return player == null ? null : player.getComponent(TraderComponent.class);
    }
}
