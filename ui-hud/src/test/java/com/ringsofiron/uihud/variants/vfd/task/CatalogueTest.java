package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Entry;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Lamp;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Lit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The alert list (9.2), the band's one warning and three cautions, acknowledging (9.3), and the lamps. */
class CatalogueTest {
    private final HudData d = new HudData();
    private final Posts p = new Posts();
    private final Catalogue c = new Catalogue();
    private float now;

    @BeforeEach
    void atTheHelm() {
        d.ctx.ship = new Entity();
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
    }

    /** One frame, {@code dt} seconds after the last, with the alerts {@code raise} gives. */
    private void frame(float dt, Runnable raise) {
        now += dt;
        d.ctx.simSeconds += dt;
        d.alerts.begin(d.ctx.simSeconds);
        raise.run();
        d.alerts.end();
        p.update(d, false);
        c.update(d, p, now);
    }

    private void fire(String room, int crew, int tiles) {
        d.alerts.raise("fire:" + room, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + room).room(room).count(tiles).handling(crew);
    }

    private void missiles(int n, float impact) {
        d.threatFrame.clear();
        for (int i = 0; i < n; i++) {
            MissileTrack m = d.threatFrame.add();
            m.side = Side.INCOMING;
            m.timeToImpact = impact + i;
        }
        d.ctx.missilesInbound = n;
    }

    @Test
    void aFireIsACautionNamingItsRoomAndWhoIsOnItAndLightsFire() {
        frame(0.1f, () -> fire("HOLD", 1, 2));
        assertNull(c.warning);
        assertEquals(1, c.row.size);
        Entry e = c.row.first();
        assertEquals("FIRE  HOLD", e.line(0));
        assertEquals("1 ON IT", e.line(1));
        assertEquals(Lit.CAUTION, c.lamps[Lamp.FIRE.ordinal()]);
        assertEquals(Lit.DARK, c.lamps[Lamp.HULL.ordinal()]);
    }

    @Test
    void aLargeFireOrOneInTheCaptainsRoomIsAWarning() {
        frame(0.1f, () -> fire("HOLD", 0, Catalogue.LARGE_FIRE));
        assertNotNull(c.warning);
        assertEquals("FIRE", c.warning.title);
        assertEquals("NOBODY ON IT", c.warning.action);
        assertEquals(Lit.WARNING, c.lamps[Lamp.FIRE.ordinal()]);

        d.captainStatus.present = true;
        d.captainStatus.room = "BRIDGE";
        frame(0.1f, () -> fire("BRIDGE", 0, 1));
        assertEquals("FIRE", c.warning.title);
        assertEquals("YOU ARE IN IT", c.warning.note);
    }

    @Test
    void aMissileIsAWarningUnderTwentySecondsAndTheActionFitsThePost() {
        missiles(2, 4f);
        frame(0.1f, () -> { });
        assertEquals("MISSILE", c.warning.title);
        assertEquals("2 INBOUND", c.warning.detail);
        assertEquals("4", c.warning.number);
        assertEquals("F", c.warning.key);
        assertEquals("DECOY", c.warning.action);

        // On deck F forces doors: the action is getting to a console
        d.ctx.presence = Presence.DECK;
        d.ctx.exteriorView = false;
        frame(0.1f, () -> { });
        assertEquals("", c.warning.key);
        assertEquals("V AT A CONSOLE TO FIGHT", c.warning.action);

        // Further out it is a caution with its short form
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
        missiles(2, 38f);
        frame(0.1f, () -> { });
        assertNull(c.warning);
        assertEquals("MISSILE  2 INBOUND", c.row.first().line(0));
        assertEquals("38 S", c.row.first().line(1));
    }

    @Test
    void pointDefenseIsTheWarningsNote() {
        Mount pd = d.weaponFrame.add();
        pd.kind = Kind.POINT_DEFENSE;
        pd.engaging = true;
        missiles(1, 6f);
        frame(0.1f, () -> { });
        assertEquals("POINT DEFENSE ENGAGING", c.warning.note);
    }

