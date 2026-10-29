package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Save slots at their edges: no folder yet, a broken summary, gaps in the numbering, and names that share a file. */
class SaveSlotsEdgesTest {

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void noSavesFolderYetMeansNoSaves(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.resolve("not-made-yet").toFile()));
        assertEquals(0, slots.list().size);
        assertNull(slots.latest(), "Continue has nothing to load");
        assertEquals("Save 1", slots.nextName());
    }

    @Test
    void aBrokenSummaryFallsBackToTheFile(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        FileHandle save = slots.fileFor("Broken");
        TestSaves.write(save, "{ not json", "");
        save.file().setLastModified(42_000L);

        SaveSummary s = slots.latest();
        assertEquals("Broken", s.name, "named by its file");
        assertEquals(42_000L, s.savedAt, "dated by it");
        assertEquals("", s.gameTime(), "game time unknown");
        assertEquals(save.path(), s.file.path());

        TestSaves.write(save, "{name:\"\",gameSeconds:0}", "");
        assertEquals("Broken", slots.latest().name, "an empty name falls back too");
        assertEquals("Day 0 00:00", slots.latest().gameTime());
    }

    @Test
    void theNextNameFillsTheFirstGap(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        slots.fileFor("Save 1").writeString("{}", false);
        slots.fileFor("Save 3").writeString("{}", false);
        assertEquals("Save 2", slots.nextName());
    }

    @Test
    void namesThatDifferOnlyInPunctuationShareAFile(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        // Documented behaviour, not a wish: saving under one overwrites the other
        assertEquals(slots.fileFor("a b").path(), slots.fileFor("a_b").path());
        assertEquals(slots.fileFor("a b").path(), slots.fileFor("a/b").path());
        assertNotEquals(slots.quickSave().path(), slots.fileFor(SaveSlots.QUICK_SAVE_NAME).path(),
                "naming a save \"Quick save\" doesn't overwrite the quick save");
    }

    @Test
    void deletingASaveWithoutASummaryRemovesJustTheSave(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        FileHandle save = slots.fileFor("Bare");
        save.writeString("{}", false);
        slots.delete(slots.latest());
        assertFalse(save.exists());
        assertEquals(0, slots.list().size);
        SaveSummary detached = new SaveSummary();
        slots.delete(detached); // no file: nothing to do
    }
}
