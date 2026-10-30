package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 8 part 5: part 6's playtest of the gunship's Black Sal fight. Its oxygen tank burst, the
 * crew patched the hull, and the cabin stayed at 0% oxygen: the crew died as their suits ran dry.
 */
class BurstOxygenTankTest {
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
        run(120);
        assertTrue(world.docking.undock(game.player));
        run(60);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    @Test
    void aPatchedShipWhoseOxygenTankBurstGetsOxygenFromLifeSupport() {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        SubsystemComponent o2 = null, unit = null;
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            if (s.type == SubsystemComponent.SubsystemType.TANK && s.fluid == Fluid.OXYGEN) o2 = s;
            if (s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) unit = s;
        }
        assertNotNull(o2);
        assertNotNull(unit);
        // The tank burst in the fight, and the crew have patched the hull
        DamageStates.burst(grid, o2, new RandomXS128(1));
        // Part 6's save: the burst left the cabin at 0% oxygen
        for (Compartment c : grid.compartments) c.oxygen = 0;
        // Life support puts oxygen back from its own reserve (the cabin stayed at 0% for good)
        run(60 * 30);
        double oxygen = 0;
        for (Compartment c : grid.compartments) oxygen += c.oxygen;
        StringBuilder air = new StringBuilder("reserve used " + unit.reserveUsed + ", unit op " + unit.isOperational + " powered " + unit.powered + ", venting " + grid.isVenting() + "; ");
        for (Compartment c : grid.compartments) air.append(String.format(java.util.Locale.ROOT, "[%.1f m3 %.0f kPa o2 %.2f kg] ", c.volume, c.pressure(), c.oxygen));
        assertTrue(unit.reserveUsed > 0, "on life support's own oxygen; tank " + o2.contents + " of " + o2.capacity + ", available " + com.ringsofiron.interiorgrid.TankSupply.available(unit, Fluid.OXYGEN));
        assertTrue(oxygen > 0, "oxygen coming back into the cabin: " + air);
    }
}
