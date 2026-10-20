package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
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
    void aPatchedShipWhoseOxygenTankBurstIsBreathableAgainAndLosesNobody() {
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
        // Suited for battle stations, as in the fight; the tank bursts and a round holes the hull
        for (Entity e : world.crewCareer.ownCrew(game.player)) world.crew.crew(e).setSuited(true);
        world.crew.crew(game.avatar).setSuited(true);
        DamageStates.burst(grid, o2, new RandomXS128(1));
        int holed = 0;
        for (int y = 0; y < grid.height && holed == 0; y++) {
            for (int x = 0; x < grid.width && holed == 0; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && besideAir(grid, x, y)) {
                    t.setIntegrity(0f);
                    holed++;
                }
            }
        }
        // Twenty minutes: twice a suit's air
        for (int i = 0; i < 20 * 60; i++) run(60);
        for (Entity e : world.crewCareer.ownCrew(game.player)) {
            assertTrue(world.crew.crew(e).isAlive(), world.crew.crew(e).name + " alive: " + world.crew.crew(e).activity);
        }
        assertTrue(world.crew.crew(game.avatar).isAlive(), "the captain alive");
        boolean breathable = false;
        for (int[] t : GameScenario.floorTiles(grid)) breathable |= TaskRules.isBreathable(grid, t[0], t[1]);
        assertTrue(breathable, "the cabin can be breathed again");
        assertTrue(unit.reserveUsed > 0, "on life support's own oxygen");
    }

    private static boolean besideAir(ShipGridComponent grid, int x, int y) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            Tile n = grid.getTile(x + d[0], y + d[1]);
            if (n != null && n.holdsAir()) return true;
        }
        return false;
    }
}
