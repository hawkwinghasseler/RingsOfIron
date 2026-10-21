package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** A folder of career saves copied into the game's own ({@code runGame -PcareerSaves=<folder>}). */
class SaveSlotsImportTest {

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void careerSavesJoinTheLoadListWithTheirNames(@TempDir Path dir) {
        SaveSlots career = new SaveSlots(new FileHandle(dir.resolve("career").toFile()));
        career.dir().mkdirs();
        writeSave(career, "Before the raid", 2_000L);
        writeSave(career, "Gunship", 1_000L);
        career.dir().child(GameSettings.FILE_NAME).writeString("{}", false);

        SaveSlots mine = new SaveSlots(new FileHandle(dir.resolve("not-made-yet").toFile()));
        Array<Array<String>> result = mine.importFrom(career.dir());
        assertEquals("Before_the_raid, Gunship", result.get(0).toString(", "));
        assertEquals(0, result.get(1).size);
        assertFalse(mine.dir().child(GameSettings.FILE_NAME).exists(), "the career's settings stay behind");

        Array<SaveSummary> list = mine.list();
        assertEquals(2, list.size);
        assertEquals("Before the raid", list.get(0).name, "listed by its summary's name, not its file");
        assertEquals("Gunship", list.get(1).name);
    }

    @Test
    void aSaveAlreadyThereIsNeverOverwritten(@TempDir Path dir) {
        SaveSlots career = new SaveSlots(new FileHandle(dir.resolve("career").toFile()));
        career.dir().mkdirs();
        writeSave(career, "Gunship", 1_000L);
        writeSave(career, "Frigate", 1_000L);

        SaveSlots mine = new SaveSlots(new FileHandle(dir.resolve("mine").toFile()));
        mine.dir().mkdirs();
        mine.fileFor("Gunship").writeString("{mine:true}", false);

        Array<Array<String>> result = mine.importFrom(career.dir());
        assertEquals("Frigate", result.get(0).toString(", "));
        assertEquals("Gunship", result.get(1).toString(", "));
        assertEquals("{mine:true}", mine.fileFor("Gunship").readString());
        assertFalse(SaveSummary.metaFor(mine.fileFor("Gunship")).exists(), "nor given the career's summary");

        result = mine.importFrom(career.dir());
        assertEquals(0, result.get(0).size, "a second run copies nothing");
        assertEquals(2, result.get(1).size);
    }

    @Test
    void aMissingFolderCopiesNothing(@TempDir Path dir) {
        SaveSlots mine = new SaveSlots(new FileHandle(dir.toFile()));
        Array<Array<String>> result = mine.importFrom(new FileHandle(dir.resolve("nowhere").toFile()));
        assertEquals(0, result.get(0).size);
        assertEquals(0, result.get(1).size);
    }

    private static void writeSave(SaveSlots slots, String name, long savedAt) {
        FileHandle save = slots.fileFor(name);
        save.writeString("{}", false);
        SaveSummary.metaFor(save).writeString("{name:\"" + name + "\",savedAt:" + savedAt + ",gameSeconds:0}", false);
    }
}
