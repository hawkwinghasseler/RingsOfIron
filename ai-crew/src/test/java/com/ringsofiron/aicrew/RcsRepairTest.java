package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import org.junit.jupiter.api.Test;

/** The crew go out on EVA to bring a wrecked RCS block back into service, but never in a fight. */
class RcsRepairTest {

    /** A room with an airlock at one end and an RCS block on the hull over its far end, its hull tile wrecked. */
    static TestShips ship(boolean airlock) {
        TestShips s = new TestShips(
                "##########",
                airlock ? "#A.......#" : "#........#",
                airlock ? "#A.......#" : "#........#",
                "#........#",
                "##########");
        Side side = RcsThruster.placement(s.grid, 7, 0, Side.BOTTOM) == null ? Side.BOTTOM : Side.TOP;
        assertNull(RcsThruster.placement(s.grid, 7, 0, side));
        s.grid.rcsThrusters.add(new RcsThruster(7, 0, side, RcsThruster.Kind.QUAD));
        s.grid.getTile(7, 0).setIntegrity(0f);
        return s;
    }

    private static RcsThruster block(TestShips s) {
        return s.grid.rcsThrusters.first();
    }

    private static boolean posted(TestShips s) {
        for (CrewTask t : s.board.tasks) if (t.isActive() && t.kind == CrewTask.Kind.REPAIR_RCS) return true;
        return false;
    }

    @Test
    void anEngineerGoesOutThroughTheAirlockAndBringsTheBlockBack() {
        TestShips s = ship(true);
        assertFalse(block(s).works(s.grid));
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 5, 2);
        s.run(20f);
        assertTrue(posted(s), "an EVA job is posted");
        boolean outside = false;
        for (int i = 0; i < 300 && !block(s).works(s.grid); i++) {
            s.run(1f);
            if ("out on EVA repairing an RCS thruster".equals(s.c(e).activity)) {
                outside = true;
                assertTrue(s.c(e).suited, "suited for the EVA");
                assertTrue(TaskRules.isEvaExit(s.grid, s.p(e).tileX(), s.p(e).tileY()), "gone out from the airlock");
            }
        }
        assertTrue(outside, "the work was done on EVA");
        assertTrue(block(s).works(s.grid), "the block fires again");
        Tile hull = s.grid.getTile(7, 0);
        assertEquals(RcsThruster.FIELD_REPAIR, hull.getIntegrity(), 1e-6f, "framed back up, still wanting a yard");
        assertTrue(s.c(e).suitAir < s.c(e).def.suitAir, "breathing from the suit while out");
        s.run(2f);
        assertFalse(posted(s));
    }

    @Test
    void nobodyGoesOutAtBattleStations() {
        TestShips s = ship(true);
        s.board.battleStations = true;
        s.spawn(CrewRole.ENGINEER, 0, 5, 2);
        s.run(200f);
        assertFalse(block(s).works(s.grid), "the block waits for the fight to be over");
        assertFalse(posted(s), "no EVA job at battle stations");

        s.board.battleStations = false;
        s.run(200f);
        assertTrue(block(s).works(s.grid), "and is mended once it is");
    }

    @Test
    void aFightStartingCallsTheEvaBackIn() {
        TestShips s = ship(true);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 5, 2);
        for (int i = 0; i < 300 && !"out on EVA repairing an RCS thruster".equals(s.c(e).activity); i++) s.run(0.5f);
        assertEquals("out on EVA repairing an RCS thruster", s.c(e).activity);
        s.board.battleStations = true;
        s.run(200f);
        assertFalse(block(s).works(s.grid), "back in, the work left undone");
        assertTrue(s.c(e).task == null || s.c(e).task.kind != CrewTask.Kind.REPAIR_RCS, "the job put down");
        assertFalse(posted(s), "and off the board until the fight is over");
    }

    @Test
    void aCrewmanLeavesTheEvaToTheEngineers() {
        TestShips s = ship(true);
        s.spawn(CrewRole.CREWMAN, 0, 5, 2);
        s.run(200f);
        assertFalse(block(s).works(s.grid));
        assertTrue(posted(s), "the job waits for an engineer");
    }

    @Test
    void withNoWayOutTheBlockWaitsForAYard() {
        TestShips s = ship(false);
        s.spawn(CrewRole.ENGINEER, 0, 5, 2);
        s.run(200f);
        assertFalse(block(s).works(s.grid));
    }

    @Test
    void aCrushedTileIsYardWork() {
        TestShips s = ship(true);
        s.grid.getTile(7, 0).setCrushed(true);
        assertFalse(RcsThruster.fieldRepairable(s.grid, 7, 0));
        s.spawn(CrewRole.ENGINEER, 0, 5, 2);
        s.run(20f);
        assertFalse(posted(s));
    }
}
