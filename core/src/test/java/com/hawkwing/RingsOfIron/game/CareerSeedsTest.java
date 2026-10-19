package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round 3's career ({@link CareerScript#playToTheGunship}) on each of CI's career seeds, played twice:
 * the two journals must match line for line, and the career must reach the gunship, unless the seed
 * is listed in {@link #STOPS} as stopping at a known place, in which case it must stop exactly there.
 * A listed seed that gets through fails too, so it is taken off the list when its fix lands.
 *
 * <p>Runs only when {@code career.seeds} is set, since each seed plays two careers: CI's "Career
 * seed" jobs run one seed each, and {@code ./gradlew :core:test --tests '*CareerSeedsTest'
 * -PcareerSeeds=2026,7,12} plays them locally. Each run's journal is written to
 * {@code build/career-seed-<seed>-<run>.txt}.
 */
@EnabledIfSystemProperty(named = "career.seeds", matches = ".+")
class CareerSeedsTest {
    /**
     * Seeds whose career stops before the gunship today (seed 7 came off with round 6 part 2): the script's step it stops in, and the start of
     * the failed check's message. Each comes off when the round 6 part that fixes it is merged here.
     */
    static final Map<Long, Stop> STOPS = Map.of(
            // The ship vents through a breach nobody seals and the captain goes down, so the prize can't
            // be boarded (round 6 part 1, sealing breaches and saving the captain)
            12L, new Stop("fightTheRaiderAndTowItHome", "taken"));

    static final class Stop {
        final String step, message;

        Stop(String step, String message) {
            this.step = step;
            this.message = message;
        }

        boolean matches(Throwable t) {
            if (t.getMessage() == null || !t.getMessage().startsWith(message)) return false;
            return Arrays.stream(t.getStackTrace()).anyMatch(f -> f.getMethodName().equals(step));
        }

        @Override
        public String toString() {
            return step + ": \"" + message + "\"";
        }
    }

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    static List<Long> seeds() {
        List<Long> seeds = new ArrayList<>();
        for (String s : System.getProperty("career.seeds", "").split("[,\\s]+")) {
            if (!s.isBlank()) seeds.add(Long.parseLong(s.trim()));
        }
        return seeds;
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void theCareerReplaysAndReachesTheGunship(long seed) throws IOException {
        Run first = Run.play(seed, 1), second = Run.play(seed, 2);
        assertLinesEqual(first.journal, second.journal, "seed " + seed + " played twice");
        Stop stop = STOPS.get(seed);
        if (stop == null) {
            if (first.failure != null) throw new AssertionError("seed " + seed + " stops before the gunship", first.failure);
            assertNotNull(first.home, "seed " + seed + " reaches the gunship");
        } else if (first.failure == null) {
            fail("seed " + seed + " now reaches the gunship: take it off CareerSeedsTest.STOPS");
        } else if (!stop.matches(first.failure)) {
            throw new AssertionError("seed " + seed + " was expected to stop at " + stop + " but stopped elsewhere",
                    first.failure);
        } else {
            System.out.println("Seed " + seed + " stops where expected, at " + stop);
        }
    }

    /** One career played to the gunship, or to where it stopped. */
    static final class Run extends CareerScript {
        final String name;
        String home;
        Throwable failure;

        private Run(long seed, int run) {
            this.seed = seed;
            this.name = "career-seed-" + seed + "-" + run;
        }

        @Override
        String journalName() {
            return name;
        }

        static Run play(long seed, int run) throws IOException {
            Run r = new Run(seed, run);
            // log() appends to build/<name>.log as it goes: start it afresh
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(System.getProperty("user.dir"))
                    .resolve("build").resolve(r.name + ".log"));
            try {
                r.home = r.playToTheGunship();
                r.snapshot("end");
            } catch (AssertionError | RuntimeException e) {
                r.failure = e;
                r.log("Stopped: %s", e.getMessage());
            } finally {
                r.tearDown();
            }
            return r;
        }
    }

    /** Equal line by line, failing on the first line that differs with the lines around it. */
    static void assertLinesEqual(List<String> expected, List<String> actual, String message) {
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                fail(message + ": line " + i + " differs\n  expected: " + expected.get(i) + "\n  actual:   " + actual.get(i)
                        + "\n  before: " + expected.subList(Math.max(0, i - 3), i));
            }
        }
        assertEquals(expected.size(), actual.size(), message + ": the journals are different lengths");
    }
}
