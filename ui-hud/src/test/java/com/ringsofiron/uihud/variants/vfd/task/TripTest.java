package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripTest {
    private static Objective contract(String kind, String title) {
        Objective o = new Objective();
        o.kind = kind;
        o.title = title;
        return o;
    }

    @Test
    void aRowSaysTheContractsKindOnce() {
        // The game's own titles lead with the kind for a bounty and a patrol's way home
        assertEquals("Raider of the Void Raiders", Trip.title(contract("Bounty", "Bounty: Raider of the Void Raiders")));
        assertEquals("back to Highport", Trip.title(contract("Patrol", "Patrol: back to Highport")));
        assertEquals("Deliver 120 Water to Highport", Trip.title(contract("Delivery", "Deliver 120 Water to Highport")));
        assertEquals("Bounty: Vex", Trip.title(contract("", "Bounty: Vex")));
    }
}
