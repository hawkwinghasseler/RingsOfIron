package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.nasapunk.stations.ShipCondition.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ship's condition (section 12): what makes Yellow and Red, how long each holds, and the states over it. */
class ShipConditionTest {
    private final ShipCondition c = new ShipCondition();
    private final ShipCondition.Input in = new ShipCondition.Input();

    @Test
    void nothingHostileAboutIsGreen() {
        c.update(in, 0.1f);
        assertEquals(Level.GREEN, c.level);
    }

    @Test
    void aHostileWithinFiftyKilometresIsYellowAndHoldsFiveSeconds() {
        in.hostileNear = true;
        c.update(in, 0.1f);
        assertEquals(Level.YELLOW, c.level);
        in.hostileNear = false;
        c.update(in, 4.9f);
        assertEquals(Level.YELLOW, c.level);
        c.update(in, 0.2f);
        assertEquals(Level.GREEN, c.level);
    }

    @Test
    void eachOfRedsReasonsMakesRed() {
        for (int i = 0; i < 4; i++) {
            ShipCondition s = new ShipCondition();
            ShipCondition.Input x = new ShipCondition.Input();
            x.firedUpon = i == 0;
            x.missilesInbound = i == 1;
            x.hostileInRange = i == 2;
            x.battleStations = i == 3;
            s.update(x, 0.1f);
            assertEquals(Level.RED, s.level, "reason " + i);
        }
    }

    @Test
    void redHoldsTenSecondsThenFallsToYellowWhileTheHostileIsNear() {
        in.missilesInbound = true;
        in.hostileNear = true;
        c.update(in, 0.1f);
        assertEquals(Level.RED, c.level);
        in.missilesInbound = false;
        c.update(in, 9.9f);
        assertEquals(Level.RED, c.level);
        c.update(in, 0.2f);
        assertEquals(Level.YELLOW, c.level);
    }

    @Test
    void theStatesLieOverTheCondition() {
        in.docked = true;
        in.disabled = true;
        in.ratedWatts = 3e6;
        in.outputWatts = 0;
        c.update(in, 0.1f);
        assertTrue(c.docked);
        assertTrue(c.emergency);
        assertTrue(c.blackout);
        assertEquals(Level.GREEN, c.level);
        in.disabled = false;
        in.abandoning = true;
        in.outputWatts = 1e6;
        c.update(in, 0.1f);
        assertTrue(c.emergency, "abandon ship is an emergency too");
        assertFalse(c.blackout);
    }

    @Test
    void aShipWithNoReactorIsNoBlackout() {
        c.update(in, 0.1f);
        assertFalse(c.blackout);
    }
}
