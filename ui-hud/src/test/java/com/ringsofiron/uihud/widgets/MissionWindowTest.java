package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionWindowTest {

    @Test
    void theBoardOpensDockedOrWithAContractHeld() {
        MissionBoard b = new MissionBoard();
        assertFalse(b.isOpen());
        assertEquals("CONTRACTS", MissionWindow.title(b));
        b.addHeld().title = "Bounty: Raider";
        assertTrue(b.isOpen(), "held contracts show away from a station");
        b.stationName = "Highport";
        b.addOffer().title = "Patrol out to Deepwell Depot and back";
        assertEquals("HIGHPORT MISSION BOARD", MissionWindow.title(b));
        b.clear();
        assertFalse(b.isOpen());
        assertEquals(0, b.offers.size);
        assertEquals(0, b.held.size);
    }
}
