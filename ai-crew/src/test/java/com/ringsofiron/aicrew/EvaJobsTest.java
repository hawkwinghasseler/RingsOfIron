package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullPlating;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import org.junit.jupiter.api.Test;

/**
 * Work outside besides an RCS block's: a wrecked hull tile plated over from outside, a job the game
 * posts, and several in one trip. The ship of {@link EvaRepairTest#ship()}, standing still.
 */
class EvaJobsTest {

    static boolean posted(TestShips s, CrewTask.Kind kind) {
        for (CrewTask t : s.board.tasks) if (t.isActive() && t.kind == kind) return true;
        return false;
    }

    /** The test ship with its RCS block's tile whole again, so the hull tile is the only work outside. */
    static TestShips ship() {
        TestShips s = EvaRepairTest.ship();
        s.grid.getTile(9, 0).setIntegrity(1f);
        return s;
    }

    @Test
    void aWreckedHullTileIsPlatedFromOutside() {
        TestShips s = ship();
        // The outer skin of the double bottom: no air behind it, so nothing to seal from inside
        Tile tile = s.grid.getTile(2, 0);
        tile.setIntegrity(0f);
        assertTrue(HullPlating.plateable(s.grid, 2, 0));
        int kits = s.grid.patchKits;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        java.util.List<EvaEvent.Kind> events = new java.util.ArrayList<>();
        s.crew.eva.events.add((signal, ev) -> events.add(ev.kind));
        boolean worked = false;
        for (int i = 0; i < 6000 && !(tile.getIntegrity() > 0f && EvaSystem.of(e) == null && !s.c(e).suited); i++) {
            s.run(0.1f);
            EvaComponent eva = EvaSystem.of(e);
            if (eva != null && "plating a holed hull tile on EVA".equals(s.c(e).activity)) {
                worked = true;
                assertEquals(2, eva.ringX);
                assertEquals(-1, eva.ringY, "beside the tile, below the hull");
            }
        }
        assertTrue(worked, "did the work out there");
        assertEquals(RcsThruster.FIELD_REPAIR, tile.getIntegrity(), 1e-6f, "framed back up");
        assertTrue(tile.isPatched(), "and plated over");
        assertEquals(0f, tile.leakFactor(), "so it holds air");
        assertEquals(kits - 1, s.grid.patchKits, "a kit from the stores");
        assertNull(EvaSystem.of(e), "back in");
        assertEquals(java.util.Arrays.asList(EvaEvent.Kind.OUT, EvaEvent.Kind.BACK), events);
        assertFalse(posted(s, CrewTask.Kind.PATCH_HULL));
    }

    @Test
    void withNoKitsTheFrameAloneGoesUp() {
        TestShips s = ship();
        s.grid.patchKits = 0;
        Tile tile = s.grid.getTile(2, 0);
        tile.setIntegrity(0f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !(tile.getIntegrity() > 0f && EvaSystem.of(e) == null); i++) s.run(0.1f);
        assertEquals(RcsThruster.FIELD_REPAIR, tile.getIntegrity(), 1e-6f);
        assertFalse(tile.isPatched());
        assertTrue(tile.leakFactor() > 0f, "standing, but leaking");
    }

