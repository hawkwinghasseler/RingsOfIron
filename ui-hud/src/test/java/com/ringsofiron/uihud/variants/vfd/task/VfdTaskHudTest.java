package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout without a GL context: what takes clicks where, which widgets each post shows and that
 * the zones keep clear of each other, and the HUD's key; on the 1280 x 800 grid and on bigger stages.
 */
class VfdTaskHudTest {
    private static final float[][] STAGES = {{1280f, 800f}, {1920f, 1200f}, {1600f, 900f}, {2560f, 1080f}};
    private final HudData d = new HudData();
    private final VfdTaskHud hud = new VfdTaskHud();
    private final FilamentActor actor = new FilamentActor(hud, hud);

    @BeforeEach
    void atTheHelm() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
        d.flightStatus.present = true;
    }

    private void frame(float w, float h) {
        frame(w, h, false, 0.016f);
    }

    private void frame(float w, float h, boolean screen, float delta) {
        d.ctx.simSeconds += delta;
        hud.layout(d, w, h, delta, screen, null, null);
        hud.boxes(actor);
    }

    private void fire() {
        d.alerts.begin(d.ctx.simSeconds);
        d.alerts.raise("fire:Hold", Level.CAUTION, Category.ENGINEERING).set("FIRE", "Fire in the hold").room("Hold").handling(1);
        d.alerts.end();
    }

    /** A raider locked 3.2 km off and firing, two harpoons inbound, five mounts, battle stations. */
    private void fight() {
        d.orderState.battleStations = true;
        TargetStatus t = d.targetStatus;
        t.present = true;
        t.label = "Raider 'Vex'";
        t.relation = Relation.HOSTILE;
        t.range = 3_200f;
        t.closing = 41f;
        t.firingOnUs = true;
        Contact c = d.contactFrame.add();
        c.label = "Raider 'Vex'";
        c.relation = Relation.HOSTILE;
        c.kind = Kind.SHIP;
        c.range = 3_200f;
        c.x = 3_200f;
        c.locked = true;
        for (int i = 0; i < 2; i++) {
            MissileTrack m = d.threatFrame.add();
            m.side = Side.INCOMING;
            m.x = 1_400f;
            m.range = 1_400f;
            m.timeToImpact = 4f;
        }
        String[] names = {"NOSE GUN", "FWD PORT PD", "FWD STBD PD", "AFT STBD BEAM", "AFT PORT MSL"};
        for (String n : names) {
            Mount g = d.weaponFrame.add();
            g.name = n;
            g.weapon = "Coilgun";
            g.controller = Controller.PLAYER;
        }
    }

    @Test
    void atRestOnlyTheScopesKeysTakeClicksAndTheMessageCentreIsDark() {
        Contact c = d.contactFrame.add();
        c.label = "Highport";
        c.kind = Kind.STATION;
        c.range = 3_000f;
        c.x = -1_500f;
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1], rx = Geo.rightX(w), by = Geo.bottomY(h);
            frame(w, h);
            assertTrue(hud.flight.on);
            assertEquals(Flight.Mode.CRUISE, hud.flight.mode);
            assertTrue(hud.scope.on);
            assertFalse(hud.weapons.on, "no weapons out at rest");
            assertFalse(hud.helm.on, "no order stands");
            assertFalse(hud.schematic.on, "nothing lit");
            assertEquals(Scope.CLICK_OUT, actor.idAt(rx + Geo.ZOOM_OUT_X + 4f, by + Geo.ZOOM_Y + 4f));
            assertEquals(Scope.CLICK_IN, actor.idAt(rx + Geo.ZOOM_IN_X + 4f, by + Geo.ZOOM_Y + 4f));
            assertEquals(-1, actor.idAt(w / 2f, h / 2f));
            assertEquals(-1, actor.idAt(Geo.midX(w) + Geo.BX + 20f, Geo.WARN_Y + 10f), "an empty message centre takes no click");
            // The 5 km step holds Highport, 3.0 km off
            assertEquals(2, hud.scope.step);
            assertTrue(hud.scope.auto);
        }
    }

    @Test
    void aClickOnTheMessageCentreAcknowledgesTheCautions() {
        fire();
        frame(1280f, 800f);
        assertEquals(1, hud.catalogue.row.size);
        assertEquals(VfdTaskHud.CLICK_BAND, actor.idAt(Geo.BX + 40f, Geo.CAUT_Y + 10f));
        hud.click(d, VfdTaskHud.CLICK_BAND);
        frame(1280f, 800f);
        assertEquals(0, hud.catalogue.row.size, "the caution leaves the band and lives on as its lamp");
        assertEquals(Catalogue.Lit.CAUTION, hud.catalogue.lamps[Catalogue.Lamp.FIRE.ordinal()]);
    }

    @Test
    void backslashAcknowledgesAndEveryOtherKeyGoesOnToTheGame() {
        fire();
        frame(1280f, 800f);
        assertTrue(hud.key(d, Keys.BACKSLASH));
        frame(1280f, 800f);
        assertEquals(0, hud.catalogue.row.size);
        assertFalse(hud.key(d, Keys.W));
        assertFalse(hud.key(d, Keys.NUM_3));
    }

    @Test
    void atGunneryInAFightTheTableTheScopeAndTheHelmsLineShareTheBottomWithoutTouching() {
        fight();
        d.ctx.presence = Presence.WEAPONS;
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1];
            frame(w, h);
            assertFalse(hud.flight.on, "the helmsman flies");
            assertTrue(hud.weapons.on);
            assertTrue(hud.weapons.expanded);
            assertEquals(5, hud.weapons.rows);
            assertTrue(hud.scope.on);
            assertTrue(hud.helm.on);
            assertTrue(hud.target.on);
            // The 5 km step holds the raider and both harpoons with room to spare
            assertEquals(2, hud.scope.step);
            assertEquals(1, hud.scope.hostiles);
            // HLM ends left of the pod; the table's top stays under the target card
            float midX = Geo.midX(w), rx = Geo.rightX(w), by = Geo.bottomY(h);
            assertTrue(midX + hud.helm.x + hud.helm.w + 4f < rx + Geo.POD_X, "HLM clear of the weapons pod at " + w);
            assertTrue(Geo.TGT_Y + hud.target.height() + 5f < by + hud.weapons.top() - 8f, "TGT clear of the pod at " + w + "x" + h);
            assertTrue(Geo.TGT_Y + hud.target.height() + 5f < by + Geo.SCOPE_POD_Y, "TGT clear of the scope at " + w + "x" + h);
            assertTrue(Geo.LX + Geo.CW + 6f < midX + hud.helm.x, "HLM clear of the schematic");
        }
    }

    @Test
    void atTheHelmInAFightTheWeaponsGoCompactBesideTheFlightInstrument() {
        fight();
        for (float[] stage : STAGES) {
            float w = stage[0], h = stage[1];
            frame(w, h);
            assertTrue(hud.flight.on);
            assertTrue(hud.weapons.on);
            assertFalse(hud.weapons.expanded);
            float fltRight = Geo.midX(w) + Geo.FLT_X + Geo.FLT_W + 6f, podLeft = Geo.rightX(w) + Weapons.COMPACT_X - 8f;
            assertTrue(fltRight <= podLeft, "FLT clear of the compact weapons at " + w);
        }
    }

    @Test
    void dockedTheFlightInstrumentDepartsAndTheGunsAndScopeStandDown() {
        fight();
        d.orderState.battleStations = false;
        d.threatFrame.clear();
        d.targetStatus.present = false;
        d.contactFrame.clear();
        d.ctx.docked = true;
        d.ctx.stationName = "Highport";
        frame(1280f, 800f);
        assertEquals(Flight.Mode.DEPARTURE, hud.flight.mode);
        assertFalse(hud.weapons.on);
        assertFalse(hud.scope.on);
        assertFalse(hud.helm.on);
        assertTrue(hud.flight.shows("UNDOCK"));
    }

    @Test
    void aClickOnTheHelmsLineListsTheOrdersAndANumberKeyClosesIt() {
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        d.ctx.helmsman = "Bea";
        frame(1280f, 800f);
        assertTrue(hud.helm.on);
        assertEquals("BEA", hud.helm.who);
        assertEquals("HOLDING HEADING", hud.helm.order);
        assertEquals(Helm.CLICK, actor.idAt(Geo.FLT_X + 20f, Geo.HLM_Y + 10f));
        hud.click(d, Helm.CLICK);
        frame(1280f, 800f);
        assertTrue(hud.helm.open);
        assertEquals(Helm.CLICK_LIST, actor.idAt(Geo.FLT_X + 20f, hud.helm.listY() + 10f));
        hud.key(d, Keys.NUM_3);
        assertFalse(hud.helm.open);
    }

    @Test
    void overAScreenOnlyTheChronometerTheBandAndTheCrisisPanelStay() {
        fire();
        frame(1280f, 800f, true, 0.016f);
        assertFalse(hud.flight.on);
        assertFalse(hud.scope.on);
        assertFalse(hud.keys.on);
        assertFalse(hud.trip.on);
        assertEquals(VfdTaskHud.CLICK_BAND, actor.idAt(Geo.BX + 40f, Geo.CAUT_Y + 10f));
        assertEquals(-1, actor.idAt(Geo.rightX(1280f) + Geo.ZOOM_IN_X + 4f, Geo.ZOOM_Y + 4f));
    }

    @Test
    void theKeyStripDropsAKeyUsedThreeTimesKeepsF1AndLeavesTheModesKeysOut() {
        hint("W/S", "thrust", "THRUST");
        hint("M", "map", "MAP");
        hint("/", "warp to burn", "WARP_TO_BURN");
        hint("F1", "controls", "HELP");
        d.navReadout.burnStartIn = 60f;
        d.navReadout.turnIn = 30f;
        frame(1280f, 800f);
        assertEquals(Flight.Mode.BURN, hud.flight.mode);
        // The burn's key is BURN's, so the strip leaves it out
        assertEquals(3, hud.keys.count);
        assertEquals("THRUST", hud.keys.words[0]);
        assertEquals("MAP", hud.keys.words[1]);
        assertEquals("CONTROLS", hud.keys.words[2]);
        for (int i = 0; i < KeyStrip.LEARNED; i++) hud.key(d, Keys.M);
        frame(1280f, 800f);
        assertEquals(2, hud.keys.count);
        assertEquals("THRUST", hud.keys.words[0]);
        assertEquals("CONTROLS", hud.keys.words[1]);
        // In a crisis, F1 alone
        d.ctx.disabledReason = "no reactor running";
        frame(1280f, 800f);
        assertEquals(1, hud.keys.count);
        assertEquals("CONTROLS", hud.keys.words[0]);
    }

    @Test
    void theKeyStripsWordsAreTheGamesWhole() {
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        hint("W/A/S/D", "walk", "THRUST");
        hint("V", "sit at the console", "VIEW");
        hint("F", "force a shut door", "DECOY");
        hint("B", "cut a shut door", "CUT");
        hint("F1", "controls", "HELP");
        frame(1280f, 800f);
        assertEquals(Posts.Post.DECK, hud.posts.post);
        assertEquals(5, hud.keys.count);
        assertEquals("SIT AT THE CONSOLE", hud.keys.words[1]);
        assertEquals("FORCE A SHUT DOOR", hud.keys.words[2]);
        assertEquals("CUT A SHUT DOOR", hud.keys.words[3]);
    }

    @Test
    void onDeckThePromptKeepsToTheBottomEdgeOverTheKeyStripAndShortOfTheHelmsLine() {
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        d.promptAction = "to put on a pressure suit";
        d.promptKey = "E";
        hint("E", "use", "INTERACT");
        hint("F1", "controls", "HELP");
        for (float[] s : STAGES) {
            frame(s[0], s[1]);
            assertEquals(Posts.Post.DECK, hud.posts.post);
            assertTrue(hud.deck.prompt);
            assertEquals("PUT ON A PRESSURE SUIT", hud.deck.verbs[0]);
            float strip = hud.bottomY + Geo.HNT_Y;
            // Its own slot at the left edge, just over the key strip: not by the captain in the middle of the view
            assertEquals(Geo.LX, hud.deck.promptX, 0.01f);
            assertTrue(hud.deck.promptY + Deck.PROMPT_H <= strip - 4f, "over the strip at " + s[0] + "x" + s[1]);
            assertTrue(hud.deck.promptY >= strip - 40f, "against the strip at " + s[0] + "x" + s[1]);
            assertTrue(hud.deck.promptY > s[1] * 0.75f, "low on the stage at " + s[0] + "x" + s[1]);
            // Short of the helm's line beside it
            assertTrue(hud.deck.promptX + hud.deck.promptMax <= hud.midX + hud.helm.x - 4f, "short of HLM at " + s[0] + "x" + s[1]);
            // The strip leaves out the key the prompt shows
            assertEquals(1, hud.keys.count);
        }
        d.promptAction = "";
        frame(1280f, 800f);
        assertFalse(hud.deck.prompt);
    }

    private void hint(String key, String label, String action) {
        KeyHint h = d.keyHints.add();
        h.key = key;
        h.label = label;
        h.action = action;
    }

    @Test
    void disabledTheCrisisPanelOffersTheTowAndThePodsAndTheStripLeavesTheirKeysOut() {
        d.ctx.disabledReason = "no reactor running";
        d.orderState.towFee = 2_400;
        d.orderState.pods = 2;
        d.orderState.freeSeats = 4;
        d.orderState.crewAboard = 3;
        hint("B", "call a tow", "CUT");
        hint("F1", "controls", "HELP");
        frame(1280f, 800f);
        assertEquals(Crisis.Kind.DISABLED, hud.crisis.kind);
        assertEquals(3, hud.crisis.rows);
        assertTrue(hud.crisis.lines[1].startsWith("CALL A TOW  2,400 CR"), hud.crisis.lines[1]);
        assertTrue(hud.crisis.lines[2].contains("2 PODS, 4 SEATS FOR 3 CREW"), hud.crisis.lines[2]);
        assertEquals(1, hud.keys.count);
    }
}
