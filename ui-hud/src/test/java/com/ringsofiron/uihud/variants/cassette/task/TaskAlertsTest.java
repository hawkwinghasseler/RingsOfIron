package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.NoticeAlerts;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Id;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Lamp;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Post;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Task-driven Cassette HUD's alert routing (spec sections 6 ANN, 9 and 13): each of the game's
 * alerts at 9.2's level and lamp, the alerts the HUD raises from its own data, the band's two rows in
 * 9.2's order, and acknowledging.
 */
class TaskAlertsTest {
    private final HudData d = new HudData();
    private final TaskAlerts t = new TaskAlerts();
    private Post post = Post.DECK;

    /** Sim seconds, run on by each frame. */
    private double now;

    private void frame(float dt, Consumer<HudData> raises) {
        now += dt;
        d.ctx.simSeconds = now;
        d.alerts.begin(now);
        raises.accept(d);
        d.alerts.end();
        t.update(d, dt, post);
    }

    private static Alert fire(HudData d, int tiles) {
        return d.alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD").count(tiles);
    }

    private static void smoke(HudData d) {
        d.alerts.raise("smoke:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in BRIDGE").room("BRIDGE");
    }

    /** A system's fault in the power frame and its alert, as GameAlerts raises it (a warning for anything wrecked or a reactor). */
    private void system(String system, Fault fault) {
        SystemRow r = d.powerFrame.addFault();
        r.system = system;
        r.fault = fault;
        r.room = "HOLD";
        r.tileX = 3;
        r.tileY = d.powerFrame.faults.size;
    }

    private static void sysAlert(HudData d, SystemRow r, Level game, String what) {
        d.alerts.raise("sys:" + r.system + ":" + r.tileX + "," + r.tileY, game, Category.ENGINEERING)
                .set(r.system, r.system + " in " + r.room + ": " + what).room(r.room);
    }

    private Level lamp(Lamp l) {
        return t.level[l.ordinal()];
    }

    private CrewProvider roster(String... nameAndCondition) {
        return new CrewProvider() {
            @Override
            public void collect(CrewSheet out) {
                out.shipName = "Kestrel";
                for (int i = 0; i + 1 < nameAndCondition.length; i += 2) {
                    Member m = out.addMember();
                    m.name = nameAndCondition[i];
                    m.condition = nameAndCondition[i + 1];
                    m.morale = "Morale 70%";
                }
            }

            @Override
            public String hire(String key) {
                return "";
            }

            @Override
            public String dismiss(long id) {
                return "";
            }

            @Override
            public String takePrisoners() {
                return "";
            }

            @Override
            public String settlePrisoner(long id) {
                return "";
            }

            @Override
            public String recruit(long id) {
                return "";
            }
        };
    }

