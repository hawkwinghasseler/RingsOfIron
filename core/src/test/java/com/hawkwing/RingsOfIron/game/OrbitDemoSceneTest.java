package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The orbit demo headless: the game's world, its orbit map data, burns, warp and a transfer to the Moon. */
class OrbitDemoSceneTest {
    static final float FRAME = 1f / 60f;

    OrbitDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new OrbitDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
        demo.load();
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    /** Steps warp to its top whenever it has dropped, and runs frames until {@code done} or the frames run out. */
    private void warpUntil(java.util.function.BooleanSupplier done, int frames) {
        for (int i = 0; i < frames && !done.getAsBoolean(); i++) {
            if (demo.world.timeWarp.getWarp() == 1) for (int s = 0; s < 10; s++) WarpNotices.step(demo.world, 1);
            demo.update(FRAME);
        }
    }

    private boolean noticed(String text) {
        for (NoticeLog.Notice n : demo.world.notices.history()) if (n.text.contains(text)) return true;
        return false;
    }

    @Test
    void theGamesOrbitMapDrawsThePlayerAroundEarthWithTheMoon() {
        demo.update(FRAME);
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(demo.path.getPlayerPath(path));
        assertSame(demo.earth, demo.path.frame());
        assertEquals("Earth", path.bodyName);
        assertEquals(2 * (PlayerOrbitPath.SEGMENTS + 1), path.current.size);
        assertEquals(1, path.bodies.size);
        assertEquals("Moon", path.bodies.first().name);
        assertFalse(path.bodies.first().orbit.isEmpty(), "the Moon's orbit is drawn");
        // In the bubble, in low orbit, as the game starts
        assertNull(demo.world.universe.orbit(demo.player));
        assertEquals(OrbitDemoScene.LEO, demo.orbitInfo.currentConic().a, 1_000);
    }

    @Test
    void anInstantBurnRaisesTheOrbitAndIsPaidFromTheTanks() {
        demo.update(FRAME);
        double before = PropellantFeed.deltaVLeft(demo.player, demo.world.shipMass(demo.player).mass);
        demo.burn(100, 0);
        demo.update(FRAME);
        KeplerOrbit k = demo.orbitInfo.currentConic();
        assertTrue(k.apoapsis() > OrbitDemoScene.LEO + 300e3, "apoapsis " + k.apoapsis());
        double after = PropellantFeed.deltaVLeft(demo.player, demo.world.shipMass(demo.player).mass);
        assertTrue(after < before - 50, before + " -> " + after);
    }

    @Test
    void railsWarpDropsOutAsTheCourierCatchesUp() {
        warpUntil(() -> noticed("Courier is coming within 20 km"), 200_000);
        assertTrue(noticed("Courier is coming within 20 km"));
        assertEquals(1, demo.world.timeWarp.getWarp());
    }

    @Test
    void theFreighterAndThePlayerFlyByTheMoon() {
        demo.update(FRAME);
        assertTrue(demo.lunarTransfer(demo.player));
        assertTrue(noticed("Transfer burn of"));
        warpUntil(() -> demo.orbitInfo.currentConic() != null && demo.orbitInfo.parent() == demo.moon, 400_000);
        assertSame(demo.moon, demo.orbitInfo.parent(), "the player falls into the Moon's sphere of influence");
        assertTrue(noticed("You enter the Moon") || noticed("entering Moon's sphere of influence"));
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(demo.path.getPlayerPath(path));
        assertEquals("Moon", path.bodyName);
        // A flyby, not a crash
        KeplerOrbit flyby = demo.orbitInfo.currentConic();
        assertTrue(flyby.periapsis() > GameScenario.R_MOON + 500e3, "periapsis " + flyby.periapsis());
        assertNull(demo.world.getCrash());
        // The freighter left on its own transfer first
        OrbitComponent freighter = demo.world.universe.orbit(demo.traffic.get(3));
        assertTrue(GameScenario.MOON.equals(freighter.parentUuid) || freighter.getOrbit().a > 1e8, freighter.parentUuid);
    }

    @Test
    void noTransferFromAnEccentricOrbit() {
        demo.update(FRAME);
        demo.burn(800, 0);
        demo.update(FRAME);
        assertFalse(demo.lunarTransfer(demo.player));
    }
}
