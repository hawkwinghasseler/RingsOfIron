package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.hawkwing.RingsOfIron.ShipEditor;

/** Launches the ship editor. Run with {@code gradlew :lwjgl3:runShipEditor}. */
public class ShipEditorLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        ShipEditor editor = new ShipEditor();
        return new Lwjgl3Application(editor, getDefaultConfiguration(editor));
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(ShipEditor editor) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("RingsOfIron - Ship Editor");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1440, 880);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        // Closing the window with unsaved changes asks for a second close first
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                return editor.requestClose();
            }
        });
        return configuration;
    }
}
