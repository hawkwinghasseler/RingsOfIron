package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Compartment;
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
    }

    @Test
    void aSuitComesOffOnceTheAirIsBackAndIsToppedUp() {
        TestShips s = CrewHealthTest.room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        CrewMemberComponent c = s.c(e);
        c.setSuited(true);
        c.suitAir = 10f;
        s.run(CrewTasksIdleDelay() + c.def.suitTime);
        assertFalse(c.suited);
        assertEquals(c.def.suitAir, c.suitAir);
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
