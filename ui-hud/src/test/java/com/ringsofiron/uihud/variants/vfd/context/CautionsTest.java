package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.context.Cautions.Handling;
import com.ringsofiron.uihud.variants.vfd.context.Cautions.Light;
import com.ringsofiron.uihud.variants.vfd.context.Cautions.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2's rows, their ranking and merging, H4's lights and the master alert cue, from the alerts alone. */
class CautionsTest {
    private final HudData d = new HudData();
    private final Cautions c = new Cautions();
    private final Cautions.Shown shown = new Cautions.Shown();

    @BeforeEach
    void seatedInFlight() {
        d.ctx.ship = new Entity();
        shown.seated = true;
        shown.flying = true;
    }

    /** One frame with the alerts {@code raise} gives. */
    private void frame(Runnable raise) {
        d.ctx.simSeconds += 0.1;
        d.alerts.begin(d.ctx.simSeconds);
        raise.run();
        d.alerts.end();
        c.update(d, shown, 0.1f);
    }

    private void fire(String room, int crew) {
        d.alerts.raise("fire:" + room, Level.WARNING, Category.ENGINEERING).set("FIRE", "Fire in the " + room).room(room).handling(crew);
    }

    @Test
    void aFireIsAWarningRowWithItsRoomAndItsCrewAndLightsTheMasterWarning() {
        frame(() -> fire("Bridge", 2));
        assertEquals(1, c.panel.size);
        Row r = c.panel.first();
        assertEquals("FIRE", r.code);
        assertEquals(Level.WARNING, r.level);
        assertEquals("BRIDGE", r.detail);
        assertEquals(Handling.CREW, r.handling);
        assertEquals(2, r.crew);
        assertTrue(c.warn);
        assertEquals("FIRE", c.warnCode);
        assertEquals(Level.WARNING, c.lights[Light.LIFE_SUP.ordinal()]);
        assertNull(c.lights[Light.HULL.ordinal()]);
    }

    @Test
    void twoFiresAreOneRowNamingTheFirstRoomAndCountingTheRest() {
        frame(() -> {
            fire("Bridge", 1);
            fire("Hold", 2);
        });
        assertEquals(1, c.panel.size);
        assertEquals("BRIDGE +1", c.panel.first().detail);
        assertEquals(3, c.panel.first().crew);
    }

    @Test
    void acknowledgingPutsTheCueOutUntilSomethingNewOrWorseComes() {
        frame(() -> fire("Bridge", 0));
        assertTrue(c.pulsing(c.panel.first(), d.ctx.simSeconds));
        c.acknowledge();
        frame(() -> fire("Bridge", 0));
        assertFalse(c.warn);
        assertFalse(c.pulsing(c.panel.first(), d.ctx.simSeconds));
        // A new warning lights it again, with its own code
        frame(() -> {
            fire("Bridge", 0);
            d.alerts.raise("breach:Hold", Level.WARNING, Category.ENGINEERING).set("BREACH", "Breach in the hold").room("Hold");
        });
        assertTrue(c.warn);
        assertEquals("BREACH", c.warnCode);
    }

    @Test
    void theMasterCautionGoesOutByItself() {
        frame(() -> d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%"));
        assertTrue(c.caut);
        assertFalse(c.warn);
        for (int i = 0; i < (int) (Cautions.CAUTION_CUE / 0.1f) + 2; i++) {
            frame(() -> d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%"));
        }
        assertFalse(c.caut);
        assertEquals(1, c.panel.size, "the row stays while its condition holds");
    }

    @Test
    void warningsComeFirstThenWhatNeedsTheCaptainThenWhatCrewWork() {
        frame(() -> {
            d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%").handling(1);
            d.alerts.raise("pressure:Hold", Level.CAUTION, Category.LIFE_SUPPORT).set("PRESSURE", "Low pressure").room("Hold").handling(0);
            fire("Galley", 1);
        });
        assertEquals(3, c.panel.size);
        assertEquals("FIRE", c.panel.get(0).code);
        assertEquals("PRESS LOW", c.panel.get(1).code);
        assertEquals(Handling.YOU, c.panel.get(1).handling);
        assertEquals("HULL DAMAGE", c.panel.get(2).code);
    }

    @Test
    void pastFiveRowsTheSixthNamesTheRest() {
        frame(() -> {
            fire("Bridge", 1);
            d.alerts.raise("breach:Hold", Level.WARNING, Category.ENGINEERING).set("BREACH", "Breach").room("Hold");
            d.alerts.raise("vacuum:Hold", Level.WARNING, Category.LIFE_SUPPORT).set("VACUUM", "Vacuum").room("Hold");
            d.alerts.raise("pressure:Galley", Level.CAUTION, Category.LIFE_SUPPORT).set("PRESSURE", "Low").room("Galley");
            d.alerts.raise("smoke:Galley", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke").room("Galley");
            d.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 60%");
            d.alerts.raise("o2", Level.CAUTION, Category.LIFE_SUPPORT).set("O2", "Oxygen low");
        });
        assertEquals(Cautions.ROWS - 1, c.panel.size);
        assertTrue(c.more.startsWith("+2 MORE: "), c.more);
        assertTrue(c.more.length() <= 30, c.more);
    }

    @Test
    void whatAnInstrumentShowsIsItsOwnButStillLightsItsLight() {
        shown.shieldControl = true;
        frame(() -> d.alerts.raise("shield:down", Level.CAUTION, Category.TACTICAL).set("SHIELD", "Shields lowered"));
        assertEquals(0, c.panel.size, "F3 shows the shield lowered");
        assertEquals(1, c.rows.size);
        assertEquals(Level.CAUTION, c.lights[Light.SHIELDS.ordinal()]);
        shown.shieldControl = false;
        frame(() -> d.alerts.raise("shield:down", Level.CAUTION, Category.TACTICAL).set("SHIELD", "Shields lowered"));
        assertEquals(1, c.panel.size);
    }

    @Test
    void theCaptainsOwnSuitAirIsNeverARowButStillLightsTheCue() {
        frame(() -> d.alerts.raise("suit", Level.WARNING, Category.CAPTAIN).set("SUIT AIR", "Suit air 1:20"));
        assertEquals(0, c.panel.size);
        assertTrue(c.warn);
    }

    @Test
    void noPowerAboardIsAWarningOnThePowerLight() {
        d.powerFrame.ratedWatts = 2e6;
        d.powerFrame.outputWatts = 0;
        d.powerFrame.addCircuit();
        frame(() -> { });
        assertEquals("NO POWER", c.panel.first().code);
        assertEquals(Level.WARNING, c.lights[Light.POWER.ordinal()]);
        assertTrue(c.noPower != null);
        assertFalse(c.powerDueBack);
    }
}