    @Test
    void aBreachSealedFromInsideIsPlatedFromOutsideAndKeepsItsSeal() {
        TestShips s = ship();
        // The top hull, right over the room, sealed from inside with an emergency bulkhead
        Tile tile = s.grid.getTile(8, 5);
        tile.setIntegrity(0f);
        tile.bulkheadSealed = true;
        assertTrue(HullPlating.plateable(s.grid, 8, 5), "the seal stops the air, not the hole");
        float hull = meanHull(s);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !(tile.getIntegrity() > 0f && EvaSystem.of(e) == null && !s.c(e).suited); i++) s.run(0.1f);
        assertEquals(RcsThruster.FIELD_REPAIR, tile.getIntegrity(), 1e-6f, "plated from outside");
        assertTrue(tile.bulkheadSealed, "the seal stays under the plate");
        assertFalse(tile.isHullBreach());
        assertTrue(meanHull(s) > hull, "the hull the stronger for it");
    }

    /** The mean integrity of the hull's tiles, as a ship's disablement judges it. */
    static float meanHull(TestShips s) {
        float sum = 0f;
        int n = 0;
        for (int y = 0; y < s.grid.height; y++) {
            for (int x = 0; x < s.grid.width; x++) {
                Tile t = s.grid.getTile(x, y);
                if (t.wallType != Tile.WallType.HULL) continue;
                sum += t.getIntegrity();
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void aCrushedTileOrOneDeepInTheHullIsNoJobOutside() {
        TestShips s = ship();
        s.grid.getTile(2, 0).setIntegrity(0f);
        s.grid.getTile(2, 0).setCrushed(true);
        // The inner skin of the double bottom: no side open to space
        s.grid.getTile(4, 1).setIntegrity(0f);
        assertFalse(HullPlating.plateable(s.grid, 2, 0), "crushed is yard work");
        assertFalse(HullPlating.plateable(s.grid, 4, 1), "out of reach from outside");
        s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(20f);
        assertFalse(posted(s, CrewTask.Kind.PATCH_HULL));
    }

    @Test
    void nobodyPlatesTheHullAtBattleStations() {
        TestShips s = ship();
        s.grid.getTile(2, 0).setIntegrity(0f);
        s.board.battleStations = true;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(60f);
        assertNull(EvaSystem.of(e));
        assertTrue(posted(s, CrewTask.Kind.PATCH_HULL), "the job waits on the board");
    }

    @Test
    void oneTripOutDoesEveryJobWithinReach() {
        TestShips s = EvaRepairTest.ship();
        Tile hullTile = s.grid.getTile(2, 0);
        hullTile.setIntegrity(0f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        java.util.List<EvaEvent.Kind> events = new java.util.ArrayList<>();
        s.crew.eva.events.add((signal, ev) -> events.add(ev.kind));
        for (int i = 0; i < 9000 && !(hullTile.getIntegrity() > 0f && EvaRepairTest.block(s).works(s.grid) && EvaSystem.of(e) == null); i++) {
            s.run(0.1f);
        }
        assertTrue(EvaRepairTest.block(s).works(s.grid), "the block mended");
        assertTrue(hullTile.getIntegrity() > 0f, "and the hull plated");
        assertEquals(java.util.Arrays.asList(EvaEvent.Kind.OUT, EvaEvent.Kind.BACK), events, "in one trip");
    }

    @Test
    void aJobTheGamePostsIsDoneOutsideWhereItSays() {
        TestShips s = ship();
        int[] done = {0};
        Entity[] by = {null};
        OutsideTask.Job job = new OutsideTask.Job() {
            @Override
            public boolean isOpen() {
                return done[0] == 0;
            }

            @Override
            public boolean workTile(HullSurface hull, int[] out) {
                // Round the end of the hull from the port's door
                out[0] = 12;
                out[1] = 3;
                return hull.isRing(12, 3);
            }

            @Override
            public float seconds(CrewDef def) {
                return 20f;
            }

            @Override
            public void done(Entity worker) {
                done[0]++;
                by[0] = worker;
            }

            @Override
            public String doing() {
                return "fixing the widget";
            }

            @Override
            public String where() {
                return "the widget";
            }
        };
        s.board.post(new OutsideTask(CrewTask.Kind.FREE_COLLAR, 11, 3, job));
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        boolean crawling = false, working = false;
        for (int i = 0; i < 6000 && !(done[0] > 0 && EvaSystem.of(e) == null); i++) {
            s.run(0.1f);
            crawling |= "crawling along the hull to the widget".equals(s.c(e).activity);
            if ("fixing the widget on EVA".equals(s.c(e).activity)) {
                working = true;
                assertEquals(12, EvaSystem.of(e).ringX);
                assertEquals(3, EvaSystem.of(e).ringY);
            }
        }
        assertTrue(crawling && working);
        assertEquals(1, done[0], "done once");
        assertSame(e, by[0]);
        assertNull(EvaSystem.of(e), "and back in");
    }
}
