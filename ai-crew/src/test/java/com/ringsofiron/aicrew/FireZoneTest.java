package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import org.junit.jupiter.api.Test;

/** Round 8: at battle stations the crew keep out of where a crewmate was hit until the fight is over. */
class FireZoneTest {

    static TestShips hall() {
        return new TestShips(
                "################",
                "#..............#",
                "#..............#",
                "#..............#",
                "################");
    }

    @Test
    void nobodyGoesInWhereTheFireFellUntilTheFightIsOver() {
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        assertEquals(Wound.CRITICAL, s.c(down).wound);
        assertTrue(s.board.underFire(13, 3), "beside the hit");
        assertFalse(s.board.underFire(9, 2), "further off");
        s.run(20f);
        assertTrue(s.p(medic).tileX() < 12 - ShipTaskBoardComponent.FIRE_ZONE_RADIUS, "kept out, at " + s.p(medic).tileX());
        assertFalse(s.c(down).stabilised);

        // The fight over, the medic goes in
        s.board.battleStations = false;
        s.board.standDown();
        s.run(30f);
        assertTrue(s.c(down).stabilised || s.c(down).wound != Wound.CRITICAL, s.c(medic).activity);
        assertTrue(s.c(down).isAlive());
    }

    @Test
    void aCasualtyAboutToBleedOutIsGoneInForAllTheSame() {
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        s.c(down).bleedOut = TaskRules.LAST_CHANCE - 10f;
        s.run(60f);
        assertTrue(s.c(down).stabilised || s.c(down).wound != Wound.CRITICAL, s.c(medic).activity);
        assertTrue(s.c(down).isAlive());
    }

    @Test
    void aCasualtyDownInBadAirIsGoneInForAllTheSame() {
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        assertFalse(s.c(down).isConscious());
        RescueTask rescue = new RescueTask(down, 12, 2);
        assertTrue(TaskRules.waitsOutFire(rescue, s.board, s.crew), "in good air it waits");
        s.grid.getTile(15, 2).setIntegrity(0f);
        for (int i = 0; i < 120 && TaskRules.isBreathable(s.grid, 12, 2); i++) s.run(1f);
        assertFalse(TaskRules.isBreathable(s.grid, 12, 2));
        assertFalse(TaskRules.waitsOutFire(rescue, s.board, s.crew), "unsuited in bad air, it doesn't");
    }

    @Test
    void aWoundOpenedAgainWithLittleLeftIsStoppedBeforeABreach() {
        // Round 10: seeds 10 and 12's raids. Hit again where they lay, three crew bled out with 34 to
        // 66 s left
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        StabiliseTask stop = new StabiliseTask(down, 12, 2);
        s.board.post(new com.ringsofiron.interiorgrid.crew.SealBreachTask(3, 3));
        s.board.post(stop);
        assertNotSame(stop, s.board.best(CrewRole.ENGINEER, 4, 2, null), "with five minutes left it waits its turn");
        s.c(down).bleedOut = TaskRules.LAST_CHANCE - 60f;
        assertSame(stop, s.board.best(CrewRole.ENGINEER, 4, 2, null), "with a minute left, first");
        Entity other = s.spawn(CrewRole.CREWMAN, 0, 5, 3);
        s.crew.applyDamage(other, 75f, DamageCause.PENETRATOR);
        s.board.post(new RescueTask(other, 5, 3));
        assertSame(stop, s.board.best(CrewRole.MEDIC, 4, 2, null), "for a medic, before a rescue nearer to hand");
        s.board.faction = 7;
        assertNotSame(stop, s.board.best(CrewRole.ENGINEER, 4, 2, null), "another crew's order is theirs");
    }

    /** A hall with life support at its far end, shorted where a crewmate was hit, at battle stations. */
    static TestShips rakedShort(int faction, boolean keepsClear) {
        TestShips s = new TestShips(
                new ChassisBlueprint("################",
                "#..............#",
                "#..............#",
                "#..............#",
                "################")
                .mount("life_support", MountFootprint.MountSize.S, 11, 2, 1));
        s.board.faction = faction;
        s.board.keepsClearOfFire = keepsClear;
        s.board.battleStations = true;
        s.board.hitAt(12, 2);
        SubsystemComponent life = ShortsAndTanksTest.system(s, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        life.shorted = true;
        s.spawn(CrewRole.ENGINEER, faction, 1, 2);
        return s;
    }

    @Test
    void aPirateCrewmanResetsAShortWhereTheFireFellWhileOurCrewWait() {
        // Round 9: pirate crews don't keep clear of the fire (crews_keep_clear_of_fire = false)
        TestShips ours = rakedShort(0, true), theirs = rakedShort(7, false);
        assertTrue(ours.board.underFire(11, 2), "the short lies where the fire fell");
        ours.run(30f);
        theirs.run(30f);
        assertTrue(ShortsAndTanksTest.system(ours, SubsystemComponent.SubsystemType.LIFE_SUPPORT).shorted,
                "our engineer waits out the fight");
        assertFalse(ShortsAndTanksTest.system(theirs, SubsystemComponent.SubsystemType.LIFE_SUPPORT).shorted,
                "the pirate goes in and resets it");
    }

    @Test
    void theHelmIsMannedWhereverTheFireFalls() {
        TestShips s = hall();
        s.board.battleStations = true;
        s.board.hitAt(5, 2);
        com.ringsofiron.interiorgrid.crew.CrewTask console = new ManConsoleTask(null, 5, 2);
        assertFalse(TaskRules.waitsOutFire(console, s.board, s.crew));
    }

    @Test
    void aShortedReactorIsResetWhereverTheFireFalls() {
        // Round 8: seed 2026's corvette, its power shorted twice under the ace's fire, stayed dark
        // while its crew kept clear, and Red Kestrel, beaten in 36 s before, won in 69 s
        TestShips s = hall();
        s.board.battleStations = true;
        s.board.hitAt(5, 2);
        assertTrue(TaskRules.waitsOutFire(new com.ringsofiron.interiorgrid.crew.PatchLeakTask(5, 2, Side.TOP), s.board, s.crew),
                "a leak there waits");
        assertFalse(TaskRules.waitsOutFire(new ResetShortTask(system(SubsystemType.REACTOR), 5, 2), s.board, s.crew));
        assertFalse(TaskRules.waitsOutFire(new ResetShortTask(5, 2), s.board, s.crew), "nor a shorted conduit");
        assertFalse(TaskRules.waitsOutFire(new RepairTask(system(SubsystemType.TURRET_MOUNT), 5, 2), s.board, s.crew),
                "nor a gun's repair");
        assertTrue(TaskRules.waitsOutFire(new RepairTask(system(SubsystemType.CARGO), 5, 2), s.board, s.crew),
                "a cargo bay's repair waits");
    }

    private static Entity system(SubsystemType type) {
        Entity e = new Entity();
        e.add(new SubsystemComponent(new MountFootprint(type.mountKind, MountFootprint.MountSize.S, 5, 2, 0), type));
        return e;
    }
}
