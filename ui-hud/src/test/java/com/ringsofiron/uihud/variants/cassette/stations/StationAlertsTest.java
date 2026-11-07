package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.Input;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.variants.cassette.stations.StationAlerts.Tile;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ship Stations Cassette HUD's alerts (spec 7.1, 7.2, 13): one tile per kind, warnings first and the
 * newest first, "+n MORE" past six; the master lamps and acknowledging; each station's worst level for
 * its board tile; the auto-page's alert; the ticker's callouts and the prompt line's responses.
 */
class StationAlertsTest {
    private final HudData d = new HudData();
    private final StationAlerts a = new StationAlerts();
    private double now;

    private void frame(float dt, Consumer<HudData> raises) {
        now += dt;
        d.alerts.begin(now);
        raises.accept(d);
        d.alerts.end();
        a.update(d, dt, false, false, "");
    }

    private static void fire(HudData d, String room) {
        d.alerts.raise("fire:" + room, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + room).room(room);
    }

    private static void smoke(HudData d) {
        d.alerts.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE");
    }

    private static void missile(HudData d) {
        d.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").count(2).timer(9f, TimerKind.HARM);
    }

    @Test
    void aFireTakesATileAndLightsEngineering() {
        frame(0.1f, x -> fire(x, "HOLD"));
        assertEquals(1, a.tiles.size);
        Tile t = a.tiles.first();
        assertEquals("FIRE", t.tag);
        assertEquals("HOLD", t.value);
        assertEquals(Page.DAMAGE, t.home);
        assertEquals(Level.WARNING, a.station[Station.ENG.ordinal()]);
        assertNull(a.station[Station.TAC.ordinal()]);
        assertTrue(a.masterWarning);
        assertFalse(a.masterCaution);
        assertEquals("fire:HOLD", a.raised.id);
        frame(0.1f, x -> fire(x, "HOLD"));
        assertNull(a.raised, "only the frame it was raised in");
    }

    @Test
    void twoFiresShareOneTile() {
        frame(0.1f, x -> {
            fire(x, "HOLD");
            fire(x, "BRIDGE");
        });
        assertEquals(1, a.tiles.size);
        assertEquals("HOLD+1", a.tiles.first().value);
        // Naming the other room reads the alerts again inside the pass over them: libGDX's cached
        // iterators throw when nested, and this is the frame that crashed the first tour
        assertEquals(1, a.lines.size, "one callout for the station, the second held by the quiet time");
        assertTrue(a.lines.first().text.startsWith("fire in the "), a.lines.first().text);
    }

    @Test
    void warningsComeFirstThenTheNewest() {
        frame(0.1f, StationAlertsTest::smoke);
        frame(0.1f, x -> {
            smoke(x);
            fire(x, "HOLD");
        });
        frame(0.1f, x -> {
            smoke(x);
            fire(x, "HOLD");
            missile(x);
        });
        assertEquals(3, a.tiles.size);
        assertEquals("MISSILE", a.tiles.get(0).tag);
        assertEquals("2  0:09", a.tiles.get(0).value);
        assertEquals("FIRE", a.tiles.get(1).tag);
        assertEquals("SMOKE", a.tiles.get(2).tag);
        assertEquals(Level.WARNING, a.station[Station.TAC.ordinal()]);
    }

    @Test
    void pastSixTheLastTileCountsTheRest() {
        frame(0.1f, x -> {
            fire(x, "HOLD");
            missile(x);
            smoke(x);
            x.alerts.raise("breach:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Breach in HOLD").room("HOLD");
            x.alerts.raise("hull", Level.CAUTION, Category.SHIP).set("HULL", "Hull damaged");
            x.alerts.raise("oxygen", Level.CAUTION, Category.LIFE_SUPPORT).set("O2", "Oxygen low");
            x.alerts.raise("propellant", Level.CAUTION, Category.SHIP).set("FUEL", "Propellant low");
            x.alerts.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
        });
        assertEquals(StationAlerts.SLOTS, a.tiles.size);
        Tile more = a.tiles.peek();
        assertTrue(more.more);
        assertEquals("+3 MORE", more.tag);
        assertEquals(Level.CAUTION, more.level, "warnings sort first, so the rest are cautions");
    }

    @Test
    void acknowledgingStopsTheLampsAndTheTilesStay() {
        frame(0.1f, x -> fire(x, "HOLD"));
        assertTrue(a.masterWarning);
        d.alerts.acknowledgeAll();
        frame(0.1f, x -> fire(x, "HOLD"));
        assertFalse(a.masterWarning);
        assertEquals(1, a.tiles.size);
        assertTrue(a.tiles.first().acknowledged);
    }

