package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 8 part 5: life support's own emergency oxygen is saved, and a station's refill makes it good. */
class EmergencyOxygenTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        for (int i = 0; i < 120; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private SubsystemComponent lifeSupport() {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) return s;
        }
        return fail("the corvette has life support");
    }

    @Test
    void whatLifeSupportUsedOfItsOwnOxygenIsSavedAndTheYardMakesItGood(@TempDir Path dir) {
        lifeSupport().reserveUsed = 12.5;
        FileHandle file = new FileHandle(dir.resolve("o2.json").toFile());
        assertNull(game.quickSave(file));
        lifeSupport().reserveUsed = 0;
        game.quickLoad(file);
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertEquals(12.5, lifeSupport().reserveUsed, 1e-9, "saved");

        Entity highport = world.engine.getEntity(game.highport);
        double cost = world.yard.refillCost(game.player);
        assertTrue(cost >= 12.5 * StationYard.OXYGEN_PRICE, "on the refill bill: " + cost);
        game.player.getComponent(TraderComponent.class).credits = 1e6;
        world.yard.orderRefill(game.player, highport);
        assertEquals(0, lifeSupport().reserveUsed, 1e-9, "made good");
        assertEquals(30, com.ringsofiron.interiorgrid.TankSupply.reserve(lifeSupport()), 1e-9);
        assertTrue(com.ringsofiron.interiorgrid.TankSupply.available(lifeSupport(), Fluid.OXYGEN) > 30, "and the tanks behind it");
    }
}
