package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.Input;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.stations.StationAlerts.Tile;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

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
}
