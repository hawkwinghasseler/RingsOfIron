package com.hawkwing.RingsOfIron.app;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.NewGame;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The quick save, autosaves and loads, without a window: what each says, and the game pointed at what was loaded. */
class SaveControllerTest {
    @TempDir
    Path saves;
    GameWorld world;
    GameScenario game;
    GameOptions options;
    SaveController controller;
    int rebinds;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        options = new GameOptions(assets, assets.resolve("no-mods-here"), false, NewGame.designed());
        options.slots = new SaveSlots(new FileHandle(saves.toFile()));
        controller = new SaveController(options, game, () -> rebinds++);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    String said() {
        return world.notices.latest().text;
    }

    @Test
    void aQuickLoadWithNoQuickSaveSaysSoAndChangesNothing() {
        controller.quickLoad();
        assertTrue(said().startsWith("No quick save yet ("), said());
        assertEquals(0, rebinds);
    }

    @Test
    void theQuickSaveLoadsBackAndThePlayerIsPointedAtIt() {
        controller.quickSave();
        assertEquals("Game saved", said());
        assertTrue(options.slots.quickSave().exists());
        controller.quickLoad();
        assertEquals("Game loaded", said());
        assertEquals(1, rebinds, "the gunnery, HUD and guides follow the loaded entities");
        assertNotNull(game.player);
    }

    @Test
    void anAutosaveIsThereToOfferOnceTaken() {
        controller.start();
        assertFalse(controller.haveAutosave());
        controller.autosave("test");
        assertTrue(controller.haveAutosave());
        assertNotNull(options.slots.latestAutosave());
        controller.loadLatestAutosave();
        assertEquals("Game loaded", said());
        assertEquals(1, rebinds);
    }

    @Test
    void noAutosaveYetIsSaid() {
        controller.loadLatestAutosave();
        assertEquals("No autosave yet", said());
    }

    @Test
    void aSaveThatWillNotLoadLeavesTheGameAsItWas() throws Exception {
        Path broken = saves.resolve("broken.json");
        Files.writeString(broken, "{ not a save");
        assertFalse(controller.load(new FileHandle(broken.toFile())));
        assertTrue(said().startsWith("Load failed"), said());
        assertNotNull(game.player);
    }
}
