package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.ShipyardProvider.Ship;
import com.ringsofiron.uihud.providers.ShipyardProvider.Yard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardWindowTest {

    @Test
    void theYardOpensDockedAtOneOrWithAPrizeToHandOver() {
        Yard y = new Yard();
        assertFalse(y.isOpen());
        y.prize = "Raider (taken), off Highport";
        assertTrue(y.isOpen(), "a prize in tow shows away from a yard");
        assertEquals("PRIZE", ShipyardWindow.title(y));
        y.stationName = "Highport";
        Ship s = new Ship();
        s.name = "Gunship";
        y.ships.add(s);
        assertEquals("HIGHPORT SHIPYARD", ShipyardWindow.title(y));
        y.clear();
        assertFalse(y.isOpen());
        assertEquals(0, y.ships.size);
        assertEquals("", y.sellLocked);
    }
}
