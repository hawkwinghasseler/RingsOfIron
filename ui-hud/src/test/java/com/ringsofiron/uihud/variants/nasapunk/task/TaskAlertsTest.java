package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The task spec's alert rules as the lamps and the band show them, from the shared registry's alerts. */
class TaskAlertsTest {
    private final HudData d = new HudData();
    private final Annunciator lamps = new Annunciator(null);
    private final AlertBand band = new AlertBand(null);

    @BeforeEach
    void keys() {
        d.keyNames = a -> "DECOY".equals(a) ? "F" : "VIEW".equals(a) ? "V" : null;
    }

    @Test
    void aLostReactorLightsPowerAndTakesTheBand() {
        d.alerts.begin(10.0);
        d.alerts.raise("sys:REACTOR:3,4", Level.WARNING, Category.ENGINEERING)
                .set("REACTOR", "REACTOR in Engineering: destroyed").room("Engineering");
        d.alerts.end();
        TaskAlerts.apply(d, Presence.WEAPONS, lamps, band, 10.0);
        assertEquals(Annunciator.State.WARNING, lamps.states[Annunciator.Lamp.POWER.ordinal()]);
        assertNotNull(band.warning);
        assertEquals("NO POWER", band.warning.big);
    }

    @Test
    void theBandNamesTheActionsItGivesAKeyFor() {
        // seated, the decoy is on a key cap
        missiles(Presence.HELM);
        assertEquals("F", band.warning.actions.get(0)[0]);
        assertEquals(List.of("DECOY"), band.warning.named);
        // on deck the band says V takes a console, in words: the hint line must leave V out too
        missiles(Presence.DECK);
        assertEquals(0, band.warning.actions.size());
        assertEquals(List.of("VIEW"), band.warning.named);
    }

    private void missiles(Presence p) {
        d.alerts.begin(20.0);
        d.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2)
                .timer(12f, TimerKind.HARM);
        d.alerts.end();
        TaskAlerts.apply(d, p, lamps, band, 20.0);
        assertNotNull(band.warning);
    }
}
