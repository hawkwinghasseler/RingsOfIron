package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import org.junit.jupiter.api.Test;

/** Round 8: at battle stations the crew keep out of where a crewmate was hit until the fight is over. */
class FireZoneTest {

    static TestShips hall() {
        return new TestShips(
                "################",
                "#..............#",
                "#..............#",
                "#..............#",
                "################");
    }

    @Test
    void nobodyGoesInWhereTheFireFellUntilTheFightIsOver() {
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        assertEquals(Wound.CRITICAL, s.c(down).wound);
        assertTrue(s.board.underFire(13, 3), "beside the hit");
        assertFalse(s.board.underFire(9, 2), "further off");
        s.run(20f);
        assertTrue(s.p(medic).tileX() < 12 - ShipTaskBoardComponent.FIRE_ZONE_RADIUS, "kept out, at " + s.p(medic).tileX());
        assertFalse(s.c(down).stabilised);

        // The fight over, the medic goes in
        s.board.battleStations = false;
        s.board.standDown();
        s.run(30f);
        assertTrue(s.c(down).stabilised || s.c(down).wound != Wound.CRITICAL, s.c(medic).activity);
        assertTrue(s.c(down).isAlive());
    }

    @Test
    void aCasualtyAboutToBleedOutIsGoneInForAllTheSame() {
        TestShips s = hall();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 12, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 1, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 95f, DamageCause.PENETRATOR);
        s.c(down).bleedOut = TaskRules.LAST_CHANCE - 10f;
        s.run(60f);
        assertTrue(s.c(down).stabilised || s.c(down).wound != Wound.CRITICAL, s.c(medic).activity);
        assertTrue(s.c(down).isAlive());
    }

    @Test
    void theHelmIsMannedWhereverTheFireFalls() {
        TestShips s = hall();
        s.board.battleStations = true;
        s.board.hitAt(5, 2);
        com.ringsofiron.interiorgrid.crew.CrewTask console = new ManConsoleTask(null, 5, 2);
        assertFalse(TaskRules.waitsOutFire(console, s.board, s.crew));
    }
}
