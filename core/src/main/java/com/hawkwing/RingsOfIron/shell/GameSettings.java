package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The player's settings: volumes, HRTF, the window, the gamepad and the key bindings. One JSON file,
 * {@link #FILE} in the saves folder, read by the launcher before the window opens and written when
 * the settings window applies.
 */
public class GameSettings {
    public static final String FILE_NAME = "settings.json";
    /** Relative to the game's working directory. */
    public static final String FILE = "saves/" + FILE_NAME;
    public static final String[] HRTF_MODES = {"auto", "on", "off"};

    public float masterVolume = 0.8f;
    public float effectsVolume = 1f;
    public float ambienceVolume = 1f;
    /** "auto" lets OpenAL Soft decide (on for headphones), or "on" / "off". */
    public String hrtf = "auto";
    public boolean fullscreen;
    public int windowWidth = 1280;
    public int windowHeight = 800;
    public boolean gamepad = true;
    /** New games open with a short walkthrough of the board, undocking and the map. */
    public boolean openingGuide = true;
    /** Action name to key name, for the actions not on their default key. */
    public ObjectMap<String, String> keys = new ObjectMap<>();

    /** The settings in {@code file}, or the defaults when it is missing or can't be read. */
    public static GameSettings load(FileHandle file) {
        if (file == null || !file.exists()) return new GameSettings();
        try {
            GameSettings s = new Json().fromJson(GameSettings.class, file);
            return s == null ? new GameSettings() : s.sanitized();
        } catch (RuntimeException e) {
            return new GameSettings();
        }
    }

    public void save(FileHandle file) {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        file.writeString(json.prettyPrint(this), false, "UTF-8");
    }

    public KeyBindings bindings() {
        KeyBindings b = new KeyBindings();
        b.fromMap(keys);
        return b;
    }

    public void setBindings(KeyBindings b) {
        keys = new ObjectMap<>(b.toMap());
    }

    private GameSettings sanitized() {
        masterVolume = unit(masterVolume, 0.8f);
        effectsVolume = unit(effectsVolume, 1f);
        ambienceVolume = unit(ambienceVolume, 1f);
        boolean known = false;
        for (String m : HRTF_MODES) known |= m.equals(hrtf);
        if (!known) hrtf = "auto";
        if (windowWidth < 640 || windowHeight < 480) {
            windowWidth = 1280;
            windowHeight = 800;
        }
        if (keys == null) keys = new ObjectMap<>();
        return this;
    }

    private static float unit(float v, float fallback) {
        return Float.isNaN(v) ? fallback : Math.max(0f, Math.min(1f, v));
    }
}
