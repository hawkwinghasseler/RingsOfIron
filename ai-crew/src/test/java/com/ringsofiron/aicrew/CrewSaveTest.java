package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
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
import java.util.Arrays;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.aicrew.pilot.PilotBurn;

class CrewSaveTest {

    @Test
    void aRescueInProgressSurvivesASaveAndLoad() {
        GameEngine game = new GameEngine();
        Engine engine = game.getEcsEngine();
        game.getSerializationService().addParticipant(new InteriorGridSaveParticipant(game));
        CrewService crew = CrewFactory.install(engine);
        CrewSaveParticipant.install(crew, game);

        Entity ship = game.createEntityWithUuid();
        ShipGridComponent grid = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "############")).grid;
        CompartmentBuilder.rebuildIfChanged(grid);
        ship.add(grid);
        ship.add(new ShipSubsystemRegistryComponent(ship));
        CrewFactory.ensureBoard(ship);
        game.addEntity(ship);
        // A scorching end of the room, with nothing to cool it (no hazard system)
        for (int y = 1; y <= 3; y++) for (int x = 7; x <= 10; x++) grid.getTile(x, y).temperature = 400f;
        Entity casualty = CrewFactory.spawn(crew, ship, "Casualty", CrewRole.CREWMAN, 0, 9, 2);
        crew.applyDamage(casualty, 76f, DamageCause.BLAST);
        Entity medic = CrewFactory.spawn(crew, ship, "Medic", CrewRole.MEDIC, 0, 1, 1);

        float dt = 1f / 30f;
        for (int i = 0; i < 600 && crew.crew(medic).carrying == null; i++) engine.update(dt);
        assertNotNull(crew.crew(medic).carrying, "the medic picked the casualty up");

        String shipId = ship.getComponent(UuidComponent.class).id;
        String save = game.getSerializationService().saveGame();
        game.getSerializationService().loadGame(save);
        assertTrue(game.getSerializationService().getLastLoadWarnings().isEmpty(),
                game.getSerializationService().getLastLoadWarnings().toString());

        Entity loadedShip = game.getEntity(shipId);
        Entity loadedCasualty = find(crew, "Casualty"), loadedMedic = find(crew, "Medic");
        assertSame(loadedShip, crew.position(loadedCasualty).ship);
        assertSame(loadedShip, crew.position(loadedMedic).ship);
        assertEquals(CrewState.UNCONSCIOUS, crew.crew(loadedCasualty).state);
        assertNull(crew.crew(loadedCasualty).carriedBy, "set down where they were");
        assertNotNull(loadedMedic.getComponent(BehaviorTreeComponent.class));

        // The medic takes the rescue up again
        for (float t = 0; t < 15f; t += dt) engine.update(dt);
        GridPositionComponent p = crew.position(loadedCasualty);
        ShipGridComponent loadedGrid = loadedShip.getComponent(ShipGridComponent.class);
        assertTrue(TaskRules.isSafe(loadedGrid, p.tileX(), p.tileY()), "moved to a safe tile, now at " + p.tileX());
        assertNull(crew.crew(loadedMedic).carrying);
    }

    @Test
    void npcPilotsKeepTheirBurnsAcrossASave() {
        GameEngine game = new GameEngine();
        Entity freighter = game.createEntityWithUuid();
        NpcPilotComponent pilot = new NpcPilotComponent(Arrays.asList(new PilotBurn(10, 2, 0), new PilotBurn(40, -1, 0.5)));
        pilot.seekTarget = new Vector2(100f, 50f);
        freighter.add(pilot);
        game.addEntity(freighter);
        String id = freighter.getComponent(UuidComponent.class).id;

        game.getSerializationService().loadGame(game.getSerializationService().saveGame());

        NpcPilotComponent loaded = game.getEntity(id).getComponent(NpcPilotComponent.class);
        assertEquals(2, loaded.burns.size());
        assertEquals(40.0, loaded.burns.peekLast().time);
        assertEquals(0.5, loaded.burns.peekLast().radial);
        assertEquals(new Vector2(100f, 50f), loaded.seekTarget);
    }

    private static Entity find(CrewService crew, String name) {
        for (Entity e : crew.engine().getEntitiesFor(CrewService.CREW)) {
            if (name.equals(crew.crew(e).name)) return e;
        }
        fail("no crew member named " + name);
        return null;
    }
}
