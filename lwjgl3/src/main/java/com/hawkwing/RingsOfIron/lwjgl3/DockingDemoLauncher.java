package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hawkwing.RingsOfIron.DockingDemo;

/** Launches the docking demo (stations-docking). Run with {@code gradlew :lwjgl3:runDockingDemo}. */
public class DockingDemoLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new DockingDemo(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("RingsOfIron - Docking Demo");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1296, 720);
        // An 8-bit stencil buffer: the hulls' roof shadows are cut out with it (HullShading)
        configuration.setBackBufferConfig(8, 8, 8, 8, 16, 8, 0);
        configuration.setWindowIcon("icon128.png", "icon64.png", "icon32.png", "icon16.png");
        return configuration;
    }
}
