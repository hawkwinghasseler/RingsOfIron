package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.Wound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The crew and interior scenarios loaded as the debug menu loads them, with no screen: their buttons and the game's world. */
class CrewInteriorScenariosTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    DevConsole dev;
    DebugScenario scenario;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (scenario != null) scenario.dispose();
        world.dispose();
    }

    private ScenarioSetup load(String id) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        DebugScenarios.Entry entry = DebugScenarios.find(id);
        assertNotNull(entry, id);
        scenario = entry.create();
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), scenario.newGame());
        dev = new DevConsole(world, game);
        ScenarioSetup setup = new ScenarioSetup(entry, world, game, assets, dev, dev.overlays, dev.menuActions, null);
        scenario.setUp(setup);
        return setup;
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) {
            world.update(FRAME);
            scenario.update(FRAME);
        }
    }

    private void press(String action) {
        DebugMenuActions.Action a = dev.menuActions.find(action);
        assertNotNull(a, action + " in " + dev.menuActions.all());
        a.run.run();
    }

    @Test
    void theCrewScenarioPlaysItsDrillAndRestarts() {
        load("crew");
        assertNull(game.raider);
        run(12f);
        Entity ship = game.player;
        press("Restart drill");
        assertNotSame(ship, game.player);
        run(3f);
    }

    @Test
    void theInteriorScenarioCyclesHullsAndArmsTools() {
        load("interior");
        Entity ship = game.player;
        press("Next hull");
        assertNotSame(ship, game.player);
        assertSame(game.player, world.getPlayer());
        run(1f);
        press("Attacker");
        run(5f);
        press("Attacker");
        press("Tool: Wound crew");
        press("Tool setting +");
        press("Heal all");
        for (Entity e : dev.actions.tools.crewAboard(game.player)) assertEquals(Wound.NONE, world.crew.crew(e).wound);
        assertTrue(dev.overlays.ids().contains("interior-tools"), dev.overlays.ids().toString());
    }
}
