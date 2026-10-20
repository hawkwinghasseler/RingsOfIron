package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.interiorgrid.InteriorHazardWarpGuard;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round 3 played by script on a fresh seeded career, headless, across several in-game weeks: hire a
 * veteran, fight the raider for its bounty and tow it home as a prize, take an operation, plan and fly
 * a transfer to the Moon, fight an ace, and buy the gunship at the Patrol's yard. Two more tests
 * check a seed replays: the same seed gives the same start and four weeks of the same events
 * (world events, pirates' hunts and robberies, the fleet each week), another seed others, and a
 * career saved and loaded halfway goes on exactly as one that never was.
 *
 * <p>As in round 2's loop test, getting the ship near a fight is done by hand (the ship is put beside
 * the target). Since round 4's balance pass the career earns its ranks and the gunship's price in
 * play: three fights (two raiders, both towed home as prizes, and an ace) and three weeks' pay. Only a fight the player loses is made up for by the script, and the test then says
 * so instead of passing quietly. Everything else is the game's own systems. The career test writes what it saw (time, credits, standing, rank,
 * difficulty, prices) to {@code build/round3-career.txt} for the round 3 playtest notes.
 */
class Round3CareerTest extends CareerScript {
    @Override
    String journalName() {
        return "round3-career";
    }

    // ------------------------------------------------------------------ the career

    @Test
    void aSeededCareerAcrossSeveralWeeks() {
        String home = playToTheGunship();
        snapshot("end");
        log("Standing granted by the script in all: %.0f. Credits granted: %.0f. Credits from prizes: %.0f. Fights lost: %d.",
                granted, grantedCredits, prizeCredits, fightsLost);
        log("Damage control: the crew patched a leak from the stores after %s.", patchedAfter == null ? "no fight" : patchedAfter);
        log("World events seen: %d.", world.events.log().size());
        for (WorldEvents.Event e : world.events.log()) log("  day %d %s: %s", e.day, e.kind, e.text);
        assertNotNull(home);
        // Round 4's pacing: won in play, the career earns both ranks and the gunship
        if (fightsLost == 0) {
            assertEquals(0, granted, 1e-6, "standing earned in play");
            assertEquals(0, grantedCredits, 1e-6, "the gunship paid for in play");
        }
    }


    // ------------------------------------------------------------------ replay

    /** Weeks of world each replay plays through. */
    static final int REPLAY_WEEKS = 4;

    @Test
    void theSameSeedReplaysItsEventLogAndAnotherSeedDoesNot() {
        // Not seed 8: since round 5 an ace holes the docked ship there in week 4 and the crew die at the berth
        Replay a = replay(7, REPLAY_WEEKS, -1, null, false), again = replay(7, REPLAY_WEEKS, -1, null, false),
                b = replay(9, REPLAY_WEEKS, -1, null, false);
        assertEquals(a.start, again.start, "the same seed, the same start: stations, traffic, boards and hires");
        assertLinesEqual(a.events, again.events, "the same seed, the same events, day by day");
        assertNotEquals(a.start, b.start, "another seed, another start");
        assertNotEquals(a.events, b.events, "and other events");
        assertTrue(a.events.size() > REPLAY_WEEKS && b.events.size() > REPLAY_WEEKS,
                "weeks bring events: " + a.events.size() + " and " + b.events.size());
        assertTrue(a.events.stream().anyMatch(l -> l.contains(" robs ")), "pirates rob freighters in the replay");
    }

    @Test
    void aCareerSavedAndLoadedMidwayGoesOnAsIfNeverSaved(@org.junit.jupiter.api.io.TempDir Path dir) {
        Replay straight = replay(7, REPLAY_WEEKS, REPLAY_WEEKS / 2, dir, false);
        Replay reloaded = replay(7, REPLAY_WEEKS, REPLAY_WEEKS / 2, dir, true);
        assertLinesEqual(straight.events, reloaded.events, "loaded halfway, the career goes on the same");
    }

    /** What a replay saw: its start, and every event (world events, hostile traffic, the fleet each week) in order. */
    static final class Replay {
        String start;
        final List<String> events = new ArrayList<>();
    }

