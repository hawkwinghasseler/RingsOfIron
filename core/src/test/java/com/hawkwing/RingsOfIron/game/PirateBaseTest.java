package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pirates' hidden base: off the map until found, growing the pirates while it stands. */
class PirateBaseTest {
    static final float FRAME = 1f / 60f;
    GameWorld world;
    GameScenario game;
    PirateBase base;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
        base = world.pirateBase;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void itIsAPirateStationOnAnEccentricOrbitThatTradesNothing() {
        assertNotNull(game.pirateBase);
        assertEquals(game.pirateBase, base.uuid());
        assertEquals(GameScenario.PIRATE_BASE_NAME, world.macroSim.station(game.pirateBase).displayName);
        assertEquals("pirates", world.standing.factions().id(world.standing.factions().ofStation(GameScenario.PIRATE_BASE_NAME)));
        var s = world.macroSim.rails().stateAt(game.pirateBase, 0, new com.ringsofiron.macrosim.rails.StateVector());
        var k = com.ringsofiron.orbital.math.KeplerOrbit.fromState(s.x, s.y, s.vx, s.vy, GameScenario.MU_EARTH, 0);
        assertEquals(GameScenario.PIRATE_BASE_PERIAPSIS, k.periapsis(), 1e3);
        assertTrue(k.e > 0.4, "eccentric: " + k.e);
        for (var o : world.macroSim.economy().orders()) {
            assertNotEquals(game.pirateBase, o.fromUuid);
            assertNotEquals(game.pirateBase, o.toUuid);
        }
        Entity station = world.engine.getEntity(game.pirateBase);
        assertNotNull(world.standing.refusal(station, game.player), "pirates clear no one to dock");
        assertTrue(world.contracts.offers(game.pirateBase).isEmpty(), "no mission board");
    }

    @Test
    void itStaysOffTheMapUntilFound() {
        assertTrue(base.standing());
        assertFalse(base.found());
        assertFalse(onMap(), "hidden");
        base.reveal("A captured log names it");
        assertTrue(base.found());
        assertTrue(onMap());
        assertTrue(world.notices.latest().text.contains(GameScenario.PIRATE_BASE_NAME));
    }

    @Test
    void comingWithinRangeFindsIt() {
        base.relink(game.highport); // a "base" right beside the player
        world.update(PirateBase.CHECK_SECONDS + FRAME);
        assertTrue(base.found());
    }

    @Test
    void whileItStandsThePiratesGrowAndGatherThere() {
        assertEquals(0, base.extraRaiders(), "nothing extra at the start, so round 2's first fights are unchanged");
        int before = raiders(), atBase = raidersAt(game.pirateBase);
        base.establish(game.pirateBase, world.macroSim.now() - 2 * PirateBase.GROWTH_DAYS * 86_400 - 1);
        assertEquals(2, base.extraRaiders());
        world.hostiles.topUpNow();
        assertEquals(before + 2, raiders());
        assertEquals(atBase + 2, raidersAt(game.pirateBase));
        base.establish(game.pirateBase, -1e9);
        assertEquals(PirateBase.MAX_EXTRA, base.extraRaiders());

        base.fall("raided");
        assertFalse(base.standing());
        assertEquals(0, base.extraRaiders(), "no more gather once it has fallen");
        assertTrue(base.found());
    }

    @Test
    void itFallsWhenNoPirateIsLeftAliveAboard() {
        Entity station = world.engine.getEntity(game.pirateBase);
        base.look(true);
        assertTrue(base.standing(), "its crew are aboard");
        int killed = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship != station) continue;
            e.getComponent(CrewMemberComponent.class).state = com.ringsofiron.aicrew.CrewState.DEAD;
            killed++;
        }
        assertTrue(killed > 0, "the base has a crew");
        base.look(false);
        assertTrue(base.standing(), "out of sight, the base is left alone");
        base.look(true);
        assertFalse(base.standing());
    }

    @Test
    void aSaveKeepsWhetherItWasFoundAndWhetherItStands(@TempDir Path dir) {
        base.reveal(null);
        base.fall(null);
        FileHandle file = new FileHandle(dir.resolve("base.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
            loaded.quickLoad(file);
            assertNotNull(loaded.pirateBase);
            assertEquals(loaded.pirateBase, fresh.pirateBase.uuid());
            assertTrue(fresh.pirateBase.found());
            assertFalse(fresh.pirateBase.standing());
            assertEquals(loaded.pirateBase, fresh.hostiles.extraLurk);
        } finally {
            fresh.dispose();
        }
    }

    private boolean onMap() {
        TrafficMapProvider.TrafficFrame frame = new TrafficMapProvider.TrafficFrame();
        new RailsTrafficMap(world).collect(frame);
        for (TrafficMapProvider.Contact c : frame.contacts) if (GameScenario.PIRATE_BASE_NAME.equals(c.name)) return true;
        return false;
    }

    private int raiders() {
        int n = 0;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (h.isPirate() && HostileTraffic.RAIDER.equals(h.shipId)) n++;
        return n;
    }

    private int raidersAt(String lurk) {
        int n = 0;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (h.isPirate() && lurk.equals(h.stationUuid)) n++;
        return n;
    }
}
