package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.career.FactionStanding;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Round 9's career sweep: round 3's career ({@link CareerScript#playToTheGunship}) and round 5's
 * ({@link Round5CareerTest}) played once each on many seeds, with no replay check, so what CI's three
 * seeds hide shows up in one table. Each career runs in a JVM of its own, several side by side, and
 * writes one row; the rows go to {@code build/career-sweep.txt}. Each career's journal is written to
 * {@code build/sweep/}, and round 5's career writes its playtest saves to
 * {@code build/playtest-saves/seed-<n>/} as on CI's seeds, so any seed can be loaded in the game.
 *
 * <p>Runs only with {@code -PcareerSweep=<seeds>}, a list of seeds and ranges:
 * {@code ./gradlew :core:test --tests '*CareerSweepTest' -PcareerSweep=1-10,2026,7,12}. A round 5
 * career runs ten to forty minutes; {@code -PsweepForks=n} sets how many run at once (the machine's
 * cores, by default). The sweep is a report: a career that stops is a row saying where and why, not a
 * failed test. It fails only if a career's JVM gave no row at all.
 */
@EnabledIfSystemProperty(named = "career.sweep", matches = ".+")
class CareerSweepTest {
    /** The pace band for the frigate, days (round 7 part 3): a frigate outside it is marked in the table. */
    static final double FRIGATE_EARLIEST = 38, FRIGATE_LATEST = 52;
    /** Longest one career may run before its JVM is stopped, minutes. */
    static final long MOST_MINUTES = Long.getLong("career.sweep.minutes", 90);

    static final String ROUND3 = "round 3", ROUND5 = "round 5";

    static Path build() {
        return Paths.get(System.getProperty("user.dir")).resolve("build");
    }

    /** {@code 1-10,2026, 7} as 1 to 10, 2026 and 7, each once, in order. */
    static List<Long> seeds(String spec) {
        Set<Long> out = new LinkedHashSet<>();
        for (String s : spec.split("[,\\s]+")) {
            if (s.isBlank()) continue;
            int dash = s.indexOf('-', 1);
            if (dash < 0) {
                out.add(Long.parseLong(s.trim()));
            } else {
                long from = Long.parseLong(s.substring(0, dash).trim()), to = Long.parseLong(s.substring(dash + 1).trim());
                for (long n = from; n <= to; n++) out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    @Test
    void sweep() throws Exception {
        List<Long> seeds = seeds(System.getProperty("career.sweep"));
        int forks = Integer.getInteger("career.sweep.forks", Runtime.getRuntime().availableProcessors());
        Path dir = build().resolve("sweep");
        Files.createDirectories(dir);
        // Round 5's careers first, being the longer; a seed's two careers never run at once, since both
        // write that seed's playtest saves
        List<Job> jobs = new ArrayList<>();
        for (long seed : seeds) jobs.add(new Job(seed, ROUND5));
        for (long seed : seeds) jobs.add(new Job(seed, ROUND3));
        List<Job> queue = new ArrayList<>(jobs);
        Set<Long> running = new java.util.HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, forks));
        long t0 = System.nanoTime();
        for (int i = 0; i < Math.max(1, forks); i++) {
            pool.submit(() -> {
                while (true) {
                    Job job = null;
                    synchronized (queue) {
                        while (job == null && !queue.isEmpty()) {
                            for (Job j : queue) if (!running.contains(j.seed)) { job = j; break; }
                            if (job == null) queue.wait();
                        }
                        if (job == null) return null;
                        queue.remove(job);
                        running.add(job.seed);
                    }
                    try {
                        job.run(dir);
                    } catch (Exception e) {
                        job.trouble = e.toString();
                    } finally {
                        synchronized (queue) {
                            running.remove(job.seed);
                            queue.notifyAll();
                        }
                    }
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(seeds.size() * 2L * MOST_MINUTES + 10, TimeUnit.MINUTES), "the sweep finished");

        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "Career sweep on %s: seeds %s, %d careers in %.0f min, %d at once.", commit(),
                System.getProperty("career.sweep"), jobs.size(), (System.nanoTime() - t0) / 60e9, forks));
        out.add("");
        out.add("| Seed | Career | Gunship (day) | Made up by the script | Ace fights | Crew dead | Player carried home | Commander | Captain | Frigate | Raid | Stopped | Minutes |");
        out.add("|---|---|---|---|---|---|---|---|---|---|---|---|---|");
        List<String> missing = new ArrayList<>();
        for (Job j : jobs) {
            if (j.row == null) missing.add(j + ": " + j.trouble);
            out.add(j.tableRow());
        }
        Files.write(build().resolve("career-sweep.txt"), out);
        out.forEach(System.out::println);
        assertTrue(missing.isEmpty(), "careers that gave no row: " + missing);
    }

    /** The commit the sweep played, with a mark if the tree had changes. */
    static String commit() {
        try {
            Process p = new ProcessBuilder("git", "describe", "--always", "--dirty", "--abbrev=8").redirectErrorStream(true).start();
            String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 ? s : "an unknown commit";
        } catch (IOException | InterruptedException e) {
            return "an unknown commit";
        }
    }

    /** One career on one seed, played in a JVM of its own. */
    static final class Job {
        final long seed;
        final String career;
        Properties row;
        String trouble = "";
        double minutes;

        Job(long seed, String career) {
            this.seed = seed;
            this.career = career;
        }

        String file() {
            return (career.equals(ROUND3) ? "round3" : "round5") + "-seed-" + seed;
        }

        void run(Path dir) throws IOException, InterruptedException {
            Path rowFile = dir.resolve(file() + ".properties");
            Files.deleteIfExists(rowFile);
            Files.deleteIfExists(dir.resolve(file() + ".log"));
            String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            List<String> cmd = new ArrayList<>(List.of(java, "-Xmx" + Runtime.getRuntime().maxMemory() / (1 << 20) + "m",
                    "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), Worker.class.getName(),
                    career.equals(ROUND3) ? "round3" : "round5", Long.toString(seed), rowFile.toString()));
            long t0 = System.nanoTime();
            Process p = new ProcessBuilder(cmd).directory(Paths.get(System.getProperty("user.dir")).toFile())
                    .redirectErrorStream(true).redirectOutput(dir.resolve(file() + ".out").toFile()).start();
            if (!p.waitFor(MOST_MINUTES, TimeUnit.MINUTES)) {
                p.destroyForcibly().waitFor();
                trouble = "stopped after " + MOST_MINUTES + " minutes";
            } else if (p.exitValue() != 0) {
                trouble = "its JVM exited with " + p.exitValue() + " (see build/sweep/" + file() + ".out)";
            }
            minutes = (System.nanoTime() - t0) / 60e9;
            if (Files.exists(rowFile)) {
                row = new Properties();
                try (Reader r = Files.newBufferedReader(rowFile, StandardCharsets.UTF_8)) {
                    row.load(r);
                }
            }
            System.out.printf(Locale.ROOT, "%s: %.1f min, %s%n", this, minutes, row == null ? trouble : row.getProperty("stopped"));
        }

        String get(String key) {
            String v = row.getProperty(key, "");
            return v.isEmpty() ? "-" : v;
        }

        String tableRow() {
            if (row == null) {
                return String.format(Locale.ROOT, "| %d | %s | | | | | | | | | | no row: %s | %.0f |", seed, career, trouble, minutes);
            }
            String frigate = get("frigate");
            if (!frigate.equals("-")) {
                double d = Double.parseDouble(frigate);
                if (d < FRIGATE_EARLIEST || d > FRIGATE_LATEST) frigate += String.format(Locale.ROOT, " (outside %.0f to %.0f)", FRIGATE_EARLIEST, FRIGATE_LATEST);
            }
            return String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %.0f |", seed, career, get("gunship"),
                    get("madeUp"), get("aces"), get("dead"), get("down"), get("commander"), get("captain"), frigate, get("raid"),
                    get("stopped") + (trouble.isEmpty() ? "" : "; " + trouble), minutes);
        }

        @Override
        public String toString() {
            return career + " seed " + seed;
        }
    }

    // ------------------------------------------------------------------ in the career's own JVM

    /** Plays one career, {@code round3 <seed> <row file>} or {@code round5 <seed> <row file>}, and writes its row. */
    static final class Worker {
        public static void main(String[] args) throws IOException {
            GdxNativesLoader.load();
            long seed = Long.parseLong(args[1]);
            Path rowFile = Paths.get(args[2]);
            Tally tally = args[0].equals("round3") ? new Round3Sweep(seed).play() : new Round5Sweep(seed).play();
            Properties row = tally.row();
            Path tmp = rowFile.resolveSibling(rowFile.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                row.store(w, null);
            }
            Files.move(tmp, rowFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // The game leaves threads behind (audio, the world's workers): end the JVM here
            System.exit(0);
        }
    }

    /** What a career saw that the table wants, gathered as it plays. */
    static final class Tally {
        final CareerScript career;
        double gunshipDay = Double.NaN, endDay = Double.NaN;
        /** The player's crew who died, in order: name, day, cause and where the ship was. */
        final List<String> dead = new ArrayList<>();
        final Set<String> deadNames = new LinkedHashSet<>();
        Throwable failure;
        String lastSnapshot = "";

        Tally(CareerScript career) {
            this.career = career;
        }

        /** Each time the player went down and the crew carried them home: the day, the cause and where. */
        final List<String> downs = new ArrayList<>();
        /** The player's avatar as it lay at the end of the last update it was found down in. */
        String avatarDown = "";

        /** Watches for the player's crew dying, by CrewCareer's notice, and reads the cause off the body. */
        void watch(GameWorld world) {
            // The avatar is revived in the same update it is found dead in: read it as the systems leave it
            world.engine.getEcsEngine().addSystem(new com.badlogic.ashley.core.EntitySystem(Integer.MAX_VALUE) {
                @Override
                public void update(float deltaTime) {
                    for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                        if (e.getComponent(PlayerAvatarComponent.class) == null) continue;
                        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                        if (c.state == CrewState.DEAD) avatarDown = (c.lastDamageCause == null ? "no damage logged"
                                : c.lastDamageCause.name().toLowerCase(Locale.ROOT)) + ", " + where(e, c);
                    }
                }
            });
            world.carriedHome.add((sig, t) -> downs.add(String.format(Locale.ROOT, "day %.2f, %s", world.engine.getSimSeconds() / 86_400, avatarDown)));
            world.notices.posted.add((sig, n) -> {
                String name = n.text.endsWith(" has died of wounds") ? n.text.substring(0, n.text.length() - 19)
                        : n.text.endsWith(" is dead") ? n.text.substring(0, n.text.length() - 8) : null;
                if (name == null || !deadNames.add(name)) return;
                String cause = "cause not known";
                for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                    if (!name.equals(c.name) || c.state != CrewState.DEAD || !playerCrew(e, c)) continue;
                    cause = (c.lastDamageCause == null ? "no damage logged" : c.lastDamageCause.name().toLowerCase(Locale.ROOT))
                            + (c.woundCause == null ? "" : ", wound " + c.woundCause.name().toLowerCase(Locale.ROOT) + " " + c.wound.name().toLowerCase(Locale.ROOT));
                    cause += ", " + where(e, c);
                }
                Entity at = world.docking.dockedTo(career.game.player);
                dead.add(String.format(Locale.ROOT, "%s, day %.2f, %s, %s", name, world.engine.getSimSeconds() / 86_400, cause,
                        at == null ? "undocked" : "docked at " + ShipNames.name(at)));
            });
        }

        /**
         * Counts the dead bodies of the player's crew still in the world at the end, for any death
         * the notices missed (a hand who died aboard a ship the player had already left, say).
         */
        void countTheRoster(GameWorld world) {
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                if (c.state == CrewState.DEAD && playerCrew(e, c) && deadNames.add(c.name)) {
                    dead.add(c.name + ", found dead at the end, " + (c.lastDamageCause == null ? "no damage logged"
                            : c.lastDamageCause.name().toLowerCase(Locale.ROOT)) + ", no notice");
                }
            }
        }

        /** Where a body lies: the ship, the tile, that tile's air, the suit and what they were doing. */
        static String where(Entity e, CrewMemberComponent c) {
            com.ringsofiron.aicrew.GridPositionComponent g = e.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class);
            if (g == null || g.ship == null) return "on no ship";
            com.ringsofiron.moddingdata.components.DefRefComponent def = g.ship.getComponent(com.ringsofiron.moddingdata.components.DefRefComponent.class);
            com.ringsofiron.interiorgrid.ShipGridComponent grid = g.ship.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class);
            String air = "";
            if (grid != null) {
                com.ringsofiron.interiorgrid.Tile t = grid.getTile((int) g.x, (int) g.y);
                com.ringsofiron.interiorgrid.Compartment room = t == null ? null : t.compartment;
                air = room == null ? ", no room" : String.format(Locale.ROOT, ", room %d at %.1f kPa, O2 %.1f kPa", room.id,
                        room.pressure(), room.oxygenPressure());
            }
            return String.format(Locale.ROOT, "in the %s at (%.0f, %.0f)%s, %s, \"%s\"", def == null ? "?" : def.defId, g.x, g.y, air,
                    c.suited ? String.format(Locale.ROOT, "suited (%.0f s of air)", c.suitAir) : "unsuited", c.activity);
        }

        static boolean playerCrew(Entity e, CrewMemberComponent c) {
            return c.faction == FactionStanding.PLAYER && !c.held && e.getComponent(PlayerAvatarComponent.class) == null;
        }

        /** Where the career stopped: the script's steps the failed check was in, innermost first, and the message. */
        String stopped() {
            if (failure == null) return "played through";
            List<String> steps = new ArrayList<>();
            for (StackTraceElement f : failure.getStackTrace()) {
                String cls = f.getClassName();
                if (!(cls.endsWith(".CareerScript") || cls.endsWith(".Round5CareerTest") || cls.endsWith(".RaidRoutine"))) continue;
                String step = f.getMethodName().startsWith("lambda$") ? null : f.getMethodName();
                if (step != null && !steps.contains(step)) steps.add(step);
                if (steps.size() == 3) break;
            }
            String message = String.valueOf(failure.getMessage()).replace('\n', ' ').replace('|', '/');
            if (message.length() > 220) message = message.substring(0, 220) + "...";
            return String.format(Locale.ROOT, "day %.1f, in %s, after [%s]: \"%s\"", endDay, String.join(" < ", steps),
                    lastSnapshot, message);
        }

        Properties row() {
            Properties p = new Properties();
            p.setProperty("gunship", Double.isNaN(gunshipDay) ? "" : String.format(Locale.ROOT, "%.1f", gunshipDay));
            p.setProperty("madeUp", career.madeUp.isEmpty() ? "none" : String.join("; ", career.madeUp));
            p.setProperty("aces", String.join("; ", career.aceFights));
            p.setProperty("dead", dead.isEmpty() ? "0" : dead.size() + ": " + String.join("; ", dead));
            p.setProperty("down", downs.isEmpty() ? "0" : downs.size() + ": " + String.join("; ", downs));
            p.setProperty("stopped", stopped());
            if (career instanceof Round5Sweep) {
                Round5Sweep r5 = (Round5Sweep) career;
                p.setProperty("commander", r5.milestone("Commander"));
                p.setProperty("captain", r5.milestone("Captain"));
                p.setProperty("frigate", r5.milestone("Frigate"));
                p.setProperty("raid", raid(career.journal));
            }
            return p;
        }

        static final Pattern TRY = Pattern.compile("^  try (\\d+): (\\S+) in (\\d+) s.*?our hull ([\\d.]+) -> ([\\d.]+).*?\\[(.*), difficulty ([\\d.]+)\\]");

        /** The raid's tries from the journal: each one's end, seconds and hull, and whether the defence drew a railgun. */
        static String raid(List<String> journal) {
            List<String> tries = new ArrayList<>();
            boolean railgun = false;
            for (String line : journal) {
                Matcher m = TRY.matcher(line);
                if (!m.find()) continue;
                tries.add(String.format(Locale.ROOT, "%s %s s, hull %s to %s", m.group(2), m.group(3), m.group(4), m.group(5)));
                if (m.group(6).contains("railgun")) railgun = true;
            }
            if (tries.isEmpty()) return "not flown";
            return tries.size() + " tr" + (tries.size() == 1 ? "y" : "ies") + ": " + String.join("; ", tries)
                    + (railgun ? "; drew a railgun" : "; no railgun");
        }
    }

    /** Round 3's career, as CI's career seeds play it ({@link CareerSeedsTest.Run}). */
    private static final class Round3Sweep extends CareerScript {
        final Tally tally = new Tally(this);

        Round3Sweep(long seed) {
            this.seed = seed;
        }

        @Override
        String journalName() {
            return "sweep/round3-seed-" + seed;
        }

        @Override
        void start(long seed) {
            super.start(seed);
            tally.watch(world);
        }

        @Override
        void snapshot(String when) {
            super.snapshot(when);
            tally.lastSnapshot = when;
        }

        Tally play() throws IOException {
            try {
                playToTheGunship();
                tally.gunshipDay = world.engine.getSimSeconds() / 86_400;
                snapshot("end");
            } catch (AssertionError | RuntimeException e) {
                tally.failure = e;
                log("Stopped: %s", e.getMessage());
                e.printStackTrace(System.out);
            } finally {
                if (world != null) {
                    tally.endDay = world.engine.getSimSeconds() / 86_400;
                    tally.countTheRoster(world);
                }
                tearDown();
            }
            return tally;
        }
    }

    /** Round 5's career, with all its checks. Private so JUnit doesn't run it as a test class of its own. */
    private static final class Round5Sweep extends Round5CareerTest {
        final Tally tally = new Tally(this);

        Round5Sweep(long seed) {
            this.seed = seed;
        }

        @Override
        String journalName() {
            return "sweep/round5-seed-" + seed;
        }

        @Override
        void start(long seed) {
            super.start(seed);
            tally.watch(world);
        }

        @Override
        void snapshot(String when) {
            super.snapshot(when);
            tally.lastSnapshot = when;
        }

        @Override
        String playToTheGunship() {
            String home = super.playToTheGunship();
            tally.gunshipDay = world.engine.getSimSeconds() / 86_400;
            return home;
        }

        /** The day of the milestone row {@code name}, or empty if the career didn't reach it. */
        String milestone(String name) {
            for (String r : rows) if (r.startsWith("| " + name + " | ")) return r.split("\\|")[2].trim();
            return "";
        }

        Tally play() throws IOException {
            try {
                theCareerPastTheGunship();
            } catch (AssertionError | RuntimeException e) {
                tally.failure = e;
                log("Stopped: %s", e.getMessage());
                e.printStackTrace(System.out);
            } finally {
                if (world != null) {
                    tally.endDay = world.engine.getSimSeconds() / 86_400;
                    tally.countTheRoster(world);
                }
                tearDown();
            }
            return tally;
        }
    }
}