    @Test
    void aClearedTileFadesThenGoes() {
        frame(0.1f, x -> fire(x, "HOLD"));
        frame(0.5f, x -> { });
        assertEquals(1, a.tiles.size);
        assertTrue(a.tiles.first().clearing());
        assertFalse(a.masterWarning, "a clearing tile lights no lamp");
        frame(StationAlerts.FADE_SECONDS + 0.1f, x -> { });
        assertEquals(0, a.tiles.size);
        assertFalse(a.annunciatorUp());
    }

    @Test
    void aBlackoutIsOneWarningForTheGrid() {
        now += 0.1;
        d.alerts.begin(now);
        d.alerts.raise("sys:REACTOR:2,3", Level.CAUTION, Category.ENGINEERING).set("REACTOR", "REACTOR in HOLD: no power");
        d.alerts.end();
        a.update(d, 0.1f, true, false, "");
        assertEquals(1, a.tiles.size);
        assertEquals("POWER", a.tiles.first().tag);
        assertEquals("NONE", a.tiles.first().value);
        assertEquals(Level.WARNING, a.tiles.first().level);
    }

    @Test
    void aMissileCalloutSaysWhatTheTileCannot() {
        frame(0.1f, x -> { });
        com.ringsofiron.uihud.providers.ContactProvider.Contact c = d.contactFrame.add();
        c.kind = com.ringsofiron.uihud.providers.ContactProvider.Kind.MISSILE;
        c.threat = true;
        c.range = 2000f;
        c.bearingDeg = 90f;
        frame(0.1f, StationAlertsTest::missile);
        assertEquals(1, a.lines.size);
        StationAlerts.Line l = a.lines.first();
        assertEquals("TAC", l.tag);
        assertEquals("SHIP", l.who);
        assertEquals("missiles inbound from port.", l.text);
        assertEquals(Level.WARNING, l.level);
    }

