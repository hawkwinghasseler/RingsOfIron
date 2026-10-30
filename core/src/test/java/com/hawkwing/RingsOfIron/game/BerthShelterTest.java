package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.FloorTiles;
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
    void aShipThatCannotHoldItsAirAtTheBerthSendsItsCasualtyAshoreButLeavesTheCaptainToThePlayer() {
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
        // The player put a suit on
        world.crew.crew(game.avatar).setSuited(true);
        holeBesideEveryRoom(grid);
        // Ashore before a suit's air is gone
        float suitAir = world.crew.crew(game.avatar).def.suitAir;
        double t0 = world.engine.getSimSeconds();
        while (world.engine.getSimSeconds() - t0 < suitAir && world.crew.position(down).ship == game.player) run(60);
        StringBuilder all = new StringBuilder();
        for (Entity e : crew) all.append(describe(e)).append("; ");
        for (Entity e : crew) assertTrue(world.crew.crew(e).isAlive(), describe(e) + " / " + all);
        assertTrue(world.crew.crew(game.avatar).isAlive(), "the captain alive");
        // The figure is the player's alone: never walked ashore
        assertSame(game.player, world.crew.position(game.avatar).ship, "the captain left where they stood: " + describe(game.avatar));
        assertNotSame(game.player, world.crew.position(down).ship, "the casualty was carried aboard the station: " + describe(down));
    }

    @Test
    void theYardMendsAShipItsCrewShelterFromAndTheyComeHome() {
        Entity highport = world.docking.dockedTo(game.player);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        List<Entity> crew = new ArrayList<>(world.crewCareer.ownCrew(game.player));
        grid.patchKits = 0;
        holeBesideEveryRoom(grid);
        // The crew sheltering aboard the station, as they went ashore while the cabin vented
        ShipGridComponent station = world.crew.grid(highport);
        List<int[]> safe = new ArrayList<>();
        for (int[] t : FloorTiles.of(station)) if (com.ringsofiron.aicrew.TaskRules.isSafe(station, t[0], t[1])) safe.add(t);
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            world.crew.releaseTask(e);
            GridPositionComponent p = world.crew.position(e);
            p.stop();
            p.setShip(highport);
            int[] t = safe.get(i * 7 % safe.size());
            p.setTile(t[0], t[1]);
        }
        game.player.getComponent(TraderComponent.class).credits = 1e6;
        String said = world.yard.orderRepairs(game.player, highport);
        assertTrue(said.startsWith("Repairs ordered"), said);
        for (int i = 0; i < 60 * 60 * 30 && (world.yard.repairing(game.player) || !allAboard(crew)); i++) world.update(FRAME);
        assertFalse(world.yard.repairing(game.player), "repaired");
        StringBuilder after = new StringBuilder();
        for (Entity e : crew) after.append(describe(e)).append("; ");
        for (Entity e : crew) assertTrue(world.crew.crew(e).isAlive(), after.toString());
        assertTrue(allAboard(crew), "back aboard: " + after);
    }

    private boolean allAboard(List<Entity> crew) {
        for (Entity e : crew) if (world.crew.position(e).ship != game.player) return false;
        return true;
    }

    private static void holeBesideEveryRoom(ShipGridComponent grid) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && besideAir(grid, x, y) && (x + y) % 3 == 0) t.setIntegrity(0f);
            }
        }
    }

    @Test
    void crewShelteringAtTheStationGoBackAboardForACasualty() {
        com.ringsofiron.aicrew.TaskRules.shelterGoesBack = true;
        try {
            shelterAndGoBack();
        } finally {
            com.ringsofiron.aicrew.TaskRules.shelterGoesBack = false;
        }
    }

    @Test
    void aCrossingLeftBehindIsDropped() {
        // Round 10's seed 5: a hand set off across the link to the station, took a breach to seal
        // instead, and after it, still marked as crossing, pressed against a wall 200 s "going to fill
        // their suit" until it ran dry
        Entity highport = world.docking.dockedTo(game.player);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        com.ringsofiron.interiorgrid.GridLink link = null;
        for (com.ringsofiron.interiorgrid.GridLink l : grid.links) if (l.otherShip == highport && l.canCross()) link = l;
        assertNotNull(link, "a link to the station");
        Entity hand = world.crewCareer.ownCrew(game.player).get(0);
        int[] away = null;
        for (int[] t : FloorTiles.of(grid)) {
            if (Math.abs(t[0] - link.x) + Math.abs(t[1] - link.y) > 4) away = t;
        }
        assertNotNull(away, "somewhere away from the link");
        GridPositionComponent p = world.crew.position(hand);
        world.crew.releaseTask(hand);
        p.stop();
        p.setShip(game.player);
        p.setTile(away[0], away[1]);
        p.crossing = link;
        run(30);
        assertNull(p.crossing, "the crossing is dropped, at " + p.tileX() + "," + p.tileY());
    }

    private void shelterAndGoBack() {
        // Round 10's seed 11: the medic and two hands stood idle on Highport with full suits while a
        // crewmate bled out aboard and another's suit ran dry where he lay
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        List<Entity> crew = new ArrayList<>(world.crewCareer.ownCrew(game.player));
        grid.patchKits = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && besideAir(grid, x, y) && (x + y) % 3 == 0) t.setIntegrity(0f);
            }
        }
        for (int i = 0; i < 120 && com.ringsofiron.aicrew.btree.CrewTasks.anywhereSafe(grid); i++) run(60);
        assertFalse(com.ringsofiron.aicrew.btree.CrewTasks.anywhereSafe(grid), "the cabin vented");

        // The crew sheltering aboard the station, but one hand cut down aboard where the air is gone
        Entity highport = world.docking.dockedTo(game.player);
        ShipGridComponent station = world.crew.grid(highport);
        List<int[]> safe = new ArrayList<>();
        for (int[] t : FloorTiles.of(station)) if (com.ringsofiron.aicrew.TaskRules.isSafe(station, t[0], t[1])) safe.add(t);
        int[] vented = null;
        for (int[] t : FloorTiles.of(grid)) if (!com.ringsofiron.aicrew.TaskRules.isBreathable(grid, t[0], t[1])) vented = t;
        assertNotNull(vented, "somewhere aboard without air");
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            world.crew.releaseTask(e);
            GridPositionComponent p = world.crew.position(e);
            p.stop();
            p.setShip(i == 0 ? game.player : highport);
            int[] t = i == 0 ? vented : safe.get(i * 7 % safe.size());
            p.setTile(t[0], t[1]);
            world.crew.crew(e).suitAir = world.crew.crew(e).def.suitAir;
        }
        // Down where the air is gone with little left in the suit, as Pell Achebe lay at the berth, the
        // ship still at battle stations from the fight (so the station's infirmary takes nobody in)
        world.medical.shot(game.player);
        Entity down = crew.get(0);
        world.crew.crew(down).setSuited(true);
        world.crew.applyDamage(down, 75f, DamageCause.PENETRATOR);
        CrewMemberComponent c = world.crew.crew(down);
        assertFalse(c.isConscious());
        c.suitAir = 150f;
        run(2);
        StringBuilder all = new StringBuilder();
        for (Entity e : crew) all.append(describe(e)).append("; ");
        assertTrue(crew.subList(1, crew.size()).stream().allMatch(e -> world.crew.position(e).ship == highport), "the rest ashore: " + all);
        boolean helped = false;
        for (int i = 0; i < 240 && c.isAlive(); i++) {
            if (i % 30 == 0) world.medical.shot(game.player);
            run(60);
            helped |= c.carriedBy != null || world.crew.position(down).ship != game.player;
        }
        StringBuilder after = new StringBuilder();
        for (Entity e : crew) after.append(describe(e)).append("; ");
        assertTrue(c.isAlive(), "the casualty lives: " + after);
        assertTrue(helped, "someone went back aboard and carried them out: " + after);
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
