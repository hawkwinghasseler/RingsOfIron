package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.btree.EvaTasks;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.SuitOutfit;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.Suit;
import org.junit.jupiter.api.Test;

/**
 * The crew go out on EVA through a docking port's airlock to bring a wrecked RCS block back into
 * service: into an EVA suit, out through the airlock, along the hull to the block, the work, and
 * back in. Never in a fight. The ship has no physics body here, so it stands still.
 */
class EvaRepairTest {

    /**
     * A room under a Medium docking port in its top hull (opening x 4 to 6, rows 4 and 5; the door
     * opens onto ring tile (5, 6)), an EVA locker beside it, and an RCS block on the double bottom hull
     * at (9, 0), its hull tile wrecked (the inner hull keeps the air in).
     */
    static TestShips ship() {
        TestShips s = new TestShips(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "############",
                "############");
        s.grid.placeMount(new MountFootprint(MountFootprint.MountKind.PORT, MountFootprint.MountSize.M, 3, 4, 0));
        assertNotNull(SuitOutfit.placeLocker(s.grid, Suit.Kind.EVA, 9, 4, 2), "an EVA locker by the port");
        s.grid.rcsThrusters.add(new RcsThruster(9, 0, Side.BOTTOM, RcsThruster.Kind.QUAD));
        s.grid.getTile(9, 0).setIntegrity(0f);
        return s;
    }

    static RcsThruster block(TestShips s) {
        return s.grid.rcsThrusters.first();
    }

    static MountFootprint port(TestShips s) {
        return s.grid.getTile(5, 5).mount;
    }

    static boolean posted(TestShips s) {
        for (CrewTask t : s.board.tasks) if (t.isActive() && t.kind == CrewTask.Kind.REPAIR_RCS) return true;
        return false;
    }

    static Furnishing evaLocker(TestShips s) {
        for (Furnishing f : SuitLockers.all(s.grid)) if (f.type.holds == Suit.Kind.EVA) return f;
        return null;
    }

    /** True if (x, y), tiles, is off the craft: past its grid or over empty space. */
    static boolean offTheCraft(TestShips s, float x, float y) {
        Tile t = s.grid.getTile((int) Math.floor(x), (int) Math.floor(y));
        return t == null || t.isVoid();
    }

    @Test
    void anEngineerSuitsUpGoesOutAlongTheHullMendsTheBlockAndComesBackIn() {
        TestShips s = ship();
        assertFalse(block(s).works(s.grid));
        int kits = s.grid.patchKits;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        java.util.List<EvaEvent.Kind> events = new java.util.ArrayList<>();
        s.crew.eva.events.add((signal, ev) -> events.add(ev.kind));
        boolean out = false, crawled = false, worked = false;
        float lowestCabin = 2f;
        for (int i = 0; i < 6000 && !(block(s).works(s.grid) && EvaSystem.of(e) == null && !s.c(e).suited); i++) {
            s.run(0.1f);
            EvaComponent eva = EvaSystem.of(e);
            GridPositionComponent p = s.p(e);
            lowestCabin = Math.min(lowestCabin, s.grid.pressureRatioAt(9, 2));
            if (eva != null && eva.outside()) {
                out = true;
                assertEquals(Suit.Kind.EVA, s.c(e).suitKind, "in an EVA suit outside");
                assertTrue(offTheCraft(s, p.x, p.y), "outside the hull: " + p.x + "," + p.y);
                assertFalse(DockingPortLayout.door(s.grid, port(s), 5, 5).bulkheadSealed, "the door stands open behind them");
                if (eva.phase == EvaComponent.Phase.ON_HULL && p.y < 0f) crawled = true;
                if ("repairing an RCS thruster on EVA".equals(s.c(e).activity)) {
                    worked = true;
                    assertEquals(9, eva.ringX);
                    assertEquals(-1, eva.ringY, "beside the block, below the hull");
                }
            }
        }
        assertTrue(out, "went out");
        assertTrue(crawled, "crawled round the hull to its far side");
        assertTrue(worked, "did the work out there");
        assertTrue(block(s).works(s.grid), "the block fires again");
        assertEquals(RcsThruster.FIELD_REPAIR, s.grid.getTile(9, 0).getIntegrity(), 1e-6f, "framed back up, still wanting a yard");
        assertTrue(s.grid.getTile(9, 0).isPatched(), "and plated over, as a holed hull tile is");
        assertEquals(0f, s.grid.getTile(9, 0).leakFactor(), "so it holds air");
        assertEquals(kits - 1, s.grid.patchKits, "a kit from the stores");
        assertNull(EvaSystem.of(e), "back in");
        assertFalse(offTheCraft(s, s.p(e).x, s.p(e).y));
        assertTrue(Airlock.of(s.grid, port(s)).isIn(), "the airlock back at rest");
        assertFalse(DockingPortLayout.hatch(s.grid, port(s), 5, 4).bulkheadSealed, "its hatch open");
        assertFalse(s.c(e).suited, "the suit hung back up");
        assertEquals(2, evaLocker(s).suits.size, "in its locker");
        assertEquals(java.util.Arrays.asList(EvaEvent.Kind.OUT, EvaEvent.Kind.BACK), events);
        assertTrue(lowestCabin > 0.9f, "the cabin kept its air through both cycles: " + lowestCabin);
        assertFalse(posted(s));
    }

