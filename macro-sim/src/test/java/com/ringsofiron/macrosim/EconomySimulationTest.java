package com.ringsofiron.macrosim;

import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.economy.TradeOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomySimulationTest {

    @Test
    void farmTurnsWaterIntoFoodAndStopsWhenWaterRunsOut() {
        EconomySimulation eco = new EconomySimulation(new DefaultCommodityCatalog());
        EconomyNode farm = new EconomyNode("farm", "farm", "Farm");
        farm.add("water", 100);
        eco.updatePrices(farm);
        eco.pass(List.of(farm), 86400); // one day: 4 cycles want 80 water, make 60 food
        assertEquals(20, farm.stock("water"), 1e-9);
        assertEquals(60, farm.stock("food"), 1e-9);
        eco.pass(List.of(farm), 86400); // only 20 water left: a quarter of a day's production
        assertEquals(0, farm.stock("water"), 1e-9);
        assertEquals(75, farm.stock("food"), 1e-9);
        assertEquals(0.25, farm.lastProductionRatio, 1e-9);
    }

    @Test
    void pricesRiseWhenStockFallsBelowTarget() {
        EconomySimulation eco = new EconomySimulation(new DefaultCommodityCatalog());
        EconomyNode hab = new EconomyNode("hab", "habitat", "Habitat");
        eco.initialize(hab, 1.0);
        double base = hab.price("food");
        hab.remove("food", hab.stock("food") * 0.8);
        eco.updatePrices(hab);
        assertTrue(hab.price("food") > base * 2, "scarce food is expensive");
        hab.add("food", 10_000);
        eco.updatePrices(hab);
        assertEquals(eco.catalog().commodity("food").basePrice * EconomyNode.MIN_PRICE_FACTOR, hab.price("food"), 1e-9);
    }

    @Test
    void surplusIsMatchedToTheDeficitThatPaysMost() {
        EconomySimulation eco = new EconomySimulation(new DefaultCommodityCatalog());
        EconomyNode farm = new EconomyNode("farm", "farm", "Farm");
        EconomyNode hab = new EconomyNode("hab", "habitat", "Habitat");
        eco.initialize(farm, 1.0);
        eco.initialize(hab, 0.2);
        farm.add("food", 500);
        eco.pass(List.of(farm, hab), 1);
        TradeOrder food = eco.orders().stream().filter(o -> o.commodity.equals("food")).findFirst().orElseThrow();
        assertEquals("farm", food.fromUuid);
        assertEquals("hab", food.toUuid);
        assertTrue(food.unitMargin() > 0);

        eco.claim(food, "f1", farm, hab);
        eco.pass(List.of(farm, hab), 1);
        assertTrue(eco.orders().contains(food), "claimed orders survive a rebuild");
        assertFalse(eco.orders().stream().anyMatch(o -> o != food && o.commodity.equals("food")
                && o.quantity > hab.projectedStock("food")), "incoming cargo counts against new orders");
    }
}
