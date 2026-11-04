package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2's rows from the game's alerts and the conditions the HUD works out (section 6, P2), with H4's lights and the cue. */
class CautionRowsTest {
    private final HudData d = new HudData();
    private final AlertRegistry derived = new AlertRegistry();
    private final CautionRows rows = new CautionRows();
    private final CautionRows.Shown shown = new CautionRows.Shown();

    /** One frame: the game's alerts as {@code raise} raises them, then the derived ones, then the rows. */
    private void frame(double now, Consumer<AlertRegistry> raise) {
        d.alerts.begin(now);
        raise.accept(d.alerts);
        d.alerts.end();
        derived.begin(now);
        rows.derive(d, derived, 0.1f);
        derived.end();
        rows.build(d, derived, shown);
    }

    private static void fire(AlertRegistry a, String room) {
        a.raise("fire:" + room, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + room).room(room);
    }

    private static List<String> codes(List<CautionRows.Row> rs) {
        List<String> out = new ArrayList<>();
        for (CautionRows.Row r : rs) out.add(r.code);
        return out;
    }

    private static CautionRows.Row find(List<CautionRows.Row> rs, String code) {
        for (CautionRows.Row r : rs) if (r.code.equals(code)) return r;
        return null;
    }

    @Test
    void aFireIsAWarningRowWithItsRoomThatLightsLifeSupportAndTheCue() {
        frame(1.0, a -> fire(a, "BRIDGE"));
        assertEquals(1, rows.panel.size());
        CautionRows.Row r = rows.panel.get(0);
        assertEquals("FIRE", r.code);
        assertEquals("BRIDGE", r.detail);
        assertEquals(Level.WARNING, r.level);
        assertEquals(Level.WARNING, rows.lights[CautionRows.Light.LIFE_SUP.ordinal()]);
        assertNull(rows.lights[CautionRows.Light.HULL.ordinal()]);
        assertSame(r, rows.cue);
    }

    @Test
    void twoFiresShareOneRow() {
        frame(1.0, a -> {
            fire(a, "BRIDGE");
            fire(a, "HOLD");
        });
        assertEquals(List.of("FIRE"), codes(rows.panel));
        assertEquals("BRIDGE +1", rows.panel.get(0).detail);
    }

    @Test
    void warningsComeFirstThenWhatNeedsTheCaptainThenWhatClearsByItself() {
        frame(2.0, a -> {
            a.raise("sys:TURRET:1,1", Level.CAUTION, Category.ENGINEERING).set("TURRET", "TURRET in BOW: stunned")
                    .tile(1, 1).timer(8f, TimerKind.RECOVERY);
            a.raise("propellant", Level.CAUTION, Category.FLIGHT).set("PROPELLANT", "Propellant low");
            a.raise("breach:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in HOLD").room("HOLD");
        });
        assertEquals(List.of("BREACH", "PROP LOW", "SYSTEM DOWN"), codes(rows.panel));
        assertEquals(CautionRows.Handling.YOU, rows.panel.get(1).handling);
        assertEquals(CautionRows.Handling.BACK, rows.panel.get(2).handling);
        assertEquals(8f, rows.panel.get(2).timer, 0.001f);
        assertFalse(rows.panel.get(2).harm, "a stun's timer counts down to recovery");
        // the breach lights the hull
        assertEquals(Level.WARNING, rows.lights[CautionRows.Light.HULL.ordinal()]);
    }

    @Test
    void aReactorMakingNothingIsNoPowerAndAStunComesBackByItself() {
        d.powerFrame.outputWatts = 0;
        frame(3.0, a -> a.raise("sys:REACTOR:2,2", Level.WARNING, Category.ENGINEERING)
                .set("REACTOR", "REACTOR in ENGINEERING: stunned").timer(12f, TimerKind.RECOVERY));
        CautionRows.Row r = rows.panel.get(0);
        assertEquals("NO POWER", r.code);
        assertEquals("STUNNED", r.detail);
        assertEquals(CautionRows.Handling.BACK, r.handling);
        assertEquals(Level.WARNING, rows.lights[CautionRows.Light.POWER.ordinal()]);
    }

    @Test
    void theHelmLosingPowerWithTheReactorIsOneRowAndNoNestedWalk() {
        // the helm's own alert looks through the alerts for a helm fault while the rows walk them
        d.powerFrame.outputWatts = 0;
        d.ctx.helmFault = "no power";
        frame(3.2, a -> {
            a.raise("sys:REACTOR:2,2", Level.WARNING, Category.ENGINEERING).set("REACTOR", "REACTOR in ENGINEERING: damaged");
            a.raise("helm", Level.CAUTION, Category.FLIGHT).set("HELM", "Helm: no power");
        });
        assertEquals(List.of("NO POWER", "SYSTEM DOWN"), codes(rows.panel));
        assertEquals("HELM", find(rows.panel, "SYSTEM DOWN").detail);
        d.ctx.helmFault = "";
    }

