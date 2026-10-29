package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A crew member adrift off the ship with no way back on their own (an empty pack, or out cold),
 * within a line's reach: a shipmate goes out on a line, pushes off the hull to them, takes hold and
 * brings them back in through the airlock. The ship of {@link EvaRepairTest#ship()}, standing still.
 */
class EvaRetrieveTest {

    TestShips s;
    Entity castaway, other;
    final List<EvaEvent.Kind> events = new ArrayList<>();
    final List<Entity> who = new ArrayList<>();
    Entity towedBy;

    /** Two engineers aboard; the one who goes out for the block is cast off {@code below} tiles under the hull, still. */
    void castOff(float below) {
        s = EvaRepairTest.ship();
        Entity a = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        Entity b = s.spawn(CrewRole.ENGINEER, 0, 2, 2);
        s.crew.eva.events.add((signal, e) -> {
            events.add(e.kind);
            who.add(e.crew);
            if (e.kind == EvaEvent.Kind.TAKEN_IN_TOW) towedBy = e.by;
        });
        for (int i = 0; i < 3000 && !(EvaSystem.outside(a) || EvaSystem.outside(b)); i++) s.run(0.1f);
        castaway = EvaSystem.outside(a) ? a : b;
        other = castaway == a ? b : a;
        assertTrue(EvaSystem.outside(castaway), "one of them out");
        // The block mended, so the only job left is them; their line gone and their pack empty
        RcsThruster.fieldRepair(s.grid, 9, 0);
        EvaComponent eva = EvaSystem.of(castaway);
        eva.phase = EvaComponent.Phase.FREE;
        eva.tethered = false;
        eva.rope = null;
        eva.rx = 9.5f;
        eva.ry = -below;
        eva.rvx = eva.rvy = 0f;
        eva.worldValid = false;
        s.c(castaway).suitJet = 0f;
        events.clear();
        who.clear();
    }

    static boolean posted(TestShips s) {
        for (CrewTask t : s.board.tasks) if (t.isActive() && t.kind == CrewTask.Kind.RETRIEVE) return true;
        return false;
    }

    @Test
    void aShipmateGoesOutOnALineAndBringsThemBackIn() {
        castOff(10f);
        boolean fetching = false, towing = false;
        for (int i = 0; i < 6000 && !(EvaSystem.of(castaway) == null && EvaSystem.of(other) == null); i++) {
            s.run(0.1f);
            fetching |= s.c(other).activity.startsWith("jetting out to ");
            towing |= s.c(other).activity.startsWith("bringing ");
        }
        assertEquals(EvaEvent.Kind.ADRIFT, events.get(0), "adrift with no way back: " + events);
        assertTrue(fetching, "pushed off the hull and jetted out to them");
        assertTrue(events.contains(EvaEvent.Kind.TAKEN_IN_TOW), "took hold of them: " + events);
        assertSame(other, towedBy);
        assertTrue(towing, "and brought them back");
        assertNull(EvaSystem.of(castaway), "the castaway back in");
        assertNull(EvaSystem.of(other), "and the one who fetched them");
        assertEquals(2, java.util.Collections.frequency(events, EvaEvent.Kind.BACK), "both through the airlock: " + events);
        assertTrue(DockingPortLayout.isOpening(EvaRepairTest.port(s), s.p(castaway).tileX(), s.p(castaway).tileY())
                || !EvaRepairTest.offTheCraft(s, s.p(castaway).x, s.p(castaway).y), "aboard");
        assertFalse(posted(s));
    }

    @Test
    void oneOutColdIsFetchedToo() {
        castOff(8f);
        s.c(castaway).suitJet = 100f;
        s.c(castaway).health = s.c(castaway).def.maxHealth * 0.05f;
        s.crew.updateState(castaway);
        assertEquals(CrewState.UNCONSCIOUS, s.c(castaway).state);
        for (int i = 0; i < 6000 && EvaSystem.of(castaway) != null; i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.TAKEN_IN_TOW), "fetched: " + events);
        assertNull(EvaSystem.of(castaway), "and brought in");
    }

    @Test
    void beyondALinesReachNobodyGoesOut() {
        castOff(40f);
        s.run(60f);
        assertTrue(events.contains(EvaEvent.Kind.ADRIFT));
        assertFalse(posted(s), "out of reach of a line");
        assertNull(EvaSystem.of(other), "nobody goes out for them");
    }

    @Test
    void oneDriftingOffTooFastIsNotChased() {
        castOff(6f);
        EvaComponent eva = EvaSystem.of(castaway);
        eva.rvy = -1f; // a tile a second away from the hull
        eva.worldValid = false;
        s.run(5f);
        assertFalse(posted(s), "gone before anyone could be out to them");
    }

    @Test
    void notAtBattleStations() {
        castOff(10f);
        s.board.battleStations = true;
        s.run(60f);
        assertNull(EvaSystem.of(other), "nobody goes out in a fight");
    }
}
