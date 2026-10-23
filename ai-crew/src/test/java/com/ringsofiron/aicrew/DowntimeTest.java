package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.FurnishingType;
import com.ringsofiron.interiorgrid.Furnishings;
import org.junit.jupiter.api.Test;

/** Crew with nothing to do use the ship's furnishings, a seat each, and earn morale doing it. */
class DowntimeTest {
    static final FurnishingType COUCH = new FurnishingType("couch", "Couch", null, 1.5f, 0.75f, 6f, 900,
            FurnishingType.Use.LOUNGE, 3, 0.04f, "lounging on the couch");
    static final FurnishingType ARCADE = new FurnishingType("arcade_cabinet", "Arcade cabinet", null, 0.75f, 0.5f, 3f, 1800,
            FurnishingType.Use.GAME, 1, 0.05f, "playing the arcade cabinet");
    static final FurnishingType PLANTER = new FurnishingType("planter", "Planter", null, 0.5f, 0.5f, 1f, 150,
            FurnishingType.Use.DECOR, 1, 0f, null);

    @Test
    void idleCrewGoAndUseAFurnishingAndEarnMorale() {
        TestShips s = CrewHealthTest.room();
        Furnishing couch = Furnishing.at(COUCH, 7.4f, 2.6f, 0f);
        assertNull(Furnishings.place(s.grid, couch));
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 1), b = s.spawn(CrewRole.ENGINEER, 0, 1, 3);
        s.run(20f);
        for (Entity e : new Entity[]{a, b}) {
            assertEquals("lounging on the couch", s.c(e).activity);
            assertEquals(7, s.p(e).tileX());
            assertEquals(2, s.p(e).tileY());
            assertTrue(s.c(e).record().pendingLeisure > 0f, "morale earned");
        }
        assertEquals(2, couch.users.size);
        // They settle at their seats, not the middle of the tile
        assertNotEquals(s.p(a).x, s.p(b).x, 0.05f);
    }

    @Test
    void aFurnishingTakesNoMoreThanItsSeatsAndDecorIsNotUsed() {
        TestShips s = CrewHealthTest.room();
        Furnishings.place(s.grid, Furnishing.at(ARCADE, 6.5f, 2.5f, 0f));
        Furnishings.place(s.grid, Furnishing.at(PLANTER, 3.5f, 1.5f, 0f));
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 1), b = s.spawn(CrewRole.CREWMAN, 0, 1, 3);
        s.run(20f);
        int playing = 0;
        for (Entity e : new Entity[]{a, b}) if (s.c(e).activity.equals("playing the arcade cabinet")) playing++;
        assertEquals(1, playing, "one at the cabinet; the planter is only for looks");
    }

    @Test
    void theyGetUpWhenItCanNoLongerBeUsed() {
        TestShips s = CrewHealthTest.room();
        Furnishing couch = Furnishing.at(COUCH, 5.5f, 2.5f, 0f);
        Furnishings.place(s.grid, couch);
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 2);
        s.run(15f);
        assertEquals("lounging on the couch", s.c(a).activity);
        couch.fuel = COUCH.fuel * 0.3f; // scorched past use
        s.run(1f);
        assertNotEquals("lounging on the couch", s.c(a).activity);
        assertEquals(0, couch.users.size, "the seat is let go");
    }

    @Test
    void afterASessionTheyMoveOnToSomethingElse() {
        TestShips s = CrewHealthTest.room();
        Furnishing couch = Furnishing.at(COUCH, 2.5f, 2.5f, 0f), arcade = Furnishing.at(ARCADE, 7.5f, 2.5f, 0f);
        Furnishings.place(s.grid, couch);
        Furnishings.place(s.grid, arcade);
        Entity a = s.spawn(CrewRole.CREWMAN, 0, 1, 2);
        s.run(15f);
        assertEquals("lounging on the couch", s.c(a).activity, "the nearest first");
        s.run(com.ringsofiron.aicrew.btree.CrewTasks.Idle.SUIT_OFF_DELAY + 180f + 30f);
        assertEquals("playing the arcade cabinet", s.c(a).activity, "then the other");
    }
}
