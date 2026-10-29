package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.DamageTaskSink;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;
import org.junit.jupiter.api.Test;

class SpaceSuitTest {

    @Test
    void engineersSuitUpToSealABreachAndTakeTheSuitOffAfter() {
        TestShips s = CrewHealthTest.room();
        Tile breach = s.grid.getTile(9, 2);
        breach.setIntegrity(0f);
        Furnishing locker = s.locker(1, 3, 4);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 1, 2);
        CrewMemberComponent c = s.c(e);
        boolean wasSuited = false;
        for (int i = 0; i < 30 * 30 && breach.isHullBreach(); i++) {
            s.run(1f / 30f);
            wasSuited |= c.suited;
        }
        assertFalse(breach.isHullBreach(), "sealed");
        assertTrue(wasSuited, "suited up first");
        assertTrue(c.health > c.def.maxHealth * 0.9f, "breathed from the suit, health " + c.health);
        assertTrue(c.suitAir < c.def.suitAir, "used suit air");
        assertEquals(CrewState.CONSCIOUS, c.state);
        assertEquals(3, locker.suits.size, "a suit from the locker");
    }

    @Test
    void withNoSuitToBeHadAnEngineerStillDashesInToSealABreach() {
        TestShips s = CrewHealthTest.room();
        Tile breach = s.grid.getTile(9, 2);
        breach.setIntegrity(0f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 6, 2);
        for (int i = 0; i < 30 * 30 && breach.isHullBreach(); i++) s.run(1f / 30f);
        assertFalse(breach.isHullBreach(), "sealed on held breath");
        assertFalse(s.c(e).suited, "no locker aboard");
    }

    @Test
    void aSuitIsTakenFromALockerAndHungBackUpInIt() {
        TestShips s = CrewHealthTest.room();
        Furnishing locker = s.locker(5, 3, 1);
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        CrewMemberComponent c = s.c(e);
        s.board.battleStations = true;
        s.run(c.def.suitTime + 8f);
        assertTrue(c.suited, c.activity);
        assertEquals(0, locker.suits.size, "the locker's one suit is on");
        assertTrue(SuitLockers.reaches(locker, s.p(e).x, s.p(e).y), "dressed at the locker");
        s.board.battleStations = false;
        s.run(CrewTasksIdleDelay() + c.def.suitTime);
        assertFalse(c.suited, c.activity);
        assertEquals(1, locker.suits.size, "hung back up");
    }

    @Test
    void aSecondHandFindsTheLockerEmptyAndGoesWithout() {
        TestShips s = CrewHealthTest.room();
        s.locker(5, 3, 1);
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 4, 2), b = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        s.board.battleStations = true;
        s.run(20f);
        assertNotEquals(s.c(a).suited, s.c(b).suited, "one suit between the two of them");
    }

    @Test
    void aSuitComesOffOnceTheAirIsBackAndIsToppedUpInItsLocker() {
        TestShips s = new TestShips(
                "##########",
                "#L.......#",
                "#L.......#",
                "#........#",
                "##########");
        Furnishing locker = s.locker(5, 3, 3);
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        CrewMemberComponent c = s.c(e);
        c.setSuited(true);
        c.suitAir = 10f;
        s.run(CrewTasksIdleDelay() + c.def.suitTime);
        assertFalse(c.suited);
        assertEquals(4, locker.suits.size, "hung up in the locker");
        float least = Float.MAX_VALUE;
        for (Suit suit : locker.suits) least = Math.min(least, suit.air);
        assertTrue(least < 60f, "back with the air it had: " + least);
        s.run(SuitLockerSystem.AIR_FILL);
        for (Suit suit : locker.suits) assertEquals(Suit.IVA_AIR, suit.air, 1e-3f, "filled from the ship's oxygen");
    }

    private static float CrewTasksIdleDelay() {
        return com.ringsofiron.aicrew.btree.CrewTasks.Idle.SUIT_OFF_DELAY + 0.5f;
    }

    @Test
    void anEmptySuitStopsProtecting() {
        TestShips s = new TestShips(new com.ringsofiron.interiorgrid.ChassisBlueprint(
                "#####",
                "#...#",
                "#####"), false);
        Compartment room = s.grid.compartmentAt(2, 1);
        room.oxygen = room.nitrogen = room.carbonDioxide = 0;
        Entity e = s.spawn(CrewRole.MARINE, 0, 2, 1);
        CrewMemberComponent c = s.c(e);
        c.setSuited(true);
        c.suitAir = 3f;
        s.run(2f);
        assertEquals(c.def.maxHealth, c.health, 1e-3f, "safe while the suit has air");
        s.run(4f);
        assertEquals(0f, c.suitAir);
        assertTrue(c.health < c.def.maxHealth, "suffocating once it ran out");
    }

    @Test
    void suitedCrewOpenADoorOntoThinAirButNotOntoSpace() {
        TestShips s = CrewDoorTest.twoRooms(true);
        Compartment far = s.grid.compartmentAt(6, 2);
        far.oxygen = far.nitrogen = far.carbonDioxide = 0;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 1, 2);
        CrewMemberComponent c = s.c(e);
        assertFalse(s.crew.doors.mayOpen(e, s.ship, 3, 2, 4, 2));
        c.setSuited(true);
        assertTrue(s.crew.doors.mayOpen(e, s.ship, 3, 2, 4, 2), "a sealed empty room: the air is shared out");
        s.grid.getTile(8, 2).setIntegrity(0f);
        s.run(0.5f); // the hazard tick finds the hole
        assertFalse(s.crew.doors.mayOpen(e, s.ship, 3, 2, 4, 2), "a room open to space would take all the air");
    }

    @Test
    void theBreachTaskSaysASuitIsNeeded() {
        TestShips s = CrewHealthTest.room();
        s.grid.getTile(9, 2).setIntegrity(0f);
        assertTrue(TaskRules.needsSuit(s.grid, 9, 2));
        assertFalse(TaskRules.needsSuit(s.grid, 3, 2), "not before the air goes");
        DamageTaskSink sink = s.crew;
        assertTrue(sink.post(s.ship, new SealBreachTask(9, 2)));
    }
}
