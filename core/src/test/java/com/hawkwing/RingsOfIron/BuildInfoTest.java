package com.hawkwing.RingsOfIron;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInfoTest {
    @Test
    void formatsVersionAndCommit() {
        assertEquals("v1.0.0 (a1b2c3d)", BuildInfo.format("1.0.0", "a1b2c3d"));
        assertEquals("v1.0.0", BuildInfo.format("1.0.0", ""));
        assertEquals("dev", BuildInfo.format("", ""));
        assertEquals("dev", BuildInfo.format(null, null));
    }

    @Test
    void missingFileFallsBackToDev() {
        assertEquals("dev", BuildInfo.load("no-such-file.properties"));
    }

    @Test
    void buildWritesTheVersionFile() {
        assertTrue(BuildInfo.label().startsWith("v"), BuildInfo.label());
    }
}
