package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The context Cassette HUD's text budgets (spec 5.6): every word that goes on flaps fits its cells, every
 * line on a tube or a card fits its characters, and what is cut says so with an ellipsis.
 */
class ContextTextTest {
    private static final String LONG = "Extraordinarily long-winded station name of the outer belt cooperative";

    @Test
    void theHandlingFieldIsThreeCharacters() {
        Conditions.Row r = new Conditions.Row();
        for (int crew = -1; crew < 400; crew++) {
            r.crew = crew;
            r.handling = Conditions.Handling.CREW;
            assertTrue(r.handlingText().length() <= 3, crew + ": " + r.handlingText());
        }
        r.handling = Conditions.Handling.YOU;
        assertEquals("YOU", r.handlingText());
    }

    @Test
    void theRailsChipCountsOnOneDrumEach() {
        // A closed window at zero, the count to nine, and nine for more
        assertEquals(" ", HeaderRail.count(0));
        assertEquals("3", HeaderRail.count(3));
        assertEquals("9", HeaderRail.count(12));
        // The chip's window sits between BATTLE STATIONS (to 610) and the clock, which stands right of it
        assertTrue(HeaderRail.CHIP_X >= 612f, "chip at " + HeaderRail.CHIP_X);
        assertTrue(624f + HeaderRail.AFTER >= HeaderRail.CHIP_X + HeaderRail.CHIP_W + 8f, "clock at " + (624f + HeaderRail.AFTER));
    }

    @Test
    void aRowsDetailIsTenCharacters() {
        Conditions.Row r = new Conditions.Row();
        r.detail = "ENGINEERING BAY";
        r.places = 12;
        String s = Conditions.detail(r);
        assertTrue(s.length() <= 10, s);
        assertTrue(s.endsWith(" +11"), s);
        assertTrue(s.contains("…"), s);
        r.detail = "HOLD";
        r.places = 1;
        assertEquals("HOLD", Conditions.detail(r));
    }

    @Test
    void theMoreRowFitsItsFlaps() {
        Array<Conditions.Row> rows = new Array<>();
        String[] codes = {"FIRE", "BREACH", "VACUUM", "SMOKE", "HULL DAMAGE", "SHIELD DOWN", "SYSTEM DOWN", "DOCK STRAIN", "POWER LOW"};
        for (String code : codes) {
            Conditions.Row r = new Conditions.Row();
            r.code = code;
            rows.add(r);
        }
        String more = Conditions.more(rows, Conditions.ROWS);
        assertTrue(more.length() <= 34, more);
        assertTrue(more.startsWith("+4 more: "), more);
        assertTrue(more.endsWith("…"), more);
    }

    @Test
    void wrappedLinesKeepTheirWidthAndSayWhenCut() {
        List<String> rows = Kit.wrap(LONG, 20, 2);
        assertEquals(2, rows.size());
        for (String row : rows) assertTrue(row.length() <= 20, row);
        assertTrue(rows.get(1).endsWith("…"), rows.get(1));
        assertEquals(List.of("Short"), Kit.wrap("Short", 20, 2));
    }

    @Test
    void aSqueezedNameKeepsItsFigures() {
        String s = Kit.squeeze("SHACKLETON ICE PROCESSING", 24, " · 1,240 KM");
        assertTrue(s.endsWith(" · 1,240 KM"), s);
        assertTrue(s.length() <= 24, s);
        assertTrue(s.contains("…"), s);
        assertEquals("ICE · 3 KM", Kit.squeeze("ICE", 24, " · 3 KM"));
    }

    @Test
    void theHeaderAndTheWordsOnFlapsFitTheirCells() {
        HudData d = new HudData();
        ContextFrame f = new ContextFrame();
        d.ctx.disabledReason = "no power to the reactor control circuits after the last hit";
        String head = EmergencyPlacard.headline(EmergencyPlacard.State.DISABLED, d, f);
        assertTrue(head.length() <= 34, head);
        assertTrue(head.startsWith("SHIP DISABLED: "), head);
        assertEquals("dead in space", EmergencyPlacard.reason(""));
        // The orders panel's reasons tube
        String why = OrdersPanel.sentence("you can't dock while the station has not given clearance, and the port is taken by another ship.");
        assertTrue(why.length() <= 56, why);
        assertTrue(Character.isUpperCase(why.charAt(0)));
        // The party flaps hold eleven
        BoardingBoard b = new BoardingBoard();
        b.craftName = "Corsair";
        b.boardRefusal = "No one to send";
        assertEquals("CAN'T GO", BoardingPanel.party(b));
        b.boarding = true;
        assertEquals("CROSSING", BoardingPanel.party(b));
        b.aboard = 3;
        b.defenders = 2;
        assertEquals("FIGHTING", BoardingPanel.party(b));
        b.defenders = 0;
        assertEquals("ABOARD", BoardingPanel.party(b));
        b.taken = true;
        assertEquals("COMING HOME", BoardingPanel.party(b));
        assertTrue(BoardingPanel.party(b).length() <= 11);
        // The suit unit's condition flaps hold eight, the lock card's state flaps eleven
        CaptainStatus c = new CaptainStatus();
        c.present = true;
        for (String state : new String[]{"conscious", "unconscious", "dead"}) {
            c.state = state;
            for (float health : new float[]{1f, 0.7f, 0.4f, 0.1f}) {
                c.health = health;
                assertTrue(SuitUnit.condition(c).length() <= 8, SuitUnit.condition(c));
            }
        }
        TargetStatus t = new TargetStatus();
        for (String s : new String[]{"", "fit", "struck", "silenced", "running", "wrecked", "disabled", "no_power"}) {
            t.state = s;
            assertTrue(LockCard.state(t, null).length() <= 11, s + ": " + LockCard.state(t, null));
        }
    }

