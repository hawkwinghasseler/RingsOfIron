package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.MountFootprint;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import org.junit.jupiter.api.Test;

/** The bar over a crew member at a timed job ({@link CrewMemberComponent#progress}). */
class WorkProgressTest {

    @Test
    void aRepairShowsItsProgressAndClearsOnceDone() {
        TestShips s = new TestShips(
                new ChassisBlueprint("#######",
                "#.....#",
                "#.....#",
                "#.....#",
                "#######")
                .mount("reactor", MountFootprint.MountSize.S, 3, 2));
        Entity reactor = s.registry.subsystems.first();
        SubsystemComponent r = reactor.getComponent(SubsystemComponent.class);
        r.health = 0.2f;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 1, 1);
        assertTrue(s.c(e).progress < 0f, "no bar before any work");
        s.board.post(new RepairTask(reactor, 3, 2));
        float last = -1f;
        boolean shown = false;
        for (int i = 0; i < 150 && r.health < 1f; i++) {
            s.run(0.1f);
            float p = s.c(e).progress;
            if (p < 0f) continue;
            shown = true;
            assertTrue(p >= last, "the bar only fills while the work goes on");
            assertEquals(r.health, p, 1e-4f, "a repair's bar is the system's health");
            last = p;
        }
        assertTrue(shown, "a bar while repairing");
        assertEquals(1f, r.health, 1e-4f);
        s.run(0.5f);
        assertTrue(s.c(e).progress < 0f, "the bar goes once the work is done");
    }

    @Test
    void showProgressClampsAndLeavesQuickJobsBare() {
        CrewMemberComponent c = new CrewMemberComponent();
        c.showProgress(20f, 40f);
        assertEquals(0.5f, c.progress, 1e-6f);
        c.showProgress(50f, 40f);
        assertEquals(1f, c.progress, 1e-6f);
        c.showProgress(1f, 5f);
        assertEquals(0.2f, c.progress, 1e-6f, "a pressure suit's five seconds get a bar");
        c.showProgress(1f, 4f);
        assertTrue(c.progress < 0f, "a job of a few seconds gets none");
        c.showProgress(1f, 0f);
        assertTrue(c.progress < 0f);
    }
}
