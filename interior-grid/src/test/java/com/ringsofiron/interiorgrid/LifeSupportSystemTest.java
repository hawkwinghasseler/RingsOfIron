package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LifeSupportSystemTest {

    private final GridHazardSystem hz = new GridHazardSystem(1);
    private final LifeSupportSystem ls = new LifeSupportSystem();

    private void run(ShipGridComponent g, float seconds) {
        MountFootprint unit = system(g, SubsystemComponent.SubsystemType.LIFE_SUPPORT).footprint;
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            hz.step(g, DT);
            ls.run(g, unit, DT);
        }
    }

    @Test
    void scrubsCarbonDioxideAndSmoke() {
        ShipGridComponent g = grid();
        left(g).nitrogen -= 100;
        left(g).carbonDioxide += 100;
        left(g).smoke += 0.5;
        run(g, 300);
        assertTrue(left(g).carbonDioxideFraction() < 0.002f, "CO2 " + left(g).carbonDioxideFraction());
        assertTrue(left(g).smoke < 0.05, "smoke " + left(g).smoke);
    }

    @Test
    void refillsASealedRoomWithStandardAir() {
        ShipGridComponent g = grid();
        g.getTile(0, 3).setIntegrity(0f);
        run(g, 30);
        double vented = left(g).pressure();
        assertTrue(vented < 60, "vented to " + vented);
        g.getTile(0, 3).bulkheadSealed = true;
        run(g, 600);
        assertEquals(Compartment.STANDARD_PRESSURE, left(g).pressure(), 2.0);
        assertEquals(Compartment.STANDARD_O2_FRACTION, left(g).oxygenFraction(), 0.005, "the refill is ordinary air");
    }

    @Test
    void addsNothingToARoomStillVentingToSpace() {
        ShipGridComponent withUnit = grid(), without = grid();
        for (ShipGridComponent g : new ShipGridComponent[]{withUnit, without}) {
            door(g).bulkheadSealed = true;
            g.getTile(0, 3).setIntegrity(0f);
        }
        run(withUnit, 60);
        TestShips.run(hz, without, 60);
        assertEquals(left(without).totalMoles(), left(withUnit).totalMoles(), 1e-6);
        assertTrue(left(withUnit).pressure() < Compartment.STANDARD_PRESSURE / 2);
    }

    @Test
    void doesNotServeRoomsBehindAClosedDoor() {
        ShipGridComponent g = grid();
        door(g).bulkheadSealed = true;
        run(g, 1);
        right(g).nitrogen -= 100;
        right(g).carbonDioxide += 100;
        double carbonDioxide = right(g).carbonDioxide;
        run(g, 120);
        assertEquals(carbonDioxide, right(g).carbonDioxide, 1e-9);
    }

    @Test
    void keepsOxygenNearTheStandardShareWhileRefilling() {
        ShipGridComponent g = grid();
        for (Compartment c : g.compartments) c.scaleContents(0.5);
        run(g, 60);
        assertEquals(Compartment.STANDARD_O2_FRACTION, left(g).oxygenFraction(), 0.005);
        assertTrue(left(g).pressure() > Compartment.STANDARD_PRESSURE * 0.5 + 5);
    }
}