    @Test
    void theRacksWordsFitTheirFlaps() {
        HudData d = new HudData();
        for (com.ringsofiron.uihud.providers.WeaponStatusProvider.State st : com.ringsofiron.uihud.providers.WeaponStatusProvider.State.values()) {
            for (Kind k : Kind.values()) {
                for (Controller who : Controller.values()) {
                    d.weaponFrame.clear();
                    Mount m = d.weaponFrame.add();
                    m.name = "PORT FORWARD GUN 2";
                    m.kind = k;
                    m.state = st;
                    m.controller = who;
                    m.cooldown = 1234f;
                    for (boolean locked : new boolean[]{false, true}) {
                        String s = WeaponsRack.state(m, locked);
                        assertTrue(s.length() <= 10, st + " " + k + ": " + s);
                        assertTrue(ArmamentPlate.guns(d.weaponFrame, locked).length() <= 10);
                    }
                    assertTrue(WeaponsRack.who(d, m).length() <= 5, WeaponsRack.who(d, m));
                    assertTrue(ArmamentPlate.who(d).length() <= 9, ArmamentPlate.who(d));
                    d.weaponFrame.missilesLeft = 4;
                    assertTrue(ArmamentPlate.missiles(d.weaponFrame).length() <= 9, ArmamentPlate.missiles(d.weaponFrame));
                }
            }
        }
        ShieldStatus s = new ShieldStatus();
        assertTrue(ArmamentPlate.shields(s).length() <= 8, ArmamentPlate.shields(s));
    }

    @Test
    void aLongReasonGivesUpItsAsideFirst() {
        String why = OrdersPanel.sentence("No burns planned for the helm to fly (N plans one, ; places it on the path).");
        assertEquals("No burns planned for the helm to fly", why);
        assertEquals("The helm needs a navigation target for that",
                OrdersPanel.sentence("the helm needs a navigation target for that (Q picks one)"));
        assertEquals("The helm needs a target (Q picks one)", OrdersPanel.sentence("the helm needs a target (Q picks one)."),
                "a reason that fits keeps its aside");
    }

    @Test
    void thePromptsBracketedKeysBecomeCells() {
        List<String[]> parts = PromptPlate.split("for the Gun panel   [V] take the gun");
        assertEquals(3, parts.size());
        assertEquals("for the Gun panel", parts.get(0)[1]);
        assertEquals("V", parts.get(1)[0]);
        assertEquals("take the gun", parts.get(2)[1]);
        assertEquals(1, PromptPlate.split("to put on a pressure suit").size());
        assertEquals("an [unclosed bracket", PromptPlate.split("an [unclosed bracket").get(0)[1]);
    }

    @Test
    void thePlacardsWaysOutSayWhatTheFirstPressDid() {
        HudData d = new HudData();
        d.orderState.towFee = 4200;
        assertEquals("P twice: abandon ship · B twice: tow, 4,200 cr", EmergencyPlacard.keysLine(d));
        d.orderState.abandonArmed = 2.5f;
        assertTrue(EmergencyPlacard.keysLine(d).startsWith("P again: abandon ship"), EmergencyPlacard.keysLine(d));
        d.orderState.towRefusal = "missiles are inbound; no tug will come into that";
        assertTrue(EmergencyPlacard.keysLine(d).endsWith(" · no tow: missiles are inbound"), EmergencyPlacard.keysLine(d));
        assertTrue(EmergencyPlacard.oneLine(EmergencyPlacard.State.DISABLED, d).contains("P again: abandon"));
        // Latched or docked, the pods won't go: the game's words name the way out instead
        d.orderState.abandonRefusal = "docked: walk off instead";
        String both = EmergencyPlacard.keysLine(d);
        assertEquals("Docked: walk off instead · no tow: missiles are inbound", both);
        assertTrue(both.length() <= 56, "fits the placard's line: " + both);
    }

