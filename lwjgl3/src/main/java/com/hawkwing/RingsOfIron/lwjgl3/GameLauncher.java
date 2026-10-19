package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hawkwing.RingsOfIron.RingsOfIronGame;

/**
 * Launches the game with every module wired together. Run with {@code gradlew :lwjgl3:runGame}, or
 * with {@code --first-play} ({@code gradlew :lwjgl3:runFirstPlay}) for the scripted first play.
 */
public class GameLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication(java.util.Arrays.asList(args).contains("--first-play"));
    }

    private static Lwjgl3Application createApplication(boolean firstPlay) {
        return new Lwjgl3Application(new RingsOfIronGame(java.nio.file.Paths.get("").toAbsolutePath(),
                java.nio.file.Paths.get("mods").toAbsolutePath(), firstPlay), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Rings of Iron");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1280, 800);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
