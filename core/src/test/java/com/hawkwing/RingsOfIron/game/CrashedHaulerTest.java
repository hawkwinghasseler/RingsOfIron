package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A Hauler that has been in the bubble, lost on the rails: its crew and systems go with it. */
class CrashedHaulerTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        for (int i = 0; i < 10; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aLostHaulerTakesItsCrewAndSystemsWithIt() {
        String uuid = world.macroSim.freighters().all().keySet().iterator().next();
        Entity hauler = world.engine.getEntity(uuid);
        FreighterShips.outfit(world, hauler);
        assertNotNull(hauler.getComponent(ShipGridComponent.class), "a Hauler, built");
        world.update(FRAME);
        List<Entity> crew = aboard(hauler);
        List<Entity> systems = new ArrayList<>();
        for (Entity s : hauler.getComponent(ShipSubsystemRegistryComponent.class).subsystems) if (s != null) systems.add(s);
        assertFalse(crew.isEmpty(), "it has a crew");
        assertFalse(systems.isEmpty(), "it has systems");

        assertTrue(world.macroSim.destroy(uuid));
        world.update(FRAME);

        assertNull(world.engine.getEntity(uuid), "the Hauler is gone");
        assertTrue(aboard(hauler).isEmpty(), "no crew left aboard it");
        for (Entity e : crew) assertFalse(world.engine.getEcsEngine().getEntities().contains(e, true), "its crew are gone");
        int left = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(SubsystemComponent.class).get())) {
            if (systems.contains(e)) left++;
        }
        assertEquals(0, left, "its systems are gone");
    }

    private List<Entity> aboard(Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent p = world.crew.position(e);
            if (p != null && p.ship == ship) out.add(e);
        }
        return out;
    }
}
