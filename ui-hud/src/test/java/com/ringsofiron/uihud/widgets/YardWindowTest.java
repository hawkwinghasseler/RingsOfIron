package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YardWindowTest {

    @Test
    void theDamageLineSaysWhatRepairsCost() {
        YardBoard b = new YardBoard();
        assertEquals("Nothing needs repairing", YardWindow.damageLine(b));
        b.damaged = 4;
        b.repairCost = 3_870;
        assertEquals("4 damaged, 3,870 cr to repair", YardWindow.damageLine(b));
    }

    @Test
    void hardpointNamesReadAsLabels() {
        assertEquals("Nose", YardWindow.capitalized("the nose"));
        assertEquals("Port", YardWindow.capitalized("port"));
    }

    @Test
    void boardClearsBackToNoYard() {
        YardBoard b = new YardBoard();
        b.stationName = "Kepler Outpost";
        b.addHardpoint().name = "aft";
        b.addOffer().name = "Laser";
        assertTrue(b.isOpen());
        b.clear();
        assertFalse(b.isOpen());
        assertEquals(0, b.hardpoints.size);
        assertEquals(0, b.offers.size);
    }
}
