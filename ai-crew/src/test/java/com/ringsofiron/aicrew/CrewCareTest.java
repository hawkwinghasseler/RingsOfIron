package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import org.junit.jupiter.api.Test;

/** Round 7 part 4: light wounds treated when nothing else waits, and the medbay. */
class CrewCareTest {

    /** A long room with a medbay of two beds in its far corner, (8, 1) and (8, 2). */
    static TestShips ward() {
        TestShips s = new TestShips(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########");
        s.grid.getTile(8, 1).covering = FloorCovering.MEDBAY;
        s.grid.getTile(8, 2).covering = FloorCovering.MEDBAY;
        return s;
    }

    @Test
    void aMedicTreatsALightWoundOffBattleStationsWithoutStores() {
        TestShips s = ward();
        Entity hurt = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.spawn(CrewRole.MEDIC, 0, 4, 2);
        s.crew.applyDamage(hurt, 25f, DamageCause.FRAGMENT);
        assertEquals(Wound.LIGHT, s.c(hurt).wound);
        int stores = s.grid.medicalStores;
        // A medic at half pace: a light wound's care time twice over, and some walking
        s.run(CrewDef.careTime(Wound.LIGHT) / CrewDef.LIGHT_CARE + 30f);
        assertEquals(Wound.NONE, s.c(hurt).wound, "treated long before it would heal by itself");
        assertEquals(stores, s.grid.medicalStores, "a light wound uses no stores");
    }

    @Test
    void aLightWoundWaitsWhileTheShipIsAtBattleStations() {
        TestShips s = ward();
        Entity hurt = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.spawn(CrewRole.MEDIC, 0, 4, 2);
        s.board.battleStations = true;
        s.crew.applyDamage(hurt, 25f, DamageCause.FRAGMENT);
        s.run(CrewDef.careTime(Wound.LIGHT) / CrewDef.LIGHT_CARE + 30f);
        assertEquals(Wound.LIGHT, s.c(hurt).wound, "nobody leaves their post for a graze");
        s.board.battleStations = false;
        // Slower in the suit they put on for battle stations, until they take it off
        s.run(CrewDef.careTime(Wound.LIGHT) / CrewDef.LIGHT_CARE * 1.5f);
        assertEquals(Wound.NONE, s.c(hurt).wound, "treated once the fight is over");
    }

    @Test
    void aloneTheWoundedPatchThemselvesUp() {
        TestShips s = ward();
        Entity hurt = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.crew.applyDamage(hurt, 25f, DamageCause.FRAGMENT);
        s.run(s.c(hurt).def.lightHealTime * 0.5f);
        assertEquals(Wound.NONE, s.c(hurt).wound, "in less than half the time it takes to heal by itself");
    }

    @Test
    void aLightWoundGivesWayToRealWork() {
        TestShips s = ward();
        Entity hurt = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 3, 2);
        s.crew.applyDamage(hurt, 25f, DamageCause.FRAGMENT);
        s.run(10f);
        assertSame(hurt, s.c(medic).task instanceof TreatTask ? ((TreatTask) s.c(medic).task).patient : null,
                "the medic is treating the graze");
        // Someone goes down across the room: the graze can wait
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 6, 3);
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        boolean switched = false;
        for (int i = 0; i < 50 && !switched; i++) {
            s.run(0.1f);
            CrewTask t = s.c(medic).task;
            switched = t instanceof TreatTask && ((TreatTask) t).patient == down
                    || t instanceof MedbayCarryTask && ((MedbayCarryTask) t).casualty == down;
        }
        assertTrue(switched, "off to the serious wound");
    }

    @Test
    void theWoundedAreCarriedToTheMedbayOnceTheFightIsOver() {
        TestShips s = ward();
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 1, 3);
        s.spawn(CrewRole.MARINE, 0, 2, 3);
        s.board.battleStations = true;
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        s.run(20f);
        assertFalse(TaskRules.isMedbay(s.grid, s.p(down).tileX(), s.p(down).tileY()), "not during the fight");
        s.board.battleStations = false;
        s.run(30f);
        assertTrue(TaskRules.isMedbay(s.grid, s.p(down).tileX(), s.p(down).tileY()),
                "in a bed: " + s.p(down).tileX() + "," + s.p(down).tileY());
        assertNull(s.c(down).carriedBy, "put down");
    }

    @Test
    void theMedbayTreatsHalfAgainAsFast() {
        float plain = timeToEase(false), bed = timeToEase(true);
        assertEquals(CrewDef.MEDBAY_CARE, plain / bed, 0.1f, "plain " + plain + " s, medbay " + bed + " s");
    }

    /** Seconds a medic beside a seriously wounded crewman takes to ease the wound, on a bed or not. */
    private static float timeToEase(boolean bed) {
        TestShips s = ward();
        if (!bed) {
            s.grid.getTile(8, 1).covering = FloorCovering.NONE;
            s.grid.getTile(8, 2).covering = FloorCovering.NONE;
        }
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 8, 1);
        s.spawn(CrewRole.MEDIC, 0, 7, 1);
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        float t = 0f, dt = 1f / 30f;
        while (s.c(down).wound == Wound.SERIOUS && t < 600f) {
            s.engine.update(dt);
            t += dt;
        }
        assertEquals(Wound.LIGHT, s.c(down).wound);
        return t;
    }
}
