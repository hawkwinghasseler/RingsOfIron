package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent.EvaOrder;
import org.junit.jupiter.api.Test;

/**
 * The player's say over who goes out ({@link EvaOrders}): sent out at battle stations or with the
 * ship holding everyone in, called back in and kept in, and the hold calling everyone in. The ship
 * of {@link EvaRepairTest#ship()}, its RCS block wrecked.
 */
class EvaOrdersTest {

    @Test
    void sentOutAtBattleStationsTheyGoOutForTheWorkAndTheOrderEndsOnceTheyAreBackIn() {
        TestShips s = EvaRepairTest.ship();
        s.board.battleStations = true;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(20f);
        assertNull(EvaSystem.of(e), "nobody goes out on their own in a fight");
        assertNull(EvaOrders.sendOut(s.crew, e));
        assertEquals(EvaOrder.OUT, s.c(e).evaOrder);
        boolean out = false;
        for (int i = 0; i < 6000 && !(EvaRepairTest.block(s).works(s.grid) && EvaSystem.of(e) == null); i++) {
            s.run(0.1f);
            out |= EvaSystem.outside(e);
        }
        assertTrue(out, "out at battle stations");
        assertTrue(EvaRepairTest.block(s).works(s.grid), "the block mended");
        assertNull(EvaSystem.of(e), "and back in");
        assertEquals(EvaOrder.NONE, s.c(e).evaOrder, "the order carried out");
    }

    @Test
    void heldInNobodyGoesOutButOneSentOutDoes() {
        TestShips s = EvaRepairTest.ship();
        assertTrue(EvaOrders.toggleHold(s.crew, s.ship));
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        s.run(60f);
        assertNull(EvaSystem.of(e), "held in");
        assertTrue(EvaRepairTest.posted(s), "the job waits");
        assertNull(EvaOrders.sendOut(s.crew, e));
        for (int i = 0; i < 3000 && !EvaSystem.outside(e); i++) s.run(0.1f);
        assertTrue(EvaSystem.outside(e), "out, sent");
    }

    @Test
    void calledInTheyComeBackLeavingTheWorkAndStayInTillLetOut() {
        TestShips s = EvaRepairTest.ship();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 3000 && !EvaSystem.outside(e); i++) s.run(0.1f);
        assertTrue(EvaSystem.outside(e), "out on their own");
        assertNull(EvaOrders.callIn(s.crew, e));
        for (int i = 0; i < 3000 && EvaSystem.of(e) != null; i++) s.run(0.1f);
        assertNull(EvaSystem.of(e), "back in");
        assertFalse(EvaRepairTest.block(s).works(s.grid), "the work left");
        s.run(120f);
        assertNull(EvaSystem.of(e), "kept in");
        assertEquals(EvaOrder.IN, s.c(e).evaOrder);
        EvaOrders.letOut(s.crew, e);
        for (int i = 0; i < 6000 && !EvaRepairTest.block(s).works(s.grid); i++) s.run(0.1f);
        assertTrue(EvaRepairTest.block(s).works(s.grid), "out again for it once let out");
    }

    @Test
    void theHoldCallsEveryoneOutBackIn() {
        TestShips s = EvaRepairTest.ship();
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 3000 && !EvaSystem.outside(e); i++) s.run(0.1f);
        assertTrue(EvaSystem.outside(e));
        assertTrue(EvaOrders.toggleHold(s.crew, s.ship));
        for (int i = 0; i < 3000 && EvaSystem.of(e) != null; i++) s.run(0.1f);
        assertNull(EvaSystem.of(e), "back in");
        s.run(60f);
        assertNull(EvaSystem.of(e), "and in they stay");
        assertFalse(EvaOrders.toggleHold(s.crew, s.ship));
        for (int i = 0; i < 6000 && !EvaRepairTest.block(s).works(s.grid); i++) s.run(0.1f);
        assertTrue(EvaRepairTest.block(s).works(s.grid), "out for it again once the hold is off");
    }

    @Test
    void nobodyIsSentOutWithNoWorkOutsideInTheirTrade() {
        TestShips s = EvaRepairTest.ship();
        Entity crewman = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        String why = EvaOrders.sendOut(s.crew, crewman);
        assertNotNull(why, "an RCS block is an engineer's work");
        assertTrue(why.startsWith("There is no work outside for "), why);
        com.ringsofiron.interiorgrid.RcsThruster.fieldRepair(s.grid, 9, 0);
        s.run(1f);
        Entity engineer = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        assertNotNull(EvaOrders.sendOut(s.crew, engineer), "nothing outside at all");
        assertEquals(EvaOrder.NONE, s.c(engineer).evaOrder);
    }
}
