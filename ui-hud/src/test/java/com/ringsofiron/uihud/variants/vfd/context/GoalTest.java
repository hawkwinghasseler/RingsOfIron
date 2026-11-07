package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalTest {
    private static Objective contract(String kind, String title) {
        Objective o = new Objective();
        o.kind = kind;
        o.title = title;
        return o;
    }

    @Test
    void theLineSaysTheContractsKindOnce() {
        // The game's own titles lead with the kind for a bounty and a patrol's way home
        assertEquals("Raider of the Void Raiders", Goal.title(contract("Bounty", "Bounty: Raider of the Void Raiders")));
        assertEquals("back to Highport", Goal.title(contract("Patrol", "Patrol: back to Highport")));
        assertEquals("Deliver 120 Water to Highport", Goal.title(contract("Delivery", "Deliver 120 Water to Highport")));
        assertEquals("Bounty: Vex", Goal.title(contract("", "Bounty: Vex")));
    }
}