    @Test
    void aSwitchedOffSystemIsNoRowUnlessItIsTheOnlyReactor() {
        d.powerFrame.outputWatts = 2e6;
        frame(3.5, a -> a.raise("sys:TURRET:4,4", Level.ADVISORY, Category.ENGINEERING).set("TURRET", "TURRET in BOW: off"));
        assertTrue(rows.all.isEmpty());
        d.powerFrame.outputWatts = 0;
        frame(3.6, a -> a.raise("sys:REACTOR:2,2", Level.ADVISORY, Category.ENGINEERING).set("REACTOR", "REACTOR in ENGINEERING: off"));
        CautionRows.Row r = find(rows.panel, "NO POWER");
        assertNotNull(r);
        assertEquals("OFF", r.detail);
        assertEquals(CautionRows.Handling.YOU, r.handling);
    }

    @Test
    void theCaptainsWoundIsD1sOnDeckAndP2sAtASeat() {
        Consumer<AlertRegistry> bleeding = a -> a.raise("captain:bleeding", Level.WARNING, Category.CAPTAIN).set("BLEEDING", "You are bleeding out");
        shown.seated = false;
        frame(4.0, bleeding);
        assertTrue(rows.panel.isEmpty());
        assertEquals(List.of("CAPT WOUND"), codes(rows.all));
        shown.seated = true;
        frame(4.1, bleeding);
        assertEquals(List.of("CAPT WOUND"), codes(rows.panel));
    }

    @Test
    void theCaptainsSuitAirIsNeverARow() {
        frame(4.5, a -> a.raise("captain:suit", Level.WARNING, Category.CAPTAIN).set("SUIT", "Suit air low"));
        assertTrue(rows.panel.isEmpty());
        assertNotNull(rows.cue, "but it still lights the cue");
    }

    @Test
    void acknowledgingPutsTheCueOutUntilSomethingNewIsRaised() {
        frame(5.0, a -> fire(a, "BRIDGE"));
        assertNotNull(rows.cue);
        d.alerts.acknowledgeAll();
        frame(5.1, a -> fire(a, "BRIDGE"));
        assertNull(rows.cue);
        frame(5.2, a -> {
            fire(a, "BRIDGE");
            fire(a, "HOLD");
        });
        assertNotNull(rows.cue, "a fire in a second room is news");
        assertEquals("FIRE", rows.cue.code);
    }

    @Test
    void missilesAndHostileLocksAreLeftToTheirOwnWidgets() {
        frame(6.0, a -> {
            a.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "1 missile inbound");
            a.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked");
        });
        assertTrue(rows.all.isEmpty());
    }

    @Test
    void aPlanTheShipCantFlyIsPlanShortUnlessH2sPlanLineShowsIt() {
        d.ctx.ship = new Entity();
        d.flightStatus.deltaVPlanned = 900;
        d.flightStatus.deltaVLeft = 600;
        frame(7.0, a -> { });
        CautionRows.Row r = find(rows.panel, "PLAN SHORT");
        assertNotNull(r);
        assertEquals("NEED 900", r.detail);
        assertEquals(CautionRows.Handling.YOU, r.handling);
        shown.planLine = true;
        frame(7.1, a -> { });
        assertNull(find(rows.panel, "PLAN SHORT"));
        assertNotNull(find(rows.all, "PLAN SHORT"));
    }

    @Test
    void closingAtTwiceTheSafeSpeedIsAWarningWithATimeToContact() {
        d.ctx.ship = new Entity();
        DockingApproach k = d.docking;
        k.present = true;
        k.range = 100f;
        k.closing = 5f;
        k.safeSpeed = 2f;
        frame(8.0, a -> { });
        CautionRows.Row r = find(rows.panel, "FAST APPR");
        assertNotNull(r);
        assertEquals(Level.WARNING, r.level);
        assertEquals("3.0 M/S OVER", r.detail);
        assertEquals(20f, r.timer, 0.01f);
        assertTrue(r.harm);
        // H3's closing gauge shows it while the docking display is up
        shown.dockingDisplay = true;
        frame(8.1, a -> { });
        assertNull(find(rows.panel, "FAST APPR"));
        k.closing = 3f;
        frame(8.2, a -> { });
        assertEquals(Level.CAUTION, find(rows.all, "FAST APPR").level);
    }

    @Test
    void dockedNothingAboutFlyingIsRaised() {
        d.ctx.ship = new Entity();
        d.ctx.docked = true;
        d.flightStatus.deltaVPlanned = 900;
        d.flightStatus.deltaVLeft = 600;
        frame(9.0, a -> { });
        assertNull(find(rows.all, "PLAN SHORT"));
    }
}
