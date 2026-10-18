package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.demo.CrewScenario;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.SealBreachTask;
import org.junit.jupiter.api.Test;

class CrewBehaviorTest {

    @Test
    void boardDropsDuplicateJobsAndPicksByRole() {
        ShipTaskBoardComponent b = new ShipTaskBoardComponent();
        assertTrue(b.post(new ExtinguishTask(3, 3)));
        assertFalse(b.post(new ExtinguishTask(3, 3)));
        assertTrue(b.post(new SealBreachTask(9, 9)));
        assertEquals(CrewTask.Kind.SEAL_BREACH, b.best(CrewRole.ENGINEER, 3, 3, null).kind, "engineers seal first");
        assertEquals(CrewTask.Kind.EXTINGUISH, b.best(CrewRole.MARINE, 3, 3, null).kind, "marines never seal");
    }

    @Test
    void crewWalkOverAndPutOutAFire() {
        TestShips s = CrewHealthTest.room();
        Tile t = s.grid.getTile(7, 2);
        t.setCovering(FloorCovering.CARPET, 1f);
        t.ignite();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        s.run(1f); // the scan posts the task
        s.run(15f);
        assertFalse(t.isBurning());
        assertEquals(0, s.board.activeCount());
        assertTrue(s.c(e).isConscious());
    }

    @Test
    void engineersRepairSystemsFromBesideTheMount() {
        TestShips s = new TestShips(
                "#######",
                "#.....#",
                "#..R..#",
                "#.....#",
                "#######");
        Entity reactor = s.registry.subsystems.first();
        SubsystemComponent r = reactor.getComponent(SubsystemComponent.class);
        r.health = 0.5f;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 1, 1);
        s.board.post(new RepairTask(reactor, 3, 2));
        s.run(10f);
        assertEquals(1f, r.health, 1e-4f);
        GridPositionComponent p = s.p(e);
        assertEquals(1, Math.abs(p.tileX() - 3) + Math.abs(p.tileY() - 2), "worked from an adjacent tile");
    }

    @Test
    void hostileInSightInterruptsWork() {
        TestShips s = CrewHealthTest.room();
        Tile t = s.grid.getTile(8, 3);
        t.setCovering(FloorCovering.CARPET, 1f);
        t.ignite();
        Entity worker = s.spawn(CrewRole.CREWMAN, 0, 1, 1);
        s.board.post(new ExtinguishTask(8, 3));
        s.run(0.2f);
        CrewTask task = s.c(worker).task;
        assertNotNull(task);
        Entity hostile = s.spawn(CrewRole.MARINE, 1, 4, 1);
        s.run(0.2f);
        assertNull(s.c(worker).task, "dropped the task to fight");
        assertNotEquals(CrewTask.Status.CLAIMED, task.status);
        assertTrue(s.c(worker).activity.startsWith("shooting") || s.c(worker).activity.startsWith("closing"));
        assertNull(s.c(hostile).task, "boarders do not work the ship's board");
    }

    @Test
    void scenarioCrewHandleFireReactorAndBreach() {
        CrewScenario s = new CrewScenario();
        float dt = 1f / 30f;
        while (s.time < 38f) s.update(dt);
        assertFalse(s.grid.getTile(CrewScenario.FIRE_X, CrewScenario.FIRE_Y).isBurning(), "fire out");
        assertEquals(1f, s.reactor.getComponent(SubsystemComponent.class).health, 1e-4f, "reactor repaired");
        Tile breach = s.grid.getTile(CrewScenario.BREACH_X, CrewScenario.BREACH_Y);
        assertFalse(breach.isHullBreach(), "breach sealed");
        assertTrue(breach.bulkheadSealed);
        for (Entity e : s.crewMembers) assertTrue(s.crew.crew(e).isConscious(), s.crew.crew(e).name + " is fine");
    }
}
