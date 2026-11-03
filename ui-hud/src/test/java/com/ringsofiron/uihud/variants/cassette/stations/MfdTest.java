package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MFDs' behaviour (spec 9.1): defaults until the player chooses; an alert takes an MFD and gives it
 * back 10 s after it clears; a pinned MFD is left alone; a worse alert keeps the MFD; the station keys
 * cycle a station's pages.
 */
class MfdTest {
    private final HudData d = new HudData();
    private final StationsFrame f = new StationsFrame();
    private final Mfd m = new Mfd(f, new Pages(), true);

    MfdTest() {
        f.read(d, false);
    }

    private Alert raise(String id, Level level) {
        d.alerts.begin(0);
        Alert a = d.alerts.raise(id, level, Category.SHIP);
        d.alerts.end();
        return a;
    }

    private void clear() {
        d.alerts.begin(0);
        d.alerts.end();
    }

    @Test
    void defaultsHoldUntilThePlayerChooses() {
        m.defaults(Page.SENSORS, true);
        assertEquals(Page.SENSORS, m.page);
        assertTrue(m.showsFolded());
        m.pick(Page.WEAPONS);
        m.defaults(Page.DAMAGE, false);
        assertEquals(Page.WEAPONS, m.page, "the player's pick holds");
        m.reset();
        m.defaults(Page.DAMAGE, false);
        assertEquals(Page.DAMAGE, m.page, "until they stand up");
    }

    @Test
    void anAlertTakesTheMfdAndGivesItBackTenSecondsAfterItClears() {
        m.defaults(Page.NAV, true);
        Alert fire = raise("fire:HOLD", Level.WARNING);
        assertTrue(m.take(fire, Page.DAMAGE, d));
        assertEquals(Page.DAMAGE, m.page);
        assertFalse(m.showsFolded(), "it unfolds");
        m.defaults(Page.SENSORS, true);
        assertEquals(Page.DAMAGE, m.page, "the alert holds it against the defaults");
        clear();
        m.tick(d, 1f);
        m.tick(d, 10.5f);
        assertEquals(Page.DAMAGE, m.page);
        m.tick(d, 11.1f);
        assertNull(m.autoId);
        assertEquals(Page.NAV, m.page, "back to what it showed");
        assertTrue(m.showsFolded(), "folded again, as it was");
    }

    @Test
    void aPinnedMfdIsLeftAlone() {
        m.defaults(Page.NAV, false);
        m.pin();
        assertFalse(m.take(raise("fire:HOLD", Level.WARNING), Page.DAMAGE, d));
        assertEquals(Page.NAV, m.page);
    }

    @Test
    void aWorseAlertKeepsTheMfd() {
        d.alerts.begin(0);
        Alert fire = d.alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT);
        Alert smoke = d.alerts.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT);
        d.alerts.end();
        assertTrue(m.take(fire, Page.DAMAGE, d));
        assertFalse(m.take(smoke, Page.ATMOS, d), "the caution waits on the annunciator");
        assertEquals(Page.DAMAGE, m.page);
    }

    @Test
    void aStationKeyCyclesItsPages() {
        m.defaults(Page.NAV, true);
        m.cycle(Station.TAC);
        assertEquals(Page.SENSORS, m.page);
        assertFalse(m.showsFolded());
        m.cycle(Station.TAC);
        assertEquals(Page.WEAPONS, m.page);
        m.cycle(Station.TAC);
        assertEquals(Page.DEFENSE, m.page);
        m.cycle(Station.TAC);
        assertEquals(Page.SENSORS, m.page);
        m.cycle(Station.ENG);
        assertEquals(Page.DAMAGE, m.page);
    }
}
