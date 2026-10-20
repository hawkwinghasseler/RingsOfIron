package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.macrosim.economy.TraderComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SaveSlotsTest {

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void slotNamesBecomeSafeFileNames(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        assertEquals("Before_the_raid.json", slots.fileFor("Before the raid").name());
        assertEquals("a_b.json", slots.fileFor("a/../b").name());
        assertEquals("save.json", slots.fileFor("   ").name());
        assertEquals("save_quicksave.json", slots.fileFor("quicksave").name(), "a named save never overwrites the quick save");
        assertEquals("quicksave.json", slots.quickSave().name());
    }

    @Test
    void theLoadListShowsEachSaveNewestFirst(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        assertNull(slots.latest());
        assertEquals("Save 1", slots.nextName());

        write(slots, "Older", 1_000L, 3_700, 120);
        write(slots, "Newer", 2_000L, 90_000, 4_500);
        // A quick save from before summaries: named by its file, dated by it
        slots.quickSave().writeString("{}", false);
        slots.quickSave().file().setLastModified(500L);

        Array<SaveSummary> list = slots.list();
        assertEquals(3, list.size, "summaries and the settings file are not saves");
        assertEquals("Newer", list.get(0).name);
        assertEquals("Older", list.get(1).name);
        assertEquals(SaveSlots.QUICK_SAVE_NAME, list.get(2).name);
        assertEquals("Day 1 01:00   Patrol Corvette   4,500 cr", list.get(0).details());
        assertEquals("", list.get(2).details());
        assertEquals("Newer", slots.latest().name);

        slots.delete(list.get(0));
        assertFalse(slots.fileFor("Newer").exists());
        assertFalse(SaveSummary.metaFor(slots.fileFor("Newer")).exists());
        assertEquals("Older", slots.latest().name);
    }

    private static void write(SaveSlots slots, String name, long savedAt, double gameSeconds, double credits) {
        FileHandle f = slots.fileFor(name);
        f.writeString("{}", false);
        SaveSummary s = new SaveSummary();
        s.name = name;
        s.savedAt = savedAt;
        s.gameSeconds = gameSeconds;
        s.ship = "Patrol Corvette";
        s.credits = credits;
        s.writeFor(f);
        new GameSettings().save(slots.dir().child(GameSettings.FILE_NAME));
    }

    @Test
    void aSummaryOfTheGameNamesTheShipAndCredits(@TempDir Path dir) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
            game.player.getComponent(TraderComponent.class).credits = 1_234;
            for (int i = 0; i < 30; i++) world.update(1f / 60f);

            SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
            FileHandle file = slots.fileFor("Mine");
            assertNull(game.quickSave(file));
            SaveSummary.of(game, "Mine").writeFor(file);

            SaveSummary read = slots.latest();
            assertEquals("Mine", read.name);
            assertEquals("Patrol Corvette", read.ship);
            assertEquals(1_234, read.credits, 1e-9);
            assertEquals(world.engine.getSimSeconds(), read.gameSeconds, 1e-6);
            assertEquals(file.path(), read.file.path());
        } finally {
            world.dispose();
        }
    }
}
