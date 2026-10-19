package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.InteriorGridSaveParticipant;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import org.junit.jupiter.api.Test;

/** Round 5: hits wound rather than kill, medics treat the wounded, and crew suit up at battle stations. */
class CrewWoundTest {

    static TestShips room() {
        return CrewHealthTest.room();
    }

    @Test
    void aHitWoundsByHowHardItLands() {
        TestShips s = room();
        Entity graze = s.spawn(CrewRole.CREWMAN, 0, 1, 1), light = s.spawn(CrewRole.CREWMAN, 0, 2, 1);
        Entity serious = s.spawn(CrewRole.CREWMAN, 0, 3, 1), critical = s.spawn(CrewRole.CREWMAN, 0, 4, 1);
        s.crew.applyDamage(graze, 3f, DamageCause.SPALL);
        s.crew.applyDamage(light, 25f, DamageCause.PENETRATOR);
        s.crew.applyDamage(serious, 50f, DamageCause.PENETRATOR);
        s.crew.applyDamage(critical, 100f, DamageCause.PENETRATOR);
        assertEquals(Wound.NONE, s.c(graze).wound);
        assertEquals(Wound.LIGHT, s.c(light).wound);
        assertEquals(Wound.SERIOUS, s.c(serious).wound);
        assertEquals(Wound.CRITICAL, s.c(critical).wound);
        assertEquals(CrewState.CONSCIOUS, s.c(light).state, "a light wound keeps working");
        assertEquals(CrewState.UNCONSCIOUS, s.c(serious).state, "a serious wound puts them down");
        assertEquals(CrewState.UNCONSCIOUS, s.c(critical).state);
        assertEquals(s.c(critical).def.bleedOutTime, s.c(critical).bleedOut, 1e-6f);
        for (Entity e : new Entity[] {graze, light, serious, critical}) {
            assertEquals(s.c(e).def.maxHealth, s.c(e).health, 1e-6f, "a wound is not health: none of them is dying of it yet");
        }
        // Light hits don't add up; a serious one on a serious wound makes it critical
        s.crew.applyDamage(light, 25f, DamageCause.FRAGMENT);
        assertEquals(Wound.LIGHT, s.c(light).wound);
        s.crew.applyDamage(serious, 40f, DamageCause.BLAST);
        assertEquals(Wound.CRITICAL, s.c(serious).wound);
    }

