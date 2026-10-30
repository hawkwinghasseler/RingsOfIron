package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.hawkwing.RingsOfIron.CrewDemo;
import com.hawkwing.RingsOfIron.DockingDemo;
import com.hawkwing.RingsOfIron.InteriorDemo;
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
        APPS.put("flight", () -> new ScenarioScript("flight", 30, "Fast pass", 60, "Fast pass", 120, "Scatter rocks",
                150, "Fast pass"));
        APPS.put("audio", () -> new ScenarioScript("audio", 10, "Fire", 20, "Vent", 40, "Door", 50, "Reactor",
                60, "Laser outside", 70, "Hull hit", 80, "Reactor", 90, "Vent", 100, "Fire", 160, "Next hull",
                220, "Previous hull", 220, "Previous hull"));
        APPS.put("orbit", () -> {
            RingsOfIronGame game = new RingsOfIronGame();
            game.setScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.find("orbit"));
            return game;
        });
        APPS.put("crew", CrewDemo::new);
        APPS.put("docking", DockingDemo::new);
        APPS.put("editor", EditorScript::new);
        APPS.put("shell", ShellScript::new);
        APPS.put("scenario-crew", () -> new ScenarioScript("crew", 150, "Tool: Start fire", 250, "Restart drill"));
        APPS.put("scenario-interior", () -> new ScenarioScript("interior", 60, "Attacker", 120, "Next hull",
                200, "Next hull", 240, "Attacker"));
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
     * continue from that save, pause and resume, then each docked panel opened and closed. The
     * continued game must hold what was saved: the same ships, crew and other saved entities. Saves
     * go to a temporary folder. Any step that fails throws, which fails the run.
     */
    private static final class ShellScript implements ApplicationListener {
        private final GameShell shell;
        private int frame;
        /** The game that was saved, and what it held when it was. */
        private RingsOfIronGame saved;
        private String savedCensus;
        private boolean loadChecked;

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
                    saved = shell.getGame();
                    savedCensus = census(saved.getWorld());
                    System.out.println("Smoke saved " + savedCensus);
                    break;
                }
                case 120: shell.quitToMenu(); break;
                case 140: check(!shell.isPlaying(), "back at the menu"); check(shell.continueGame(), "a save to continue"); break;
                case 220:
                    check(loadChecked, "the save was loaded");
                    check(shell.isPlaying() && !shell.isPaused(), "continued the save");
                    shell.openPauseMenu();
                    break;
                case 230: shell.openSettings(); break;
                case 240: shell.resumeGame(); break;
                // The seeded career starts docked at a yard, so each station panel opens; boarding
                // needs a latched ship and is left to the game's tests
                case 242: check(hud().getTradeWindow().setOpen(true), "the market opens"); break;
                case 244: hud().getTradeWindow().setOpen(false); break;
                case 246: check(hud().getYardWindow().setOpen(true), "the yard window opens"); break;
                case 248: hud().getYardWindow().setOpen(false); break;
                case 250: check(hud().getMissionWindow().setOpen(true), "the mission board opens"); break;
                case 252: hud().getMissionWindow().setOpen(false); break;
                case 254: check(hud().getShipyardWindow().setOpen(true), "the shipyard window opens"); break;
                case 256: hud().getShipyardWindow().setOpen(false); break;
                case 260: check(hud().getCrewWindow().setOpen(true), "the crew window opens"); break;
                case 280: hud().getCrewWindow().setOpen(false); break;
                case 284: hud().setMapVisible(true); check(hud().isMapOpen(), "the map opens"); break;
                case 288: hud().setMapVisible(false); check(!hud().isMapOpen(), "the map closes"); break;
                default: break;
            }
            shell.render();
            // The save is read as the continued game starts, inside that frame's render
            if (!loadChecked && saved != null && shell.getGame() != null && shell.getGame() != saved) {
                String loaded = census(shell.getGame().getWorld());
                System.out.println("Smoke loaded " + loaded);
                check(loaded.equals(savedCensus), "the loaded game holds what was saved: saved " + savedCensus
                        + ", loaded " + loaded);
                loadChecked = true;
            }
            // The main menu, the new game panel, the HUD in flight, the pause menu, the settings and the crew window
            if (frame == 5 || frame == 15 || frame == 99 || frame == 105 || frame == 235 || frame == 275) shot("shell-" + frame);
        }
        @Override public void pause() { shell.pause(); }
        @Override public void resume() { shell.resume(); }
        @Override public void dispose() { shell.dispose(); }

        private com.ringsofiron.uihud.UiHud hud() {
            return shell.getGame().getHud();
        }

        /** What a save holds, counted: entities that are saved, and of them ships and crew. */
        private static String census(com.hawkwing.RingsOfIron.game.GameWorld world) {
            com.badlogic.ashley.utils.ImmutableArray<com.badlogic.ashley.core.Entity> all = world.engine.getEcsEngine().getEntities();
            int saved = 0;
            for (com.badlogic.ashley.core.Entity e : all) {
                if (e.getComponent(com.ringsofiron.coreengine.components.TransientComponent.class) == null) saved++;
            }
            int ships = world.engine.getEcsEngine().getEntitiesFor(com.badlogic.ashley.core.Family
                    .all(com.ringsofiron.interiorgrid.ShipGridComponent.class).get()).size();
            check(saved > 0, "a game with something in it");
            return saved + " entities, " + ships + " ships, " + world.crew.crewEntities().size() + " crew";
        }

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
                case 45: {
                    // Someone in each state the deck draws: a light wound on their feet, dead, out cold
                    java.util.List<com.badlogic.ashley.core.Entity> aboard = lab.debug.crewAboard(ship);
                    lab.debug.hurt(aboard.get(0), 30f, com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.PENETRATOR);
                    lab.debug.hurt(aboard.get(1), 250f, com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.BLAST);
                    if (aboard.size() > 3) lab.debug.hurt(aboard.get(3), 0.85f * lab.world.crew.crew(aboard.get(3)).def.maxHealth,
                            com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.ASPHYXIATION);
                    break;
                }
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
     * The ship editor on a copy of the game's data: it opens the first chassis, erases the tile at
     * the middle of the view, saves with Ctrl+S through its own input, and the saved data must load
     * again without problems, with that chassis changed and every other one as it was. Any step
     * that fails throws, which fails the run.
     */
    private static final class EditorScript implements ApplicationListener {
        private final java.nio.file.Path assets;
        private final ShipEditor editor;
        private com.ringsofiron.interiorgrid.editor.EditorProject before;
        private java.util.Map<java.nio.file.Path, String> filesBefore;
        private int frame;

        EditorScript() {
            try {
                assets = java.nio.file.Files.createTempDirectory("ringsofiron-smoke-editor");
                java.nio.file.Path from = java.nio.file.Paths.get("data").toAbsolutePath();
                try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(from)) {
                    for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk::iterator) {
                        java.nio.file.Path to = assets.resolve("data").resolve(from.relativize(p).toString());
                        if (java.nio.file.Files.isDirectory(p)) java.nio.file.Files.createDirectories(to);
                        else java.nio.file.Files.copy(p, to);
                    }
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            editor = new ShipEditor(assets, null);
        }

        @Override public void create() {
            before = com.ringsofiron.interiorgrid.editor.EditorProject.load(assets, null);
            check(!before.chassis.isEmpty(), "the data has a chassis");
            check(before.problems.isEmpty(), "the data loads cleanly: " + before.problems);
            filesBefore = dataFiles();
            editor.create();
        }
        @Override public void resize(int width, int height) { editor.resize(width, height); }
        @Override public void render() {
            com.badlogic.gdx.InputProcessor input = Gdx.input.getInputProcessor();
            // The view is fitted to the chassis left of the 440-pixel panel; its middle is on the hull
            int x = (Gdx.graphics.getWidth() - 440) / 2, y = Gdx.graphics.getHeight() / 2;
            switch (frame++) {
                case 20: check(input.keyDown(com.badlogic.gdx.Input.Keys.NUM_6), "the erase tool is picked"); break;
                case 30:
                    input.mouseMoved(x, y);
                    input.touchDown(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
                    input.touchUp(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
                    break;
                case 40:
                    input.keyDown(com.badlogic.gdx.Input.Keys.CONTROL_LEFT);
                    input.keyDown(com.badlogic.gdx.Input.Keys.S);
                    input.keyUp(com.badlogic.gdx.Input.Keys.S);
                    input.keyUp(com.badlogic.gdx.Input.Keys.CONTROL_LEFT);
                    break;
                case 50: checkSaved(); break;
                default: break;
            }
            editor.render();
            if (frame == 35) shot("editor-" + frame);
        }

        /** The save changed one chassis's file, and the data loads again with only that chassis changed. */
        private void checkSaved() {
            java.util.Map<java.nio.file.Path, String> after = dataFiles();
            java.util.List<java.nio.file.Path> changed = new java.util.ArrayList<>();
            for (java.util.Map.Entry<java.nio.file.Path, String> e : after.entrySet()) {
                if (!e.getValue().equals(filesBefore.get(e.getKey()))) changed.add(e.getKey());
            }
            check(after.keySet().equals(filesBefore.keySet()), "the save wrote no new data files: " + after.keySet());
            check(changed.size() == 1, "the save changed one data file: " + changed);
            com.ringsofiron.interiorgrid.editor.EditorProject reloaded = com.ringsofiron.interiorgrid.editor.EditorProject.load(assets, null);
            check(reloaded.problems.isEmpty(), "the saved data loads cleanly: " + reloaded.problems);
            check(reloaded.chassis.keySet().equals(before.chassis.keySet()), "the same chassis load again");
            java.util.List<String> differ = new java.util.ArrayList<>();
            for (String id : before.chassis.keySet()) {
                if (!layout(before, id).equals(layout(reloaded, id))) differ.add(id);
            }
            String first = before.chassis.keySet().iterator().next();
            check(differ.equals(java.util.List.of(first)), "only " + first + " changed: " + differ);
            System.out.println("Smoke editor saved " + first + " to " + changed.get(0).getFileName() + " and loaded it again");
        }

        /** A chassis as the editor would write it. */
        private static String layout(com.ringsofiron.interiorgrid.editor.EditorProject project, String id) {
            StringBuilder out = new StringBuilder();
            for (com.ringsofiron.interiorgrid.editor.TomlSections.Entry e : com.ringsofiron.interiorgrid.editor.Defs.chassis(
                    project.chassis.get(id).value.toDef(project.catalog()))) {
                out.append(e.key).append(" = ").append(e.value).append('\n');
            }
            return out.toString();
        }

        private java.util.Map<java.nio.file.Path, String> dataFiles() {
            java.util.Map<java.nio.file.Path, String> out = new java.util.TreeMap<>();
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(assets.resolve("data"))) {
                for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk::iterator) {
                    if (java.nio.file.Files.isRegularFile(p)) out.put(p, new String(java.nio.file.Files.readAllBytes(p),
                            java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }

        @Override public void pause() { editor.pause(); }
        @Override public void resume() { editor.resume(); }
        @Override public void dispose() { editor.dispose(); }

        private static void check(boolean ok, String what) {
            if (!ok) throw new IllegalStateException("Editor smoke step failed: " + what);
        }
    }

    /**
     * The game loaded straight into a debug menu scenario, with its menu buttons pressed at the frames
     * given ({@code frame, "button", frame, "button" ...}). A button that is not there, or one that
     * throws, fails the run. With -Dsmoke.shots=<dir> it saves a screenshot every hundred frames.
     */
    private static final class ScenarioScript implements ApplicationListener {
        private final RingsOfIronGame game = new RingsOfIronGame();
        private final String id;
        private final Object[] presses;
        private int frame;

        ScenarioScript(String id, Object... presses) {
            this.id = id;
            this.presses = presses;
            com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry entry = com.hawkwing.RingsOfIron.debug.DebugScenarios.find(id);
            if (entry == null) throw new IllegalStateException("No scenario " + id);
            game.setScenario(entry);
        }

        @Override public void create() { game.create(); }
        @Override public void resize(int width, int height) { game.resize(width, height); }
        @Override public void render() {
            frame++;
            for (int i = 0; i + 1 < presses.length; i += 2) {
                if ((Integer) presses[i] != frame) continue;
                com.hawkwing.RingsOfIron.debug.DebugMenuActions.Action a = game.getDevConsole().menuActions.find((String) presses[i + 1]);
                if (a == null) throw new IllegalStateException("Scenario " + id + " has no button " + presses[i + 1]);
                a.run.run();
            }
            game.render();
            if (frame % 100 == 0) shot("scenario-" + id + "-" + frame);
        }
        @Override public void pause() { game.pause(); }
        @Override public void resume() { game.resume(); }
        @Override public void dispose() { game.dispose(); }
    }

    /**
     * The shots compared with a golden picture in -Dsmoke.golden=<dir> (lwjgl3/golden in the smoke
     * tasks): the menu, the HUD in flight, a window over the game, the deck and the editor. One that
     * differs by more than {@link #GOLDEN_TOLERANCE} fails the run.
     */
    static final java.util.Set<String> GOLDEN = java.util.Set.of("shell-5", "shell-99", "shell-275", "interior-260", "editor-35");
    /** A pixel differs when a channel is off by more than this, out of 255... */
    static final int GOLDEN_CHANNEL = 48;
    /** ...and a shot fails when more than this share of its pixels differ (fonts and stars shimmer a little). */
    static final double GOLDEN_TOLERANCE = 0.02;

    /**
     * With -Dsmoke.shots=<dir>, saves what is on screen there as {@code name}.png. A {@link #GOLDEN}
     * shot is also compared with its golden picture, or with -Dsmoke.updateGolden=true written as it.
     */
    private static void shot(String name) {
        String shots = System.getProperty("smoke.shots");
        String golden = GOLDEN.contains(name) ? System.getProperty("smoke.golden") : null;
        if (shots == null && golden == null) return;
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        // Flipped to top-down rows, and opaque: what is on screen, whatever alpha the frame buffer kept
        byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for (int i = 3; i < pixels.length; i += 4) pixels[i] = (byte) 255;
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        com.badlogic.gdx.utils.BufferUtils.copy(pixels, 0, p.getPixels(), pixels.length);
        if (shots != null) com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(shots + "/" + name + ".png"), p);
        if (golden != null) compareGolden(name, p, Gdx.files.absolute(golden + "/" + name + ".png"));
        p.dispose();
    }

    /** Fails the run when {@code shot} is too far from its golden picture; the shot and a diff go to build/smoke-golden. */
    private static void compareGolden(String name, com.badlogic.gdx.graphics.Pixmap shot, com.badlogic.gdx.files.FileHandle file) {
        if (Boolean.getBoolean("smoke.updateGolden")) {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(file, shot);
            System.out.println("Smoke golden " + name + " written to " + file.path());
            return;
        }
        if (!file.exists()) throw new IllegalStateException("No golden picture " + file.path()
                + "; write it with ./gradlew :lwjgl3:smokeTest -PupdateGolden");
        com.badlogic.gdx.graphics.Pixmap want = new com.badlogic.gdx.graphics.Pixmap(file);
        try {
            if (want.getWidth() != shot.getWidth() || want.getHeight() != shot.getHeight()) {
                throw new IllegalStateException("Golden " + name + " is " + want.getWidth() + "x" + want.getHeight()
                        + " but the shot is " + shot.getWidth() + "x" + shot.getHeight());
            }
            com.badlogic.gdx.graphics.Pixmap diff = new com.badlogic.gdx.graphics.Pixmap(shot.getWidth(), shot.getHeight(),
                    com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            int differ = 0;
            for (int y = 0; y < shot.getHeight(); y++) {
                for (int x = 0; x < shot.getWidth(); x++) {
                    int a = shot.getPixel(x, y), b = want.getPixel(x, y);
                    int d = Math.max(Math.max(Math.abs((a >>> 24) - (b >>> 24)), Math.abs((a >>> 16 & 0xff) - (b >>> 16 & 0xff))),
                            Math.abs((a >>> 8 & 0xff) - (b >>> 8 & 0xff)));
                    boolean off = d > GOLDEN_CHANNEL;
                    if (off) differ++;
                    // Off pixels red over the golden picture, dimmed
                    diff.drawPixel(x, y, off ? 0xff0000ff
                            : (b >>> 24) / 3 << 24 | (b >>> 16 & 0xff) / 3 << 16 | (b >>> 8 & 0xff) / 3 << 8 | 0xff);
                }
            }
            double share = differ / (double) (shot.getWidth() * shot.getHeight());
            System.out.printf(java.util.Locale.ROOT, "Smoke golden %s: %.2f%% of pixels differ%n", name, share * 100);
            if (share > GOLDEN_TOLERANCE) {
                com.badlogic.gdx.files.FileHandle out = Gdx.files.absolute(new java.io.File("../lwjgl3/build/smoke-golden").getAbsolutePath());
                com.badlogic.gdx.graphics.PixmapIO.writePNG(out.child(name + "-actual.png"), shot);
                com.badlogic.gdx.graphics.PixmapIO.writePNG(out.child(name + "-diff.png"), diff);
                diff.dispose();
                throw new IllegalStateException(String.format(java.util.Locale.ROOT,
                        "Shot %s differs from its golden picture in %.2f%% of pixels (at most %.2f%%); see %s. "
                                + "If the change is meant, ./gradlew :lwjgl3:smokeTest -PupdateGolden",
                        name, share * 100, GOLDEN_TOLERANCE * 100, out.path()));
            }
            diff.dispose();
        } finally {
            want.dispose();
        }
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