    /** 9.2's table of the game's warnings: a warning is the band's only while its alert is up, else news on the telex. */
    @Test
    void aWarningIsTheBandsOnlyWhileItsAlertIsUp() {
        frame(0.1f, x -> { });
        assertFalse(t.carries(NoticeAlerts.VENTING, false), "a leak before its room's air falls: news");
        frame(0.1f, x -> x.alerts.raise("breach:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in HOLD").room("HOLD"));
        assertTrue(t.carries(NoticeAlerts.VENTING, false), "VENTING says it");
        assertFalse(t.carries(NoticeAlerts.WOUNDED, false), "nobody wounded");
        assertFalse(t.carries(NoticeAlerts.BOARDERS, false), "an alert this HUD can't raise yet");
        assertTrue(t.carries(NoticeAlerts.ASKED, true), "the placard asks");
        assertFalse(t.carries(NoticeAlerts.ASKED, false));
        frame(0.1f, x -> x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2)
                .timer(30f, TimerKind.HARM));
        assertTrue(t.carries(NoticeAlerts.MISSILE, false));
        assertFalse(t.carries(NoticeAlerts.VENTING, false), "the breach gone");
    }

    @Test
    void aFireIsACautionUntilNobodyHasTakenItOnFiveSecondsInOrOneWhereYouStand() {
        frame(0.1f, x -> fire(x, 2).handling(0));
        assertEquals(Level.CAUTION, lamp(Lamp.FIRE));
        assertNull(t.warning);
        assertEquals("fire:HOLD", t.cautions.first().id);
        assertTrue(t.schematicWanted());
        assertArrayEquals(new String[]{"FIRE", "HOLD", "2 TILES · NOBODY"}, t.lines(t.cautions.first(), d));
        assertEquals("FIRE: HOLD, 2 tiles, nobody on it", t.why[Lamp.FIRE.ordinal()], "sized in tiles, as the game counts it");

        // Nobody on it 5 s after it started: a warning
        for (int i = 0; i < 52; i++) frame(0.1f, x -> fire(x, 3).handling(0));
        assertEquals(Level.WARNING, lamp(Lamp.FIRE));
        assertEquals("fire:HOLD", t.warning.id);
        assertTrue(t.masterWarning);
        assertEquals("FIRE   HOLD, 3 TILES", TaskAlerts.title(t.warning, d));

        // Someone takes it on: a caution again
        frame(0.1f, x -> fire(x, 3).handling(1));
        assertEquals(Level.CAUTION, lamp(Lamp.FIRE));
        assertArrayEquals(new String[]{"FIRE", "HOLD", "3 TILES · 1 CREW"}, t.lines(t.cautions.first(), d));
        assertEquals("FIRE: HOLD, 3 tiles, 1 crew on it", t.why[Lamp.FIRE.ordinal()]);
        // Named where the game names them (v16: "Cole on it")
        frame(0.1f, x -> fire(x, 3).handling(1).crew("Cole", true, Float.NaN));
        assertArrayEquals(new String[]{"FIRE", "HOLD", "3 TILES · COLE"}, t.lines(t.cautions.first(), d));
        assertEquals("FIRE: HOLD, 3 tiles, Cole on it", t.why[Lamp.FIRE.ordinal()]);
        frame(0.1f, x -> fire(x, 3).handling(2).crew("Cole", true, Float.NaN));
        assertEquals("FIRE: HOLD, 3 tiles, Cole and 1 more on it", t.why[Lamp.FIRE.ordinal()]);
        assertEquals("COLE +1 ON IT", TaskAlerts.who(t.cautions.first()));

        d.captainStatus.present = true;
        d.captainStatus.room = "HOLD";
        frame(0.1f, x -> fire(x, 1).handling(1));
        assertEquals("fire:HOLD", t.warning.id, "a fire in the captain's room");
    }

    @Test
    void aFireNobodyCanBeSaidToBeOnIsAWarningAfterFiveSeconds() {
        // Raised at 0.1 s: 4.7 s old after 48 frames, 5.1 s after 52
        for (int i = 0; i < 48; i++) frame(0.1f, x -> fire(x, 1));
        assertNull(t.warning, "under 5 s old");
        for (int i = 0; i < 4; i++) frame(0.1f, x -> fire(x, 1));
        assertEquals("fire:HOLD", t.warning.id);
    }

    @Test
    void aCautionLeavesTheBandAfterTenSecondsAndKeepsItsLamp() {
        frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(1, t.cautions.size);
        assertTrue(t.masterCaution);
        for (int i = 0; i < 110; i++) frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(0, t.cautions.size);
        assertFalse(t.bandUp());
        assertEquals(Level.CAUTION, lamp(Lamp.AIR));
    }

    @Test
    void aCautionThatWorsensComesBack() {
        for (int i = 0; i < 110; i++) frame(0.1f, x -> fire(x, 2).handling(1));
        assertFalse(t.bandUp(), "the fire the crew are on has left for its lamp");
        frame(0.1f, x -> fire(x, 2).handling(0));
        assertEquals("fire:HOLD", t.warning.id, "nobody on it any more, long past its first 5 s");
        assertTrue(t.warningNew);
    }

    @Test
    void theSoonestHarmTakesTheWarningRowAndTheOtherWarningsGoBelow() {
        d.captainStatus.present = true;
        d.captainStatus.room = "HOLD";
        frame(0.1f, x -> fire(x, 6));
        frame(0.1f, x -> {
            fire(x, 6);
            x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").timer(14f, TimerKind.HARM);
        });
        assertEquals("missile", t.warning.id);
        assertEquals("fire:HOLD", t.cautions.first().id);
    }

    @Test
    void aMissileFarOffIsACautionUntilTwentySecondsOut() {
        frame(0.1f, x -> x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2)
                .timer(38f, TimerKind.HARM));
        assertNull(t.warning);
        assertArrayEquals(new String[]{"MISSILE", "2", "38 S"}, t.lines(t.cautions.first(), d));
        frame(0.1f, x -> x.alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2)
                .timer(14f, TimerKind.HARM));
        assertEquals("missile", t.warning.id);
        assertTrue(t.warningNew);
    }

    @Test
    void pressingTheBandAcknowledgesItsCautions() {
        frame(0.1f, TaskAlertsTest::smoke);
        t.acknowledge(d);
        frame(0.1f, TaskAlertsTest::smoke);
        assertEquals(0, t.cautions.size);
        assertFalse(t.masterCaution);
        // The lamp stays lit: the caution lives on there
        assertEquals(Level.CAUTION, lamp(Lamp.AIR));
    }

    @Test
    void theTargetsLockIsLockedOnOnDeckAndTheTargetPlatesAtTheHelm() {
        Consumer<HudData> locked = x -> x.alerts.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", "Pirate raider 6 has you locked");
        frame(0.1f, locked);
        for (Lamp l : Lamp.values()) assertNull(lamp(l), l.name());
        assertArrayEquals(new String[]{"LOCKED ON", "", "PIRATE RAIDER 6"}, t.lines(t.cautions.first(), d));
        post = Post.HELM;
        frame(0.1f, locked);
        assertEquals(0, t.cautions.size, "the target plate shows the lock");
    }

    @Test
    void aWreckedEngineIsADriveCautionAndAShortIsPowersAndLightsDriveToo() {
        system("ENGINE", Fault.DESTROYED);
        SystemRow engine = d.powerFrame.faults.first();
        frame(0.1f, x -> sysAlert(x, engine, Level.WARNING, "destroyed"));
        assertNull(t.warning);
        assertEquals(Level.CAUTION, lamp(Lamp.DRIVE));
        assertEquals(Id.DRIVE, t.row("sys:ENGINE:3,1").id);

        engine.fault = Fault.SHORTED;
        frame(0.1f, x -> sysAlert(x, engine, Level.CAUTION, "shorted"));
        assertEquals(Level.CAUTION, lamp(Lamp.DRIVE));
        assertEquals(Level.CAUTION, lamp(Lamp.POWER));
        assertEquals(Id.POWER, t.row("sys:ENGINE:3,1").id);
    }

    @Test
    void aReactorFaultIsAWarningOnlyWhenNoReactorFeedsTheShip() {
        system("REACTOR", Fault.SHORTED);
        SystemRow reactor = d.powerFrame.faults.first();
        d.powerFrame.ratedWatts = 2e6;
        d.powerFrame.addCircuit().outputWatts = 1e6;
        d.powerFrame.outputWatts = 1e6;
        frame(0.1f, x -> sysAlert(x, reactor, Level.WARNING, "shorted"));
        assertNull(t.warning, "the other reactor feeds the ship");
        assertEquals(Level.CAUTION, lamp(Lamp.POWER));

        d.powerFrame.circuits.clear();
        d.powerFrame.outputWatts = 0;
        frame(0.1f, x -> sysAlert(x, reactor, Level.WARNING, "shorted"));
        assertEquals(Level.WARNING, lamp(Lamp.POWER));
        assertEquals("NO POWER   REACTOR SHORTED", TaskAlerts.title(t.warning, d));
    }

    @Test
    void withNoReactorFeedingAnythingTheCutOffSystemsLightTheirLampsAndLeaveTheBandToTheReactor() {
        system("REACTOR", Fault.DESTROYED);
        system("ENGINE", Fault.NO_POWER);
        system("TURRET", Fault.NO_POWER);
        SystemRow reactor = d.powerFrame.faults.get(0), engine = d.powerFrame.faults.get(1), gun = d.powerFrame.faults.get(2);
        d.powerFrame.ratedWatts = 1e6;
        Consumer<HudData> dark = x -> {
            sysAlert(x, reactor, Level.WARNING, "destroyed");
            sysAlert(x, engine, Level.CAUTION, "no power");
            sysAlert(x, gun, Level.CAUTION, "no power");
        };
        frame(0.1f, dark);
        assertEquals("sys:REACTOR:3,1", t.warning.id);
        assertEquals("NO POWER   REACTOR WRECKED", TaskAlerts.title(t.warning, d));
        assertEquals(0, t.cautions.size, "the reactor's warning says it once");
        assertEquals(Level.WARNING, lamp(Lamp.POWER));
        assertEquals(Level.CAUTION, lamp(Lamp.ARMS), "a gun cut off still lights ARMS");

        // Disabled for want of power: DISABLED says it, and the reactor's row only lights POWER
        d.ctx.disabledReason = "reactor down";
        Consumer<HudData> disabled = x -> {
            dark.accept(x);
            x.alerts.raise("disabled", Level.WARNING, Category.ENGINEERING).set("DISABLED", "Ship disabled: reactor down");
        };
        frame(0.1f, disabled);
        assertEquals("disabled", t.warning.id);
        assertEquals(0, t.cautions.size);
        assertEquals(Level.WARNING, lamp(Lamp.POWER));

        // Disabled for its hull, the reactor's own warning goes below
        d.ctx.disabledReason = "hull broken";
        frame(0.1f, disabled);
        assertEquals("disabled", t.warning.id);
        assertEquals("sys:REACTOR:3,1", t.cautions.first().id);
        assertEquals(1, t.cautions.size);

        // With a reactor feeding the ship, a system cut off is a caution of its own
        d.ctx.disabledReason = "";
        d.powerFrame.faults.removeIndex(0);
        d.powerFrame.addCircuit().outputWatts = 5e5;
        d.powerFrame.outputWatts = 5e5;
        frame(0.1f, x -> sysAlert(x, engine, Level.CAUTION, "no power"));
        assertEquals("sys:ENGINE:3,2", t.cautions.first().id);
        assertArrayEquals(new String[]{"ENGINE", "HOLD", "CUT OFF"}, t.lines(t.cautions.first(), d));
    }

    @Test
    void aReactorSwitchedOffIsPowersWarningWhenNoneFeedsTheShip() {
        system("REACTOR", Fault.OFF);
        SystemRow reactor = d.powerFrame.faults.first();
        d.powerFrame.ratedWatts = 1e6;
        frame(0.1f, x -> sysAlert(x, reactor, Level.ADVISORY, "off"));
        assertEquals("NO POWER   REACTOR OFF", TaskAlerts.title(t.warning, d));
        assertEquals(Level.WARNING, lamp(Lamp.POWER));
    }

    @Test
    void aSystemsFaultIsSaidInTheShipsConsolesWords() {
        system("ENGINE", Fault.DISABLED);
        SystemRow engine = d.powerFrame.faults.first();
        frame(0.1f, x -> sysAlert(x, engine, Level.CAUTION, "disabled"));
        assertArrayEquals(new String[]{"ENGINE", "HOLD", "DAMAGED"}, t.lines(t.cautions.first(), d));
        assertEquals("ENGINE   DAMAGED", TaskAlerts.title(t.cautions.first(), d));
        assertEquals("WRECKED", TaskAlerts.word(Fault.DESTROYED));
        assertEquals("CUT OFF", TaskAlerts.word(TaskAlerts.faultIn("TURRET in HOLD: no power")));
    }

    @Test
    void aSystemSwitchedOffIsPowersAdvisoryAndTheHelmsFaultIsItsSystemsAlert() {
        system("TURRET", Fault.OFF);
        SystemRow gun = d.powerFrame.faults.first();
        frame(0.1f, x -> {
            sysAlert(x, gun, Level.ADVISORY, "off");
            x.alerts.raise("helm", Level.CAUTION, Category.FLIGHT).set("HELM", "Helm: the helm is switched off");
        });
        assertEquals(Level.ADVISORY, lamp(Lamp.POWER));
        assertNull(lamp(Lamp.ARMS));
        assertNull(t.row("helm"));
        assertFalse(t.bandUp());
    }

    @Test
    void whatTheGameCallsAWarningAndTheSpecACautionTakesTheCautionRow() {
        frame(0.1f, x -> {
            x.alerts.raise("propellant", Level.WARNING, Category.FLIGHT).set("FUEL", "Propellant low: 500 kg left");
            x.alerts.raise("breach:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in HOLD").room("HOLD").handling(0);
            x.alerts.raise("air:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("VACUUM", "HOLD is in vacuum").room("HOLD");
            x.alerts.raise("oxygen", Level.WARNING, Category.LIFE_SUPPORT).set("O2", "Oxygen tanks dry: emergency oxygen 40%");
            x.alerts.raise("hull", Level.WARNING, Category.ENGINEERING).set("HULL", "Hull at 38%");
        });
        assertNull(t.warning);
        assertEquals(5, t.cautions.size);
        assertEquals(Level.CAUTION, lamp(Lamp.PROP));
        assertEquals(Level.CAUTION, lamp(Lamp.HULL));
        assertEquals(Level.CAUTION, lamp(Lamp.AIR));
        assertArrayEquals(new String[]{"VENTING", "HOLD", "NOBODY ON IT"}, t.lines(t.cautions.first(), d));
    }

    @Test
    void theCautionRowRunsInTheListsOrderPeopleThenTheShipThenTheJob() {
        system("ENGINE", Fault.DISABLED);
        SystemRow engine = d.powerFrame.faults.first();
        frame(0.1f, x -> {
            x.alerts.raise("propellant", Level.CAUTION, Category.FLIGHT).set("FUEL", "Propellant low: 500 kg left");
            x.alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 62%");
            sysAlert(x, engine, Level.CAUTION, "disabled");
            fire(x, 1);
        });
        assertEquals("fire:HOLD", t.cautions.get(0).id);
        assertEquals("sys:ENGINE:3,1", t.cautions.get(1).id);
        assertEquals("hull", t.cautions.get(2).id);
        assertEquals("propellant", t.cautions.get(3).id);
    }

    @Test
    void noSuitInThinAirIsTheRoomsCautionUntilTheAirHurts() {
        d.captainStatus.present = true;
        d.captainStatus.room = "BRIDGE";
        d.captainStatus.roomPressure = 0.6f;
        d.captainStatus.roomOxygen = 0.6f;
        Consumer<HudData> thin = x -> {
            x.alerts.raise("captain:air", Level.WARNING, Category.CAPTAIN).set("NO SUIT", "No suit, and the air is thin").room("BRIDGE");
            x.alerts.raise("air:BRIDGE", Level.CAUTION, Category.LIFE_SUPPORT).set("PRESSURE", "BRIDGE at 60% pressure").room("BRIDGE");
        };
        frame(0.1f, thin);
        assertNull(t.row("captain:air"));
        assertNull(t.warning);
        assertArrayEquals(new String[]{"THIN AIR", "BRIDGE", "60%"}, t.lines(t.cautions.first(), d));

        d.captainStatus.roomOxygen = 0.2f;
        frame(0.1f, thin);
        assertEquals("captain:air", t.warning.id);
        assertEquals("YOUR AIR   BRIDGE 20% OXYGEN", TaskAlerts.title(t.warning, d));
        assertEquals(Level.WARNING, lamp(Lamp.AIR));
        // Pushed down below a warning that outranks it, its short form keeps the oxygen
        assertArrayEquals(new String[]{"YOUR AIR", "NO SUIT", "BRIDGE  20% O2"}, TaskAlerts.gameLines(t.warning, d));
    }

    @Test
    void suitAirGoesByTimeInBadAirOnly() {
        d.captainStatus.present = true;
        d.captainStatus.suited = true;
        d.captainStatus.suitAir = 200f;
        d.captainStatus.suitAirCapacity = 1800f;
        Consumer<HudData> suit = x -> x.alerts.raise("captain:suit", Level.CAUTION, Category.CAPTAIN).set("SUIT AIR", "Suit air 11%")
                .timer(x.captainStatus.suitAir, TimerKind.HARM);
        frame(0.1f, suit);
        assertNull(t.row("captain:suit"));
        assertEquals(Id.SUIT_AIR, t.row(TaskAlerts.OWN + "SUIT_AIR").id);
        assertArrayEquals(new String[]{"SUIT AIR", "YOU", "3:20"}, t.lines(t.cautions.first(), d));

        d.captainStatus.suitAir = 50f;
        frame(0.1f, suit);
        assertEquals(TaskAlerts.OWN + "CAPTAIN_AIR", t.warning.id);
        assertEquals("YOUR AIR   50 S", TaskAlerts.title(t.warning, d));

        // In good air the suit's own air doesn't matter
        d.captainStatus.roomPressure = 1f;
        d.captainStatus.roomOxygen = 1f;
        frame(0.1f, suit);
        assertFalse(t.bandUp());
    }

    @Test
    void aWoundBeingTreatedIsAnAdvisoryAndOneNobodyTreatsIsWounded() {
        d.crew = roster("Cole", "Critical wound, being treated", "Bea", "Serious wound, down", "Ada", "Light wound 80%");
        frame(0.1f, x -> x.alerts.raise("casualty:Cole", Level.WARNING, Category.CREW).set("CASUALTY", "Cole is bleeding out")
                .timer(250f, TimerKind.HARM));
        assertNull(t.warning, "treatment holds the clock");
        assertEquals(Level.ADVISORY, t.row("casualty:Cole").level);
        assertEquals(Level.CAUTION, lamp(Lamp.CREW));
        assertArrayEquals(new String[]{"WOUNDED", "BEA", "DOWN"}, t.lines(t.cautions.first(), d));
        assertTrue(t.why[Lamp.CREW.ordinal()].contains("Ada: light wound 80%"));

        d.crew = roster("Cole", "Critical wound, 4:10 to live");
        for (int i = 0; i < 8; i++) {
            frame(0.1f, x -> x.alerts.raise("casualty:Cole", Level.WARNING, Category.CREW).set("CASUALTY", "Cole is bleeding out")
                    .timer(250f, TimerKind.HARM).handling(0));
        }
        assertEquals("casualty:Cole", t.warning.id);
        assertEquals("CRITICAL   COLE   4:10 TO LIVE", TaskAlerts.title(t.warning, d));
        assertEquals(Level.WARNING, lamp(Lamp.CREW));
    }

    @Test
    void crewOutsideWithABurnUnderWayOrDueIsEvaBurn() {
        d.ctx.crewOutside = true;
        Consumer<HudData> eva = x -> x.alerts.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "EVA: Ada on the hull, 12 m of line out");
        frame(0.1f, eva);
        assertEquals(Level.ADVISORY, lamp(Lamp.EVA));
        assertFalse(t.bandUp());

        d.navReadout.burning = true;
        frame(0.1f, eva);
        assertEquals(TaskAlerts.OWN + "EVA_BURN", t.warning.id);
        assertEquals("CREW OUTSIDE   ADA", TaskAlerts.title(t.warning, d));
        assertEquals(Level.WARNING, lamp(Lamp.EVA));

        d.navReadout.burning = false;
        d.navReadout.burnsLeft = 1;
        d.navReadout.burnStartIn = 25f;
        frame(0.1f, eva);
        assertEquals(25f, t.warning.timer);
        d.navReadout.burnStartIn = 40f;
        frame(0.1f, eva);
        assertNull(t.warning);
    }

    @Test
    void crewAdriftIsAnEvaCaution() {
        frame(0.1f, x -> x.alerts.raise("eva", Level.ADVISORY, Category.CREW)
                .set("EVA", "EVA: Ada adrift 340 m off, drifting away at 3 m/s, 31 min of air: waiting to be picked up; Bea on the hull, no line"));
        assertEquals(Level.CAUTION, lamp(Lamp.EVA));
        assertArrayEquals(new String[]{"ADRIFT", "ADA", "340 M"}, t.lines(t.cautions.first(), d));
        assertEquals("ADA +1", TaskAlerts.outside(d.alerts.get("eva").text));
    }

    @Test
    void noGunWorkingInCombatIsTheArmsWarning() {
        for (int i = 0; i < 2; i++) d.weaponFrame.add().state = WeaponStatusProvider.State.DESTROYED;
        frame(0.1f, x -> { });
        assertFalse(t.bandUp(), "out of combat");
        d.ctx.combat = true;
        frame(0.1f, x -> { });
        assertEquals(TaskAlerts.OWN + "ARMS", t.warning.id);
        assertEquals(Level.WARNING, lamp(Lamp.ARMS));
    }

    @Test
    void theShieldCloudBelowAQuarterIsACautionAndBelowHalfAnAdvisory() {
        d.shieldStatus.present = true;
        d.shieldStatus.emitters = 2;
        d.shieldStatus.raised = true;
        d.shieldStatus.capacity = 100f;
        d.shieldStatus.cloud = 20f;
        frame(0.1f, x -> { });
        assertEquals(Level.CAUTION, lamp(Lamp.SHIELD));
        assertArrayEquals(new String[]{"SHIELD", "CLOUD", "20%"}, t.lines(t.cautions.first(), d));
        d.shieldStatus.cloud = 40f;
        frame(0.1f, x -> { });
        assertEquals(Level.ADVISORY, lamp(Lamp.SHIELD));
        assertFalse(t.bandUp());
    }

    @Test
    void propellantThinsToAnAdvisoryAndAPlanItCantFlyIsACaution() {
        d.flightStatus.deltaVLeft = 1100;
        d.flightStatus.deltaVHome = 800;
        frame(0.1f, x -> { });
        assertEquals(Level.ADVISORY, lamp(Lamp.PROP));
        assertFalse(t.bandUp());

        d.flightStatus.deltaVLeft = 500;
        d.flightStatus.deltaVHome = 300;
        d.navReadout.planDeltaV = 800;
        frame(0.1f, x -> { });
        assertEquals(Level.CAUTION, lamp(Lamp.PROP));
        assertArrayEquals(new String[]{"PROP", "500 M/S", "PLAN 800"}, t.lines(t.cautions.first(), d));
    }

    @Test
    void anEmptyLauncherAContactAndAbandonShipLightNoLamp() {
        frame(0.1f, x -> {
            x.alerts.raise("empty:2", Level.ADVISORY, Category.TACTICAL).set("MSL EMPTY", "Launcher has no missiles left");
            x.alerts.raise("hostile", Level.ADVISORY, Category.TACTICAL).set("HOSTILE", "Hostile contact");
            x.alerts.raise("abandon", Level.WARNING, Category.SHIP).set("ABANDON", "Abandon ship: crew to the pods");
        });
        for (Lamp l : Lamp.values()) assertNull(lamp(l), l.name());
        assertNull(t.row("empty:2"));
        assertNull(t.row("hostile"));
        assertEquals("abandon", t.warning.id);
    }
}
