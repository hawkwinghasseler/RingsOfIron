package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GridHazardSystemTest {

    private final GridHazardSystem hz = new GridHazardSystem(1);

    @Test
    void airIsConservedWhileTheShipIsSealed() {
        ShipGridComponent g = grid();
        left(g).nitrogen += 500;
        double before = totalMoles(g);
        run(hz, g, 60);
        assertEquals(before, totalMoles(g), before * 1e-6);
        assertEquals(left(g).pressure(), right(g).pressure(), 0.5, "the open door evens out the pressure");
    }

    @Test
    void aBreachVentsTheShipUntilItIsSealed() {
        ShipGridComponent g = grid();
        g.getTile(0, 3).setIntegrity(0f);
        run(hz, g, 10);
        assertTrue(g.isVenting());
        assertTrue(left(g).pressure() < 90, "left room loses air: " + left(g).pressure());

        g.getTile(0, 3).bulkheadSealed = true;
        run(hz, g, 1);
        double sealed = totalMoles(g);
        run(hz, g, 30);
        assertFalse(g.isVenting());
        assertEquals(sealed, totalMoles(g), sealed * 1e-6, "no more air is lost once sealed");
    }

    @Test
    void aClosedDoorHoldsAirBack() {
        ShipGridComponent g = grid();
        door(g).bulkheadSealed = true;
        run(hz, g, 1);
        double right = right(g).pressure();
        g.getTile(0, 3).setIntegrity(0f);
        run(hz, g, 30);
        assertTrue(left(g).pressure() < 50);
        assertEquals(right, right(g).pressure(), 0.01);
    }

    @Test
    void gasesMixMostOfTheWayThroughAnOpenDoorInTwoMinutes() {
        ShipGridComponent g = grid();
        left(g).nitrogen -= 200;
        left(g).carbonDioxide += 200;
        float start = left(g).carbonDioxideFraction() - right(g).carbonDioxideFraction();
        run(hz, g, 120);
        float gap = left(g).carbonDioxideFraction() - right(g).carbonDioxideFraction();
        assertTrue(gap > 0 && gap < start * 0.4f, "CO2 gap " + start + " -> " + gap);
    }

    @Test
    void gasesDoNotMixThroughAClosedDoor() {
        ShipGridComponent g = grid();
        door(g).bulkheadSealed = true;
        run(hz, g, 1);
        left(g).nitrogen -= 200;
        left(g).carbonDioxide += 200;
        run(hz, g, 120);
        assertEquals(0f, right(g).carbonDioxideFraction(), 1e-6f);
    }

    @Test
    void fireSpreadsOverMinutesAndBurnsOutWhenTheAirRunsOut() {
        ShipGridComponent g = grid();
        g.getTile(5, 3).ignite();
        run(hz, g, 30);
        assertTrue(burning(g) < 10, "after 30 s only a few tiles burn: " + burning(g));
        run(hz, g, 120);
        assertTrue(burning(g) > 1, "the fire has spread after 2.5 minutes");
        double oxygenBefore = left(g).oxygen + right(g).oxygen;

        run(hz, g, 1200);
        assertFalse(anyFire(g), "with no life support the fire smothers itself");
        assertTrue(left(g).oxygen + right(g).oxygen < oxygenBefore);
        assertTrue(left(g).carbonDioxide > 0 && left(g).smoke > 0);
    }

    @Test
    void fireTemperaturePeaksWellBelowTheOldRunaway() {
        ShipGridComponent g = grid();
        g.getTile(5, 3).ignite();
        float peak = 0;
        for (int i = 0; i < 600 / DT; i++) {
            hz.step(g, DT);
            for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) peak = Math.max(peak, g.getTile(x, y).temperature);
        }
        assertTrue(peak > GridHazardSystem.IGNITION_TEMPERATURE && peak < 1800, "peak " + peak);
    }

    @Test
    void heatDamagesSystemsUntilTheyFail() {
        ShipGridComponent g = grid();
        SubsystemComponent lifeSupport = system(g, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        g.getTile(4, 3).ignite();
        g.getTile(4, 5).ignite();
        run(hz, g, 400);
        assertTrue(lifeSupport.health < 1f, "health " + lifeSupport.health);
        assertTrue(lifeSupport.isDisabled(), "life support in the fire fails: " + lifeSupport.health);
        assertTrue(g.gridDirty || !lifeSupport.isOperational);
    }

    @Test
    void overpressureBlowsTheDoorButNotTheWall() {
        ShipGridComponent g = grid();
        door(g).bulkheadSealed = true;
        run(hz, g, 1);
        assertEquals(2, g.compartments.size);
        left(g).nitrogen += left(g).totalMoles() * 2.5; // about 350 kPa against 101
        hz.step(g, DT);
        assertEquals(0f, door(g).integrity, "the light door gives way");
        for (int y = 1; y <= 6; y++) {
            if (y != 3) assertEquals(1f, g.getEdge(10, y, Side.LEFT).integrity, "wall at y=" + y + " holds");
        }
        run(hz, g, 60);
        assertEquals(left(g).pressure(), right(g).pressure(), 5.0, "air rushes through the broken door");
    }

    @Test
    void doorsHoldOrdinaryPressureDifferences() {
        ShipGridComponent g = grid();
        door(g).bulkheadSealed = true;
        run(hz, g, 1);
        g.getTile(0, 3).setIntegrity(0f); // left room to vacuum: 101 kPa across the door
        run(hz, g, 60);
        assertTrue(door(g).integrity > 0f);
        assertTrue(GridHazardSystem.burstPressure(WeightClass.LIGHT) > Compartment.STANDARD_PRESSURE);
    }

    private static int burning(ShipGridComponent g) {
        int n = 0;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) if (g.getTile(x, y).isBurning()) n++;
        return n;
    }
}
