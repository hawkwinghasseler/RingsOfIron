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
        SaveSlots.Imported result = mine.importFrom(career.dir());
        assertEquals("Before_the_raid, Gunship", result.copied.toString(", "));
        assertEquals(0, result.kept.size);
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

        SaveSlots.Imported result = mine.importFrom(career.dir());
        assertEquals("Frigate", result.copied.toString(", "));
        assertEquals("Gunship", result.kept.toString(", "));
        assertEquals("{mine:true}", mine.fileFor("Gunship").readString());
        assertFalse(SaveSummary.metaFor(mine.fileFor("Gunship")).exists(), "nor given the career's summary");

        result = mine.importFrom(career.dir());
        assertEquals(0, result.copied.size, "a second run copies nothing new");
        assertEquals("Frigate", result.replaced.toString(", "), "it replaces its own copy");
        assertEquals("Gunship", result.kept.toString(", "), "and still leaves the player's alone");
        assertEquals("{mine:true}", mine.fileFor("Gunship").readString());
    }

    /** Round 9: a new round's saves take the place of the last round's, and nothing else is touched. */
    @Test
    void aLaterCopyReplacesTheEarlierCopysSavesAndOnlyThose(@TempDir Path dir) {
        SaveSlots round8 = new SaveSlots(new FileHandle(dir.resolve("round8").toFile()));
        round8.dir().mkdirs();
        writeSave(round8, "Before the raid", 1_000L);
        writeSave(round8, "Gunship", 1_000L);
        writeSave(round8, "Frigate", 1_000L);

        SaveSlots mine = new SaveSlots(new FileHandle(dir.resolve("mine").toFile()));
        mine.dir().mkdirs();
        mine.fileFor("My own").writeString("{own:1}", false);
        assertEquals(3, mine.importFrom(round8.dir()).copied.size);
        assertTrue(mine.dir().child(SaveSlots.CAREER_LIST).exists(), "the copy keeps its list");
        // The player plays on from the gunship and saves over it: that one is theirs now
        mine.fileFor("Gunship").writeString("{played:on}", false);

        SaveSlots round9 = new SaveSlots(new FileHandle(dir.resolve("round9").toFile()));
        round9.dir().mkdirs();
        writeSave(round9, "Before the raid", 9_000L);
        writeSave(round9, "Gunship", 9_000L);
        writeSave(round9, "First ace fight", 9_000L);
        round9.fileFor("Before the raid").writeString("{round:9}", false);

        SaveSlots.Imported result = mine.importFrom(round9.dir());
        assertEquals("First_ace_fight", result.copied.toString(", "));
        assertEquals("Before_the_raid", result.replaced.toString(", "));
        assertEquals("Gunship", result.kept.toString(", "));
        assertEquals("{round:9}", mine.fileFor("Before the raid").readString());
        assertEquals(9_000L, SaveSummary.readFor(mine.fileFor("Before the raid"), "?").savedAt, "with the new summary");
        assertEquals("{played:on}", mine.fileFor("Gunship").readString(), "a save played on is never overwritten");
        assertEquals("{own:1}", mine.fileFor("My own").readString());
        assertTrue(mine.fileFor("Frigate").exists(), "a save the new folder lacks stays");

        // And a third run replaces round 9's, Frigate included, but still not the gunship
        round9.fileFor("Before the raid").writeString("{round:10}", false);
        result = mine.importFrom(round9.dir());
        assertEquals("Before_the_raid, First_ace_fight", result.replaced.toString(", "));
        assertEquals("Gunship", result.kept.toString(", "));
        assertEquals("{round:10}", mine.fileFor("Before the raid").readString());

        // A copied save the player deletes leaves the list too
        mine.delete(SaveSummary.readFor(mine.fileFor("First ace fight"), "First ace fight"));
        mine.importFrom(round9.dir());
        assertTrue(mine.fileFor("First ace fight").exists(), "copied again as new");
        assertEquals(5, mine.list().size, "my own, the gunship played on, and three career saves");
    }

    @Test
    void aMissingFolderCopiesNothing(@TempDir Path dir) {
        SaveSlots mine = new SaveSlots(new FileHandle(dir.toFile()));
        SaveSlots.Imported result = mine.importFrom(new FileHandle(dir.resolve("nowhere").toFile()));
        assertEquals(0, result.copied.size);
        assertEquals(0, result.kept.size);
    }

    private static void writeSave(SaveSlots slots, String name, long savedAt) {
        FileHandle save = slots.fileFor(name);
        save.writeString("{}", false);
        SaveSummary.metaFor(save).writeString("{name:\"" + name + "\",savedAt:" + savedAt + ",gameSeconds:0}", false);
    }
}
