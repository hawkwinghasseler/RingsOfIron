package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout without a GL context: what takes clicks where, where each zone's widgets stand and that
 * they keep to their zone, and the HUD's two keys; on the 1280 x 800 grid and on bigger stages.
 */
class VfdContextHudTest {
    private static final float[][] STAGES = {{1280f, 800f}, {1920f, 1200f}, {1600f, 900f}, {2560f, 1080f}};
    private static final Presence[] SEATS = {Presence.HELM, Presence.WEAPONS, Presence.GUN};
    private final HudData d = new HudData();
    private final VfdContextHud hud = new VfdContextHud();
    private final FilamentActor actor = new FilamentActor(hud, hud);

    @BeforeEach
    void atTheHelm() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
    }

    private void frame(float w, float h) {
        frame(w, h, false, 0.016f);
    }

    private void frame(float w, float h, boolean fullScreen, float delta) {
        d.ctx.simSeconds += delta;
        hud.layout(d, w, h, delta, fullScreen, null);
        hud.boxes(actor);
    }

    /** A fire on the bridge and the hull hit: a warning and a caution, each with its master lamp. */
    private void alerts() {
        d.alerts.begin(d.ctx.simSeconds);
        d.alerts.raise("fire:Bridge", Level.WARNING, Category.ENGINEERING).set("FIRE", "Fire in the bridge").room("Bridge").handling(1);
        d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%");
        d.alerts.end();
    }

    /** A gunship locked and firing, a missile inbound, three guns, a shield, battle stations. */
    private void fight() {
        d.orderState.battleStations = true;
        d.ctx.condition = Condition.RED;
        TargetStatus t = d.targetStatus;
        t.present = true;
        t.label = "Pirate gunship";
        t.relation = Relation.HOSTILE;
        t.state = "fit";
        t.range = 3_000f;
        t.closing = 40f;
        t.firingOnUs = true;
        Contact c = d.contactFrame.add();
        c.label = "Pirate gunship";
        c.relation = Relation.HOSTILE;
        c.range = 3_000f;
        c.x = 3_000f;
        c.locked = true;
        c.threat = true;
        MissileTrack m = d.threatFrame.add();
        m.side = Side.INCOMING;
        m.x = 2_000f;
        m.range = 2_000f;
        m.timeToImpact = 12f;
        m.shooter = "Pirate gunship";
        for (int i = 0; i < 3; i++) {
            Mount g = d.weaponFrame.add();
            g.name = "GUN " + (i + 1);
            g.weapon = "Railgun";
            g.range = 5_000f;
            g.hitChance = 0.6f;
        }
        ShieldStatus sh = d.shieldStatus;
        sh.present = true;
        sh.emitters = sh.emittersWorking = 2;
        sh.raised = true;
        sh.capacity = 100f;
        sh.cloud = 80f;
        alerts();
    }

    @Test
    void atTheHelmTheLampsTheLightsAndTheScopesKeyTakeClicksAndTheWorldTheRest() {
        alerts();
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1], by = Geo.bottomY(h);
            frame(w, h);
            assertEquals(Rack.Tower.FLIGHT, hud.rack.tower);
            float cy = by + hud.rack.cueY + Geo.CUE_H / 2f;
            assertEquals(Rack.CLICK_WARN, actor.idAt(w / 2f - 4f - Geo.CUE_W / 2f, cy));
            assertEquals(Rack.CLICK_CAUT, actor.idAt(w / 2f + 4f + Geo.CUE_W / 2f, cy));
            assertEquals(VfdContextHud.CLICK_LIGHTS, actor.idAt(Geo.LIGHTS_X + 20f, by + Geo.LIGHTS_Y + 10f));
            // Nothing within 50 km: the scope folds to its key on the right wing
            assertFalse(hud.scope.open);
            assertEquals(Rack.CLICK_SCOPE, actor.idAt(Geo.rightX(w) + Geo.KEY5_X + 24f, by + Geo.KEY5_Y + 24f));
            assertEquals(-1, actor.idAt(w / 2f, h / 2f));
            assertEquals(-1, actor.idAt(w / 2f, by + Geo.MID_Y));
            assertEquals(-1, actor.idAt(Geo.LX + 20f, Geo.MID_Y + 10f));
        }
    }

    @Test
    void theScopesKeyBringsThePodOutAndItsKeysStepTheRangeUntilTheSituationChanges() {
        frame(1280f, 800f);
        hud.click(d, Rack.CLICK_SCOPE);
        frame(1280f, 800f);
        assertTrue(hud.scope.open);
        assertFalse(hud.rack.scopeKey);
        assertEquals(Scope.CRUISE_STEP, hud.scope.step);
        float cx = Geo.SCOPE_CX, cy = Geo.SCOPE_CY, r = Geo.SCOPE_R;
        assertEquals(Scope.CLICK_IN, actor.idAt(cx + r - 4f, cy + r - 4f));
        assertEquals(Scope.CLICK_OUT, actor.idAt(cx - r + 4f, cy + r - 4f));
        hud.click(d, Scope.CLICK_IN);
        frame(1280f, 800f);
        assertEquals(Scope.CRUISE_STEP - 1, hud.scope.step);
        hud.click(d, Scope.CLICK_OUT);
        hud.click(d, Scope.CLICK_OUT);
        frame(1280f, 800f);
        assertEquals(Scope.CRUISE_STEP + 1, hud.scope.step);
        // A fight takes the range back: the smallest step that holds the lock and the missile
        fight();
        frame(1280f, 800f);
        assertTrue(hud.scope.open);
        assertEquals(Scope.holding(3_000f), hud.scope.step);
        // Ten seconds after the fight the pod folds again
        d.orderState.battleStations = false;
        d.targetStatus.present = false;
        d.threatFrame.missiles.clear();
        d.contactFrame.contacts.clear();
        for (int i = 0; i < 11; i++) frame(1280f, 800f, false, 1f);
        assertFalse(hud.sit.combat);
        assertFalse(hud.scope.open);
        assertTrue(hud.rack.scopeKey);
    }

    @Test
    void inAFullScreenModeOnlyTheChipTakesClicks() {
        alerts();
        frame(1280f, 800f, true, 0.016f);
        assertTrue(hud.top.chip);
        assertEquals(Rack.Tower.NONE, hud.rack.tower);
        assertEquals(VfdContextHud.CLICK_CHIP, actor.idAt(hud.top.chipX + 30f, Geo.STRIP_Y + 10f));
        assertEquals(-1, actor.idAt(640f - 4f - Geo.CUE_W / 2f, Geo.CUE_Y_HELM + 10f));
        assertEquals(-1, actor.idAt(Geo.LIGHTS_X + 20f, Geo.LIGHTS_Y + 10f));
        hud.click(d, VfdContextHud.CLICK_CHIP);
        assertTrue(d.alerts.get("fire:Bridge").acknowledged);
        assertFalse(hud.cautions.warn);
    }

    @Test
    void backslashAcknowledgesAndSevenOpensTheOrdersWhichAnOrdersKeyClosesButEscLeavesToTheGame() {
        alerts();
        frame(1280f, 800f);
        assertTrue(hud.cautions.warn);
        assertTrue(hud.key(d, Keys.BACKSLASH));
        assertFalse(hud.cautions.warn);
        assertTrue(d.alerts.get("fire:Bridge").acknowledged);
        assertTrue(hud.key(d, Keys.NUM_7));
        frame(1280f, 800f);
        assertTrue(hud.orders.open);
        // The menu stands on the tower, below the top centre's cards
        float top = hud.ordersFoot - hud.orders.height();
        assertTrue(top >= Geo.TOP_Y + Geo.P3_H, "the menu's top " + top);
        assertTrue(hud.ordersFoot <= hud.rack.cueY, "the menu stands above the lamps");
        // Esc quits the game with no window open, so the HUD never takes it
        assertFalse(hud.key(d, Keys.ESCAPE));
        assertTrue(hud.orders.open);
        assertFalse(hud.key(d, Keys.O), "O stays the lights");
        assertFalse(hud.key(d, Keys.NUM_1), "the order still goes to the game");
        assertFalse(hud.orders.open);
        // With no ship there are no orders to give
        d.ctx.ship = null;
        frame(1280f, 800f);
        assertFalse(hud.key(d, Keys.NUM_7));
        assertFalse(hud.orders.open);
    }

    @Test
    void inAFightEveryColumnsWidgetsKeepToTheirZone() {
        fight();
        // An objective too, on the left above navigation
        d.objectiveFrame.operation = "Clear the lane";
        for (Presence seat : SEATS) {
            d.ctx.presence = seat;
            for (float[] stage : STAGES) {
                float w = stage[0], h = stage[1];
                frame(w, h);
                assertTrue(hud.sit.combat);
                String at = seat + " on " + (int) w + " x " + (int) h + ": ";
                // Left: the objective, navigation and the threats stack down the middle zone
                assertTrue(hud.navY + hud.nav.height() <= Geo.MID_B, at + "navigation ends at " + (hud.navY + hud.nav.height()));
                assertTrue(hud.threatsY + hud.threats.height() <= Geo.MID_B, at + "the threats end at " + (hud.threatsY + hud.threats.height()));
                // Right: the lock card, then the armament
                float right = hud.stripY + hud.arms.stripHeight();
                assertTrue(right <= Geo.MID_B, at + "the right column ends at " + right);
                // Bottom left: F3 above H4's face, both in the bottom zone
                if (hud.shields.on) assertTrue(hud.shieldsFoot - hud.shieldsH >= Geo.BOT_Y, at + "F3's top");
                if (hud.condition.on) assertTrue(hud.faceFoot - hud.condition.height() >= Geo.BOT_Y, at + "H4's top");
                // Top centre: P3 and nothing under it but X3
                assertTrue(hud.interrupts.threat, at + "the missile's P3");
                assertTrue(hud.x3Y >= Geo.TOP_Y + Interrupts.P3_H, at + "X3 below P3");
                // The lamps take clicks on whatever tower the seat has
                float cy = Geo.bottomY(h) + hud.rack.cueY + Geo.CUE_H / 2f;
                assertEquals(Rack.CLICK_WARN, actor.idAt(w / 2f - 4f - Geo.CUE_W / 2f, cy), at + "the warning lamp");
            }
        }
    }

    @Test
    void dockedTheStationPanelStandsOnTheRightAndTheTowerSinksToItsShelf() {
        d.ctx.docked = true;
        d.ctx.stationName = "Highport";
        StationInfo st = d.navReadout.docked;
        st.present = true;
        st.name = "Highport";
        st.owner = "Ring Authority";
        st.market = st.yard = st.contracts = st.hiring = true;
        frame(1280f, 800f);
        assertEquals(Rack.Tower.SHELF, hud.rack.tower);
        assertTrue(hud.panels.station);
        assertEquals(4, hud.panels.lines);
        assertTrue(hud.panelY + hud.panels.stationHeight() <= Geo.MID_B);
        assertFalse(hud.scope.seated);
        assertFalse(hud.rack.scopeKey);
        assertEquals(-1, actor.idAt(Geo.KEY5_X + 24f, Geo.KEY5_Y + 24f));
    }

    @Test
    void theStationPanelSignsTheStandingAndNamesTheInfirmaryOnlyWhileItHasWorkOrABill() {
        d.ctx.docked = true;
        StationInfo st = d.navReadout.docked;
        st.present = true;
        st.name = "Highport";
        st.market = st.infirmary = true;
        st.standing = 0f;
        frame(1280f, 800f);
        assertEquals("+0", hud.panels.standing, "the game's number, signed");
        assertEquals(1, hud.panels.lines, "an infirmary with nobody to treat says nothing");
        st.standing = -12.4f;
        st.treating.add("Ada");
        frame(1280f, 800f);
        assertEquals("-12", hud.panels.standing);
        assertEquals(2, hud.panels.lines);
        assertEquals("INFIRMARY: TREATING ADA", hud.panels.words[1]);
        st.treating.add("Bram");
        frame(1280f, 800f);
        assertEquals("INFIRMARY: TREATING 2", hud.panels.words[1]);
        st.treating.clear();
        st.treated = 1;
        st.treatedCost = 1_150;
        frame(1280f, 800f);
        assertEquals("INFIRMARY: 1 TREATED · 1,150 CR", hud.panels.words[1]);
    }

    @Test
    void aTakenCraftSaysHowItWasTakenAndShowsOnlyTheRowsWithSomethingToSay() {
        BoardingBoard board = new BoardingBoard();
        board.craftName = "Hauler Kestrel";
        board.disabled = board.taken = board.undefended = true;
        board.state = "Taken";
        for (double value : new double[] {100, 7_450}) board.addPart().value = value;
        hud.layout(d, 1280f, 800f, 0.016f, false, board);
        Panels p = hud.panels;
        assertTrue(p.boarding);
        assertEquals("TAKEN: UNDEFENDED", p.state);
        assertFalse(p.partyRow, "nobody fought for it: no party to speak of");
        assertTrue(p.salvRow);
        assertFalse(p.anyMarked);
        assertEquals("2", p.worth);
        assertEquals("7,550 CR", p.worthCr);
        assertFalse(p.cargoRow);
        assertFalse(p.prisoners);
        board.parts.first().marked = true;
        board.undefended = false;
        board.prisoners = 3;
        board.berthsFree = 2;
        board.addCargo().onOrder = 4;
        board.addCargo();
        hud.layout(d, 1280f, 800f, 0.016f, false, board);
        assertEquals("TAKEN: CREW SURRENDERED", p.state);
        assertTrue(p.partyRow);
        assertTrue(p.anyMarked);
        assertEquals("1", p.marked);
        assertEquals("0", p.off);
        assertTrue(p.cargoRow);
        assertEquals("2", p.lots);
        assertEquals("1", p.coming);
        assertTrue(p.prisoners);
        assertEquals("3", p.held);
        assertEquals("2", p.berths);
    }

    @Test
    void aShipInReachIsABoardingOnlyOnceItIsBeaten() {
        BoardingBoard board = new BoardingBoard();
        board.craftName = "Hauler Kestrel";
        board.byEva = true;
        board.state = "Fit to fight";
        board.boardRefusal = "Hauler Kestrel can still fight: disable it first, or storm it";
        hud.layout(d, 1280f, 800f, 0.016f, false, board);
        assertFalse(hud.sit.boarding);
        assertFalse(hud.panels.boarding);
        board.disabled = true;
        board.state = "Disabled: no power";
        board.boardRefusal = "No EVA suits aboard to go across in";
        hud.layout(d, 1280f, 800f, 0.016f, false, board);
        assertTrue(hud.sit.boarding);
        assertTrue(hud.panels.boarding);
        assertEquals("NO EVA SUITS ABOARD TO GO ACROSS", hud.panels.refusal);
        assertTrue(hud.panelY + hud.panels.boardingHeight() <= Geo.MID_B);
    }

    @Test
    void onDeckTheLampsStandAtTheBottomAndNothingElseTakesClicks() {
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        d.captainStatus.present = true;
        alerts();
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1], by = Geo.bottomY(h);
            frame(w, h);
            assertEquals(Rack.Tower.NONE, hud.rack.tower);
            assertEquals(Rack.CLICK_WARN, actor.idAt(w / 2f - 4f - Geo.CUE_W / 2f, by + Geo.CUE_Y_DECK + 10f));
            assertEquals(-1, actor.idAt(Geo.LIGHTS_X + 20f, by + Geo.LIGHTS_Y + 10f));
            assertEquals(-1, actor.idAt(Geo.rightX(w) + Geo.KEY5_X + 24f, by + Geo.KEY5_Y + 24f));
            assertTrue(hud.faceFoot <= Geo.D1_Y, "H4 stands on D1");
        }
    }
}
