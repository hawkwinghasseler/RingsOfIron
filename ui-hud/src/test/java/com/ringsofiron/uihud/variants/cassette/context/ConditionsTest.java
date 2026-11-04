package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.context.Conditions.Handling;
import com.ringsofiron.uihud.variants.cassette.context.Conditions.Row;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The context Cassette HUD's caution and warning catalogue (spec P2): one row per code with its places
 * counted; the handling field from the crew working each alert (a dot each, "•4" from four, YOU when
 * nobody is on what the crew could work, blank when no one can); the panel's order; the master cue and the
 * acknowledge; an instrument's own condition left off the panel while it is on screen, yet still lighting
 * the cue.
 */
class ConditionsTest {
    private final HudData d = new HudData();
    private final ContextFrame f = new ContextFrame();
    private final Conditions c = new Conditions();
    private final Conditions.Shown shown = new Conditions.Shown();
    private float now;

    ConditionsTest() {
        f.seat = ContextFrame.Seat.HELM;
        f.data = d;
    }

    private void frame(float dt, Consumer<HudData> raises) {
        now += dt;
        f.time = now;
        d.alerts.begin(now);
        raises.accept(d);
        d.alerts.end();
        c.update(d, f, shown, now, dt);
    }

    private static void fire(HudData d, String room, int crew) {
        d.alerts.raise("fire:" + room, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + room).room(room).handling(crew);
    }

    private static void smoke(HudData d, int crew) {
        d.alerts.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE").handling(crew);
    }

    private static void bleeding(HudData d) {
        d.alerts.raise("captain:bleeding", Level.WARNING, Category.CREW).set("BLEED", "You are bleeding").timer(90f, TimerKind.HARM);
    }

    private Row row(String code) {
        for (int i = 0; i < c.all.size; i++) if (c.all.get(i).code.equals(code)) return c.all.get(i);
        return null;
    }

    @Test
    void theCrewOnAFireShowAsDots() {
        frame(0.1f, x -> fire(x, "HOLD", 2));
        Row r = row("FIRE");
        assertNotNull(r);
        assertEquals(Handling.CREW, r.handling);
        assertEquals("••", r.handlingText());
    }

    @Test
    void aFireNobodyIsOnNeedsTheCaptain() {
        frame(0.1f, x -> fire(x, "HOLD", 0));
        Row r = row("FIRE");
        assertEquals(Handling.YOU, r.handling);
        assertEquals("YOU", r.handlingText());
        // Someone takes it on: the crew's again
        frame(0.1f, x -> fire(x, "HOLD", 1));
        assertEquals(Handling.CREW, row("FIRE").handling);
        assertEquals("•", row("FIRE").handlingText());
    }

    @Test
    void aConditionNoOneCanWorkLeavesTheFieldBlank() {
        frame(0.1f, x -> fire(x, "HOLD", -1));
        Row r = row("FIRE");
        assertEquals(Handling.CREW, r.handling);
        assertEquals("", r.handlingText());
    }

    @Test
    void fourOrMoreAreCountedInThreeCharacters() {
        frame(0.1f, x -> fire(x, "HOLD", 5));
        assertEquals("•5", row("FIRE").handlingText());
        frame(0.1f, x -> fire(x, "HOLD", 250));
        assertEquals("•99", row("FIRE").handlingText());
        assertTrue(row("FIRE").handlingText().length() <= 3);
    }

    @Test
    void twoFiresAreOneRowWithTheirCrewAdded() {
        frame(0.1f, x -> {
            fire(x, "HOLD", 1);
            fire(x, "BRIDGE", 2);
        });
        assertEquals(1, c.all.size);
        Row r = row("FIRE");
        assertEquals(2, r.places);
        assertEquals("•••", r.handlingText());
        assertTrue(Conditions.detail(r).endsWith(" +1"), Conditions.detail(r));
        // One room the crew can work with nobody on it, one they are on: still the crew's
        frame(0.1f, x -> {
            fire(x, "HOLD", 0);
            fire(x, "BRIDGE", 1);
        });
        assertEquals(Handling.CREW, row("FIRE").handling);
    }

