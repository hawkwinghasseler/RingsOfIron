package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
    private static long files(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void replacesTheFileWholeAndLeavesNoTemporaryBehind(@TempDir Path dir) throws IOException {
        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        AtomicFiles.writeString(file, "first");
        AtomicFiles.writeString(file, "second, longer");
        AtomicFiles.writeString(file, "3");

        assertEquals("3", file.readString("UTF-8"));
        assertEquals(1, files(dir));
    }

    @Test
    void makesTheFoldersItNeeds(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("saves").resolve("deep").resolve("save.json").toFile());
        AtomicFiles.writeString(file, "x");
        assertEquals("x", file.readString("UTF-8"));
    }

    @Test
    void aWriteThatFailsLeavesWhatWasThere(@TempDir Path dir) throws IOException {
        // A folder where the file goes: the rename over it fails after the text is written
        Path target = dir.resolve("save.json");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "kept");

        assertThrows(GdxRuntimeException.class, () -> AtomicFiles.writeString(new FileHandle(target.toFile()), "new"));

        assertEquals("kept", Files.readString(target.resolve("keep.txt")));
        assertEquals(1, files(dir), "the temporary file is cleaned up");
    }
}
