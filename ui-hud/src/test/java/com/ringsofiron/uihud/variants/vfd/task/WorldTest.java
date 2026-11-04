package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What OVL and TGT read off a contact's label, which carries the game's tags for the classic HUD. */
class WorldTest {
    private final Array<Objective> objectives = new Array<>();
    private final NavReadout nav = new NavReadout();

    private Objective objective(String title) {
        Objective o = new Objective();
        o.title = title;
        o.kind = "Bounty";
        objectives.add(o);
        return o;
    }

    @Test
    void aContractsWaypointIsOneByItsTagOrByName() {
        assertTrue(World.contract("Kestrel [contract]", objectives, nav, false), "the game's tag");
        assertFalse(World.contract("Pirate raider 6 (surrendered)", objectives, nav, false));
        objective("Bounty on Pirate raider 6");
        assertTrue(World.contract("Pirate raider 6 (surrendered)", objectives, nav, false), "named, tag and all");
        assertFalse(World.contract("Freighter", objectives, nav, false));
    }

    @Test
    void theTargetCardKnowsTheSameCraftByItsBareName() {
        HudData d = new HudData();
        assertEquals("CONTRACT", TargetCard.contract(d, "Kestrel [contract]"));
        assertEquals("", TargetCard.contract(d, "Freighter"));
        assertTrue(TargetCard.sameName("Pirate raider 6 (surrendered)", "Pirate raider 6"));
        assertFalse(TargetCard.sameName("Pirate raider 6", "Pirate raider 7"));
    }
}
