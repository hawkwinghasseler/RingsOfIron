package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.shipops.StationYard;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 8 part 5: the yard puts the air back. Round 7's "Before the Moon trip" save loaded at 4 kPa,
 * because the yard mended the hull the ace holed and left the refilling to life support.
 */
class YardAirTest {
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    @Test
    void aHoledCorvetteRepairedAtHighportHasEveryRoomBreathableWhenTheYardFinishes() {
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        // Hole the hull beside two rooms, as the ace's rounds do, and let the cabin vent: the crew
        // stand by rather than patch it
        for (Entity e : world.crewCareer.ownCrew(game.player)) world.crew.crew(e).held = true;
        // The player puts a suit on: the game no longer does it for them
        world.crew.crew(game.avatar).setSuited(true);
        int holed = 0;
        for (int y = 0; y < grid.height && holed < 2; y++) {
            for (int x = 0; x < grid.width && holed < 2; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType != Tile.WallType.HULL || t.mount != null || !besideAir(grid, x, y)) continue;
                t.setIntegrity(0f);
                holed++;
                y += 3;
            }
        }
        assertEquals(2, holed);
        run(60 * 120);
        double lowest = Compartment.STANDARD_PRESSURE;
        for (Compartment c : grid.compartments) lowest = Math.min(lowest, c.pressure());
        assertTrue(lowest < 30, "the cabin vented, to " + lowest + " kPa");

        TraderComponent trader = game.player.getComponent(TraderComponent.class);
        trader.credits = 1e6;
        double air = StationYard.airCost(game.player), hull = world.yard.repairCost(game.player, highport);
        assertTrue(air > 1, "the missing air has a price: " + air);
        String said = world.yard.orderRepairs(game.player, highport);
        assertTrue(said.startsWith("Repairs ordered"), said);
        double credits = trader.credits;
        for (int i = 0; i < 60 * 60 * 30 && world.yard.repairing(game.player); i++) world.update(FRAME);
        assertFalse(world.yard.repairing(game.player), "repaired");
        assertTrue(world.yard.damage(game.player).isEmpty());

        for (Compartment c : grid.compartments) {
            assertEquals(Compartment.STANDARD_PRESSURE, c.pressure(), 2, "room " + c.id + " back at station pressure");
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).holdsAir()) assertTrue(TaskRules.isBreathable(grid, x, y), "breathable at " + x + "," + y);
            }
        }
        double paid = credits - trader.credits;
        assertTrue(paid > hull * 1.02, "the air is on the repair bill: paid " + paid + " for " + hull + " of repairs and " + air + " of air");
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
