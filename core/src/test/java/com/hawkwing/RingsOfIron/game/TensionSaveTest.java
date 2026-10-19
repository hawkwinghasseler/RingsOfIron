package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What faction tension keeps through a save besides the strain itself: its reinforcements and its week. */
class TensionSaveTest {
    static final float FRAME = 1f / 60f;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private GameWorld load(FileHandle file) {
        GameWorld fresh = new GameWorld();
        GameScenario.start(fresh, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none")).quickLoad(file);
        return fresh;
    }

    @Test
    void patrolsSentForHighTensionAreNotSentAgainAfterALoad(@TempDir Path dir) {
        world.tension.raise("patrol", "selene", FactionTension.HIGH, null);
        world.update(FRAME);
        FileHandle file = new FileHandle(dir.resolve("reinforced.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = load(file);
        try {
            int guards = fresh.hostiles.patrols("selene");
            // Already reinforced and not yet eased below the line: running high again sends no more
            fresh.tension.raise("patrol", "selene", -1f, null);
            fresh.tension.raise("patrol", "selene", 1f, null);
            fresh.update(FRAME);
            assertEquals(guards, fresh.hostiles.patrols("selene"));
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void theWeeksDriftKeepsItsDayThroughALoad(@TempDir Path dir) {
        world.update(FRAME);
        double start = world.macroSim.now();
        world.tension.raise("patrol", "selene", 20f, null);
        // Most of a week gone before the save
        world.macroSim.advanceTo(start + ServiceBudget.WEEK - 3_600);
        world.update(FRAME);
        assertEquals(20f, world.tension.get(world.standing.factions().index("patrol"),
                world.standing.factions().index("selene")), 1e-4, "not a week yet");
        FileHandle file = new FileHandle(dir.resolve("week.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = load(file);
        try {
            // Two hours after the load the week is out, and the strain eases once
            fresh.macroSim.advanceTo(fresh.macroSim.now() + 7_200);
            fresh.update(FRAME);
            Factions f = fresh.standing.factions();
            assertEquals(20f - FactionTension.RELAX_PER_WEEK, fresh.tension.get(f.index("patrol"), f.index("selene")), 1e-4);
        } finally {
            fresh.dispose();
        }
    }
}
