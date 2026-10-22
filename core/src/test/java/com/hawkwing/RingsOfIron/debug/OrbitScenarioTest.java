package com.hawkwing.RingsOfIron.debug;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.PlayerOrbitPath;
import com.hawkwing.RingsOfIron.game.PropellantFeed;
import com.hawkwing.RingsOfIron.game.WarpNotices;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The orbit scenario headless, on the career's world as the debug menu loads it: the map data, burns, warp, a flyby. */
class OrbitScenarioTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    OrbitScenario orbit;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        // As the game screen does
        WarpNotices.attach(world);
        orbit = new OrbitScenario();
        orbit.build(world);
    }

    @AfterEach
    void tearDown() {
        orbit.dispose();
        world.dispose();
    }

    private void update() {
        world.update(FRAME);
        world.notices.update(FRAME);
        orbit.update(FRAME);
    }

    /** Steps warp to its top whenever it has dropped, and runs frames until {@code done} or the frames run out. */
    private void warpUntil(java.util.function.BooleanSupplier done, int frames) {
        for (int i = 0; i < frames && !done.getAsBoolean(); i++) {
            if (world.timeWarp.getWarp() == 1) for (int s = 0; s < 10; s++) WarpNotices.step(world, 1);
            update();
        }
    }

    private boolean noticed(String text) {
        for (NoticeLog.Notice n : world.notices.history()) if (n.text.contains(text)) return true;
        return false;
    }

    @Test
    void isInTheDebugMenu() {
        DebugScenarios.Entry entry = DebugScenarios.find("orbit");
        assertNotNull(entry);
        assertInstanceOf(OrbitScenario.class, entry.create());
    }

    @Test
    void theGamesOrbitMapDrawsThePlayerAroundEarthWithTheMoon() {
        update();
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(orbit.path.getPlayerPath(path));
        assertSame(orbit.earth, orbit.path.frame());
        assertEquals("Earth", path.bodyName);
        assertEquals(2 * (PlayerOrbitPath.SEGMENTS + 1), path.current.size);
        assertEquals("Moon", path.bodies.first().name);
        assertFalse(path.bodies.first().orbit.isEmpty(), "the Moon's orbit is drawn");
        assertEquals(4, orbit.traffic.size);
        // In the bubble, in low orbit, as a career starts
        assertNull(world.universe.orbit(game.player));
        assertEquals(GameScenario.ORBIT_RADIUS, orbit.orbitInfo.currentConic().a, 1_000);
    }

    @Test
    void anInstantBurnRaisesTheOrbitAndIsPaidFromTheTanks() {
        update();
        double before = PropellantFeed.deltaVLeft(game.player, world.shipMass(game.player).mass);
        orbit.burn(OrbitScenario.BURN, 0);
        update();
        KeplerOrbit k = orbit.orbitInfo.currentConic();
        assertTrue(k.apoapsis() > GameScenario.ORBIT_RADIUS + 300e3, "apoapsis " + k.apoapsis());
        double after = PropellantFeed.deltaVLeft(game.player, world.shipMass(game.player).mass);
        assertTrue(after < before - 50, before + " -> " + after);
    }

    @Test
    void railsWarpDropsOutAsTheCourierCatchesUp() {
        warpUntil(() -> noticed("Courier is coming within 20 km"), 200_000);
        assertTrue(noticed("Courier is coming within 20 km"));
    }

    @Test
    void theFreighterAndThePlayerFlyByTheMoon() {
        update();
        assertTrue(orbit.lunarTransfer(game.player));
        assertTrue(noticed("Moon flyby: burn"));
        warpUntil(() -> orbit.orbitInfo.currentConic() != null && orbit.orbitInfo.parent() == orbit.moon, 400_000);
        assertSame(orbit.moon, orbit.orbitInfo.parent(), "the player falls into the Moon's sphere of influence");
        assertTrue(noticed("You enter the Moon"));
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(orbit.path.getPlayerPath(path));
        assertEquals("Moon", path.bodyName);
        // A flyby, not a crash
        KeplerOrbit flyby = orbit.orbitInfo.currentConic();
        assertTrue(flyby.periapsis() > GameScenario.R_MOON + 500e3, "periapsis " + flyby.periapsis());
        assertNull(world.getCrash());
        // The freighter left on its own transfer first
        OrbitComponent freighter = world.universe.orbit(orbit.traffic.get(3));
        assertTrue(GameScenario.MOON.equals(freighter.parentUuid) || freighter.getOrbit().a > 1e8, freighter.parentUuid);
    }

    @Test
    void noFlybyFromAnEccentricOrbit() {
        update();
        orbit.burn(800, 0);
        update();
        assertFalse(orbit.lunarTransfer(game.player));
    }

    @Test
    void theMapZoomStaysInItsLimits() {
        for (int i = 0; i < 40; i++) orbit.zoom(0.5);
        assertEquals(OrbitScenario.MIN_SCALE, orbit.scale);
        for (int i = 0; i < 40; i++) orbit.zoom(2);
        assertEquals(OrbitScenario.MAX_SCALE, orbit.scale);
    }
}
