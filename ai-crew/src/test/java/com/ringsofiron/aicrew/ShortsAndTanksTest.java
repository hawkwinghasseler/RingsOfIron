package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.MountFootprint;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import org.junit.jupiter.api.Test;

/** The crew reset short circuits and isolate leaking tanks. */
class ShortsAndTanksTest {

    static TestShips ship() {
        return new TestShips(
                new ChassisBlueprint("##########",
                "#........#",
                "#........#",
                "#........#",
                "##########")
                .mount("life_support", MountFootprint.MountSize.S, 2, 2, 1)
                .mount("oxygen_tank", MountFootprint.MountSize.S, 6, 2, 1));
    }

    static SubsystemComponent system(TestShips s, SubsystemComponent.SubsystemType type) {
        for (Entity e : s.registry.subsystems) {
            SubsystemComponent c = e.getComponent(SubsystemComponent.class);
            if (c.type == type) return c;
        }
        return null;
    }

    @Test
    void anEngineerResetsAShortBeforeRepairingTheSystem() {
        TestShips s = ship();
        SubsystemComponent life = system(s, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        life.health = 0.8f;
        life.shorted = true;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 8, 3);
        s.run(1f);
        assertTrue(s.c(e).task instanceof ResetShortTask, "the quick reset comes first: " + s.c(e).task);
        s.run(15f);
        assertFalse(life.shorted, "reset");
        s.run(10f);
        assertEquals(1f, life.health, 1e-6f, "then repaired");
    }

    @Test
    void aRepairToFullHealthAlsoClearsAShort() {
        TestShips s = ship();
        SubsystemComponent life = system(s, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        life.health = 0.9f;
        life.shorted = true;
        // Only the repair is on the board: the scan is not run
        s.board.post(new RepairTask(life.footprint.installed, life.footprint.originX, life.footprint.originY));
        s.engine.removeSystem(s.engine.getSystem(ShipHazardScanSystem.class));
        s.spawn(CrewRole.ENGINEER, 0, 8, 3);
        s.run(10f);
        assertEquals(1f, life.health, 1e-6f);
        assertFalse(life.shorted);
    }

    @Test
    void theCrewIsolateALeakingTank() {
        TestShips s = ship();
        SubsystemComponent tank = system(s, SubsystemComponent.SubsystemType.TANK);
        tank.capacity = tank.contents = 300;
        tank.health = 0.4f;
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 8, 3);
        s.run(1f);
        boolean posted = false;
        for (CrewTask t : s.board.tasks) posted |= t instanceof IsolateTankTask;
        assertTrue(posted, "an isolate job is up");
        assertTrue(s.c(e).task instanceof IsolateTankTask, "shutting the valve comes before the repair");
        for (int i = 0; i < 25 && !tank.isolated; i++) s.run(1f);
        assertTrue(tank.isolated, "valve shut");
        assertTrue(tank.health < 1f, "before the tank is repaired");
        assertFalse(DamageStates.needsIsolating(tank));
        s.run(40f);
        assertEquals(1f, tank.health, 1e-6f, "then repaired");
        assertFalse(tank.isolated, "and opened again");
    }

    @Test
    void jobsAreOnePerShortOrTank() {
        ShipTaskBoardComponent b = new ShipTaskBoardComponent();
        Entity a = new Entity(), c = new Entity();
        assertTrue(b.post(new ResetShortTask(a, 1, 1)));
        assertFalse(b.post(new ResetShortTask(a, 2, 2)));
        assertTrue(b.post(new ResetShortTask(1, 1)), "a conduit on the same tile is another job");
        assertFalse(b.post(new ResetShortTask(1, 1)));
        assertTrue(b.post(new IsolateTankTask(c, 1, 1)));
        assertFalse(b.post(new IsolateTankTask(c, 1, 1)));
        assertEquals(0f, CrewRole.MARINE.affinity(CrewTask.Kind.RESET_SHORT));
        assertTrue(CrewRole.ENGINEER.affinity(CrewTask.Kind.ISOLATE_TANK) > 1f);
    }
}
