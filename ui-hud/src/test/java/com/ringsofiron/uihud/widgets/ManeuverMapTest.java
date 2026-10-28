package com.ringsofiron.uihud.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManeuverMapTest {

    @Test
    void aHandleGivesMoreTheFartherItIsDragged() {
        assertEquals(ManeuverMap.DRAG_DV, ManeuverMap.dragDeltaV(ManeuverMap.DRAG_UNIT, false), 1e-9);
        assertEquals(4 * ManeuverMap.DRAG_DV, ManeuverMap.dragDeltaV(2 * ManeuverMap.DRAG_UNIT, false), 1e-9);
        assertEquals(0, ManeuverMap.dragDeltaV(0, false), 1e-9);
    }

    @Test
    void draggedBackItTakesAwayAndShiftIsFiner() {
        assertEquals(-ManeuverMap.DRAG_DV, ManeuverMap.dragDeltaV(-ManeuverMap.DRAG_UNIT, false), 1e-9);
        assertEquals(ManeuverMap.DRAG_DV / 10, ManeuverMap.dragDeltaV(ManeuverMap.DRAG_UNIT, true), 1e-9);
    }
}
