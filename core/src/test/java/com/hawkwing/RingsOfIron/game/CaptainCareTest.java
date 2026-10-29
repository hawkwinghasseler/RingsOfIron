package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 10 part 4: the captain is looked after like the crew. Round 9's real window (9f41c90) lost
 * "First ace fight", was towed to Highport with the cabin in vacuum, and at warp the captain stood
 * in the port's walkway until the suit ran dry; then the career ended as if nobody was left, with
 * four crew alive ashore.
 */
class CaptainCareTest {
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

    private boolean runUntil(float seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private CrewMemberComponent you() {
        return world.crew.crew(game.avatar);
    }

    private GridPositionComponent where() {
        return world.crew.position(game.avatar);
    }

    /** Holes the hull beside every room, with no patches aboard: the cabin can't be held. */
    private void holeTheCabin() {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        grid.patchKits = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && t.mount == null && besideAir(grid, x, y) && (x + y) % 3 == 0) t.setIntegrity(0f);
            }
        }
    }

    private boolean vented() {
        return !CrewTasks.anywhereSafe(game.player.getComponent(ShipGridComponent.class));
    }

    @Test
    void atWarpWithLongFramesTheSuitComesOffAfterTheSameSimTime() {
        // Safe at the berth, suited: the suit comes off once the captain has stood calm this long
        double calmFor = CrewTasks.Idle.SUIT_OFF_DELAY + you().def.suitTime * 0.5f;
        you().setSuited(true);
        world.timeWarp.setWarp(10);
        assertEquals(10, world.timeWarp.getWarp());
        double start = world.engine.getSimSeconds();
        // 4 frames a second at 10x: 150 ticks, 2.5 sim seconds, a frame. Counted once a frame and
        // capped at half a second, the calm took five times as long
        while (you().suited && world.engine.getSimSeconds() - start < calmFor * 4) world.update(0.25f);
        assertFalse(you().suited, "the suit came off");
        assertEquals(calmFor, world.engine.getSimSeconds() - start, 2.5 + 0.1, "within a frame of the calm time");
    }

    @Test
    void towedInVentedAndWarpedTheCaptainIsNeverWalkedAshore() {
        // A lost fight's ship: out from the berth, holed past holding, then towed in
        world.docking.undock(game.player);
        run(60);
        holeTheCabin();
        assertTrue(runUntil(120f, this::vented), "the cabin vented");
        run(60 * 30);
        assertNull(world.bridge.sit(com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.HELM), "seated at the helm");
        assertNull(world.rescue.tow(game.player));
        assertTrue(runUntil(30f, () -> world.docking.dockedTo(game.player) != null), "towed in and docked");
        assertTrue(vented(), "still in vacuum at the berth");
        assertTrue(you().suited, "the captain is suited");
        assertSame(game.player, where().ship, "the captain is aboard");

        // Low on air, at warp: the figure is the player's alone, and stays where it is
        you().suitAir = 120f;
        for (int i = 0; i < 4; i++) world.timeWarp.step(1);
        assertEquals(10, world.timeWarp.getWarp());
        int x = where().tileX(), y = where().tileY();
        com.ringsofiron.aicrew.PlayerAvatarComponent walker = game.avatar.getComponent(com.ringsofiron.aicrew.PlayerAvatarComponent.class);
        for (int i = 0; i < 60; i++) {
            // The screen clears the figure's walk from the keys before each update: no key held
            walker.moveX = walker.moveY = 0f;
            world.update(FRAME);
            assertEquals(0f, walker.moveX);
            assertEquals(0f, walker.moveY);
        }
        assertSame(game.player, where().ship, "still aboard");
        assertEquals(x, where().tileX());
        assertEquals(y, where().tileY());
        assertEquals(10, world.timeWarp.getWarp(), "warp is the player's too");
    }

    @Test
    void aSuitedCaptainInAVentedShipAtTheBerthIsNeverWalkedAshore() {
        // Joe's save: docked, the ship vented, the captain suited with a few minutes of air, and the
        // figure was walked off the ship to the station without a key pressed
        holeTheCabin();
        assertTrue(runUntil(120f, this::vented), "the cabin vented");
        world.bridge.stand();
        you().setSuited(true);
        you().suitAir = 124f;
        int x = where().tileX(), y = where().tileY();
        com.ringsofiron.aicrew.PlayerAvatarComponent walker = game.avatar.getComponent(com.ringsofiron.aicrew.PlayerAvatarComponent.class);
        for (int i = 0; i < 20f / FRAME; i++) {
            walker.moveX = walker.moveY = 0f;
            world.update(FRAME);
        }
        assertSame(game.player, where().ship, () -> "still aboard: on " + PlayerDocking.name(where().ship));
        assertEquals(x, where().tileX());
        assertEquals(y, where().tileY());
        assertTrue(you().isAlive());
    }

    @Test
    void withTheCrewAshoreTheCaptainKilledAboardIsBroughtHome() {
        holeTheCabin();
        assertTrue(runUntil(120f, this::vented), "the cabin vented");
        // The crew sheltering aboard Highport, as round 9's were at the berth
        Entity highport = world.docking.dockedTo(game.player);
        ShipGridComponent station = world.crew.grid(highport);
        List<int[]> safe = new ArrayList<>();
        for (int[] t : GameScenario.floorTiles(station)) if (com.ringsofiron.aicrew.TaskRules.isSafe(station, t[0], t[1])) safe.add(t);
        List<Entity> crew = new ArrayList<>(world.crewCareer.ownCrew(game.player));
        for (int i = 0; i < crew.size(); i++) {
            world.crew.releaseTask(crew.get(i));
            GridPositionComponent c = world.crew.position(crew.get(i));
            c.stop();
            c.setShip(highport);
            c.setTile(safe.get(i * 7 % safe.size())[0], safe.get(i * 7 % safe.size())[1]);
        }
        run(2);
        assertTrue(crew.stream().allMatch(e -> world.crew.position(e).ship == highport), () -> "the crew ashore: " + describe(crew));
        assertSame(game.player, where().ship, "the captain still aboard");
        world.crew.applyDamage(game.avatar, 10_000f, DamageCause.PENETRATOR);
        assertEquals(CrewState.DEAD, you().state);
        int[] home = new int[1];
        world.carriedHome.add((s, t) -> home[0]++);
        run(2);
        assertNull(world.getCareerOver(), "the career goes on");
        assertEquals(1, home[0], "the crew ashore brought the captain home");
        assertTrue(you().isAlive());
        GridPositionComponent p = where();
        ShipGridComponent there = world.crew.grid(p.ship);
        assertTrue(com.ringsofiron.aicrew.TaskRules.isSafe(there, p.tileX(), p.tileY()),
                "laid where they can breathe: on " + PlayerDocking.name(p.ship));
        assertSame(highport, p.ship, "ashore, where the crew shelter");
    }

    @Test
    void laidAshoreAtTheBerthIsKeptThroughASave(@org.junit.jupiter.api.io.TempDir Path dir) {
        holeTheCabin();
        assertTrue(runUntil(120f, this::vented), "the cabin vented");
        Entity highport = world.docking.dockedTo(game.player);
        world.crew.applyDamage(game.avatar, 10_000f, DamageCause.PENETRATOR);
        run(2);
        assertSame(highport, where().ship, "laid ashore");
        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(dir.resolve("ashore.json").toFile());
        assertNull(game.quickSave(file));
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            for (int i = 0; i < 3; i++) fresh.update(FRAME);
            GridPositionComponent p = fresh.crew.position(loaded.avatar);
            assertSame(fresh.docking.dockedTo(loaded.player), p.ship, "still ashore after a load");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void theCrewFillTheSuitOfACaptainDownInVacuum() {
        world.docking.undock(game.player);
        run(60);
        holeTheCabin();
        assertTrue(runUntil(120f, this::vented), "the cabin vented");
        world.crew.applyDamage(game.avatar, 75f, DamageCause.PENETRATOR);
        assertFalse(you().isConscious(), "down");
        you().setSuited(true);
        you().suitAir = 60f;
        boolean filled = runUntil(180f, () -> you().suitAir > 300f);
        assertTrue(filled, "a crewman filled the captain's suit: " + you().suitAir + " s, " + you().state);
        assertTrue(you().isAlive());
    }

    @Test
    void standingBesideTheShipsOxygenTheCaptainsSuitFills() {
        world.docking.undock(game.player);
        run(60);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        int[] spot = null;
        com.ringsofiron.aicrew.nav.GridNavGraph nav = world.crew.pathfinder(grid).graph;
        for (int y = 0; y < grid.height && spot == null; y++) {
            for (int x = 0; x < grid.width && spot == null; x++) {
                if (nav.isWalkable(x, y) && CrewTasks.suitOxygenBeside(game.player, grid, x, y) != null) spot = new int[]{x, y};
            }
        }
        assertNotNull(spot, () -> "somewhere to stand beside the oxygen: " + supplies());
        GridPositionComponent p = where();
        p.stop();
        p.setTile(spot[0], spot[1]);
        you().setSuited(true);
        you().suitAir = 100f;
        Compartment room = grid.compartmentAt(spot[0], spot[1]);
        float until = you().def.suitRefillTime + 2;
        for (float t = 0; t < until; t += FRAME) {
            // Hold the room empty, whatever life support puts back
            room.oxygen = 0;
            room.nitrogen = 0;
            world.update(FRAME);
            p.setTile(spot[0], spot[1]);
        }
        assertTrue(you().suitAir > you().def.suitAir - 5f, "filled: " + you().suitAir);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.equals(CaptainSafety.SUIT_FILLED)));
    }

    private String describe(List<Entity> crew) {
        StringBuilder s = new StringBuilder();
        for (Entity e : crew) {
            CrewMemberComponent c = world.crew.crew(e);
            s.append(c.name).append(' ').append(c.state).append(' ').append(c.activity).append(c.suited ? " suited " + (int) c.suitAir : " unsuited")
                    .append(world.crew.position(e).ship == game.player ? " aboard" : " ashore").append("; ");
        }
        return s.toString();
    }

    private String supplies() {
        StringBuilder s = new StringBuilder();
        for (Entity e : game.player.getComponent(com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class).subsystems) {
            com.ringsofiron.interiorgrid.SubsystemComponent c = e == null ? null : e.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
            if (c != null) s.append(c.type).append(c.type == com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.TANK ? "/" + c.fluid + " " + c.contents : "")
                    .append(" op ").append(c.isOperational).append("; ");
        }
        return s.toString();
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
