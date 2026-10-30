package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.economy.StationMarket;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.shipsystems.ShipWeaponPower;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pieces carried cargo and weapons settle through: moving goods hold to hold with no money
 * (the outpost, and salvage carried home), how much a wallet buys, a trader trading out of the
 * ship's bays, and guns drawing on the ship's power.
 */
class CargoSettlementTest {
    private final DefaultCommodityCatalog catalog = new DefaultCommodityCatalog();

    /** A craft with a hold of {@code tiles} bay tiles, one meter each. */
    private static Entity craft(int tiles) {
        int[] xy = new int[tiles * 2];
        for (int i = 0; i < tiles; i++) xy[i * 2] = i;
        Entity e = new Entity();
        e.add(new CargoHoldComponent(1f, xy));
        return e;
    }

    private static CargoHoldComponent hold(Entity e) {
        return e.getComponent(CargoHoldComponent.class);
    }

    private static CargoOrdersComponent.CargoOrder order(String commodity, double units, boolean toShip) {
        return new CargoOrdersComponent.CargoOrder(null, commodity, units, toShip);
    }

    @Test
    void holdTransferMovesGoodsBothWaysWithNoMoney() {
        Entity ship = craft(4), station = craft(4);
        hold(station).load("ore", 6, 0.5, 1250);
        HoldTransfer transfer = new HoldTransfer(catalog);

        assertEquals(6, transfer.available(ship, station, order("ore", 6, true)), 1e-9);
        assertEquals(4, transfer.settle(ship, station, order("ore", 4, true), 4), 1e-9);
        assertEquals(4, hold(ship).amount("ore"), 1e-9);
        assertEquals(2, hold(station).amount("ore"), 1e-9);

        assertEquals(3, transfer.settle(ship, station, order("ore", 3, false), 3), 1e-9);
        assertEquals(1, hold(ship).amount("ore"), 1e-9);
        assertEquals(5, hold(station).amount("ore"), 1e-9);
        assertEquals(0.5, transfer.unitVolume("ore"), 1e-9);
    }

    @Test
    void holdTransferStopsAtTheRoomInTheReceivingHold() {
        Entity ship = craft(1), station = craft(8);
        hold(station).load("water", 15, 1.0, 1000);
        double room = hold(ship).room("water", 1.0);
        assertTrue(room < 15, "the one-tile hold cannot take it all: " + room);

        HoldTransfer transfer = new HoldTransfer(catalog);
        assertEquals(room, transfer.available(ship, station, order("water", 15, true)), 1e-9);
        double moved = transfer.settle(ship, station, order("water", 15, true), 15);
        assertEquals(room, moved, 1e-9);
        assertEquals(15, hold(ship).amount("water") + hold(station).amount("water"), 1e-9, "nothing lost or made");
    }

    @Test
    void holdTransferDoesNothingWithoutBothHolds() {
        Entity ship = craft(2), bare = new Entity();
        hold(ship).load("food", 1, 1.0, 500);
        HoldTransfer transfer = new HoldTransfer(catalog);
        assertEquals(0, transfer.available(ship, bare, order("food", 1, false)), 1e-9);
        assertEquals(0, transfer.settle(ship, bare, order("food", 1, false), 1), 1e-9);
        assertEquals(0, transfer.settle(ship, null, order("food", 1, false), 1), 1e-9);
        assertEquals(1, hold(ship).amount("food"), 1e-9, "the goods stayed aboard");
    }

    @Test
    void affordableIsTheMostAWalletPaysFor() {
        EconomySimulation economy = new EconomySimulation(catalog);
        StationMarket market = new StationMarket(economy);
        EconomyNode farm = new EconomyNode("farm-1", "farm", "Greenhouse");
        economy.initialize(farm, 1);

        assertEquals(10, MarketCargo.affordable(market, farm, "food", 10, 1e9), 1e-9, "a rich buyer takes all");
        assertEquals(0, MarketCargo.affordable(market, farm, "food", 0, 1e9), 1e-9);
        assertEquals(0, MarketCargo.affordable(market, farm, "food", -5, 1e9), 1e-9);

        double credits = market.buyCost(farm, "food", 10) / 2;
        double units = MarketCargo.affordable(market, farm, "food", 10, credits);
        assertTrue(units > 0 && units < 10, "a short wallet buys part: " + units);
        assertTrue(market.buyCost(farm, "food", units) <= credits + 1e-6, "and can pay for it");
        assertTrue(market.buyCost(farm, "food", units + 0.01) > credits, "but no more");
    }

    @Test
    void aTraderAboardTradesOutOfTheShipsBays() {
        Entity ship = craft(4);
        TraderComponent trader = new TraderComponent(1_000, 0);
        ship.add(trader);
        ShipCargo.attach(ship);

        assertEquals(hold(ship).capacity(), trader.hold.capacity(), 1e-9);
        hold(ship).load("metals", 2, 0.3, 2400);
        assertEquals(2, trader.amount("metals"), 1e-9, "what is in the bays is the trader's");
        assertEquals(hold(ship).room("food", 1.0), trader.room(catalog.commodity("food")), 1e-9);
    }

    @Test
    void gunsDrawFromTheCircuitOfTheSystemTheyAreWiredTo() {
        ShipWeaponPower power = new ShipWeaponPower();
        WeaponMountComponent mount = new WeaponMountComponent();
        assertTrue(power.tryDraw(mount, 1e12), "a mount wired to nothing has unlimited power");
        assertEquals(500, power.draw(mount, 500), 1e-9);

        SubsystemComponent weapons = new SubsystemComponent(new MountFootprint(MountFootprint.MountKind.AUXILIARY,
                MountFootprint.MountSize.S, 0, 0, 0), SubsystemComponent.SubsystemType.WEAPONS);
        Entity system = new Entity();
        system.add(weapons);
        mount.subsystem = system;
        assertFalse(power.tryDraw(mount, 1), "no circuit, no power");
        assertEquals(0, power.draw(mount, 1), 1e-9);

        weapons.circuit = new PowerCircuit();
        weapons.circuit.stored = weapons.circuit.capacity = 100;
        assertTrue(power.tryDraw(mount, 60));
        assertFalse(power.tryDraw(mount, 60), "all or nothing when the store is short");
        assertEquals(40, power.draw(mount, 60), 1e-9, "a partial draw takes what is left");

        weapons.circuit.stored = 100;
        weapons.isOperational = false;
        assertFalse(power.tryDraw(mount, 1), "a broken weapons system passes no power");
        assertEquals(0, power.draw(mount, 1), 1e-9);
    }
}
