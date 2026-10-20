package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.shell.GameSettings;
import com.hawkwing.RingsOfIron.shell.GameShell;
import com.hawkwing.RingsOfIron.shell.SaveSlots;

/**
 * Launches the game at its main menu ({@link GameShell}). Run with {@code gradlew :lwjgl3:runGame}.
 * With {@code --first-play} ({@code gradlew :lwjgl3:runFirstPlay}) it starts straight into the scripted
 * first play, with no menu, as before the menu existed. The window opens as the settings file says.
 * With {@code --career-saves <folder>} ({@code gradlew :lwjgl3:runGame -PcareerSaves=<folder>}) the saves in
 * that folder are first copied into the game's own ({@code saves} under the working directory), so Load
 * lists them; a save already there is never overwritten.
 */
public class GameLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        java.util.List<String> list = java.util.Arrays.asList(args);
        int at = list.indexOf("--career-saves");
        if (at >= 0 && at + 1 < list.size()) importCareerSaves(list.get(at + 1));
        createApplication(list.contains("--first-play"));
    }

    /** Copies a folder of career saves into the game's saves folder and says what it did. */
    static void importCareerSaves(String folder) {
        FileHandle from = new FileHandle(folder);
        if (!from.isDirectory()) {
            System.out.println("Career saves: no folder at " + from.file().getAbsolutePath());
            return;
        }
        SaveSlots mine = new SaveSlots(new FileHandle(java.nio.file.Paths.get("saves").toAbsolutePath().toFile()));
        Array<Array<String>> result = mine.importFrom(from);
        System.out.println("Career saves from " + from.file().getAbsolutePath() + ": copied "
                + result.get(0).size + (result.get(0).size == 0 ? "" : " (" + result.get(0).toString(", ") + ")")
                + ", kept " + result.get(1).size + " already in " + mine.dir().file().getAbsolutePath()
                + (result.get(1).size == 0 ? "" : " (" + result.get(1).toString(", ") + ")"));
    }

    private static Lwjgl3Application createApplication(boolean firstPlay) {
        ApplicationListener app = firstPlay
                ? new RingsOfIronGame(java.nio.file.Paths.get("").toAbsolutePath(),
                        java.nio.file.Paths.get("mods").toAbsolutePath(), true)
                : new GameShell();
        return new Lwjgl3Application(app, getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        GameSettings settings = GameSettings.load(new FileHandle(GameSettings.FILE));
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Rings of Iron");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        if (settings.fullscreen) configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        else configuration.setWindowedMode(settings.windowWidth, settings.windowHeight);
        configuration.setWindowIcon("icon128.png", "icon64.png", "icon32.png", "icon16.png");
        return configuration;
    }
}
