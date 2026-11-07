package com.hawkwing.RingsOfIron.app;

import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** V sits at the seat the prompt names, so midway between the helm and a gun it takes the one offered. */
class SeatNamedTest {
    @Test
    void thePromptsConsoleNamesTheSeatAndAnyOtherLeavesItToTheNearest() {
        assertEquals(SubsystemType.HELM, GameInput.seatNamed("helm"));
        assertEquals(SubsystemType.WEAPONS, GameInput.seatNamed("weapons"));
        assertEquals(SubsystemType.TURRET_MOUNT, GameInput.seatNamed("turret_mount"));
        assertNull(GameInput.seatNamed("reactor"));
        assertNull(GameInput.seatNamed("life_support"));
        assertNull(GameInput.seatNamed(""));
    }
}
