package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The post, the conditions and the visibility table (functional-task 2, 8). */
class PostsTest {
    private final HudData d = new HudData();
    private final Posts p = new Posts();

    @BeforeEach
    void aShip() {
        d.ctx.ship = new Entity();
    }

    private void at(Presence presence, boolean outside) {
        d.ctx.presence = presence;
        d.ctx.exteriorView = outside;
        p.update(d, false);
    }

    @Test
    void thePostIsWhereTheCaptainIs() {
        at(Presence.HELM, true);
        assertEquals(Posts.Post.HELM, p.post);
        at(Presence.WEAPONS, true);
        assertEquals(Posts.Post.GUNNERY, p.post);
        assertFalse(p.atGun);
        at(Presence.GUN, true);
        assertEquals(Posts.Post.GUNNERY, p.post);
        assertTrue(p.atGun);
        at(Presence.DECK, false);
        assertEquals(Posts.Post.DECK, p.post);
        at(Presence.EVA, true);
        assertEquals(Posts.Post.DECK, p.post);
    }

    @Test
    void atRestTheHelmShowsTheFlightInstrumentAndTheScopeButNoWeapons() {
        d.weaponFrame.add().kind = Kind.GUN;
        at(Presence.HELM, true);
        assertTrue(p.flight());
        assertTrue(p.scope());
        assertFalse(p.weapons());
        assertFalse(p.helm(false));
        assertTrue(p.helm(true));
        assertTrue(p.common());
    }

    @Test
    void inCombatTheHelmShowsTheWeaponsCompactAndGunneryAlwaysExpanded() {
        d.weaponFrame.add().kind = Kind.GUN;
        d.orderState.battleStations = true;
        at(Presence.HELM, true);
        assertTrue(p.combat);
        assertTrue(p.weapons());
        assertFalse(p.weaponsExpanded());
        d.orderState.battleStations = false;
        at(Presence.WEAPONS, true);
        assertTrue(p.weapons());
        assertTrue(p.weaponsExpanded());
        assertFalse(p.flight());
        assertTrue(p.helm(false));
    }

    @Test
    void dockedHidesTheWeaponsAndTheScopeUnlessAHostileIsNear() {
        d.ctx.docked = true;
        at(Presence.WEAPONS, true);
        assertFalse(p.weapons());
        assertFalse(p.scope());
        assertFalse(p.helm(true));
        Contact c = d.contactFrame.add();
        c.relation = Relation.HOSTILE;
        c.range = 40_000f;
        at(Presence.WEAPONS, true);
        assertTrue(p.scope());
    }

    @Test
    void railsWarpHidesTheFlightInstrumentTheScopeAndTheWeapons() {
        d.ctx.warp = 10_000;
        d.ctx.railsWarp = true;
        at(Presence.HELM, true);
        assertTrue(p.rails);
        assertFalse(p.flight());
        assertFalse(p.scope());
        // Physics warp is only the clock's
        d.ctx.warp = 10;
        d.ctx.railsWarp = false;
        at(Presence.HELM, true);
        assertTrue(p.flight());
    }

    @Test
    void aCrisisIsDisabledAbandoningOrStrandedWithATowOnOffer() {
        at(Presence.HELM, true);
        assertFalse(p.crisis);
        d.ctx.disabledReason = "no reactor running";
        at(Presence.HELM, true);
        assertTrue(p.crisis);
        assertTrue(p.disabled);
        d.ctx.disabledReason = "";
        d.orderState.towFee = 5_000;
        at(Presence.DECK, false);
        assertTrue(p.stranded);
        d.orderState.towRefusal = "your ship can still fly home";
        at(Presence.DECK, false);
        assertFalse(p.crisis);
    }

    @Test
    void theDeckShowsItsOwnWidgetsAndAScreenHidesTheZones() {
        at(Presence.DECK, false);
        assertTrue(p.deck());
        assertFalse(p.flight());
        assertFalse(p.scope());
        assertFalse(p.targetCard(true));
        assertTrue(p.helm(false));
        p.update(d, true);
        assertFalse(p.common());
        assertFalse(p.deck());
    }
}
