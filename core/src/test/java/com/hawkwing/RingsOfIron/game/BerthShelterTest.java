package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.macrosim.economy.TraderComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 8 part 5: nobody dies of a leak at a station's berth. Seed 2026's round 5 career lost the
 * gunship's whole crew and captain at Highport's berth while the leak the ace left was patched.
 */
class BerthShelterTest {
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
    void aShipThatCannotHoldItsAirAtTheBerthLosesNobody() {
        assertSame(world.engine.getEntity(game.highport), world.docking.dockedTo(game.player));
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        List<Entity> crew = new ArrayList<>(world.crewCareer.ownCrew(game.player));
        assertTrue(crew.size() >= 2);
        // One hand down with a serious wound, as a lost fight leaves them
        Entity down = crew.get(0);
        world.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        assertFalse(world.crew.crew(down).isConscious(), "down");
        // Holed beside every room, with no patches aboard: the cabin can't be held
        grid.patchKits = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && besideAir(grid, x, y) && (x + y) % 3 == 0) t.setIntegrity(0f);
            }
        }
        run(60 * 60);
        double highest = 0;
        for (Compartment c : grid.compartments) highest = Math.max(highest, c.pressure());
        assertTrue(highest < 20, "the cabin vented, to " + highest + " kPa");
        // Long past a suit's air
        boolean casualtyAshore = false, captainAshore = false;
        for (int i = 0; i < 25 * 60; i++) {
            run(60);
            casualtyAshore |= world.crew.position(down).ship != game.player;
            captainAshore |= world.crew.position(game.avatar).ship != game.player;
        }
        StringBuilder all = new StringBuilder();
        for (Entity e : crew) all.append(describe(e)).append("; ");
        for (Entity e : crew) assertTrue(world.crew.crew(e).isAlive(), describe(e) + " / " + all);
        assertTrue(world.crew.crew(game.avatar).isAlive(), "the captain alive");
        assertTrue(captainAshore, "the captain went aboard the station: " + describe(game.avatar));
        assertTrue(casualtyAshore, "the casualty was carried aboard the station: " + describe(down));

        // The yard mends it and puts the air back: everyone comes home
        Entity highport = world.engine.getEntity(game.highport);
        game.player.getComponent(TraderComponent.class).credits = 1e6;
        String said = world.yard.orderRepairs(game.player, highport);
        assertTrue(said.startsWith("Repairs ordered"), said);
        for (int i = 0; i < 60 * 60 * 30 && world.yard.repairing(game.player); i++) world.update(FRAME);
        assertFalse(world.yard.repairing(game.player), "repaired");
        run(60 * 60);
        StringBuilder after = new StringBuilder();
        for (Entity e : crew) after.append(describe(e)).append("; ");
        for (Entity e : crew) {
            assertTrue(world.crew.crew(e).isAlive(), after.toString());
            assertSame(game.player, world.crew.position(e).ship, "back aboard: " + after);
        }
        assertSame(game.player, world.crew.position(game.avatar).ship, "the captain home: " + describe(game.avatar));
    }

    private String describe(Entity e) {
        CrewMemberComponent c = world.crew.crew(e);
        GridPositionComponent p = world.crew.position(e);
        return c.name + " " + c.state + " " + c.wound + " (" + c.activity + ", " + (c.suited ? "suited" : "unsuited") + ") on "
                + (p.ship == game.player ? "the ship" : "the station");
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