    @Test
    void aNoticeAfterAKeyIsItsResponseAndTheRestGoToTheTicker() {
        frame(0.1f, x -> { });
        a.keyPressed(Input.Keys.Q);
        d.messages.add("Navigation target: Highport", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("Navigation target: Highport", a.response);
        assertEquals(0, a.lines.size);
        // Long after the key, a notice is the crew's or the ship's
        for (int i = 0; i < 10; i++) frame(0.1f, x -> { });
        d.messages.add("Helm: approaching Highport to 500 m", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals(1, a.lines.size);
        assertEquals("HELM", a.lines.first().tag);
        assertEquals("SHIP", a.lines.first().who);
        assertEquals("approaching Highport to 500 m", a.lines.first().text);
        // The same again counts rather than stacking
        d.messages.add("Helm: approaching Highport to 500 m", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals(1, a.lines.size);
    }

    @Test
    void aBlackoutDoesNotCallOutEverySystemWithoutPower() {
        now += 0.1;
        d.alerts.begin(now);
        d.alerts.raise("sys:TURRET:4,5", Level.CAUTION, Category.ENGINEERING).set("TURRET", "TURRET in HOLD: no power");
        d.alerts.end();
        a.update(d, 0.1f, true, false, "");
        assertEquals(0, a.lines.size, "the POWER tile and Command's callout say it once");
        // Out of a blackout a system without power is news: which one, and where
        now += 0.1;
        d.alerts.begin(now);
        d.alerts.raise("sys:WEAPONS:6,2", Level.CAUTION, Category.ENGINEERING).set("WEAPONS", "WEAPONS in BRIDGE: no power").room("BRIDGE");
        d.alerts.end();
        a.update(d, 0.1f, false, false, "");
        assertEquals(1, a.lines.size);
        assertTrue(a.lines.first().text.contains("no power"), a.lines.first().text);
    }

    @Test
    void theShipsOwnDisabledLineIsTheLogsNotTheTickers() {
        frame(0.1f, x -> { });
        for (int i = 0; i < 10; i++) frame(0.1f, x -> { });
        // The status strip and Command's callout ("disabled, reactor down.") already say it
        d.messages.add("Your ship is disabled: reactor down", Level.WARNING, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals(0, a.lines.size);
    }

    @Test
    void theAirGoingIsTheCaptainsBusinessNotTacticals() {
        // "a suit locker": a lock is not a target lock
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "The air here is going: get to a pressurised room, or a suit locker to put on a suit"));
        assertEquals("TAC", StationAlerts.tagOf("SHIP", "Pirate raider 6 has you locked"));
    }

    @Test
    void theGuidesWordsStayOnItsCard() {
        frame(0.1f, x -> { });
        d.messages.add("Undock from Highport: U lets go of the port", Level.ADVISORY, now + 0.05);
        now += 0.1;
        d.alerts.begin(now);
        d.alerts.end();
        a.update(d, 0.1f, false, false, "Undock from Highport: U lets go of the port");
        assertEquals(0, a.lines.size);
    }

    @Test
    void walkingKeysAreNoOnesAnswer() {
        frame(0.1f, x -> { });
        a.keyPressed(Input.Keys.W);
        d.messages.add("Bea: holding position.", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("", a.response);
        assertEquals(1, a.lines.size);
        assertEquals("Bea", a.lines.first().who);
    }

    private Objective hold(String title, String kind, double reward) {
        Objective o = d.objectiveFrame.add();
        o.title = title;
        o.kind = kind;
        o.giver = "patrol";
        o.station = "Highport";
        o.reward = reward;
        return o;
    }

    @Test
    void aContractsEndIsCommandsLineByTheContractsName() {
        hold("Bounty: Raider of the Void Raiders (robbed 3 freighters, last of Food)", "Bounty", 7500);
        Objective food = hold("Deliver 40 Food to Shackleton Ice", "Delivery", 3000);
        frame(0.1f, x -> { });
        // A delivery's title counts down as it goes; it is the same contract
        food.title = "Deliver 20 Food to Shackleton Ice";
        for (int i = 0; i < 10; i++) frame(0.1f, x -> { });
        d.objectiveFrame.clear();
        d.messages.add("Contract done: delivered. Paid 3,000 cr, standing +5 with Selene Combine", Level.ADVISORY, now + 0.05);
        d.messages.add("Contract done: Raider of the Void Raiders is beaten. Paid 7,500 cr, standing +6 with Terran Patrol Authority",
                Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals(2, a.lines.size);
        // Each is named by what it paid, whichever ended first
        assertEquals("CMD", a.lines.get(0).tag);
        assertEquals("SHIP", a.lines.get(0).who);
        assertEquals("Raider bounty done: +7,500 cr, +6 standing.", a.lines.get(0).text);
        assertEquals("Food delivery done: +3,000 cr, +5 standing.", a.lines.get(1).text);
        assertEquals(Page.CONTRACTS, a.lines.get(1).home);
        // With none known to have ended, it is just a contract; a failure gives its reason for the pay
        d.messages.add("Contract failed: out of time. Standing -3 with Terran Patrol Authority", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("Contract failed: out of time, -3 standing.", a.lines.peek().text);
        // "Contract taken" ends nothing, and is no contract's end line
        assertEquals(null, StationAlerts.contractEnd("Contract taken", "Contract"));
        assertEquals("Highport defence given up: -2 standing.",
                StationAlerts.contractEnd("Contract abandoned: abandoned. Standing -2 with Selene Combine", "Highport defence"));
        assertEquals("Kestrel escort called off: Kestrel is gone.",
                StationAlerts.contractEnd("Contract called off: Kestrel is gone", "Kestrel escort"));
    }

    @Test
    void aContractIsNamedByItsTargetAndKind() {
        assertEquals("Raider bounty", StationAlerts.contractName("Bounty: Raider of the Void Raiders", "Bounty"));
        assertEquals("Red Jack bounty", StationAlerts.contractName("Bounty: the ace Red Jack of the Void Raiders (robbed 6 freighters)", "Bounty"));
        assertEquals("Food delivery", StationAlerts.contractName("Deliver 40 Food to Shackleton Ice", "Delivery"));
        assertEquals("Kestrel escort", StationAlerts.contractName("Escort Kestrel to Highport", "Escort"));
        assertEquals("Shackleton Ice patrol", StationAlerts.contractName("Patrol out to Shackleton Ice and back", "Patrol"));
        assertEquals("Patrol", StationAlerts.contractName("Patrol: back to Highport", "Patrol"));
        assertEquals("Mary Ann rescue", StationAlerts.contractName("Rescue 3 from Mary Ann", "Rescue"));
        assertEquals("Rescue", StationAlerts.contractName("Bring 3 survivors to Highport", "Rescue"));
        assertEquals("Water recovery", StationAlerts.contractName("Recover 20 Water from Mary Ann", "Recovery"));
        assertEquals("Water recovery", StationAlerts.contractName("Bring 20 Water to Highport", "Recovery"));
        assertEquals("Highport convoy", StationAlerts.contractName("Convoy of 3 to Highport", "Convoy"));
        assertEquals("Highport defence", StationAlerts.contractName("Defend Highport from 3 raiders", "Defend"));
        assertEquals("Highport defence", StationAlerts.contractName("Defend Highport from 1 raider", "Defend"));
        assertEquals("Black Rock search", StationAlerts.contractName("Find Black Rock", "Recon"));
        assertEquals("Black Rock raid", StationAlerts.contractName("Raid Black Rock", "Raid"));
    }

    @Test
    void aStationSpeaksInItsOwnName() {
        assertArrayEquals(new String[]{"Highport", "clearance granted, port 2."},
                StationAlerts.stationSays("Highport gives clearance: port 2 is armed for you"));
        assertArrayEquals(new String[]{"Shackleton Ice", "clearance refused: hostile act."},
                StationAlerts.stationSays("Shackleton Ice refuses docking clearance (hostile act); port armed"));
        assertArrayEquals(new String[]{"Highport", "clearance refused."},
                StationAlerts.stationSays("Highport refuses docking clearance; port armed"));
        assertArrayEquals(new String[]{"Highport", "no free M port."}, StationAlerts.stationSays("Highport has no free M port; port armed"));
        assertNull(StationAlerts.stationSays("Docked at Highport"));
        // Heard on deck: a notice that comes by itself is a COMMS line in the station's name
        frame(0.1f, x -> { });
        for (int i = 0; i < 10; i++) frame(0.1f, x -> { });
        d.messages.add("Highport gives clearance: port 2 is armed for you", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("COMMS", a.lines.peek().tag);
        assertEquals("Highport", a.lines.peek().who);
        assertEquals("clearance granted, port 2.", a.lines.peek().text);
        // A station with a name of two words, addressing the ship
        d.messages.add("Selene Yard: your ship is venting at the berth. Time warp waits until it is patched or repaired",
                Level.WARNING, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("COMMS", a.lines.peek().tag);
        assertEquals("Selene Yard", a.lines.peek().who);
    }

    @Test
    void whatTheStationAnswersTheHelmsCrewIsHeardOnceOnDeck() {
        d.ctx.helmOrder = "Helm: docking at Highport: closing on Highport";
        frame(0.1f, x -> { });
        frame(0.1f, x -> { });
        assertEquals(0, a.lines.size);
        d.ctx.helmOrder = "Helm: docking at Highport: cleared for port 2";
        frame(0.1f, x -> { });
        assertEquals(1, a.lines.size);
        assertEquals("COMMS", a.lines.first().tag);
        assertEquals("Highport", a.lines.first().who);
        assertEquals("clearance granted, port 2.", a.lines.first().text);
        frame(0.1f, x -> { });
        assertEquals(1, a.lines.first().count, "said once, not every frame");
        assertArrayEquals(new String[]{"Highport", "clearance refused."},
                StationAlerts.stationAnswered("Helm: docking at Highport: refused clearance; holding off"));
        assertNull(StationAlerts.stationAnswered("Helm order (docking at Highport: cleared for port 2) waits for a helmsman"));
    }

    @Test
    void thePiratesNewsIsTheCaptainsAndTheFightTacticals() {
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "Pirate raider 6 robbed Kestrel of 40 Food"));
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "The ace Red Jack is beaten: a spare Heavy Cannon is crated in its hold"));
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "Pirate raider 6 got away from you: it flies as the ace Red Jack now"));
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "Black Rock has fallen"));
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "Raiders have moved back into Black Rock: the pirates have a base again"));
        assertEquals("CMD", StationAlerts.tagOf("SHIP", "Pirate base located: Black Rock, now on the traffic map"));
        assertEquals("TAC", StationAlerts.tagOf("SHIP", "Pirate raider 6 is breaking off"));
        assertEquals("TAC", StationAlerts.tagOf("SHIP", "Black Rock scrambles 2 raiders against you"));
        // "Word on the lanes" is no one's name
        frame(0.1f, x -> { });
        for (int i = 0; i < 10; i++) frame(0.1f, x -> { });
        d.messages.add("Word on the lanes: the ace Red Jack flies a pirate gunship now", Level.ADVISORY, now + 0.05);
        frame(0.1f, x -> { });
        assertEquals("CMD", a.lines.peek().tag);
        assertEquals("SHIP", a.lines.peek().who);
        assertEquals("Word on the lanes: the ace Red Jack flies a pirate gunship now", a.lines.peek().text);
    }
}
