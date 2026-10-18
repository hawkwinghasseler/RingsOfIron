package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FMOD's libraries are not installed in CI, so these check that the game falls back cleanly.
 * With the FMOD Engine and banks installed, the game logs "FMOD" at startup instead.
 */
class FmodAudioBackendTest {

    @Test
    void missingLibrariesFallBackToBuiltInAudio() {
        assertNull(FmodAudioBackend.tryCreate(new HeadlessAudioBackend()));
    }

    @Test
    void libraryNamesMatchThePlatform() {
        String name = FmodAudioBackend.libraryFile("fmodstudio");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) assertEquals("fmodstudio.dll", name);
        else if (os.contains("mac")) assertEquals("libfmodstudio.dylib", name);
        else assertEquals("libfmodstudio.so", name);
    }
}
