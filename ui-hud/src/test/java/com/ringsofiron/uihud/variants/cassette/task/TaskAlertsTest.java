package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Lamp;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Task-driven Cassette HUD's alert routing (spec section 9): lamps, the band's two rows, acknowledging. */
class TaskAlertsTest {
    private final HudData d = new HudData();
    private final TaskAlerts t = new TaskAlerts();

    private void frame(float dt, Consumer<HudData> raises) {
        d.alerts.begin(0);
        raises.accept(d);
        d.alerts.end();
        t.update(d, dt, false);
    }

    private static void fire(HudData d) {
        d.alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
    }

    private static void smoke(HudData d) {
        d.alerts.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE");
    }

    @Test
    void aFireLightsItsLampAndTakesTheWarningRow() {
        frame(0.1f, TaskAlertsTest::fire);
        assertEquals(Level.WARNING, t.level[Lamp.FIRE.ordinal()]);
        assertEquals("fire:HOLD", t.warning.id);
        assertTrue(t.masterWarning);
        assertTrue(t.bandUp());
        assertTrue(t.schematicWanted());
    }

    @Test
    void aCautionLeavesTheBandAfterTenSecondsAndKeepsItsLamp() {
        frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(1, t.cautions.size);
        assertTrue(t.masterCaution);
        for (int i = 0; i < 110; i++) frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(0, t.cautions.size);
        assertFalse(t.bandUp());
        assertEquals(Level.CAUTION, t.level[Lamp.AIR.ordinal()]);
    }

    @Test
    void theSoonestHarmTakesTheWarningRowAndTheOtherWarningsGoBelow() {
        frame(0.1f, TaskAlertsTest::fire);
        frame(0.1f, x -> {
            fire(x);
            x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").timer(14f, TimerKind.HARM);
        });
        assertEquals("missile", t.warning.id);
        assertEquals("fire:HOLD", t.cautions.first().id);
    }

    @Test
    void pressingTheBandAcknowledgesItsCautions() {
        frame(0.1f, TaskAlertsTest::smoke);
        d.alerts.acknowledgeAll();
        frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(0, t.cautions.size);
        assertFalse(t.masterCaution);
        // The lamp stays lit: the caution lives on there
        assertEquals(Level.CAUTION, t.level[Lamp.AIR.ordinal()]);
    }

    @Test
    void anAlertTheBandOwnsLightsNoLamp() {
        frame(0.1f, x -> x.alerts.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Raider has you locked"));
        for (Lamp l : Lamp.values()) assertNull(t.level[l.ordinal()], l.name());
        assertEquals(1, t.cautions.size);
    }
}
