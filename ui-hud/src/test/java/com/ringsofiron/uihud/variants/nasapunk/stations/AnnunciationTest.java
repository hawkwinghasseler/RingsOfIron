package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The annunciator's tiles from the game's alerts (sections 7.1 and 13.4), as GameAlerts raises them. */
class AnnunciationTest {
    private final AlertRegistry alerts = new AlertRegistry();
    private final Annunciation a = new Annunciation();

    private void frame(double now, Consumer<AlertRegistry> raise) {
        frame(now, false, raise);
    }

    private void frame(double now, boolean blackout, Consumer<AlertRegistry> raise) {
        alerts.begin(now);
        raise.accept(alerts);
        alerts.end();
        a.build(alerts, blackout, now);
    }

    private static void fire(AlertRegistry r, String room) {
        r.raise("fire:" + room, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + room).room(room).count(2);
    }

    private static void sys(AlertRegistry r, String system, String room, Level l, String what, float timer) {
        r.raise("sys:" + system + ":" + system.length() + ",1", l, Category.ENGINEERING)
                .set(system, system + (room.isEmpty() ? "" : " in " + room) + ": " + what).room(room)
                .timer(timer, Float.isNaN(timer) ? TimerKind.NONE : TimerKind.RECOVERY);
    }

    private static List<String> lines(List<Annunciation.Tile> ts) {
        List<String> out = new ArrayList<>();
        for (Annunciation.Tile t : ts) out.add(t.tag + "/" + t.value);
        return out;
    }

    @Test
    void twoFiresShareOneTileWithTheWorstRoomAndTheCount() {
        frame(1.0, r -> {
            fire(r, "BRIDGE");
            fire(r, "HOLD");
        });
        assertEquals(List.of("FIRE/BRIDGE+1"), lines(a.row));
        Annunciation.Tile t = a.row.get(0);
        assertEquals(Level.WARNING, t.level);
        assertEquals(Station.ENG, t.station);
        assertEquals(Page.DAMAGE, t.home);
        assertEquals(Level.WARNING, a.stations[Station.ENG.ordinal()]);
        assertNull(a.stations[Station.TAC.ordinal()]);
    }

