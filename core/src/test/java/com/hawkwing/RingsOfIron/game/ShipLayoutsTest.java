package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.CrewObstacles;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.moddingdata.defs.ShipDef;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every ship type in ships.toml is built with the interior systems a ship is expected to have: conduits
 * carrying power, thin walls with doors dividing it into rooms, pipes joining its tanks to the systems
 * that draw on them, and RCS thruster blocks on its hull. The audio demo's hull is laid out to show off
 * sound, not to fly, and is left out.
 */
class ShipLayoutsTest {
    /** Ship types not held to this: the audio demo's purpose-built hull. */
    static final Set<String> EXEMPT = Set.of("audio_demo");
    /** Systems that do nothing without power. */
    static final EnumSet<SubsystemType> NEED_POWER = EnumSet.complementOf(
            EnumSet.of(SubsystemType.CARGO, SubsystemType.TANK, SubsystemType.ESCAPE_POD));

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
    void everyShipHasConduitsPipesThinWallsAndRcs() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        float x = 3_000f;
        for (ShipDef def : game.content.defs.all("ship", ShipDef.class)) {
            if (def.chassis == null || def.chassis.isEmpty() || EXEMPT.contains(def.id)) continue;
            Entity ship = game.content.factory.spawnShip(def.id, x, 3_000f, 0f);
            x += 200f;
            for (int i = 0; i < 3; i++) world.update(1f / 60f);
            ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
            ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
            assertNotNull(grid, def.id + " has a layout");
            check(def.id, ship, grid, registry, problems);
            checked++;
        }
        assertTrue(checked >= 11, "checked every ship type with a layout, found " + checked);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private void check(String id, Entity ship, ShipGridComponent grid, ShipSubsystemRegistryComponent registry,
                       List<String> problems) {
        if (grid.rcsThrusters.isEmpty()) problems.add(id + ": no RCS thruster blocks");

        // Thin walls, each with a conduit in it, and doors in them
        int walls = 0, doors = 0, conduitTiles = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int tx = 0; tx < grid.width; tx++) {
                if (grid.getTile(tx, y).isPowerConduit()) conduitTiles++;
                for (Side side : new Side[]{Side.TOP, Side.RIGHT}) {
                    EdgeWall wall = grid.getEdge(tx, y, side);
                    if (wall == null) continue;
                    // A docking port's door is the port's own, not a wall between rooms
                    if (isPort(grid, tx, y) || isPort(grid, tx + side.dx, y + side.dy)) continue;
                    walls++;
                    if (wall.hasBulkhead) doors++;
                    if (grid.getConduit(tx, y, side) == null) problems.add(id + ": no conduit in the thin wall on " + side + " of " + tx + "," + y);
                }
            }
        }
        if (walls == 0) problems.add(id + ": no thin walls");
        if (doors == 0) problems.add(id + ": no doors in its thin walls");
        if (conduitTiles == 0) problems.add(id + ": no conduit tiles");

        // Pipes: life support reaches oxygen and nitrogen, every engine propellant
        List<SubsystemComponent> systems = new ArrayList<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null) systems.add(s);
        }
        boolean hasLifeSupport = false;
        for (SubsystemComponent s : systems) {
            if (s.type == SubsystemType.LIFE_SUPPORT) {
                hasLifeSupport = true;
                if (!piped(s, Fluid.OXYGEN) || !piped(s, Fluid.NITROGEN)) problems.add(id + ": life support is not piped to oxygen and nitrogen");
            }
            if (s.type == SubsystemType.ENGINE && !piped(s, Fluid.PROPELLANT)) problems.add(id + ": an engine is not piped to propellant");
        }
        if (!hasLifeSupport) problems.add(id + ": no life support");

        // Every tile crew can stand on can be walked to from the helm while the doors are open
        int[] start = null;
        for (SubsystemComponent s : systems) {
            if (s.type == SubsystemType.HELM || (start == null && s.type == SubsystemType.WEAPONS)) {
                start = new int[]{s.footprint.originX, s.footprint.originY};
            }
        }
        if (start == null) {
            problems.add(id + ": no helm or weapons console");
        } else {
            boolean[][] reached = new boolean[grid.height][grid.width];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            reached[start[1]][start[0]] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int[] t = queue.poll();
                for (Side side : Side.values()) {
                    int nx = t[0] + side.dx, ny = t[1] + side.dy;
                    if (!grid.inBounds(nx, ny) || reached[ny][nx] || !walkable(grid, nx, ny) || !grid.canPass(t[0], t[1], nx, ny)) continue;
                    reached[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
            for (int y = 0; y < grid.height; y++)
                for (int tx = 0; tx < grid.width; tx++)
                    if (walkable(grid, tx, y) && !reached[y][tx]) problems.add(id + ": crew cannot walk to " + tx + "," + y);
        }

        // With every door shut, the conduits still carry power to every system that needs it
        for (int y = 0; y < grid.height; y++)
            for (int tx = 0; tx < grid.width; tx++)
                for (Side side : new Side[]{Side.TOP, Side.RIGHT}) {
                    EdgeWall wall = grid.getEdge(tx, y, side);
                    if (wall != null && wall.hasBulkhead) wall.bulkheadSealed = true;
                }
        grid.gridDirty = true;
        for (int i = 0; i < 2; i++) world.update(1f / 60f);
        for (SubsystemComponent s : systems) {
            if (NEED_POWER.contains(s.type) && !s.powered) {
                problems.add(id + ": " + s.type + " at " + s.footprint.originX + "," + s.footprint.originY + " unpowered with the doors shut");
            }
        }
    }

    private static boolean piped(SubsystemComponent system, Fluid fluid) {
        for (SubsystemComponent tank : system.pipedTanks) if (tank.fluid == fluid) return true;
        return false;
    }

    private static boolean isPort(ShipGridComponent grid, int x, int y) {
        Entity e = grid.inBounds(x, y) ? grid.subsystemAt(x, y) : null;
        SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemType.DOCKING_PORT;
    }

    private static boolean walkable(ShipGridComponent grid, int x, int y) {
        return grid.getTile(x, y).isOpen() && !CrewObstacles.blocks(grid, null, x, y);
    }
}
