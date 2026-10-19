package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class PlayerShipStatusTest {
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
        // Long enough for the power to be routed aboard
        for (int i = 0; i < 90; i++) world.update(1f / 60f);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aFreshCorvetteReadsFullOnEveryGauge() {
        ShipStatus s = new PlayerShipStatus(() -> game.player).getShipStatus();
        assertEquals(1f, s.propellant, 1e-3f);
        assertTrue(s.propellantKg > 0);
        assertEquals(1f, s.structure, 1e-6f);
        assertTrue(s.power > 0.9f, "store charged: " + s.power);
        assertTrue(s.powerOutputWatts > 0);
        assertFalse(Float.isNaN(s.oxygen), "the corvette carries oxygen tanks");
        assertEquals(1f, s.cabinPressure, 0.05f);
    }

    @Test
    void theGaugesFollowTheTanks() {
        ShipSubsystemRegistryComponent registry = game.player.getComponent(ShipSubsystemRegistryComponent.class);
        double full = 0;
        for (SubsystemComponent t : TankSupply.tanks(registry, Fluid.PROPELLANT)) {
            full += t.contents;
            t.contents *= 0.25;
        }
        ShipStatus s = new PlayerShipStatus(() -> game.player).getShipStatus();
        assertEquals(0.25f, s.propellant, 1e-3f);
        assertEquals(full * 0.25, s.propellantKg, 1e-6);
    }

    @Test
    void noShipNoGauges() {
        assertNull(new PlayerShipStatus(() -> null).getShipStatus());
        // A ship from before tanks has no propellant gauge
        Entity bare = world.engine.createEntity();
        ShipStatus s = PlayerShipStatus.read(bare, new ShipStatus(), new com.ringsofiron.uihud.minimap.ShipAlerts());
        assertTrue(Float.isNaN(s.propellant));
        assertTrue(Float.isNaN(s.oxygen));
    }
}