    /**
     * A seeded career's start (station places, traffic, Highport's board and hiring pool) and
     * {@code weeks} of world events, hostile traffic and the fleet's state, as text, docked at
     * Highport under time warp. With {@code saveAfterWeeks} at 0 or more, the game is saved then into
     * {@code dir}, and with {@code reload} loaded into a fresh world, which plays the rest.
     */
    private Replay replay(long seed, int weeks, int saveAfterWeeks, Path dir, boolean reload) {
        Replay r = new Replay();
        start(seed);
        try {
            game.beginCareer();
            run(120);
            StringBuilder out = new StringBuilder();
            StateVector s = new StateVector();
            for (var n : world.macroSim.stations()) {
                world.macroSim.rails().stateAt(n.stationUuid, 0, s);
                out.append(n.displayName).append(String.format(Locale.ROOT, " %.3f; ", Math.atan2(s.y, s.x)));
            }
            out.append("\nfreighters ").append(world.macroSim.freighters().all().size());
            out.append("\nboard");
            for (Contract c : world.contracts.offers(game.highport)) out.append(" | ").append(c.type).append(' ').append(c.reward);
            out.append("\nhires");
            for (CrewHiring.Candidate c : world.crewHiring.pool(game.highport)) out.append(" | ").append(c.name);
            r.start = out.toString();
            listen(r);
            double[] seen = {-1};
            for (int week = 0; week < weeks; week++) {
                if (week == saveAfterWeeks) {
                    com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(dir.resolve("replay.json").toFile());
                    assertNull(game.quickSave(file));
                    if (reload) {
                        world.dispose();
                        world = new GameWorld();
                        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"));
                        game.quickLoad(file);
                        assertTrue(world.engine.getSerializationService().getLastLoadWarnings().isEmpty(),
                                String.valueOf(world.engine.getSerializationService().getLastLoadWarnings()));
                        listen(r);
                    }
                    // A loaded ship is welded back to Highport in its first frames; both runs wait as long
                    run(5);
                    assertNull(world.engine.getRailsWarpBlockedReason());
                }
                for (int day = 0; day < 7; day++) {
                    warpFor(WorldEvents.DAY);
                    for (WorldEvents.Event e : world.events.log()) {
                        if (e.at <= seen[0]) continue;
                        seen[0] = e.at;
                        r.events.add(String.format(Locale.ROOT, "day %d %.0f %s %s", e.day, e.at, e.kind, e.text));
                    }
                }
                r.events.add("week " + (week + 1) + " fleet:" + fleet());
            }
            return r;
        } finally {
            world.dispose();
            world = null;
        }
    }

    /** Writes hostile traffic's doings into {@code r} as they happen, by name. */
    private void listen(Replay r) {
        var hostiles = world.macroSim.hostiles();
        hostiles.addListener(new com.ringsofiron.macrosim.hostile.HostileTrafficController.Listener() {
            @Override
            public void intercepting(String hostile, String target, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s goes after %s", t, name(hostile), name(target)));
            }

            @Override
            public void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s robs %s of %.1f %s", t, name(hostile), name(freighter),
                        quantity, commodity));
            }

            @Override
            public void drivenOff(String hostile, String patrol, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s is driven off by %s", t, name(hostile), name(patrol)));
            }
        });
    }

    /** A ship or station by its name, so lines read the same whatever its uuid. */
    private String name(String uuid) {
        if (HostileComponent.PLAYER.equals(uuid)) return "the player";
        HostileComponent h = world.macroSim.hostiles().get(uuid);
        if (h != null && h.displayName != null) return h.displayName;
        var f = world.macroSim.freighters().get(uuid);
        if (f != null && f.displayName != null) return f.displayName;
        var n = world.macroSim.station(uuid);
        return n != null ? n.displayName : uuid;
    }

    /** Every freighter's name, state, trips, credits and cargo, in name order. */
    private String fleet() {
        java.util.TreeMap<String, String> rows = new java.util.TreeMap<>();
        for (var f : world.macroSim.freighters().all().values()) {
            rows.put(f.displayName, String.format(Locale.ROOT, "%s %d %.0f %s %.1f", f.state, f.tripsCompleted,
                    f.credits, f.cargoCommodity, f.cargoQuantity));
        }
        StringBuilder out = new StringBuilder();
        rows.forEach((k, v) -> out.append(" | ").append(k).append(' ').append(v));
        return out.toString();
    }

    /** Equal line by line, failing on the first line that differs with the lines around it. */
    private static void assertLinesEqual(List<String> expected, List<String> actual, String message) {
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                fail(message + ": line " + i + " differs\n  expected: " + expected.get(i) + "\n  actual:   " + actual.get(i)
                        + "\n  before: " + expected.subList(Math.max(0, i - 3), i));
            }
        }
        assertEquals(expected.size(), actual.size(), message + ": " + expected + "\nvs\n" + actual);
    }

}
