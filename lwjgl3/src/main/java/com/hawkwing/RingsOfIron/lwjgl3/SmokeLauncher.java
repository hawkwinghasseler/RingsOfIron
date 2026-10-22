package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hawkwing.RingsOfIron.CrewDemo;
import com.hawkwing.RingsOfIron.DockingDemo;
import com.hawkwing.RingsOfIron.FlightDemo;
import com.hawkwing.RingsOfIron.HudDemo;
import com.hawkwing.RingsOfIron.InteriorDemo;
import com.hawkwing.RingsOfIron.OrbitDemo;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.ShipEditor;
import com.hawkwing.RingsOfIron.shell.GameShell;

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
        APPS.put("interior", InteriorScript::new);
        APPS.put("flight", FlightDemo::new);
        APPS.put("hud", HudDemo::new);
        APPS.put("audio", AudioScript::new);
        APPS.put("orbit", OrbitDemo::new);
        APPS.put("crew", CrewDemo::new);
        APPS.put("docking", DockingDemo::new);
        APPS.put("editor", ShipEditor::new);
        APPS.put("shell", ShellScript::new);
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
        // CI machines have no sound device, so audio is off unless smoke.audio is set; then OpenAL
        // Soft needs an output, such as its wave writer (see the audioSmokeTest tasks)
        configuration.disableAudio(!Boolean.getBoolean("smoke.audio"));
        return configuration;
    }

    /**
     * The main menu driven through a session: new game, pause, save to a slot, quit to the menu,
     * continue from that save, pause and resume. Saves go to a temporary folder. Any step that
     * fails throws, which fails the run.
     */
    private static final class ShellScript implements ApplicationListener {
        private final GameShell shell;
        private int frame;

        ShellScript() {
            try {
                java.io.File dir = java.nio.file.Files.createTempDirectory("ringsofiron-smoke-saves").toFile();
                dir.deleteOnExit();
                shell = new GameShell(java.nio.file.Paths.get("").toAbsolutePath(),
                        java.nio.file.Paths.get("mods").toAbsolutePath(), new com.badlogic.gdx.files.FileHandle(dir));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override public void create() { shell.create(); }
        @Override public void resize(int width, int height) { shell.resize(width, height); }
        @Override public void render() {
            switch (frame++) {
                case 10: shell.openNewGame(); break;
                // A seeded career in the Combine's service, starting above the Moon
                case 20: shell.newGame(com.hawkwing.RingsOfIron.game.NewGame.seeded(7, "selene")); break;
                case 100: check(shell.isPlaying(), "new game started"); shell.openPauseMenu(); break;
                case 110: {
                    String failed = shell.saveGame("Smoke slot");
                    check(failed == null, "saved: " + failed);
                    break;
                }
                case 120: shell.quitToMenu(); break;
                case 140: check(!shell.isPlaying(), "back at the menu"); check(shell.continueGame(), "a save to continue"); break;
                case 220: check(shell.isPlaying() && !shell.isPaused(), "continued the save"); shell.openPauseMenu(); break;
                case 230: shell.openSettings(); break;
                case 240: shell.resumeGame(); break;
                case 260: check(shell.getGame().getHud().getCrewWindow().setOpen(true), "the crew window opens"); break;
                case 280: shell.getGame().getHud().getCrewWindow().setOpen(false); break;
                default: break;
            }
            shell.render();
            // With -Dsmoke.shots=<dir>: the main menu, the new game panel, the HUD in flight, the pause menu, the settings and the crew window
            String shots = System.getProperty("smoke.shots");
            if (shots != null && (frame == 5 || frame == 15 || frame == 99 || frame == 105 || frame == 235 || frame == 275)) {
                int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
                // Flipped to top-down rows, and opaque: what is on screen, whatever alpha the frame buffer kept
                byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                for (int i = 3; i < pixels.length; i += 4) pixels[i] = (byte) 255;
                com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
                com.badlogic.gdx.utils.BufferUtils.copy(pixels, 0, p.getPixels(), pixels.length);
                com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(shots + "/shell-" + frame + ".png"), p);
                p.dispose();
            }
        }
        @Override public void pause() { shell.pause(); }
        @Override public void resume() { shell.resume(); }
        @Override public void dispose() { shell.dispose(); }

        private static void check(boolean ok, String what) {
            if (!ok) throw new IllegalStateException("Shell smoke step failed: " + what);
        }
    }

    /**
     * The interior demo with its tools at work: an attacker set on the ship, a crew member critically
     * wounded and hit again, a railgun round, a ram, a blast and a laser, a medic spawned, then the next
     * hull. Any tool that throws fails the run. With -Dsmoke.shots=<dir> it saves a screenshot after the
     * first hits and one on the next hull.
     */
    private static final class InteriorScript implements ApplicationListener {
        private final InteriorDemo demo = new InteriorDemo();
        private int frame;

        @Override public void create() { demo.create(); }
        @Override public void resize(int width, int height) { demo.resize(width, height); }
        @Override public void render() {
            com.hawkwing.RingsOfIron.game.InteriorLab lab = demo.lab();
            com.badlogic.ashley.core.Entity ship = lab.ship;
            com.ringsofiron.interiorgrid.ShipGridComponent grid = ship.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class);
            int mid = grid.width / 2;
            switch (frame++) {
                case 5: lab.attack("corvette_raider", 90f); break;
                case 10: {
                    com.badlogic.ashley.core.Entity hurt = lab.debug.crewAboard(ship).get(2);
                    lab.debug.hurt(hurt, 100f, com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.SPALL);
                    demo.select(hurt);
                    break;
                }
                case 15: lab.debug.fire(ship, "railgun_mk1", mid, grid.height - 1); break;
                case 20: lab.debug.ram(ship, mid - 2, 0); break;
                case 25: lab.debug.blast(ship, "frag_light", mid + 1, grid.height / 2); break;
                case 30: lab.debug.fire(ship, "laser_mk1", mid + 3, grid.height - 1); break;
                case 35: lab.debug.hurt(lab.debug.crewAboard(ship).get(2), 80f,
                        com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.FRAGMENT); break;
                case 40: lab.debug.spawnCrew(ship, com.ringsofiron.aicrew.CrewRole.MEDIC); break;
                case 200: lab.cycle(1); break;
                default: break;
            }
            demo.render();
            if (frame == 150 || frame == 260) shot("interior-" + frame);
        }
        @Override public void pause() { demo.pause(); }
        @Override public void resume() { demo.resume(); }
        @Override public void dispose() { demo.dispose(); }
    }

    /**
     * The game started as its audio scenario, with each of its buttons pressed: a fire, the lab vented
     * and patched, the reactor off and on, a door, a laser and a hull hit, then the next hulls (a ship,
     * and on to a station). Any button that throws fails the run. With -Dsmoke.shots=<dir> it saves a
     * screenshot aboard the demo hull and one aboard the next.
     */
    private static final class AudioScript implements ApplicationListener {
        private final RingsOfIronGame game = new RingsOfIronGame();
        private int frame;

        AudioScript() {
            game.setScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.find("audio"));
        }

        private void press(String name) {
            com.hawkwing.RingsOfIron.debug.DebugMenuActions.Action a = game.getDevConsole().menuActions.find(name);
            if (a == null) throw new IllegalStateException("No audio scenario button " + name);
            a.run.run();
        }

        @Override public void create() { game.create(); }
        @Override public void resize(int width, int height) { game.resize(width, height); }
        @Override public void render() {
            switch (frame++) {
                case 10: press("Fire"); break;
                case 20: press("Vent"); break;
                case 40: press("Door"); break;
                case 50: press("Reactor"); break;
                case 60: press("Laser outside"); break;
                case 70: press("Hull hit"); break;
                case 80: press("Reactor"); break;
                case 90: press("Vent"); break;
                case 100: press("Fire"); break;
                case 160: press("Next hull"); break;
                case 220: press("Previous hull"); press("Previous hull"); break;
                default: break;
            }
            game.render();
            if (frame == 120 || frame == 200) shot("audio-" + frame);
        }
        @Override public void pause() { game.pause(); }
        @Override public void resume() { game.resume(); }
        @Override public void dispose() { game.dispose(); }
    }

    /** With -Dsmoke.shots=<dir>, saves what is on screen there as {@code name}.png. */
    private static void shot(String name) {
        String shots = System.getProperty("smoke.shots");
        if (shots == null) return;
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        // Flipped to top-down rows, and opaque: what is on screen, whatever alpha the frame buffer kept
        byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for (int i = 3; i < pixels.length; i += 4) pixels[i] = (byte) 255;
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        com.badlogic.gdx.utils.BufferUtils.copy(pixels, 0, p.getPixels(), pixels.length);
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(shots + "/" + name + ".png"), p);
        p.dispose();
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
