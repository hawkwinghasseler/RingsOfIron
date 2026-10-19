package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.files.FileHandle;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.shell.GameSettings;
import com.hawkwing.RingsOfIron.shell.GameShell;

/**
 * Launches the game at its main menu ({@link GameShell}). Run with {@code gradlew :lwjgl3:runGame}.
 * With {@code --first-play} ({@code gradlew :lwjgl3:runFirstPlay}) it starts straight into the scripted
 * first play, with no menu, as before the menu existed. The window opens as the settings file says.
 */
public class GameLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication(java.util.Arrays.asList(args).contains("--first-play"));
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
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
