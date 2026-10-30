package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The orbit map, the burn planner and crashes, in the starting scenario. */
class BurnsAndCrashesTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        for (int i = 0; i < 10; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    OrbitInfoProvider.OrbitInfo orbit() {
        return new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer).getPlayerOrbit();
    }

    @Test
    void theMapShowsTheOrbitNowAndAfterTheBurn() {
        PlayerOrbitPath map = new PlayerOrbitPath(world, world.burns);
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(map.getPlayerPath(path));
        assertEquals("Earth", path.bodyName);
        assertEquals(2 * (PlayerOrbitPath.SEGMENTS + 1), path.current.size);
        for (int i = 0; i < path.current.size; i += 2) {
            assertEquals(GameScenario.ORBIT_RADIUS, Math.hypot(path.current.get(i), path.current.get(i + 1)), 2_000);
        }
        assertEquals(0, path.planned.size, "nothing planned");

        world.burns.addNode();
        world.burns.adjust(100, 0);
        path.clear();
        assertTrue(map.getPlayerPath(path));
        assertEquals(2, path.nodes.size);
        assertEquals(0, path.selectedNode);
        float farthest = 0;
        for (int i = 0; i < path.planned.size; i += 2) {
            farthest = Math.max(farthest, (float) Math.hypot(path.planned.get(i), path.planned.get(i + 1)));
        }
        assertTrue(farthest > GameScenario.ORBIT_RADIUS + 300e3, "a 100 m/s prograde burn raises the far side");
        assertTrue(path.caption.toString().contains("Pro +100.0"), path.caption.toString());
    }

    @Test
    void plannedBurnsStayInTimeOrder() {
        ManeuverNodeComponent.Node first = world.burns.addNode();
        ManeuverNodeComponent.Node second = world.burns.addNode();
        assertTrue(second.time > first.time);
        assertSame(second, world.burns.selected());
        world.burns.shiftTime(-1000);
        assertEquals(world.universe.now() + BurnPlanner.MIN_LEAD, second.time, 1e-6, "never in the past");
        assertSame(second, world.burns.nodes().next(), "moved ahead of the first");
        world.burns.deleteSelected();
        assertSame(first, world.burns.selected());
    }

    @Test
    void aBurnFiresInTheBubble() {
        ManeuverNodeComponent.Node n = world.burns.addNode();
        world.burns.adjust(50, 0);
        n.time = world.universe.now() + 0.5;
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertNull(world.burns.selected(), "fired");
        assertNotNull(game.player.getComponent(PhysicsBodyComponent.class), "still flying in the bubble");
        assertTrue(orbit().apoapsis > 400e3 + 150e3, "apoapsis " + orbit().apoapsis);
    }

    @Test
    void aBurnFiresOnRailsAndWarpStopsThere() {
        ManeuverNodeComponent.Node n = world.burns.addNode();
        world.burns.adjust(50, 0);
        double due = n.time;
        assertTrue(world.timeWarp.setWarp(1_000), world.engine.getRailsWarpBlockedReason());
        for (int i = 0; i < 600 && world.timeWarp.isRailsWarp(); i++) world.update(FRAME);
        assertFalse(world.timeWarp.isRailsWarp(), "dropped out for the burn");
        assertEquals(due, world.engine.getSimSeconds(), 1.0);
        assertNull(world.burns.selected(), "fired");
        assertTrue(orbit().apoapsis > 400e3 + 150e3, "apoapsis " + orbit().apoapsis);
    }

    @Test
    void deorbitingOnRailsLosesTheShip() {
        world.burns.addNode();
        world.burns.adjust(-300, 0);
        assertTrue(world.timeWarp.setWarp(1_000), world.engine.getRailsWarpBlockedReason());
        for (int i = 0; i < 3_000 && world.getCrash() == null; i++) {
            if (!world.timeWarp.isRailsWarp()) world.timeWarp.setWarp(1_000);
            world.update(FRAME);
        }
        assertNotNull(world.getCrash(), "crashed");
        assertEquals("Earth", world.getCrash().bodyName);
        assertEquals(1, world.timeWarp.getWarp());
        double t = world.engine.getSimSeconds();
        world.update(FRAME);
        assertEquals(t, world.engine.getSimSeconds(), "the game is over");
    }

    @Test
    void flyingIntoThePlanetLosesTheShip() {
        PhysicsBodyComponent pb = game.player.getComponent(PhysicsBodyComponent.class);
        world.physics.setTrueState(pb.body, GameScenario.R_EARTH - 10, 0, 0, 0);
        world.update(FRAME);
        assertNotNull(world.getCrash());
        assertEquals("Earth", world.getCrash().bodyName);
    }

    @Test
    void otherShipsOnRailsAreDestroyedOnImpact() {
        Entity rock = world.engine.createEntity();
        rock.add(new UuidComponent("falling-rock"));
        double r = GameScenario.R_EARTH + 500;
        rock.add(new OrbitComponent(GameScenario.EARTH, KeplerOrbit.fromState(0, -r, 50, 200,
                GameScenario.MU_EARTH, world.universe.now())));
        world.engine.addEntity(rock);
        world.timeWarp.setWarp(10);
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertNull(world.engine.getEntity("falling-rock"), "destroyed");
        assertNull(world.getCrash(), "the player is fine");
    }

    @Test
    void theMapShowsTheMoonAndItsOrbit() {
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(new PlayerOrbitPath(world, world.burns).getPlayerPath(path));
        assertEquals(1, path.bodies.size);
        OrbitPathProvider.MapBody moon = path.bodies.first();
        assertEquals("Moon", moon.name);
        assertEquals(GameScenario.MOON_DISTANCE, Math.hypot(moon.x, moon.y), 1e3);
        assertEquals(2 * (PlayerOrbitPath.SEGMENTS + 1), moon.orbit.size);
    }

    @Test
    void anIceMineOrbitsTheMoon() {
        assertEquals(GameScenario.MOON, world.macroSim.rails().parentOf(game.lunarMine));
        assertNotNull(world.macroSim.station(game.lunarMine));
        assertEquals("ice_mine", world.macroSim.station(game.lunarMine).stationTypeId);
    }

    @Tag("slow")
    @Test
    void haulersFlyBetweenEarthAndTheMoon() {
        java.util.Set<String> parents = new java.util.HashSet<>();
        double start = world.engine.getSimSeconds();
        for (int i = 0; i < 20_000 && world.engine.getSimSeconds() < start + 20 * 86400.0; i++) {
            if (!world.timeWarp.isRailsWarp()) world.timeWarp.setWarp(100_000);
            world.update(FRAME);
            for (String uuid : world.macroSim.freighters().all().keySet()) {
                if (world.macroSim.rails().isOnRails(uuid)) parents.add(uuid + ">" + world.macroSim.rails().parentOf(uuid));
            }
        }
        assertTrue(world.engine.getSimSeconds() >= start + 20 * 86400.0, "warped 20 days");
        int crossed = 0;
        for (String uuid : world.macroSim.freighters().all().keySet()) {
            if (parents.contains(uuid + ">" + GameScenario.EARTH) && parents.contains(uuid + ">" + GameScenario.MOON)) crossed++;
        }
        assertTrue(crossed > 0, "a hauler flew between Earth and the Moon: " + parents);
    }
}