    @Test
    void withNoKitsTheBlocksTileIsFramedButLeaks() {
        TestShips s = ship();
        s.grid.patchKits = 0;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !(block(s).works(s.grid) && EvaSystem.of(e) == null); i++) s.run(0.1f);
        assertTrue(block(s).works(s.grid), "the block fires again");
        assertFalse(s.grid.getTile(9, 0).isPatched());
        assertTrue(s.grid.getTile(9, 0).leakFactor() > 0f, "standing, but leaking");
    }

    @Test
    void aSuitWithItsPackSpentIsChangedForOneFitToGoOutIn() {
        TestShips s = ship();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        // Back in in a suit with all its air and nothing in its pack, as one fetched in from adrift
        Suit spent = SuitLockers.take(evaLocker(s), Suit.Kind.EVA);
        spent.jet = 0f;
        s.c(e).wear(spent);
        float jetOut = -1f;
        for (int i = 0; i < 3000 && jetOut < 0f; i++) {
            s.run(0.1f);
            if (EvaSystem.of(e) != null) jetOut = s.c(e).suitJet;
        }
        assertTrue(jetOut >= EvaTasks.MIN_JET, "out in the full suit, not the spent one: " + jetOut);
        assertEquals(1, evaLocker(s).suits.size, "the spent one hung up to fill");
    }

    @Test
    void withEveryPackSpentNobodyGoesOutTillOneIsHalfFull() {
        TestShips s = ship();
        for (Suit suit : evaLocker(s).suits) suit.jet = 0f;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        float t = 0f;
        for (; t < 900f && EvaSystem.of(e) == null; t += 0.1f) s.run(0.1f);
        assertNotNull(EvaSystem.of(e), "out once a pack had filled");
        assertTrue(t >= SuitLockerSystem.JET_FILL * 0.5f, "not before the locker had half filled one: " + t);
        assertTrue(s.c(e).suitJet >= EvaTasks.MIN_JET, "out with half a pack: " + s.c(e).suitJet);
    }

    @Test
    void aFightStartingCallsTheEvaBackIn() {
        TestShips s = ship();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 3000 && !EvaSystem.outside(e); i++) s.run(0.1f);
        assertTrue(EvaSystem.outside(e), "out");
        s.run(5f);
        s.board.battleStations = true;
        for (int i = 0; i < 3000 && EvaSystem.of(e) != null; i++) s.run(0.1f);
        assertNull(EvaSystem.of(e), "back in");
        assertFalse(block(s).works(s.grid), "the work left undone");
        assertTrue(s.c(e).task == null || s.c(e).task.kind != CrewTask.Kind.REPAIR_RCS, "the job put down");
        s.run(5f);
        assertTrue(posted(s), "the job waits on the board till the fight is over");
        for (CrewTask t : s.board.tasks) if (t.kind == CrewTask.Kind.REPAIR_RCS && t.isActive()) assertEquals(CrewTask.Status.OPEN, t.status);
    }

    @Test
    void nobodyGoesOutAtBattleStations() {
        TestShips s = ship();
        s.board.battleStations = true;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(120f);
        assertNull(EvaSystem.of(e));
        assertTrue(posted(s), "the job waits on the board for anyone sent out to it");
    }

    @Test
    void aCrewmanLeavesTheEvaToTheEngineers() {
        TestShips s = ship();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 9, 2);
        s.run(120f);
        assertNull(EvaSystem.of(e));
        assertTrue(posted(s), "the job waits for an engineer");
    }

    @Test
    void withNoEvaSuitAboardNobodyGoesOut() {
        TestShips s = ship();
        evaLocker(s).suits.clear();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(120f);
        assertNull(EvaSystem.of(e));
        assertFalse(s.c(e).suited);
        assertTrue(posted(s));
    }

    @Test
    void aPortWithoutItsHatchIsNoWayOut() {
        TestShips s = ship();
        // Its hatch gone: a port that can't cycle is no airlock
        for (int x = 4; x <= 6; x++) s.grid.setEdge(x, 4, Side.BOTTOM, null);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(120f);
        assertNull(EvaSystem.of(e));
        assertFalse(block(s).works(s.grid));
    }

    @Test
    void aCrushedTileIsYardWork() {
        TestShips s = ship();
        s.grid.getTile(9, 0).setCrushed(true);
        assertFalse(RcsThruster.fieldRepairable(s.grid, 9, 0));
        s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(20f);
        assertFalse(posted(s));
    }

    @Test
    void oneKnockedOutOnTheHullIsWinchedInOnTheirLine() {
        TestShips s = ship();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 3000 && !(EvaSystem.outside(e) && s.p(e).x > 11f); i++) s.run(0.1f);
        assertTrue(EvaSystem.outside(e), "out and along the hull");
        s.c(e).health = s.c(e).def.maxHealth * 0.05f;
        s.crew.updateState(e);
        assertEquals(CrewState.UNCONSCIOUS, s.c(e).state);
        s.run(1f);
        assertEquals(EvaComponent.Phase.FREE, EvaSystem.of(e).phase, "their hold gone");
        for (int i = 0; i < 3000 && EvaSystem.of(e) != null; i++) s.run(0.1f);
        assertNull(EvaSystem.of(e), "reeled in and cycled back in");
        assertTrue(DockingPortLayout.isOpening(port(s), s.p(e).tileX(), s.p(e).tileY()), "lying in the chamber");
        assertTrue(Airlock.of(s.grid, port(s)).isIn());
    }
}