    @Test
    void theIntercomsSourcesAreFiveCharacters() {
        String[] who = {"helm", "gunnery", "shields", "engineering", "medical", "crew", "boarding", "station", "sponsor", "comms", "log", "", "Navigator Okafor"};
        String[] body = {"Game saved", "Docking clearance granted", "Hit on the target", "Fire in the hold", "", "Burn complete"};
        for (String w : who) {
            for (String t : body) {
                String tag = Intercom.tag(w, t);
                assertTrue(tag.length() <= 5, w + "/" + t + ": " + tag);
            }
        }
        // A suit locker or an airlock is not a target lock
        assertEquals("CREW", Intercom.tag("", "The air here is going: get to a pressurised room, or a suit locker to put on a suit"));
        assertEquals("GUNS", Intercom.tag("", "Target locked: Pirate raider 6"));
    }

    @Test
    void theBoardingCardSaysHowTheCraftStandsAndWhoIsHeld() {
        BoardingBoard b = new BoardingBoard();
        b.craftName = "Pirate raider 3";
        b.state = "Disabled: reactor down";
        b.disabled = true;
        b.defenders = 3;
        assertEquals("Disabled: reactor down", BoardingPanel.state(b));
        assertEquals("READY", BoardingPanel.party(b));
        b.boarding = true;
        b.aboard = 1;
        assertEquals("Being boarded", BoardingPanel.state(b));
        // Taken: the party comes home, and the prisoners are held with the berths free for them
        b.boarding = false;
        b.taken = true;
        b.defenders = 0;
        b.prisoners = 3;
        b.state = "Taken, 3 held";
        assertEquals("Taken: crew held", BoardingPanel.state(b));
        assertEquals("COMING HOME", BoardingPanel.party(b));
        b.aboard = 0;
        assertEquals(null, BoardingPanel.party(b), "home: the party has nothing to say");
        assertEquals(4, BoardingPanel.berthsFree("4 / 8 berths, 470 cr/wk in wages, 3 medical stores"));
        assertEquals(0, BoardingPanel.berthsFree("9 / 8 berths, 0 cr/wk in wages, 0 medical stores"));
        assertEquals(-1, BoardingPanel.berthsFree(""));
        // A craft nobody defends: no party goes, and the salvage takes it
        BoardingBoard u = new BoardingBoard();
        u.craftName = "Raider";
        u.state = "Disabled: crew down";
        u.disabled = true;
        u.salvage = true;
        assertEquals("Nobody aboard: a part marked takes it", BoardingPanel.state(u));
        assertEquals(null, BoardingPanel.party(u));
        u.taken = true;
        assertEquals("Taken: undefended", BoardingPanel.state(u));
    }

    @Test
    void theSalvageRowCountsThePartsWorthTakingAndWhatTheySellFor() {
        BoardingBoard b = new BoardingBoard();
        b.salvage = true;
        String[] names = {"Laser Mk1", "Reactor", "Escape Pod", "Harpoon Launcher"};
        double[] values = {2250, 2000, 0, 1500};
        for (int i = 0; i < names.length; i++) {
            Part p = b.addPart();
            p.name = names[i];
            p.value = values[i];
        }
        assertEquals(3, BoardingPanel.worth(b), "an escape pod is worth nothing: left out");
        assertEquals(5750, BoardingPanel.worthTotal(b), 1e-9);
        assertEquals("PARTS · 5,750 CR", BoardingPanel.worthWords(b));
        // Off its hull, a part is no longer one to take
        b.parts.get(0).marked = b.parts.get(0).off = true;
        assertEquals(2, BoardingPanel.worth(b));
        assertEquals("PARTS · 3,500 CR", BoardingPanel.worthWords(b));
        b.parts.get(1).off = true;
        assertEquals("PART · 1,500 CR", BoardingPanel.worthWords(b));
        assertEquals(2, BoardingPanel.off(b));
    }

