package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hawkwing.RingsOfIron.AudioDemo;
import com.hawkwing.RingsOfIron.CrewDemo;
import com.hawkwing.RingsOfIron.DockingDemo;
import com.hawkwing.RingsOfIron.FlightDemo;
import com.hawkwing.RingsOfIron.HudDemo;
import com.hawkwing.RingsOfIron.Main;
import com.hawkwing.RingsOfIron.OrbitDemo;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.ShipEditor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Boots the game or one demo for a fixed number of frames, then exits; any exception fails the run
 * with a non-zero exit code. Run with {@code gradlew :lwjgl3:smokeTest} (needs a display; CI uses Xvfb).
 * Usage: {@code SmokeLauncher <app> [frames]}, where app is one of {@link #APPS}.
 */
public class SmokeLauncher {
    public static final Map<String, Supplier<ApplicationListener>> APPS = new LinkedHashMap<>();
    static {
        APPS.put("game", RingsOfIronGame::new);
        APPS.put("firstplay", () -> new RingsOfIronGame(java.nio.file.Paths.get("").toAbsolutePath(),
                java.nio.file.Paths.get("mods").toAbsolutePath(), true));
        APPS.put("interior", Main::new);
        APPS.put("flight", FlightDemo::new);
        APPS.put("hud", HudDemo::new);
        APPS.put("audio", AudioDemo::new);
        APPS.put("orbit", OrbitDemo::new);
        APPS.put("crew", CrewDemo::new);
        APPS.put("docking", DockingDemo::new);
        APPS.put("editor", ShipEditor::new);
    }

    public static void main(String[] args) {
        if (args.length < 1 || !APPS.containsKey(args[0])) {
            System.err.println("Usage: SmokeLauncher <" + String.join("|", APPS.keySet()) + "> [frames]");
            System.exit(2);
        }
        String name = args[0];
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 300;

        // Background threads (the rails simulation, loaders) fail the run too
        Throwable[] background = new Throwable[1];
        Thread.setDefaultUncaughtExceptionHandler((thread, t) -> {
            System.err.println("SMOKE FAIL " + name + ": uncaught in thread " + thread.getName());
            t.printStackTrace();
            background[0] = t;
        });

        FrameCounter counter = new FrameCounter(APPS.get(name).get(), frames);
        try {
            new Lwjgl3Application(counter, configuration(name));
        } catch (Throwable t) {
            System.err.println("SMOKE FAIL " + name + " after " + counter.rendered + " frames");
            t.printStackTrace();
            System.exit(1);
        }
        if (background[0] != null) System.exit(1);
        if (counter.rendered < frames) {
            System.err.println("SMOKE FAIL " + name + ": closed after " + counter.rendered + " of " + frames + " frames");
            System.exit(1);
        }
        System.out.println("SMOKE OK " + name + " (" + counter.rendered + " frames)");
        System.exit(0);
    }

    private static Lwjgl3ApplicationConfiguration configuration(String name) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("RingsOfIron smoke - " + name);
        configuration.useVsync(false);
        configuration.setForegroundFPS(0);
        configuration.setWindowedMode(1280, 800);
        // CI machines have no sound device; demos that need audio fall back to their own backends
        configuration.disableAudio(true);
        return configuration;
    }

    /** Passes every call through to the app and exits once enough frames have rendered. */
    private static final class FrameCounter implements ApplicationListener {
        private final ApplicationListener app;
        private final int frames;
        int rendered;

        FrameCounter(ApplicationListener app, int frames) {
            this.app = app;
            this.frames = frames;
        }

        @Override public void create() { app.create(); }
        @Override public void resize(int width, int height) { app.resize(width, height); }
        @Override public void render() {
            app.render();
            if (++rendered == frames) Gdx.app.exit();
        }
        @Override public void pause() { app.pause(); }
        @Override public void resume() { app.resume(); }
        @Override public void dispose() { app.dispose(); }
    }
}