    @Test
    void theSoonerHarmHasTheWindowAndTheOtherWarningGoesDownToTheRow() {
        d.captainStatus.present = true;
        d.captainStatus.bleedOut = 50f;
        missiles(1, 6f);
        frame(0.1f, () -> d.alerts.raise("captain:bleeding", Level.WARNING, Category.CAPTAIN).set("BLEEDING", "You are bleeding out"));
        assertEquals("MISSILE", c.warning.title);
        assertEquals(1, c.row.size);
        assertEquals("CAPTAIN HURT", c.row.first().id);
        assertEquals(Lit.WARNING, c.lamps[Lamp.CREW.ordinal()]);
    }

    @Test
    void cautionsLeaveTheBandAfterTenSecondsAndKeepTheirLamp() {
        frame(0.1f, () -> fire("HOLD", 1, 1));
        assertEquals(1, c.row.size);
        frame(Catalogue.CAUTION_STAYS + 0.1f, () -> fire("HOLD", 1, 1));
        assertEquals(0, c.row.size);
        assertEquals(Lit.CAUTION, c.lamps[Lamp.FIRE.ordinal()]);
    }

    @Test
    void clickingTheBandAcknowledgesAndACautionThatWorsensComesBack() {
        frame(0.1f, () -> {
            fire("HOLD", 1, 1);
            d.alerts.raise("breach:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in BRIDGE").room("BRIDGE").count(1).handling(0);
        });
        assertEquals(2, c.row.size);
        c.acknowledge();
        frame(0.1f, () -> {
            fire("HOLD", 1, 1);
            d.alerts.raise("breach:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in BRIDGE").room("BRIDGE").count(1).handling(0);
        });
        assertEquals(0, c.row.size);
        // A second breach in the room: it vents worse, and comes back
        frame(0.1f, () -> {
            fire("HOLD", 1, 1);
            d.alerts.raise("breach:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in BRIDGE").room("BRIDGE").count(2).handling(0);
        });
        assertEquals(1, c.row.size);
        assertEquals("VENTING  BRIDGE", c.row.first().line(0));
        assertEquals("NOBODY", c.row.first().line(1));
    }

    @Test
    void moreThanThreeCautionsShowThreeAndCountTheRest() {
        frame(0.1f, () -> {
            fire("HOLD", 1, 1);
            fire("BRIDGE", 1, 1);
            fire("AFT", 1, 1);
            fire("FORE", 1, 1);
            fire("MID", 1, 1);
        });
        assertEquals(Catalogue.SLOTS, c.row.size);
        assertEquals(2, c.more);
    }

    @Test
    void aShortIsAPowerCautionAndNoReactorFeedingIsNoPower() {
        SystemRow r = d.powerFrame.addFault();
        r.system = "TURRET";
        r.room = "BRIDGE";
        r.fault = Fault.SHORTED;
        r.tileX = 3;
        r.tileY = 2;
        frame(0.1f, () -> d.alerts.raise("sys:TURRET:3,2", Level.CAUTION, Category.ENGINEERING).set("TURRET", "TURRET in BRIDGE: shorted").handling(1));
        Entry e = c.row.first();
        assertEquals("POWER  BRIDGE TURRET SHORT", e.full1);
        assertEquals("1 RESETTING", e.line(1));
        assertEquals(Lit.CAUTION, c.lamps[Lamp.POWER.ordinal()]);

        d.powerFrame.clear();
        SystemRow reactor = d.powerFrame.addFault();
        reactor.system = "REACTOR";
        reactor.fault = Fault.SHORTED;
        reactor.timer = 41f;
        d.powerFrame.addCircuit();
        frame(0.1f, () -> { });
        assertEquals("NO POWER", c.warning.title);
        assertEquals("REACTOR SHORTED", c.warning.detail);
        assertEquals("41", c.warning.number);
        assertEquals(Lit.WARNING, c.lamps[Lamp.POWER.ordinal()]);
    }

    @Test
    void propellantIsACautionUnderOneAndAQuarterTimesHomeAndTheLampWarnsThere() {
        d.flightStatus.deltaVLeft = 500;
        d.flightStatus.deltaVHome = 410;
        frame(0.1f, () -> { });
        Entry e = c.row.first();
        assertEquals("PROPELLANT", e.id);
        assertEquals("PROP 500 M/S", e.line(0));
        assertEquals("HOME TAKES 410", e.line(1));
        assertEquals(Lit.WARNING, c.lamps[Lamp.PROP.ordinal()]);

        d.flightStatus.deltaVLeft = 580;
        frame(0.1f, () -> { });
        assertEquals(0, c.row.size);
        assertEquals(Lit.CAUTION, c.lamps[Lamp.PROP.ordinal()]);
    }