    @Test
    void aCriticalWoundUntreatedKillsAfterItsTime() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.crew.applyDamage(e, 100f, DamageCause.PENETRATOR);
        float bleed = s.c(e).def.bleedOutTime;
        s.run(bleed - 10f);
        assertEquals(CrewState.UNCONSCIOUS, s.c(e).state, "still alive with 10 s left");
        s.run(15f);
        assertEquals(CrewState.DEAD, s.c(e).state, "nobody aboard to treat them");
        assertEquals(DamageCause.PENETRATOR, s.c(e).lastDamageCause, "died of the round's wound");
    }

    @Test
    void aSeriousWoundStaysDownUntilTreatedButDoesNotKill() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.crew.applyDamage(e, 50f, DamageCause.PENETRATOR);
        s.run(900f);
        assertEquals(CrewState.UNCONSCIOUS, s.c(e).state);
        assertEquals(Wound.SERIOUS, s.c(e).wound);
    }

    @Test
    void aMedicTreatsACriticalWoundInTimeUsingTheStores() {
        TestShips s = room();
        Entity patient = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        Entity medic = s.spawn(CrewRole.MEDIC, 0, 7, 3);
        s.crew.applyDamage(patient, 100f, DamageCause.PENETRATOR);
        int stores = s.grid.medicalStores;
        // Critical to serious, then serious to light: one store each
        float care = CrewDef.careTime(Wound.CRITICAL) + CrewDef.careTime(Wound.SERIOUS);
        s.run(care + 30f);
        CrewMemberComponent p = s.c(patient);
        assertEquals(Wound.LIGHT, p.wound, "treated down to a light wound");
        assertEquals(CrewState.CONSCIOUS, p.state, "and back on their feet");
        assertEquals(stores - 2, s.grid.medicalStores);
        assertTrue(s.c(medic).record().pendingCare > 0f, "the medic learns from it");
        // The light wound heals by itself
        s.run(p.def.lightHealTime + 5f);
        assertEquals(Wound.NONE, p.wound);
    }

    @Test
    void anyoneTreatsButAMedicIsFasterAndStoresSpeedItUp() {
        CrewMemberComponent medic = new CrewMemberComponent("Medic", CrewRole.MEDIC, 0);
        CrewMemberComponent hand = new CrewMemberComponent("Hand", CrewRole.CREWMAN, 0);
        assertEquals(1f, medic.careRate(), 1e-6f, "a baseline medic");
        assertEquals(CrewDef.UNTRAINED_CARE, hand.careRate(), 1e-6f);
        medic.record().medicine = 0.9f;
        assertTrue(medic.careRate() > 1.2f, "skill speeds treatment");

        // A crewman alone saves a critical patient, slowly
        TestShips s = room();
        Entity patient = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        Entity carer = s.spawn(CrewRole.CREWMAN, 0, 7, 3);
        s.crew.applyDamage(patient, 100f, DamageCause.PENETRATOR);
        s.run(CrewDef.careTime(Wound.CRITICAL) / CrewDef.UNTRAINED_CARE + 30f);
        assertNotEquals(CrewState.DEAD, s.c(patient).state);
        assertEquals(Wound.SERIOUS, s.c(patient).wound, "stabilised");
        assertNotNull(s.c(patient).treatedBy);
        assertSame(carer, s.c(patient).treatedBy);

        // Without stores a medic is slow
        TestShips bare = room();
        bare.grid.medicalStores = 0;
        Entity p2 = bare.spawn(CrewRole.CREWMAN, 0, 2, 2);
        bare.spawn(CrewRole.MEDIC, 0, 7, 3);
        bare.crew.applyDamage(p2, 100f, DamageCause.PENETRATOR);
        bare.run(CrewDef.careTime(Wound.CRITICAL) + 30f);
        assertEquals(Wound.CRITICAL, bare.c(p2).wound, "no stores: not stabilised in a medic's usual time");
        bare.run(CrewDef.careTime(Wound.CRITICAL) / CrewDef.NO_STORES_CARE);
        assertEquals(Wound.SERIOUS, bare.c(p2).wound, "but in the end");
        assertEquals(0, bare.grid.medicalStores);
    }

    @Test
    void aLightWoundSlowsWorkAndWalking() {
        CrewMemberComponent c = new CrewMemberComponent("Hand", CrewRole.CREWMAN, 0);
        assertEquals(1f, c.workPace(), 1e-6f);
        c.wound = Wound.LIGHT;
        assertEquals(c.def.woundedWorkFactor, c.workPace(), 1e-6f);
        c.setSuited(true);
        assertEquals(c.def.woundedWorkFactor * c.def.suitWorkFactor, c.workPace(), 1e-6f, "a suit slows work too");
    }

    @Test
    void smallArmsStillTakeHealth() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.crew.applyDamage(e, 40f, DamageCause.SMALL_ARMS);
        assertEquals(Wound.NONE, s.c(e).wound);
        assertEquals(60f, s.c(e).health, 1e-6f);
    }

    @Test
    void aPrisonerHeldAboardIsTreatedByTheShipsCrew() {
        TestShips s = room();
        Entity prisoner = s.spawn(CrewRole.CREWMAN, 3, 2, 2);
        s.c(prisoner).held = true;
        s.spawn(CrewRole.MEDIC, 0, 7, 3);
        s.crew.applyDamage(prisoner, 100f, DamageCause.PENETRATOR);
        s.run(CrewDef.careTime(Wound.CRITICAL) + CrewDef.careTime(Wound.SERIOUS) + 30f);
        assertEquals(Wound.LIGHT, s.c(prisoner).wound);
        assertNotEquals(CrewState.DEAD, s.c(prisoner).state);
    }

    @Test
    void crewSuitUpAtBattleStationsAndOutOfThemAfter() {
        TestShips s = room();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 2, 2);
        s.run(1f);
        assertFalse(s.c(e).suited);
        s.board.battleStations = true;
        s.run(s.c(e).def.suitTime + 1f);
        assertTrue(s.c(e).suited, "suited up for battle stations");
        s.run(30f);
        assertTrue(s.c(e).suited, "and kept it on");
        s.board.battleStations = false;
        s.run(CrewTasks.Idle.SUIT_OFF_DELAY + s.c(e).def.suitTime + 1f);
        assertFalse(s.c(e).suited, "the fight over, off it comes");

        s.board.suitsAtBattleStations = false;
        s.board.battleStations = true;
        s.run(s.c(e).def.suitTime + 5f);
        assertFalse(s.c(e).suited, "the player turned suits at battle stations off");
    }

    @Test
    void woundsStoresAndTheSuitSettingSurviveASave() {
        GameEngine game = new GameEngine();
        Engine engine = game.getEcsEngine();
        game.getSerializationService().addParticipant(new InteriorGridSaveParticipant(game));
        CrewService crew = CrewFactory.install(engine);
        CrewSaveParticipant.install(crew, game);
        Entity ship = game.createEntityWithUuid();
        ShipGridComponent grid = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "##########")).grid;
        CompartmentBuilder.rebuildIfChanged(grid);
        ship.add(grid);
        ship.add(new ShipSubsystemRegistryComponent(ship));
        ShipTaskBoardComponent board = CrewFactory.ensureBoard(ship);
        board.suitsAtBattleStations = false;
        grid.medicalStores = 2;
        game.addEntity(ship);
        Entity e = CrewFactory.spawn(crew, ship, "Casualty", CrewRole.CREWMAN, 0, 2, 1);
        Entity medic = CrewFactory.spawn(crew, ship, "Medic", CrewRole.MEDIC, 0, 7, 2);
        crew.crew(medic).record().medicine = 0.8f;
        crew.applyDamage(e, 100f, DamageCause.PENETRATOR);
        float dt = 1f / 30f;
        for (int i = 0; i < 30; i++) engine.update(dt);
        float bleed = crew.crew(e).bleedOut;

        String shipId = ship.getComponent(UuidComponent.class).id;
        String save = game.getSerializationService().saveGame();
        game.getSerializationService().loadGame(save);
        assertTrue(game.getSerializationService().getLastLoadWarnings().isEmpty(),
                game.getSerializationService().getLastLoadWarnings().toString());
        Entity loadedShip = game.getEntity(shipId);
        CrewMemberComponent c = null, m = null;
        for (Entity x : crew.crewEntities()) {
            if ("Casualty".equals(crew.crew(x).name)) c = crew.crew(x);
            if ("Medic".equals(crew.crew(x).name)) m = crew.crew(x);
        }
        assertNotNull(c);
        assertEquals(Wound.CRITICAL, c.wound);
        assertEquals(DamageCause.PENETRATOR, c.woundCause);
        assertEquals(bleed, c.bleedOut, 1e-3f);
        assertEquals(CrewState.UNCONSCIOUS, c.state);
        assertEquals(0.8f, m.record().medicine, 1e-6f);
        assertEquals(2, loadedShip.getComponent(ShipGridComponent.class).medicalStores);
        assertFalse(loadedShip.getComponent(ShipTaskBoardComponent.class).suitsAtBattleStations);
        // The medic takes the patient up again after the load
        boolean treating = false;
        for (int i = 0; i < 30 * 30 && !treating; i++) {
            engine.update(dt);
            treating = c.treatedBy != null;
        }
        assertTrue(treating, "treatment picks up after a load");
    }
}
