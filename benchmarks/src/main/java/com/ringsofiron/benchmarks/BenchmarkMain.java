package com.ringsofiron.benchmarks;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.coreengine.TickProfiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the benchmark scenes headless and prints a Markdown report of tick time per system:
 * {@code orbit} (freighters on rails and a full bubble), {@code warp} (rails warp through it), {@code docked} (crew walking the decks
 * of ships docked at a hub), or {@code all}. Arguments: scene, then measured frames per run.
 * The report also goes to {@code benchmarks/build/reports/benchmark.md}.
 */
public final class BenchmarkMain {
    /** Frames run before measuring, so the JIT has compiled the hot paths. */
    static final int WARMUP_FRAMES = 600;
    /** Sections faster than this on average (and never ten times slower) are left out of the tables. */
    static final double MIN_MEAN_MS = 0.01;
    /** Rails warp level for the warp scene. */
    static final int WARP = 10_000;

    public static void main(String[] args) throws IOException {
        String scene = args.length > 0 ? args[0] : "all";
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 1200;
        GdxNativesLoader.load();
        StringBuilder report = new StringBuilder("# Rings of Iron benchmark\n\n");
        report.append(String.format(Locale.ROOT, "%d warm-up frames, then %d measured frames at 60 Hz. JVM %s, %d cores.%n%n",
                WARMUP_FRAMES, frames, System.getProperty("java.version"), Runtime.getRuntime().availableProcessors()));
        if (scene.equals("all") || scene.equals("orbit")) {
            int[][] sizes = {{0, 0}, {100, 10}, {500, 30}, {2000, 30}};
            for (int[] s : sizes) report.append(orbit(s[0], s[1], frames));
        }
        if (scene.equals("all") || scene.equals("warp")) {
            for (int n : new int[]{0, 500, 2000}) report.append(warp(n, frames));
        }
        if (scene.equals("all") || scene.equals("docked")) {
            for (int ships : new int[]{0, 1, 2, 3, 4}) report.append(docked(ships, frames));
        }
        System.out.println(report);
        Path out = Paths.get(System.getProperty("user.dir"), "build", "reports", "benchmark.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Report written to " + out);
    }

    static String orbit(int freighters, int bubbleShips, int frames) {
        try (BusyOrbitScene scene = new BusyOrbitScene(freighters, bubbleShips)) {
            TickStats stats = new TickStats();
            ThreadsSeen threads = new ThreadsSeen(stats);
            Runnable frame = scene::frame;
            run(scene.world, threads, stats, frame, frames);
            return String.format(Locale.ROOT, "## Busy orbit: %d extra freighters on rails, %d extra ships in the bubble%n%n"
                            + "%d freighters in all, %d crew walking at the end. Macro-sim ran on: %s.%n%n%s%n",
                    freighters, bubbleShips, scene.world.macroSim.freighters().all().size(), scene.walking(),
                    threads.macroThreads(), stats.table(MIN_MEAN_MS));
        }
    }

    /**
     * Rails warp at 10,000x through the busy orbit: each frame is one rails jump of about three
     * minutes, with the economy, the freighters and encounter prediction catching up in it.
     */
    static String warp(int freighters, int frames) {
        try (BusyOrbitScene scene = new BusyOrbitScene(freighters, 0, false)) {
            TickStats stats = new TickStats();
            ThreadsSeen threads = new ThreadsSeen(stats);
            for (int i = 0; i < WARMUP_FRAMES; i++) scene.frame();
            install(scene.world, threads);
            int dropOuts = 0, refused = 0;
            double simStart = scene.world.engine.getSimSeconds();
            for (int i = 0; i < frames; i++) {
                if (!scene.world.timeWarp.isRailsWarp() && !scene.world.timeWarp.setWarp(WARP)) refused++;
                long start = System.nanoTime();
                scene.frame();
                stats.record(TickStats.FRAME, System.nanoTime() - start);
                stats.endFrame();
                if (!scene.world.timeWarp.isRailsWarp()) dropOuts++;
            }
            install(scene.world, null);
            return String.format(Locale.ROOT, "## Rails warp at %dx: %d extra freighters%n%n"
                            + "%.1f sim days in %d frames, %d drop-outs, %d frames where warp was refused. Macro-sim ran on: %s.%n%n%s%n",
                    WARP, freighters, (scene.world.engine.getSimSeconds() - simStart) / 86_400, frames, dropOuts, refused,
                    threads.macroThreads(), stats.table(MIN_MEAN_MS));
        }
    }

    static String docked(int ships, int frames) {
        try (DockedDecksScene scene = new DockedDecksScene(ships)) {
            TickStats stats = new TickStats();
            Runnable frame = scene::frame;
            run(scene.world, stats, stats, frame, frames);
            return String.format(Locale.ROOT, "## Docked decks: %d ships docked at a hub%n%n"
                            + "%d deck worlds stepping, %d crew walking at the end.%n%n%s%n",
                    scene.docked(), scene.world.crew.physics.worldCount(), scene.walking(), stats.table(MIN_MEAN_MS));
        }
    }

    /** Warms up, then runs {@code frames} frames with {@code profiler} installed everywhere it can be. */
    static void run(GameWorld world, TickProfiler profiler, TickStats stats, Runnable frame, int frames) {
        for (int i = 0; i < WARMUP_FRAMES; i++) frame.run();
        install(world, profiler);
        stats.reset();
        for (int i = 0; i < frames; i++) {
            long start = System.nanoTime();
            frame.run();
            stats.record(TickStats.FRAME, System.nanoTime() - start);
            stats.endFrame();
        }
        install(world, null);
    }

    /** Installs {@code profiler} on the engine, the physics step, the crew deck worlds and macro-sim; null removes it. */
    public static void install(GameWorld world, TickProfiler profiler) {
        world.engine.setProfiler(profiler);
        world.physics.setProfiler(profiler);
        world.crew.physics.setProfiler(profiler);
        world.macroSim.setProfiler(profiler);
    }

    /** Passes every record on, noting which threads ran macro-sim's sections. */
    static final class ThreadsSeen implements TickProfiler {
        private final TickProfiler next;
        private final Set<String> macro = ConcurrentHashMap.newKeySet();

        ThreadsSeen(TickProfiler next) {
            this.next = next;
        }

        @Override
        public void record(String section, long nanos) {
            if (section.startsWith("macro/") || section.equals("tick/MacroSimSystem")) macro.add(Thread.currentThread().getName());
            next.record(section, nanos);
        }

        String macroThreads() {
            return String.join(", ", new TreeSet<>(macro));
        }
    }
}
