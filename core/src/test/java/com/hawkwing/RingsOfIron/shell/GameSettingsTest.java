package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameSettingsTest {

    @Test
    void settingsReadBackAsWritten(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve(GameSettings.FILE_NAME).toFile());
        GameSettings s = new GameSettings();
        s.masterVolume = 0.5f;
        s.effectsVolume = 0.25f;
        s.ambienceVolume = 0f;
        s.hrtf = "on";
        s.fullscreen = true;
        s.windowWidth = 1920;
        s.windowHeight = 1080;
        s.gamepad = false;
        KeyBindings keys = new KeyBindings();
        keys.bind(KeyBindings.Action.FIRE, Input.Keys.J);
        s.setBindings(keys);
        s.save(file);

        GameSettings read = GameSettings.load(file);
        assertEquals(0.5f, read.masterVolume);
        assertEquals(0.25f, read.effectsVolume);
        assertEquals(0f, read.ambienceVolume);
        assertEquals("on", read.hrtf);
        assertTrue(read.fullscreen);
        assertEquals(1920, read.windowWidth);
        assertEquals(1080, read.windowHeight);
        assertFalse(read.gamepad);
        assertEquals(Input.Keys.J, read.bindings().keyOf(KeyBindings.Action.FIRE));
    }

    @Test
    void aMissingOrBrokenFileGivesTheDefaults(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve(GameSettings.FILE_NAME).toFile());
        assertEquals(0.8f, GameSettings.load(file).masterVolume);
        file.writeString("{ this is not json", false);
        GameSettings s = GameSettings.load(file);
        assertEquals(0.8f, s.masterVolume);
        assertEquals("auto", s.hrtf);
        assertTrue(s.bindings().isDefault());
    }

    @Test
    void valuesOutOfRangeAreMadeSafe(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve(GameSettings.FILE_NAME).toFile());
        file.writeString("{masterVolume: 4, effectsVolume: -1, hrtf: loud, windowWidth: 10, windowHeight: 10}", false);
        GameSettings s = GameSettings.load(file);
        assertEquals(1f, s.masterVolume);
        assertEquals(0f, s.effectsVolume);
        assertEquals("auto", s.hrtf);
        assertEquals(1280, s.windowWidth);
        assertEquals(800, s.windowHeight);
    }
}
