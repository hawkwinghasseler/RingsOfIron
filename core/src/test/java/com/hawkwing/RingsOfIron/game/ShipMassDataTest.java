package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 9 (part 3): a ship with a layout flies at its layout's mass, so the {@code mass} its entry in
 * ships.toml lists only stands in for it. That figure must still say what the ship weighs, with full
 * tanks and an empty hold, within {@link #DRIFT}: round 8 found the corvette listed at 120 t and flying
 * at 195 t.
 */
class ShipMassDataTest {
    /** Most a listed mass may differ from the flown one, as a share of the flown. */
    static final float DRIFT = 0.05f;

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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void everyListedMassIsWhatTheShipFliesAt() {
        List<String> off = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        int weighed = 0;
        float x = 3_000f;
        for (ShipDef def : game.content.defs.all("ship", ShipDef.class)) {
            if (def.chassis == null || def.chassis.isEmpty()) continue;
            Entity ship = game.content.factory.spawnShip(def.id, x, 3_000f, 0f);
            x += 200f;
            // Two fixed ticks: power and pipes route, tanks fill the thrusters
            for (int i = 0; i < 3; i++) world.update(1f / 60f);
            assertNotNull(ship.getComponent(ShipGridComponent.class), def.id + " has a layout");
            float flown = PhysicsManager.getMass(ship.getComponent(PhysicsBodyComponent.class).body);
            String line = String.format(java.util.Locale.ROOT, "%s: listed %.0f t, flies at %.1f t", def.id, def.mass / 1000f, flown / 1000f);
            all.append(line).append('\n');
            if (Math.abs(def.mass - flown) > DRIFT * flown) off.add(line);
            weighed++;
        }
        System.out.print(all);
        assertTrue(weighed >= 8, "weighed every ship with a layout:\n" + all);
        assertTrue(off.isEmpty(), "listed masses more than " + Math.round(DRIFT * 100) + "% off: " + off);
    }
}
