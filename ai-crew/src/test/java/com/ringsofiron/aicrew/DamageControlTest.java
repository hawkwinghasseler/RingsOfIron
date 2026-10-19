package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import org.junit.jupiter.api.Test;

/** The crew patch leaking hull in the field from the ship's stores. */
class DamageControlTest {

    @Test
    void anEngineerPatchesALeakingHullTile() {
        TestShips s = CrewHealthTest.room();
        Tile hull = s.grid.getTile(0, 2);
        hull.setIntegrity(0.4f);
        int kits = s.grid.patchKits;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 6, 2);
        s.run(1f);
        assertTrue(s.grid.isVenting(), "the damaged hull vents");
        s.run(30f);
        assertTrue(hull.isPatched(), "patched");
        assertEquals(0.4f, hull.getIntegrity(), 1e-6f, "still damaged: a yard mends it");
        assertEquals(kits - 1, s.grid.patchKits, "one patch from the stores");
        assertFalse(s.grid.isVenting());
        assertEquals(0, s.board.activeCount());
        assertTrue(s.c(e).isConscious());
    }

    @Test
    void withNoPatchesLeftTheLeakWaitsForAYard() {
        TestShips s = CrewHealthTest.room();
        Tile hull = s.grid.getTile(0, 2);
        hull.setIntegrity(0.4f);
        s.grid.patchKits = 0;
        s.spawn(CrewRole.ENGINEER, 0, 6, 2);
        s.run(20f);
        assertFalse(hull.isPatched());
        assertTrue(LeakPatches.outOfKits(s.grid));
        for (CrewTask t : s.board.tasks) assertFalse(t.isActive() && t instanceof PatchLeakTask, "no patch job posted");
    }

    @Test
    void patchJobsAreOnePerLeakingPiece() {
        ShipTaskBoardComponent b = new ShipTaskBoardComponent();
        assertTrue(b.post(new PatchLeakTask(3, 3, null)));
        assertFalse(b.post(new PatchLeakTask(3, 3, null)));
        assertTrue(b.post(new PatchLeakTask(3, 3, com.ringsofiron.interiorgrid.Side.LEFT)), "a thin wall beside it is another job");
        assertEquals(CrewTask.Kind.PATCH_LEAK, b.best(CrewRole.ENGINEER, 3, 3, null).kind);
    }
}
