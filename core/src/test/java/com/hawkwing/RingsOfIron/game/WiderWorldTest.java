package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.orbital.math.KeplerOrbit;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Round 3's wider world: the new stations and who owns, guards and haunts them. */
class WiderWorldTest {
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    @Test
    void theNewStationsUseTheUnusedTypesAndBelongToTheirFactions() {
        assertFalse(game.content.report.hasErrors(), game.content.report.toString());
        Factions f = world.standing.factions();
        assertStation(game.shipyard, GameScenario.SHIPYARD_NAME, "shipyard", "patrol");
        assertStation(game.foundry, GameScenario.FOUNDRY_NAME, "foundry", "selene");
        assertStation(game.oreMine, GameScenario.ORE_MINE_NAME, "ore_mine", "selene");
        assertStation(game.freehold, GameScenario.FREEHOLD_NAME, "free_port", "traders");
        // The Free Traders fight only pirates, and adding them moved no one's crew faction number
        int traders = f.index("traders"), pirates = f.index("pirates");
        assertTrue(f.atWar(traders, pirates));
        assertFalse(f.atWar(traders, f.index("patrol")));
        assertFalse(f.atWar(traders, f.index("selene")));
        assertEquals(HostileTraffic.PIRATE_CREW, pirates);
        assertEquals(HostileTraffic.SELENE_CREW, f.index("selene"));
        assertEquals(10, world.macroSim.freighters().all().size());
    }

    @Test
    void freeholdTrailsTheMoonBySixtyDegreesForGood() {
        StateVector s = new StateVector();
        for (double t : new double[]{0, 3 * 86_400, 40 * 86_400}) {
            world.macroSim.rails().stateAt(game.freehold, t, s);
            var moon = KeplerOrbit.circular(GameScenario.MU_EARTH, GameScenario.MOON_DISTANCE,
                    GameScenario.MOON_ANGLE, 0, 1).stateAt(t);
            double apart = Math.acos((s.x * moon.x + s.y * moon.y) / (Math.hypot(s.x, s.y) * Math.hypot(moon.x, moon.y)));
            assertEquals(Math.PI / 3, apart, 1e-3, "at " + t + " s");
        }
    }

    @Test
    void patrolsGuardTheNewStationsAndPiratesLurkByThem() {
        List<String> sponsor = new ArrayList<>(), selene = new ArrayList<>();
        for (HostileComponent h : world.macroSim.hostiles().all().values()) {
            if (h.role != HostileComponent.Role.PATROL) continue;
            (HostileTraffic.SPONSOR.equals(h.faction) ? sponsor : selene).addAll(List.of(h.route));
        }
        assertTrue(sponsor.contains(game.shipyard), sponsor.toString());
        assertTrue(selene.containsAll(List.of(game.lunarMine, game.foundry, game.oreMine)), selene.toString());
    }

    private void assertStation(String uuid, String name, String type, String owner) {
        assertNotNull(uuid, name);
        EconomyNode node = world.macroSim.station(uuid);
        assertEquals(name, node.displayName);
        assertEquals(type, node.stationTypeId);
        assertEquals(owner, world.standing.factions().id(world.standing.factions().ofStation(name)));
        assertNotNull(world.engine.getEntity(uuid).getComponent(com.ringsofiron.stationsdocking.StationComponent.class),
                name + " has an interior and ports");
    }
}