    @Test
    void theStationCardGivesTheStandingAsANumberAndItsServicesOnlyWhenTheySaySomething() {
        assertEquals("TANKS 72%", StationPanel.yardNote(0, "Propellant 72%, oxygen 100%"));
        assertEquals("2 REPAIRS · TANKS 86%", StationPanel.yardNote(2, "Propellant 86%, oxygen 100%"));
        assertEquals("1 REPAIR", StationPanel.yardNote(1, ""));
        assertEquals("", StationPanel.yardNote(0, ""));
        com.ringsofiron.uihud.providers.CrewProvider.CrewSheet sheet = new com.ringsofiron.uihud.providers.CrewProvider.CrewSheet();
        com.ringsofiron.uihud.providers.CrewProvider.Member ada = sheet.addMember();
        ada.name = "Ada";
        ada.condition = "OK 100%";
        assertEquals("", StationPanel.treating(sheet), "day 0: nobody hurt, no infirmary line");
        ada.condition = "Serious wound, being treated";
        com.ringsofiron.uihud.providers.CrewProvider.Member cole = sheet.addMember();
        cole.name = "Cole";
        cole.condition = "Unconscious 40%";
        assertEquals("TREATING ADA +1", StationPanel.treating(sheet));
        cole.condition = "Light wound 80%";
        assertEquals("TREATING ADA", StationPanel.treating(sheet));
    }

    @Test
    void aGunIsItsWeaponAndItsMount() {
        assertEquals("FWD PORT", Kit.place("FWD PORT GUN"));
        assertEquals("AFT 2", Kit.place("AFT PD 2"));
        assertEquals("GUN", Kit.place("GUN"));
        Mount m = new Mount();
        m.name = "FWD STBD PD";
        m.weapon = "Autocannon Mk1";
        assertEquals("Autocannon Mk1 (FWD STBD)", Kit.gun(m));
        // P1 names the gun seat by its mount
        HudData d = new HudData();
        ContextFrame f = new ContextFrame();
        f.seat = ContextFrame.Seat.GUN;
        com.ringsofiron.uihud.providers.StationBoardProvider.Seat s = d.stations.add();
        s.post = com.ringsofiron.uihud.providers.StationBoardProvider.Post.GUN;
        s.hand = com.ringsofiron.uihud.providers.StationBoardProvider.Hand.PLAYER;
        s.name = "FWD PORT GUN";
        assertEquals("GUN (FWD PORT)", HeaderRail.seat(d, f));
        assertTrue(HeaderRail.seat(d, f).length() <= 14);
        // At a berth the ship can't fly, so nobody's helm is news
        f.docked = true;
        d.ctx.helmsman = "";
        assertEquals("", HeaderRail.authority(d, f));
    }

    @Test
    void pointDefenseInHandIsAGunLikeAnyOther() {
        HudData d = new HudData();
        Mount m = d.weaponFrame.add();
        m.name = "FWD PORT PD";
        m.weapon = "Autocannon Mk1";
        m.kind = Kind.POINT_DEFENSE;
        m.controller = Controller.AUTO;
        m.defending = true;
        assertTrue(WeaponsRack.pdFree(m));
        assertEquals("ENGAGING", WeaponsRack.state(m, true));
        assertEquals("PD", WeaponsRack.who(d, m));
        // Taken at its gun: it shoots where the captain aims, out of arc or not
        m.controller = Controller.PLAYER;
        m.defending = false;
        m.inArc = false;
        assertTrue(!WeaponsRack.pdFree(m));
        assertEquals("OUT OF ARC", WeaponsRack.state(m, true));
        assertEquals("YOU", WeaponsRack.who(d, m));
    }

    @Test
    void aConsolesPromptNamesItThenWhatEDoesThere() {
        // E is the object's main action (P6): at a seat it sits, at a system's console it opens Ship › Systems
        assertEquals("Helm · [E] sit and fly", PromptPlate.prompt("E", "for the Helm panel   [V] sit and fly", null));
        assertEquals("Weapons · [E] sit and fight", PromptPlate.prompt("E", "for the Weapons panel   [V] sit and fight", null));
        assertEquals("Reactor · [E] systems", PromptPlate.prompt("E", "for the Reactor panel", null));
        assertEquals("Life support · [E] systems", PromptPlate.prompt("E", "for the Life support panel", null));
        assertEquals("[E] put on a pressure suit", PromptPlate.prompt("E", "to put on a pressure suit", null));
        Mount gun = new Mount();
        gun.name = "FWD GUN";
        gun.weapon = "Coilgun Mk1";
        assertEquals("Coilgun Mk1 (FWD) · [E] take the gun", PromptPlate.prompt("E", "for the Gun panel   [V] take the gun", gun));
        gun.name = "FWD PORT PD";
        gun.weapon = "Autocannon Mk1";
        gun.kind = Kind.POINT_DEFENSE;
        assertEquals("Autocannon Mk1 (FWD PORT) · [E] take the gun (point defense pauses)",
                PromptPlate.prompt("E", "for the Gun panel   [V] take the gun", gun));
        // Not knowing which gun, the console's own title; the interact key as the player has bound it
        assertEquals("Gun · [E] take the gun", PromptPlate.prompt("E", "for the Gun panel   [V] take the gun", null));
        assertEquals("Helm · [F] sit and fly", PromptPlate.prompt("F", "for the Helm panel   [V] sit and fly", null));
    }
}
