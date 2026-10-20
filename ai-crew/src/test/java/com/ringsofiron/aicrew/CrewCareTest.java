package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import org.junit.jupiter.api.Test;

/**
 * Round 7 part 4: light wounds treated when nothing else waits, and the medbay. Round 8 part 5:
 * light wounds closed after a fight (first aid at a medic's rate, a hand at a console sees to their
 * own, and carers out of their suits once the fight is over).
 */
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

    @Test
    void anyoneGivesFirstAidAtAMedicsRate() {
        // Round 7 had a crewman treat a graze at a fifth of a medic's pace: 450 s for one wound
        TestShips s = ward();
        Entity hurt = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        Entity hand = s.spawn(CrewRole.CREWMAN, 0, 4, 2);
        s.crew.applyDamage(hurt, 25f, DamageCause.FRAGMENT);
        assertEquals(Wound.LIGHT, s.c(hurt).wound);
        assertEquals(s.c(hand).careRate(Wound.SERIOUS) / CrewDef.UNTRAINED_CARE, s.c(hand).careRate(Wound.LIGHT), 1e-6f);
        s.run(CrewDef.careTime(Wound.LIGHT) / s.c(hand).careRate(Wound.LIGHT) + 20f);
        assertEquals(Wound.NONE, s.c(hurt).wound, "patched up by a crewmate");
    }

    @Test
    void aHandAtAConsoleSeesToTheirOwnLightWoundOnceTheFightIsOver() {
        // Round 8's wound survey: the one hand left manned the guns, which no graze may call them
        // off, and nobody else was free, so their wound was still open ten minutes on
        TestShips s = ConsoleTest.bridge();
        Entity marine = s.spawn(CrewRole.MARINE, 0, 8, 4);
        s.run(5f);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS));
        s.crew.applyDamage(marine, 25f, DamageCause.FRAGMENT);
        assertEquals(Wound.LIGHT, s.c(marine).wound);
        s.board.battleStations = true;
        s.run(60f);
        assertEquals(Wound.LIGHT, s.c(marine).wound, "not at battle stations");
        s.board.battleStations = false;
        s.run(CrewDef.careTime(Wound.LIGHT) / s.c(marine).careRate(Wound.LIGHT) + 10f);
        assertEquals(Wound.NONE, s.c(marine).wound, "seen to at the console");
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), "and never left it");
    }

    @Test
    void aCarerTakesTheirSuitOffOnceTheFightIsOver() {
        // A suit slows care by a fifth: worn the whole ten minutes, it left a critical wound short
        // of healed. Battle stations over and the air good, the carer takes it off
        TestShips s = ward();
        // Down in a medbay bed already, so nobody carries them anywhere
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 8, 1);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 6, 2);
        s.board.battleStations = true;
        s.run(10f);
        assertTrue(s.c(medic).suited, "suited up for battle stations");
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        // Hit elsewhere and already abed: no fire falls on the ward (FireZoneTest)
        s.board.standDown();
        s.run(10f);
        assertTrue(s.c(medic).task instanceof TreatTask, "treating: " + s.c(medic).activity);
        assertTrue(s.c(medic).suited, "still suited while at battle stations");
        s.board.battleStations = false;
        s.run(5f);
        assertFalse(s.c(medic).suited, "out of the suit to work");
        assertTrue(s.c(medic).task instanceof TreatTask, "and treating still");
    }
    @Test
    void inThinAirSuitedHandsStillGiveFirstAid() {
        // Round 8, part 6's burst oxygen tank: the cabin came back slowly on life support's own
        // oxygen, nowhere aboard was safe, and a suited hand's graze stayed open
        TestShips s = ward();
        Entity grazed = s.spawn(CrewRole.CREWMAN, 0, 3, 2);
        Entity mate = s.spawn(CrewRole.MARINE, 0, 6, 2);
        s.c(grazed).setSuited(true);
        s.c(mate).setSuited(true);
        s.crew.applyDamage(grazed, 30f, DamageCause.PENETRATOR);
        s.board.standDown();
        assertEquals(Wound.LIGHT, s.c(grazed).wound);
        float dt = 1f / 30f;
        for (float t = 0; t < 240f; t += dt) {
            com.ringsofiron.interiorgrid.Compartment room = s.grid.compartmentAt(3, 2);
            double full = room.volume * com.ringsofiron.interiorgrid.Compartment.STANDARD_PRESSURE * 1000.0
                    / (com.ringsofiron.interiorgrid.Compartment.R * room.temperature);
            room.oxygen = Math.min(room.oxygen, 0.6 * full * com.ringsofiron.interiorgrid.Compartment.STANDARD_O2_FRACTION);
            s.engine.update(dt);
        }
        assertFalse(TaskRules.isSafe(s.grid, 3, 2), "the air still thin");
        assertEquals(Wound.NONE, s.c(grazed).wound, s.c(mate).activity);
        assertTrue(s.c(grazed).isAlive() && s.c(mate).isAlive());
    }

    @Test
    void aHandAlreadyAtTheGunsSuitsUpThereForBattleStations() {
        // Round 9's trace: on four of the wound survey's seeds the gunner sat at the weapons from
        // before the fight began, so never suited up for it, and suffocated there as the cabin vented
        TestShips s = ConsoleTest.bridge();
        Entity marine = s.spawn(CrewRole.MARINE, 0, 8, 4);
        s.run(8f);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), s.c(marine).activity);
        assertFalse(s.c(marine).suited);
        s.board.battleStations = true;
        s.run(s.c(marine).def.suitTime + 1f);
        assertTrue(s.c(marine).suited, s.c(marine).activity);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), "and the guns held all the while");
    }

    @Test
    void anEnemyCrewGetsNoSuitCare() {
        TestShips s = ConsoleTest.bridge();
        s.board.faction = 1;
        Entity marine = s.spawn(CrewRole.MARINE, 1, 8, 4);
        s.run(8f);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), s.c(marine).activity);
        s.grid.getTile(9, 1).setIntegrity(0f);
        s.run(30f);
        assertFalse(s.c(marine).suited, "as before round 9: " + s.c(marine).activity);
    }

    @Test
    void aHandAtAConsoleSuitsUpThereAsTheRoomVentsOffBattleStations() {
        TestShips s = ConsoleTest.bridge();
        Entity marine = s.spawn(CrewRole.MARINE, 0, 8, 4);
        s.run(8f);
        assertTrue(CrewQueries.isManning(marine, SubsystemType.WEAPONS), s.c(marine).activity);
        s.run(30f);
        assertFalse(s.c(marine).suited, "a steady cabin never empties a console");
        s.grid.getTile(9, 1).setIntegrity(0f);
        s.run(60f);
        assertTrue(s.c(marine).suited, s.c(marine).activity);
        assertTrue(s.c(marine).isConscious(), "and on their feet");
    }

    @Test
    void aCasualtysSuitIsFilledForThemWithNowhereAboardToBreathe() {
        // Round 9's trace: six of the wound survey's dead lay down, suited, in a ship holed past
        // holding, until their suits ran dry beside oxygen enough to fill them many times over
        TestShips s = new TestShips(
                "##########",
                "#L.......#",
                "#L.......#",
                "#........#",
                "##########");
        for (com.ringsofiron.interiorgrid.Compartment room : s.grid.compartments) {
            room.oxygen = 0;
            room.nitrogen = 0;
        }
        Entity down = s.spawn(CrewRole.CREWMAN, 0, 7, 3);
        Entity mate = s.spawn(CrewRole.CREWMAN, 0, 5, 2);
        s.c(down).setSuited(true);
        s.c(mate).setSuited(true);
        s.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        assertEquals(CrewState.UNCONSCIOUS, s.c(down).state);
        s.c(down).suitAir = 100f;
        s.run(300f);
        assertTrue(s.c(down).isAlive(), s.c(mate).activity);
        assertTrue(s.c(down).suitAir > 100f, "filled: " + s.c(down).suitAir);
    }
}
