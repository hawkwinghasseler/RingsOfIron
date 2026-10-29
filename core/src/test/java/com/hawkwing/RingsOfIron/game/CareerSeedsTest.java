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
 * Round 3's career ({@link CareerScript#playToTheGunship}) on each of the given seeds, played twice:
 * the two journals must match line for line, and the career must reach the gunship, unless the seed
 * is listed in {@link #STOPS} as stopping at a known place, in which case it must stop exactly there.
 * A listed seed that gets through fails too, so it is taken off the list when its fix lands. How the
 * careers are paced (the gunship paid for in play, the base falling to the raid) is not checked here:
 * that is the playtest benchmark {@link CareerPacingBenchmark}.
 * Round 5's career ({@link Round5CareerTest}, on to the raid) is played the same way, against
 * {@link #ROUND5_STOPS}.
 *
 * <p>Runs only when {@code career.seeds} is set, since each seed plays two careers: a tool to run by
 * hand, not part of CI. {@code ./gradlew :core:test --tests '*CareerSeedsTest' -PcareerSeeds=2026,7,12}
 * plays them ({@code --tests '*CareerSeedsTest.round5*'} plays only
 * round 5's). Each run's journal is written to {@code build/career-seed-<seed>-<run>.txt}, or
 * {@code build/round5-career-seed-<seed>-<run>.txt}.
 */
@EnabledIfSystemProperty(named = "career.seeds", matches = ".+")
class CareerSeedsTest {
    /**
     * Seeds whose career stops before the gunship at a known place: the script's step it stops in, and
     * the start of the failed check's message. None today: seed 7 (the Moon trip) came off with round
     * 6 part 2 and seed 12 (the first prize) with part 1. A seed found stopping goes here, with why,
     * until its fix lands.
     */
    static final Map<Long, Stop> STOPS = Map.of();
    /**
     * Seeds whose round 5 career stops before its end at a known place, in the same form as
     * {@link #STOPS}. None: seeds 7 and 12 came on in round 7 part 5, with parts 1 (the raid) and 3
     * (pacing) merged, and play through to the base's fall.
     */
    static final Map<Long, Stop> ROUND5_STOPS = Map.of();

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
            // Whether it was paid for in play is the playtest benchmark CareerPacingBenchmark
        } else if (first.failure == null) {
            fail("seed " + seed + " now reaches the gunship: take it off CareerSeedsTest.STOPS");
        } else if (!stop.matches(first.failure)) {
            throw new AssertionError("seed " + seed + " was expected to stop at " + stop + " but stopped elsewhere",
                    first.failure);
        } else {
            System.out.println("Seed " + seed + " stops where expected, at " + stop);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void round5CareerReplaysAndTakesTheBase(long seed) {
        Round5Run first = Round5Run.play(seed, 1), second = Round5Run.play(seed, 2);
        assertLinesEqual(first.journal, second.journal, "round 5 seed " + seed + " played twice");
        Stop stop = ROUND5_STOPS.get(seed);
        if (stop == null) {
            if (first.failure != null) throw new AssertionError("round 5 seed " + seed + " stops before its end", first.failure);
        } else if (first.failure == null) {
            fail("round 5 seed " + seed + " now plays through: take it off CareerSeedsTest.ROUND5_STOPS");
        } else if (!stop.matches(first.failure)) {
            throw new AssertionError("round 5 seed " + seed + " was expected to stop at " + stop + " but stopped elsewhere",
                    first.failure);
        } else {
            System.out.println("Round 5 seed " + seed + " stops where expected, at " + stop);
        }
    }

    /**
     * Round 5's career, with all its checks, on one seed. Private so JUnit doesn't run it as a test
     * class of its own.
     */
    private static final class Round5Run extends Round5CareerTest {
        final String name;
        Throwable failure;

        private Round5Run(long seed, int run) {
            this.seed = seed;
            this.name = "round5-career-seed-" + seed + "-" + run;
        }

        @Override
        String journalName() {
            return name;
        }

        static Round5Run play(long seed, int run) {
            Round5Run r = new Round5Run(seed, run);
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(System.getProperty("user.dir"))
                        .resolve("build").resolve(r.name + ".log"));
                r.theCareerPastTheGunship();
            } catch (AssertionError | RuntimeException | IOException e) {
                r.failure = e;
                r.log("Stopped: %s", e.getMessage());
            } finally {
                try {
                    r.tearDown();
                } catch (IOException e) {
                    if (r.failure == null) r.failure = e;
                }
            }
            return r;
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
