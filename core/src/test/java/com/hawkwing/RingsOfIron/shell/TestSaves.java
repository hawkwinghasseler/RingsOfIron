package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/** Stand-in saves for the save-slot tests: a summary at the top, and no game under it. */
final class TestSaves {
    private TestSaves() {
    }

    /** A save holding just {@code summary}. */
    static void write(FileHandle save, SaveSummary summary) {
        write(save, new Json(JsonWriter.OutputType.json).toJson(summary), "");
    }

    /** A save opening with the summary {@code summaryJson}, then {@code rest} (members, comma-led). */
    static void write(FileHandle save, String summaryJson, String rest) {
        save.writeString("{summary:" + summaryJson + rest + "}", false);
    }
}
