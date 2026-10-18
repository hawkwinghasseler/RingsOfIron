package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.moddingdata.ModContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boots the whole wired game headless and checks the modules work together. */
class GameWorldTest {
    static final double MU_EARTH = 3.986004418e14;
    static final double R_EARTH = 6.371e6;
    static final double LEO = R_EARTH + 400e3;
    static final float FRAME = 1f / 60f;

    GameWorld world;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        Entity earth = world.engine.createEntity();
        earth.add(new UuidComponent("earth"));
        earth.add(new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        world.engine.addEntity(earth);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    Entity playerOnRails() {
        Entity ship = world.engine.createEntity();
        ship.add(new UuidComponent("player"));
        ship.add(new PlayerControllerComponent());
        ship.add(new TransformComponent());
        ship.add(new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1)));
        world.engine.addEntity(ship);
        return ship;
    }

    @Test
    void realTimeAdvancesOneTickPerFrame() {
        playerOnRails();
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertEquals(120, world.engine.getTick(), 1);
    }

    @Test
    void physicsWarpThroughTheController() {
        playerOnRails();
        assertTrue(world.timeWarp.setWarp(5));
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertEquals(300, world.engine.getTick(), 5);
    }

    @Test
    void railsWarpJumpsTheSharedClock() {
        playerOnRails();
        assertTrue(world.timeWarp.setWarp(1_000));
        assertTrue(world.timeWarp.isRailsWarp());

        for (int i = 0; i < 60; i++) world.update(FRAME);

        // 1 s of real time at 1000x is 1000 s of sim time, plus the 60 ordinary ticks.
        assertEquals(1_000.0, world.engine.getSimSeconds(), 2.0);
        assertEquals(world.engine.getSimSeconds(), world.universe.now(), 1e-9);
    }

    @Test
    void fireAboardRefusesRailsWarp() {
        Entity ship = playerOnRails();
        ShipGridComponent grid = new ShipGridComponent(4, 4, 1f);
        grid.getTile(1, 1).fireIntensity = 0.5f;
        ship.add(grid);

        assertFalse(world.timeWarp.setWarp(1_000));
        assertFalse(world.timeWarp.isRailsWarp());
        assertNotNull(world.engine.getRailsWarpBlockedReason());
    }

    @Test
    void saveAndLoadKeepsTimeAndOrbits() {
        playerOnRails();
        world.timeWarp.setWarp(1_000);
        for (int i = 0; i < 30; i++) world.update(FRAME);
        long tick = world.engine.getTick();
        String save = world.engine.getSerializationService().saveGame();

        GameWorld loaded = new GameWorld();
        try {
            loaded.engine.getSerializationService().loadGame(save);
            assertEquals(tick, loaded.engine.getTick());
            assertNotNull(loaded.getPlayer());
            assertNotNull(loaded.universe.orbit(loaded.getPlayer()));
            assertTrue(loaded.engine.getSerializationService().getLastLoadWarnings().isEmpty());
        } finally {
            loaded.dispose();
        }
    }

    @Test
    void loadsBaseDataAndSpawnsShipsFromIt() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        boolean[] registered = {false};
        GameWorld.Modules modules = new GameWorld.Modules();
        modules.contentRegistrations.add(ctx -> registered[0] = true);
        GameWorld withContent = new GameWorld(modules);
        try {
            ModContext ctx = withContent.loadContent(assets, assets.resolve("no-mods-here"));

            assertTrue(registered[0]);
            assertFalse(ctx.report.hasErrors(), ctx.report.toString());
            assertFalse(ctx.defs.ids("ship").isEmpty());
            String shipId = ctx.defs.ids("ship").iterator().next();
            assertNotNull(ctx.factory.spawnShip(shipId, 0, 0, 0));
            withContent.update(FRAME);
        } finally {
            withContent.dispose();
        }
    }
}