    @Test
    void withNoEngineWorkingTheDeltaVReadsNoneButThatIsDrivesWarningNotAPropellantCaution() {
        // The game counts the delta-v working engines can draw: with the reactor out it reads 0 on full tanks
        c.engines = 2;
        c.enginesWorking = 0;
        d.flightStatus.deltaVLeft = 0;
        d.flightStatus.deltaVHome = 0.3;
        frame(0.1f, () -> { });
        for (Entry e : c.entries) assertFalse("PROPELLANT".equals(e.id), "no propellant caution");
        assertEquals(Lit.DARK, c.lamps[Lamp.PROP.ordinal()]);
        assertEquals(Lit.WARNING, c.lamps[Lamp.DRIVE.ordinal()]);
    }

    @Test
    void atGunneryTheShieldCautionIsWeaponsRowAndTakesNoSlot() {
        d.shieldStatus.present = true;
        d.shieldStatus.emitters = 1;
        d.shieldStatus.emittersTripped = 1;
        frame(0.1f, () -> { });
        assertEquals("SHIELD TRIPPED", c.row.first().line(0));
        assertEquals(Lit.WARNING, c.lamps[Lamp.SHIELD.ordinal()]);
        assertTrue(c.shieldLamp);

        d.ctx.presence = Presence.WEAPONS;
        frame(0.1f, () -> { });
        assertEquals(0, c.row.size);
        assertEquals(Lit.WARNING, c.lamps[Lamp.SHIELD.ordinal()]);
    }

    @Test
    void aShipWithoutAnEmitterHasNoShieldLamp() {
        frame(0.1f, () -> { });
        assertFalse(c.shieldLamp);
        assertEquals(Lit.DARK, c.lamps[Lamp.SHIELD.ordinal()]);
    }

    @Test
    void crewOutsideAreAnAdvisoryAndABurnWithThemOutIsAWarning() {
        d.ctx.crewOutside = true;
        frame(0.1f, () -> d.alerts.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "EVA: Ada on the hull. A hard burn can throw them off the hull"));
        assertNull(c.warning);
        assertEquals(Lit.ADVISORY, c.lamps[Lamp.EVA.ordinal()]);
        assertTrue(c.schematicLit());

        d.navReadout.burnsLeft = 1;
        d.navReadout.burnStartIn = 20f;
        frame(0.1f, () -> d.alerts.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "EVA: Ada on the hull. A hard burn can throw them off the hull"));
        assertEquals("CREW OUT", c.warning.title);
        assertEquals("ADA ON THE HULL", c.warning.detail);
        assertEquals("I", c.warning.key);
        assertEquals(Lit.WARNING, c.lamps[Lamp.EVA.ordinal()]);
    }

    @Test
    void aWoundedCrewMemberWithNobodyOnItIsACautionAndLightsCrewAtWarning() {
        frame(0.1f, () -> d.alerts.raise("casualty:Cole", Level.WARNING, Category.CREW).set("CASUALTY", "Cole is bleeding out").handling(0));
        assertNull(c.warning);
        assertEquals("WOUNDED  COLE", c.row.first().line(0));
        assertEquals("NO MEDIC FREE", c.row.first().line(1));
        assertEquals(Lit.WARNING, c.lamps[Lamp.CREW.ordinal()]);
    }

    @Test
    void aNewWarningPulsesForThreeSecondsUntilAcknowledged() {
        missiles(1, 6f);
        frame(0.1f, () -> { });
        assertTrue(c.pulse);
        frame(Catalogue.WARNING_PULSE + 0.1f, () -> { });
        assertFalse(c.pulse);
        assertEquals("MISSILE", c.warning.title);

        missiles(0, 0f);
        frame(0.1f, () -> { });
        missiles(1, 6f);
        frame(0.1f, () -> { });
        assertTrue(c.pulse);
        c.acknowledge();
        frame(0.1f, () -> { });
        assertFalse(c.pulse);
    }

    @Test
    void evaWhoIsTheFirstNameAndPlace() {
        assertEquals("Ada on the hull", Catalogue.evaWho("EVA: Ada on the hull; Bea adrift. A hard burn can throw them off the hull"));
        assertEquals("", Catalogue.evaWho(""));
    }
}
