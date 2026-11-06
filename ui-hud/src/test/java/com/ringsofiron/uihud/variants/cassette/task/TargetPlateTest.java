package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** The target plate's states in the game's words (spec round 14). */
class TargetPlateTest {
    private final HudData d = new HudData();
    private final TargetStatus t = new TargetStatus();

    @Test
    void aHostileFightsWhileItHasTheShipLockedAndIsNotEngagedWhileItHasNone() {
        t.present = true;
        t.relation = Relation.HOSTILE;
        t.state = "fit";
        assertArrayEquals(new String[]{"NOT ENGAGED", ""}, TargetPlate.state(t, null, d));
        t.firingOnUs = true;
        assertArrayEquals(new String[]{"FIGHTING", ""}, TargetPlate.state(t, null, d));
        t.relation = Relation.NEUTRAL;
        t.firingOnUs = false;
        assertArrayEquals(new String[]{"UNDER WAY", ""}, TargetPlate.state(t, null, d));
    }

    @Test
    void aBeatenShipGivesTheGamesReason() {
        Contact c = new Contact();
        c.state = ContactProvider.State.DISABLED;
        c.disabledReason = "NO_POWER";
        assertArrayEquals(new String[]{"DISABLED", "REACTOR DOWN"}, TargetPlate.state(t, c, d));
        c.state = ContactProvider.State.STRUCK;
        assertArrayEquals(new String[]{"SURRENDERED", "HOLD YOUR FIRE"}, TargetPlate.state(t, c, d));
        c.state = ContactProvider.State.WRECKED;
        assertArrayEquals(new String[]{"WRECK", ""}, TargetPlate.state(t, c, d));
        c.state = ContactProvider.State.SILENCED;
        assertArrayEquals(new String[]{"SILENCED", ""}, TargetPlate.state(t, c, d));
        // With no contact, the target's own word for it
        t.state = "HULL";
        assertArrayEquals(new String[]{"DISABLED", "HULL BROKEN"}, TargetPlate.state(t, null, d));
        t.state = "struck";
        assertArrayEquals(new String[]{"SURRENDERED", "HOLD YOUR FIRE"}, TargetPlate.state(t, null, d));
    }
}
