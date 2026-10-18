package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.bubble.MockBubbleGateway;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.economy.MarketQuote;
import com.ringsofiron.macrosim.economy.StationMarket;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.macrosim.rails.stub.StubRailsModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationMarketTest {

    private final EconomySimulation economy = new EconomySimulation(new DefaultCommodityCatalog());
    private final StationMarket market = new StationMarket(economy);

    private EconomyNode farm() {
        EconomyNode node = new EconomyNode("farm-1", "farm", "Greenhouse");
        economy.initialize(node, 1);
        return node;
    }

    @Test
    void buyingMovesGoodsAndCreditsAndRaisesThePrice() {
        EconomyNode farm = farm();
        TraderComponent trader = new TraderComponent(10_000, 100);
        double stock = farm.stock("food");
        double before = farm.price("food");
        double cost = market.buyCost(farm, "food", 50);

        assertEquals(50, market.buy(farm, trader, "food", 50), 1e-9);
        assertEquals(stock - 50, farm.stock("food"), 1e-9);
        assertEquals(50, trader.amount("food"), 1e-9);
        assertEquals(10_000 - cost, trader.credits, 1e-6);
        assertTrue(cost > 50 * before, "paid more than the listed price: " + cost);
        assertTrue(farm.price("food") > before, "price rose after the purchase");
    }

    @Test
    void buyingStopsAtTheHoldTheWalletAndFreighterReservations() {
        EconomyNode farm = farm();
        TraderComponent smallHold = new TraderComponent(1e9, 20);
        assertEquals(20, market.buy(farm, smallHold, "food", 1_000), 1e-9);

        TraderComponent poor = new TraderComponent(100, 1_000);
        double got = market.buy(farm, poor, "food", 1_000);
        assertTrue(got > 0 && got < 20, "bought " + got);
        assertTrue(poor.credits >= -1e-6, "never overdrawn: " + poor.credits);

        farm.reserveOut("food", farm.stock("food") - 5); // promised to a freighter
        TraderComponent rich = new TraderComponent(1e9, 1_000);
        assertEquals(5, market.buy(farm, rich, "food", 1_000), 1e-6);
    }

    @Test
    void buyingAndSellingBackLosesMoney() {
        EconomyNode farm = farm();
        TraderComponent trader = new TraderComponent(10_000, 100);
        market.buy(farm, trader, "food", 80);
        assertEquals(80, market.sell(farm, trader, "food", 80), 1e-9);
        assertTrue(trader.credits < 10_000, "credits " + trader.credits);
        assertTrue(trader.credits > 9_000, "but only the spread: " + trader.credits);
        assertEquals(0, trader.cargoUsed(), 1e-9);
    }

    @Test
    void stationsOnlyBuyWhatTheyTrade() {
        EconomyNode farm = farm();
        TraderComponent trader = new TraderComponent(0, 100);
        trader.cargo.put("ore", 10.0);
        assertEquals(0, market.sell(farm, trader, "ore", 10), 1e-9);
        assertEquals(10, trader.amount("ore"), 1e-9);
        boolean listsWater = false;
        for (MarketQuote q : market.quotes(farm)) {
            assertTrue(!q.commodityId.equals("ore"), "ore is not on a farm's board");
            if (q.commodityId.equals("water")) {
                listsWater = true;
                assertTrue(q.stationBuys && q.sellPrice > 0 && q.buyPrice > q.sellPrice);
            }
        }
        assertTrue(listsWater);
    }

    @Test
    void findsTheStationAShipCanDockAt() {
        double mu = MacroSimDemo.EARTH_MU;
        double r = 8_000e3;
        StubRailsModel rails = new StubRailsModel();
        rails.addBody("p", mu);
        MockBubbleGateway gateway = new MockBubbleGateway("p", StubKeplerOrbit.circular(mu, r, 0, 0, 1));
        MacroSimulation sim = new MacroSimulation(new GameEngine(), rails, gateway, new DefaultCommodityCatalog(), null, 0);
        sim.registerMassiveBody("p");
        String near = sim.addStation("Near", "farm", "p", StubKeplerOrbit.circular(mu, r, 300 / r, 0, 1).stateAt(0, new StateVector()), 1);
        sim.addStation("Far", "farm", "p", StubKeplerOrbit.circular(mu, r, 5e3 / r, 0, 1).stateAt(0, new StateVector()), 1);

        // The player's ship flies in the bubble, at the anchor.
        gateway.spawnFromTrueState("player", StubKeplerOrbit.circular(mu, r, 0, 0, 1).stateAt(0, new StateVector()), 0);
        assertEquals(near, sim.dockableStation("player"));

        gateway.kick("player", 0, 50, 0); // too fast to dock
        assertNull(sim.dockableStation("player"));
    }

    @Test
    void cargoAndCreditsSurviveSaveAndLoad() {
        GameEngine engine = new GameEngine();
        Entity ship = engine.createEntityWithUuid();
        TraderComponent trader = new TraderComponent(1_234.5, 60);
        trader.cargo.put("food", 12.0);
        ship.add(trader);
        engine.addEntity(ship);
        String uuid = ship.getComponent(UuidComponent.class).id;

        String save = engine.getSerializationService().saveGame();
        engine.getSerializationService().loadGame(save);

        TraderComponent loaded = engine.getEntity(uuid).getComponent(TraderComponent.class);
        assertNotNull(loaded);
        assertEquals(1_234.5, loaded.credits, 1e-9);
        assertEquals(60, loaded.cargoCapacity, 1e-9);
        assertEquals(12, loaded.amount("food"), 1e-9);
    }
}
