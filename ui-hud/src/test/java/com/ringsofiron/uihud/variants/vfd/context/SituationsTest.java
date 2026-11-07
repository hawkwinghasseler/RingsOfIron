package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.context.Situations.Seat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The situations of functional-context 4.2, with their entry and exit rules, read from the data alone. */
class SituationsTest {
    private final HudData d = new HudData();
    private final Situations s = new Situations();

    @BeforeEach
    void aboardAtTheHelm() {
        d.ctx.ship = new Entity();
        d.ctx.presence = Presence.HELM;
    }

    private void frames(int n, float delta) {
        for (int i = 0; i < n; i++) s.update(d, false, false, false, delta);
    }

    @Test
    void theSeatIsWhereTheCaptainIs() {
        d.ctx.presence = Presence.WEAPONS;
        frames(1, 0.1f);
        assertEquals(Seat.FIRE_CONTROL, s.seat);
        d.ctx.presence = Presence.GUN;
        frames(1, 0.1f);
        assertEquals(Seat.GUN, s.seat);
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        frames(1, 0.1f);
        assertEquals(Seat.DECK, s.seat);
        assertFalse(s.exterior());
        // The outside view from the deck counts as the helm
        d.ctx.exteriorView = true;
        frames(1, 0.1f);
        assertEquals(Seat.HELM, s.seat);
        d.ctx.presence = Presence.EVA;
        frames(1, 0.1f);
        assertEquals(Seat.DECK, s.seat);
        d.ctx.presence = Presence.NONE;
        frames(1, 0.1f);
        assertEquals(Seat.NONE, s.seat);
    }

    @Test
    void combatHoldsTenSecondsAfterItsLastCause() {
        frames(1, 0.1f);
        assertTrue(s.cruise);
        d.orderState.battleStations = true;
        frames(1, 0.1f);
        assertTrue(s.combat);
        assertFalse(s.cruise);
        d.orderState.battleStations = false;
        frames(95, 0.1f);
        assertTrue(s.combat, "still standing to after 9.5 s");
        frames(6, 0.1f);
        assertFalse(s.combat);
        assertTrue(s.cruise);
    }

    @Test
    void aHostileLockInsideTheGunsReachIsCombat() {
        d.targetStatus.present = true;
        d.targetStatus.relation = Relation.HOSTILE;
        d.targetStatus.range = Situations.GUN_RANGE + 500f;
        frames(1, 0.1f);
        assertFalse(s.combat);
        d.targetStatus.range = Situations.GUN_RANGE - 500f;
        frames(1, 0.1f);
        assertTrue(s.combat);
    }

    @Test
    void anApproachStartsInsideFiveKilometresAndWaitsThreeSecondsWhenItsTriggerGoes() {
        d.docking.present = true;
        d.docking.capture = Capture.APPROACH;
        d.docking.other = "Highport";
        d.docking.range = 6_000f;
        d.navReadout.present = true;
        d.navReadout.kind = Kind.STATION;
        d.navReadout.name = "Highport";
        frames(1, 0.1f);
        assertFalse(s.approach, "beyond 5 km");
        d.docking.range = 4_000f;
        frames(1, 0.1f);
        assertTrue(s.approach);
        d.docking.range = 5_200f;
        frames(1, 0.1f);
        assertTrue(s.approach, "it holds to 5.5 km");
        // The nav target moves on: three seconds' grace
        d.navReadout.name = "Elsewhere";
        frames(25, 0.1f);
        assertTrue(s.approach);
        frames(6, 0.1f);
        assertFalse(s.approach);
        // Past 5.5 km it ends at once
        d.navReadout.name = "Highport";
        d.docking.range = 4_000f;
        frames(1, 0.1f);
        assertTrue(s.approach);
        d.docking.range = 5_600f;
        frames(1, 0.1f);
        assertFalse(s.approach);
    }

    @Test
    void aHostilesPortIsNoApproach() {
        d.docking.present = true;
        d.docking.capture = Capture.APPROACH;
        d.docking.other = "Raider";
        d.docking.range = 1_000f;
        d.navReadout.present = true;
        d.navReadout.kind = Kind.SHIP;
        d.navReadout.name = "Raider";
        d.navReadout.relation = Relation.HOSTILE;
        frames(1, 0.1f);
        assertFalse(s.approach);
        // Arming the port is the captain's intent, whoever is on the other side
        d.docking.armed = true;
        frames(1, 0.1f);
        assertTrue(s.approach);
    }

    @Test
    void theShipDisabledIsAnEmergencyUnlessItsPowerIsComingBack() {
        d.ctx.disabledReason = "No power";
        s.update(d, false, true, false, 0.1f);
        assertFalse(s.disabled);
        assertFalse(s.emergency);
        s.update(d, false, false, false, 0.1f);
        assertTrue(s.disabled);
        assertTrue(s.emergency);
        assertFalse(s.cruise);
    }

    @Test
    void suitAirRunningLowAwayFromAirIsAnEmergency() {
        CaptainStatus c = d.captainStatus;
        c.present = true;
        c.suited = true;
        c.suitAir = Situations.SUIT_WARN - 40f;
        c.roomPressure = 0.1f;
        frames(1, 0.1f);
        assertTrue(s.suitAir);
        assertTrue(s.emergency);
        c.roomPressure = 1f;
        frames(1, 0.1f);
        assertFalse(s.suitAir, "in a room fit to breathe");
        c.roomPressure = Float.NaN;
        c.suitAir = Situations.SUIT_WARN + 60f;
        frames(1, 0.1f);
        assertFalse(s.suitAir);
    }

    @Test
    void dockedAndLatchedNeedAShip() {
        d.ctx.docked = true;
        frames(1, 0.1f);
        assertTrue(s.docked);
        assertFalse(s.cruise);
        d.ctx.ship = null;
        frames(1, 0.1f);
        assertFalse(s.docked);
        d.ctx.ship = new Entity();
        d.ctx.docked = false;
        d.ctx.latched = true;
        frames(1, 0.1f);
        assertTrue(s.latched);
        assertTrue(s.boarding);
    }
}
