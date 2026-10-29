package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The three rotating autosave slots. */
class SaveSlotsAutosaveTest {

    private static void write(SaveSlots slots, int n, long at) {
        SaveSummary s = new SaveSummary();
        s.name = SaveSlots.autosaveName(n);
        s.savedAt = at;
        TestSaves.write(slots.autosave(n), s);
    }

    @Test
    void autosavesFillTheEmptySlotsThenOverwriteTheOldest(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        assertNull(slots.latestAutosave());
        assertEquals(1, slots.nextAutosave());
        write(slots, 1, 100);
        assertEquals(2, slots.nextAutosave());
        write(slots, 2, 200);
        write(slots, 3, 300);
        assertEquals(1, slots.nextAutosave(), "the oldest");
        write(slots, 1, 400);
        assertEquals(2, slots.nextAutosave());
        assertEquals("Autosave 1", slots.latestAutosave().name);
        assertEquals(3, slots.list().size, "they show in the load list");
        assertEquals("save_autosave-1.json", slots.fileFor("autosave-1").name(), "a named save never takes an autosave slot");
    }
}
