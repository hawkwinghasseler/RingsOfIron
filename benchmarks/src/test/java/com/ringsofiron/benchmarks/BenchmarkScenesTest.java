package com.ringsofiron.benchmarks;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Keeps the benchmark scenes running: small sizes, a few frames, the profiler installed. */
class BenchmarkScenesTest {

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void busyOrbitTimesEverySystemOnTheMainThread() {
        try (BusyOrbitScene scene = new BusyOrbitScene(6, 4)) {
            TickStats stats = new TickStats();
            BenchmarkMain.ThreadsSeen threads = new BenchmarkMain.ThreadsSeen(stats);
            BenchmarkMain.install(scene.world, threads);
            for (int i = 0; i < 60; i++) {
                scene.frame();
                stats.endFrame();
            }
            BenchmarkMain.install(scene.world, null);
            assertEquals(60, stats.frames());
            assertTrue(stats.meanMs("tick/PhysicsUpdateSystem") > 0, stats.table(0));
            assertTrue(stats.meanMs("step/Box2D") > 0, stats.table(0));
            assertTrue(stats.meanMs("tick/MacroSimSystem") > 0, stats.table(0));
            assertTrue(stats.meanMs("frame/AudioUpdateSystem") > 0, stats.table(0));
            assertEquals(13, scene.world.macroSim.freighters().all().size());
            assertEquals(Thread.currentThread().getName(), threads.macroThreads());
            assertTrue(scene.walking() > 0, "crew are kept walking");
        }
    }

    @Test
    void dockedDecksStepADeckWorldPerCrewedCraft() {
        try (DockedDecksScene scene = new DockedDecksScene(2)) {
            assertEquals(2, scene.docked());
            TickStats stats = new TickStats();
            BenchmarkMain.install(scene.world, stats);
            for (int i = 0; i < 30; i++) {
                scene.frame();
                stats.endFrame();
            }
            assertEquals(3, scene.world.crew.physics.worldCount(), "the hub and both ships");
            assertTrue(stats.meanMs("crew/deck world") > 0, stats.table(0));
        }
    }

    @Test
    void statsSumEachSectionPerFrame() {
        TickStats stats = new TickStats();
        stats.record("a", 1_000_000);
        stats.record("a", 2_000_000);
        stats.endFrame();
        stats.record("b", 4_000_000);
        stats.endFrame();
        assertEquals(1.5, stats.meanMs("a"), 1e-9);
        assertEquals(3.0, stats.percentileMs("a", 100), 1e-9);
        assertEquals(0.0, stats.percentileMs("b", 50), 1e-9, "b did not run in the first frame");
        assertEquals(2.0, stats.meanMs("b"), 1e-9);
    }
}