    @Test
    void warningsComeFirstThenTheCaptainsThenTheCrews() {
        frame(0.1f, x -> smoke(x, 0));
        frame(0.1f, x -> {
            smoke(x, 0);
            fire(x, "HOLD", 2);
        });
        frame(0.1f, x -> {
            smoke(x, 0);
            fire(x, "HOLD", 2);
            bleeding(x);
        });
        assertEquals(3, c.all.size);
        // Two warnings: the fire the crew are on, and the captain's wound (the crew's to treat, with a harm timer)
        assertTrue(c.all.get(0).warning && c.all.get(1).warning);
        assertEquals("SMOKE", c.all.get(2).code, "the caution last, though the captain must see to it");
        assertEquals(Handling.YOU, c.all.get(2).handling);
        assertEquals("CAPT WOUND", c.all.get(0).code, "a harm timer before the newest");
    }

    @Test
    void theCueLightsForANewWarningUntilAcknowledged() {
        frame(0.1f, x -> fire(x, "HOLD", 1));
        assertTrue(c.cue.lit);
        assertTrue(c.cue.warning);
        assertEquals("FIRE", c.cue.code);
        assertEquals("Fire", Conditions.longName(c.cue.code));
        frame(10f, x -> fire(x, "HOLD", 1));
        assertTrue(c.cue.lit, "a warning's cue waits for the captain");
        c.acknowledge(d);
        assertFalse(c.cue.lit);
        frame(0.1f, x -> fire(x, "HOLD", 1));
        assertFalse(c.cue.lit, "acknowledged, it stays out while the row holds");
        assertEquals(1, c.listed.size, "and the row stays");
        // A second room is news again
        frame(0.1f, x -> {
            fire(x, "HOLD", 1);
            fire(x, "BRIDGE", 1);
        });
        assertTrue(c.cue.lit);
    }

    @Test
    void aCautionsCueGoesOutByItself() {
        frame(0.1f, x -> smoke(x, 1));
        assertTrue(c.cue.lit);
        assertFalse(c.cue.warning);
        frame(Conditions.CAUTION_CUE + 0.1f, x -> smoke(x, 1));
        assertFalse(c.cue.lit);
        assertEquals(1, c.listed.size);
    }

    @Test
    void anInstrumentsOwnConditionLeavesThePanelButLightsTheCue() {
        d.navReadout.planDeltaV = 900;
        d.flightStatus.deltaVLeft = 400;
        shown.planLine = true;
        frame(0.1f, x -> { });
        Row r = row("PLAN SHORT");
        assertNotNull(r);
        assertEquals("H2", r.shownBy);
        assertEquals(0, c.listed.size, "the nav tube's plan line shows it");
        assertTrue(c.cue.lit);
        // The tube takes its compact face: the panel lists it again
        shown.planLine = false;
        frame(0.1f, x -> { });
        assertNull(row("PLAN SHORT").shownBy);
        assertEquals(1, c.listed.size);
    }

    @Test
    void theMissileAndTheShipsDisablementAreNotRows() {
        frame(0.1f, x -> {
            x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").count(2).timer(9f, TimerKind.HARM);
            x.alerts.raise("disabled", Level.WARNING, Category.ENGINEERING).set("DISABLED", "Ship disabled");
        });
        assertEquals(0, c.all.size, "the threat unit and the emergency placard carry them");
    }

    @Test
    void longNamesReadAsWords() {
        assertEquals("Hull breach", Conditions.longName("BREACH"));
        assertEquals("Approach too fast", Conditions.longName("FAST APPR"));
        assertEquals("Docking strain", Conditions.longName("DOCK STRAIN"));
        assertEquals("Odd thing", Conditions.longName("ODD THING"));
        assertEquals("", Conditions.longName(""));
        assertEquals("", Conditions.longName(null));
    }
}
