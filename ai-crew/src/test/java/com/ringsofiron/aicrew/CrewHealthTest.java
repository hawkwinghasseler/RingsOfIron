package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import org.junit.jupiter.api.Test;

class CrewHealthTest {

    static TestShips room() {
        return new TestShips(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########");
    }

    @Test
    void damageMovesCrewThroughTheStates() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        CrewMemberComponent c = s.c(e);
        // Health is what the environment and small arms take; hits wound instead (CrewWoundTest)
        s.crew.applyDamage(e, 70f, DamageCause.OTHER);
        assertEquals(CrewState.CONSCIOUS, c.state);
        s.crew.applyDamage(e, 5f, DamageCause.SMALL_ARMS);
        assertEquals(CrewState.UNCONSCIOUS, c.state, "at the 25% threshold");
        Array<Entity> on = new Array<>();
        s.crew.occupantsAt(s.ship, 2, 2, on);
        assertTrue(on.contains(e, true), "unconscious crew still occupy their tile");
        s.crew.applyDamage(e, 50f, DamageCause.OTHER);
        assertEquals(CrewState.DEAD, c.state, "further harm kills the unconscious");
        assertEquals(0f, c.health);
        s.crew.heal(e, 100f);
        assertEquals(CrewState.DEAD, c.state, "the dead do not recover");
    }

    @Test
    void knockedOutCrewDropTheirTask() {
        TestShips s = room();
        s.grid.getTile(7, 2).setCovering(com.ringsofiron.interiorgrid.FloorCovering.CARPET, 1f);
        s.grid.getTile(7, 2).ignite();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        s.board.post(new ExtinguishTask(7, 2));
        s.run(0.2f);
        CrewTask task = s.c(e).task;
        assertNotNull(task);
        assertEquals(CrewTask.Status.CLAIMED, task.status);
        s.crew.applyDamage(e, 80f, DamageCause.FRAGMENT);
        assertEquals(CrewState.UNCONSCIOUS, s.c(e).state);
        assertNull(s.c(e).task);
        assertEquals(CrewTask.Status.OPEN, task.status, "back on the board for someone else");
    }

    @Test
    void crewInAVentingRoomSuffocateAndARescueIsPosted() {
        TestShips s = room();
        s.grid.getTile(9, 2).setIntegrity(0f); // hull breach to space; nobody aboard can seal it
        Entity victim = s.spawn(CrewRole.MARINE, 0, 7, 2);
        CrewMemberComponent v = s.c(victim);
        boolean sawRescue = false;
        for (int i = 0; i < 30 * 60 && !sawRescue; i++) {
            s.engine.update(1f / 30f);
            for (CrewTask t : s.board.tasks) if (t instanceof RescueTask && ((RescueTask) t).casualty == victim) sawRescue = true;
        }
        assertEquals(CrewState.UNCONSCIOUS, v.state, "passed out before dying");
        assertEquals(DamageCause.ASPHYXIATION, v.lastDamageCause);
        assertTrue(sawRescue, "a rescue was posted");
        s.run(30f);
        assertEquals(CrewState.DEAD, v.state, "left in vacuum, they die");
    }

    @Test
    void rescuersCarryCasualtiesToSafety() {
        TestShips s = new TestShips(new com.ringsofiron.interiorgrid.ChassisBlueprint(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "############"), false);
        // A scorching end of the room, with nothing to cool it (no hazard system)
        for (int y = 1; y <= 3; y++) for (int x = 7; x <= 10; x++) s.grid.getTile(x, y).temperature = 400f;
        Entity casualty = s.spawn(CrewRole.CREWMAN, 0, 9, 2);
        s.crew.applyDamage(casualty, 76f, DamageCause.BLAST);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 1);
        s.run(15f);
        GridPositionComponent p = s.p(casualty);
        assertTrue(TaskRules.isSafe(s.grid, p.tileX(), p.tileY()), "moved to a safe tile, now at " + p.tileX());
        assertNull(s.c(medic).carrying);
        assertNull(s.c(casualty).carriedBy);
        assertNotEquals(CrewState.DEAD, s.c(casualty).state);
    }

    @Test
    void unconsciousCrewRecoverOnASafeTile() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.crew.applyDamage(e, 80f, DamageCause.SMALL_ARMS);
        assertEquals(CrewState.UNCONSCIOUS, s.c(e).state);
        s.run(30f);
        assertEquals(CrewState.CONSCIOUS, s.c(e).state);
        assertTrue(Compartment.AMBIENT_TEMPERATURE > 0f);
    }
}