    @Test
    void missilesReadTheirCountAndTheSoonestImpact() {
        frame(1.0, r -> r.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2).timer(8.2f, TimerKind.HARM));
        assertEquals(List.of("MISSILE/2  0:09"), lines(a.row));
        assertEquals(Page.DEFENSE, a.row.get(0).home);
    }

    @Test
    void warningsComeFirstThenCautionsNewestFirstAndAdvisoriesTakeNoTile() {
        frame(1.0, r -> r.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked"));
        frame(2.0, r -> {
            r.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
            r.raise("smoke:HOLD", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in HOLD").room("HOLD");
            r.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "Crew outside");
        });
        frame(3.0, r -> {
            r.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
            r.raise("smoke:HOLD", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in HOLD").room("HOLD");
            r.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "Crew outside");
            r.raise("hull", Level.WARNING, Category.ENGINEERING).set("HULL", "Hull at 52%");
        });
        assertEquals(List.of("HULL/52%", "SMOKE/HOLD", "HOSTILE/FIRING"), lines(a.row));
    }

    @Test
    void systemsThePowerDoesntReachCountOnOnePowerTile() {
        frame(1.0, r -> {
            sys(r, "TURRET", "BRIDGE", Level.CAUTION, "shorted", 37f);
            sys(r, "ENGINE", "HOLD", Level.CAUTION, "no power", Float.NaN);
        });
        assertEquals(List.of("POWER/2 SYS"), lines(a.row));
        assertEquals(Level.CAUTION, a.row.get(0).level);
        assertEquals(Page.POWER, a.row.get(0).home);
    }

    @Test
    void noReactorWorkingReadsPowerNoneAsAWarning() {
        frame(1.0, true, r -> {
            sys(r, "REACTOR", "BRIDGE", Level.WARNING, "disabled", Float.NaN);
            sys(r, "ENGINE", "HOLD", Level.CAUTION, "no power", Float.NaN);
        });
        assertEquals(List.of("POWER/NONE"), lines(a.row));
        assertEquals(Level.WARNING, a.row.get(0).level);
        // a blackout with nothing raised yet still reads it
        frame(2.0, true, r -> { });
        assertEquals(List.of("POWER/NONE"), lines(a.row));
    }

    @Test
    void aStunReadsTheSystemAndItsSecondsAndAWreckIsItsOwnTile() {
        frame(1.0, r -> {
            sys(r, "REACTOR", "BRIDGE", Level.WARNING, "stunned", 21.3f);
            sys(r, "LIFE SUP", "BRIDGE", Level.CAUTION, "disabled", Float.NaN);
        });
        assertEquals(List.of("STUN/RCTR 22", "LIFE SUP/DISABLED"), lines(a.row));
        frame(2.0, r -> {
            sys(r, "LIFE SUP", "BRIDGE", Level.CAUTION, "disabled", Float.NaN);
            sys(r, "TURRET", "BRIDGE", Level.WARNING, "destroyed", Float.NaN);
        });
        assertEquals(List.of("SYSTEMS/2 DOWN"), lines(a.row));
        assertEquals(Level.WARNING, a.row.get(0).level);
    }

    @Test
    void theHelmsFaultIsAWord() {
        frame(1.0, r -> r.raise("helm", Level.CAUTION, Category.FLIGHT).set("HELM", "Helm: the helm has no power"));
        assertEquals(List.of("HELM/NO POWER"), lines(a.row));
        assertEquals(Station.HELM, a.row.get(0).station);
    }

    @Test
    void theShipDisabledTakesNoTileButLightsCommand() {
        frame(1.0, r -> r.raise("disabled", Level.WARNING, Category.SHIP).set("DISABLED", "Ship disabled: reactor down"));
        assertTrue(a.row.isEmpty());
        assertEquals(Level.WARNING, a.stations[Station.CMD.ordinal()]);
        assertTrue(a.warnUnacked, "it still flashes the master warning");
    }

    @Test
    void pastSixTheLastTileReadsHowManyMore() {
        frame(1.0, r -> {
            r.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").count(1).timer(9f, TimerKind.HARM);
            fire(r, "BRIDGE");
            r.raise("hull", Level.WARNING, Category.ENGINEERING).set("HULL", "Hull at 40%");
            r.raise("captain:bleeding", Level.WARNING, Category.CAPTAIN).set("BLEEDING", "You are bleeding out").timer(130f, TimerKind.HARM);
            r.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
            r.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE");
            r.raise("oxygen", Level.CAUTION, Category.LIFE_SUPPORT).set("O2", "Oxygen 20%");
        });
        assertEquals(7, a.all.size());
        assertEquals(Annunciation.ROW, a.row.size());
        Annunciation.Tile more = a.row.get(Annunciation.ROW - 1);
        assertEquals("+2", more.tag);
        assertEquals("MORE", more.value);
        assertEquals(Level.CAUTION, more.level, "it hides two cautions, lit as the worst of them");
    }

    @Test
    void acknowledgingPutsTheMasterLampsOutUntilSomethingNew() {
        frame(1.0, r -> fire(r, "BRIDGE"));
        assertTrue(a.warnUnacked);
        assertFalse(a.cautUnacked);
        alerts.acknowledgeAll();
        frame(2.0, r -> fire(r, "BRIDGE"));
        assertFalse(a.warnUnacked);
        frame(3.0, r -> {
            fire(r, "BRIDGE");
            r.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE");
        });
        assertFalse(a.warnUnacked);
        assertTrue(a.cautUnacked);
    }

    @Test
    void everyTileFitsEightCapitalsALine() {
        frame(1.0, r -> {
            r.raise("air:FWD PORT 2", Level.CAUTION, Category.LIFE_SUPPORT).set("PRESSURE", "FWD PORT 2 at 60% pressure").room("FWD PORT 2");
            r.raise("air:HOLD", Level.CAUTION, Category.LIFE_SUPPORT).set("PRESSURE", "HOLD at 60% pressure").room("HOLD");
            r.raise("sys:ARMOURED BULKHEAD:1,1", Level.CAUTION, Category.ENGINEERING).set("ARMOURED BULKHEAD", "ARMOURED BULKHEAD: disabled");
            r.raise("captain:suit", Level.CAUTION, Category.CAPTAIN).set("SUIT AIR", "Suit air 20%").timer(212f, TimerKind.HARM);
        });
        for (Annunciation.Tile t : a.all) {
            assertTrue(t.tag.length() <= Annunciation.CHARS, t.tag);
            assertTrue(t.value.length() <= Annunciation.CHARS, t.value);
        }
        assertEquals("FWD PO+1", a.get("AIR").value);
        assertEquals("3:32", a.get("SUIT AIR").value);
    }
}
