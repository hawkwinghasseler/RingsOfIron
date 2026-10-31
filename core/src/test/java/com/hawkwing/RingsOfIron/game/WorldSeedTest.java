package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A new game's seed: the same seed gives the same start, another seed another world. */
class WorldSeedTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void theSameSeedGivesTheSameStartAndAnotherSeedAnotherWorld() {
        String a = start(NewGame.seeded(42, null)), again = start(NewGame.seeded(42, null)), b = start(NewGame.seeded(43, null));
        assertEquals(a, again);
        assertNotEquals(a, b);
    }

    @Test
    void theDesignedStartIsTheWorldAsLaidOut() {
        assertEquals(start(NewGame.designed()), start(NewGame.designed()));
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
            StateVector s = new StateVector();
            world.macroSim.rails().stateAt(game.depot, 0, s);
            assertEquals(2.5, Math.atan2(s.y, s.x), 1e-6, "the depot where round 2 put it");
            assertEquals(10, world.macroSim.freighters().all().size());
            assertFalse(game.newGame().seeded);
        } finally {
            world.dispose();
        }
    }

    @Test
    void seededWorldsFlyTenToFourteenFreighters() {
        int least = Integer.MAX_VALUE, most = 0;
        for (long seed = 1; seed <= 6; seed++) {
            GameWorld world = new GameWorld();
            try {
                GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"), NewGame.seeded(seed, null));
                int n = world.macroSim.freighters().all().size();
                least = Math.min(least, n);
                most = Math.max(most, n);
            } finally {
                world.dispose();
            }
        }
        assertTrue(least >= 10 && most <= 14, least + " to " + most);
        assertTrue(most > least, "the count varies by seed");
    }

    @Test
    void everySeededWorldsStationsAreWithinAFreightersReach() {
        for (long seed = 11; seed <= 14; seed++) {
            GameWorld world = new GameWorld();
            try {
                GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"), NewGame.seeded(seed, null));
                var rails = world.macroSim.rails();
                double t = 1_000;
                for (var from : world.macroSim.stations()) {
                    for (var to : world.macroSim.stations()) {
                        if (from == to) continue;
                        StateVector s = rails.stateAt(from.stationUuid, t, new StateVector());
                        var route = com.ringsofiron.macrosim.fleet.RoutePlanner.estimate(rails, rails.parentOf(from.stationUuid),
                                s, to.stationUuid, t, t + 600);
                        assertNotNull(route, "seed " + seed + ": " + from.displayName + " to " + to.displayName);
                        assertTrue(route.arrivalTime - t < com.ringsofiron.macrosim.fleet.FreighterController.MAX_TRIP_SECONDS,
                                "seed " + seed + ": " + from.displayName + " to " + to.displayName);
                    }
                }
            } finally {
                world.dispose();
            }
        }
    }

    @Test
    void aSaveKeepsItsSeedAndItsPiratesLurkWhereTheyDid(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("seeded.json").toFile());
        List<String> lurks;
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"),
                    NewGame.seeded(7, null));
            lurks = names(world, game);
            assertNull(game.quickSave(file));
        } finally {
            world.dispose();
        }
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
            loaded.quickLoad(file);
            assertTrue(loaded.newGame().seeded);
            assertEquals(7, loaded.newGame().seed);
            assertEquals(lurks, names(fresh, loaded));
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void typedSeedsReadAsNumbersOrWords() {
        assertEquals(1234, NewGame.fromText(" 1234 ", null).seed);
        assertEquals(NewGame.fromText("Kestrel", null).seed, NewGame.fromText("kestrel", null).seed);
        assertTrue(NewGame.fromText("", null).seeded, "nothing typed rolls a new world");
        assertEquals("Seed 99", NewGame.seeded(99, null).describe());
    }

    /** The lurks' station names, in turn. */
    private static List<String> names(GameWorld world, GameScenario game) {
        List<String> out = new ArrayList<>();
        for (String uuid : game.lurksForTest()) out.add(world.macroSim.station(uuid).displayName);
        return out;
    }

    /** Everything a seed sets, by name: station places and stock, freighters and their credits, pirates' lurks. */
    private static String start(NewGame newGame) {
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"), newGame);
            TreeMap<String, String> out = new TreeMap<>();
            StateVector s = new StateVector();
            for (var node : world.macroSim.stations()) {
                world.macroSim.rails().stateAt(node.stationUuid, 0, s);
                out.put("station " + node.displayName, String.format(Locale.ROOT, "%.0f %.0f %s", s.x, s.y, new TreeMap<>(node.inventory)));
            }
            for (FreighterComponent f : world.macroSim.freighters().all().values()) {
                out.merge("freighter " + f.displayName, String.format(Locale.ROOT, "%.0f at %s", f.credits,
                        world.macroSim.station(f.dockedAtUuid).displayName), (x, y) -> x + "; " + y);
            }
            for (HostileComponent h : world.macroSim.hostiles().all().values()) {
                if (h.isPirate()) out.put("pirate " + h.displayName, world.macroSim.station(h.stationUuid) == null
                        ? String.valueOf(h.state) : world.macroSim.station(h.stationUuid).displayName);
            }
            out.put("lurks", names(world, game).toString());
            return out.toString();
        } finally {
            world.dispose();
        }
    }
}
