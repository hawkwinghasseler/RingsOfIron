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

    /** Holds the air of the compartment over (x, y) at {@code share} of standard for {@code seconds}. */
    private static void thinAir(TestShips s, int x, int y, double share, float seconds) {
        float dt = 1f / 30f;
        for (float t = 0; t < seconds; t += dt) {
            Compartment room = s.grid.compartmentAt(x, y);
            double full = room.volume * Compartment.STANDARD_PRESSURE * 1000.0 / (Compartment.R * room.temperature);
            room.oxygen = Math.min(room.oxygen, share * full * Compartment.STANDARD_O2_FRACTION);
            room.nitrogen = Math.min(room.nitrogen, share * full * (1 - Compartment.STANDARD_O2_FRACTION));
            s.engine.update(dt);
        }
    }

    @Test
    void aDownedCaptainIsCarriedOutOfAVentingRoomToAir() {
        // Two rooms with a shut door between; the right one losing its air
        TestShips s = new TestShips(new com.ringsofiron.interiorgrid.ChassisBlueprint(
                "###########",
                "#....I....#",
                "#.........#",
                "#....I....#",
                "###########")
                .edgeWall(5, 2, com.ringsofiron.interiorgrid.Side.LEFT, com.ringsofiron.interiorgrid.WeightClass.MEDIUM, true), true);
        s.grid.getEdge(5, 2, com.ringsofiron.interiorgrid.Side.LEFT).bulkheadSealed = true;
        s.grid.gridDirty = true;
        s.run(0.1f);
        Entity you = CrewFactory.spawnPlayer(s.crew, s.ship, "You", 8, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 2);
        s.crew.applyDamage(you, 80f, DamageCause.ASPHYXIATION);
        thinAir(s, 8, 2, 0.28, 3f);
        assertEquals(CrewState.UNCONSCIOUS, s.c(you).state, "the captain went down in the thin air");
        thinAir(s, 8, 2, 0.28, 30f);
        GridPositionComponent p = s.p(you);
        assertTrue(p.tileX() < 5, "carried through the door to the other room, now at " + p.tileX());
        assertTrue(TaskRules.isSafe(s.grid, p.tileX(), p.tileY()));
        assertNull(s.c(medic).carrying);
        assertTrue(s.c(you).isAlive());
    }

    @Test
    void withNoAirAboardARescuerPutsTheCasualtysSuitOn() {
        TestShips s = room();
        Entity you = CrewFactory.spawnPlayer(s.crew, s.ship, "You", 7, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 2, 2);
        s.crew.applyDamage(you, 80f, DamageCause.ASPHYXIATION);
        thinAir(s, 7, 2, 0.28, 3f);
        assertEquals(CrewState.UNCONSCIOUS, s.c(you).state);
        assertFalse(s.c(you).suited);
        thinAir(s, 7, 2, 0.28, 20f);
        assertTrue(s.c(you).suited, "suited where they lay: nowhere aboard has air");
        assertTrue(s.c(medic).suited, "the medic suited up to go in");
        thinAir(s, 7, 2, 0.28, 60f);
        assertTrue(s.c(you).isAlive(), "breathing from the suit");
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
