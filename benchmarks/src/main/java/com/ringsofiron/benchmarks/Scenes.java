package com.ringsofiron.benchmarks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What the benchmark scenes share: finding the assets, putting crew aboard and keeping them walking. */
final class Scenes {
    private Scenes() {
    }

    /** The game's assets: the {@code ringsofiron.assets} property, else {@code assets} beside the working directory. */
    static Path assets() {
        String prop = System.getProperty("ringsofiron.assets");
        if (prop != null) return Paths.get(prop);
        Path here = Paths.get(System.getProperty("user.dir"));
        return here.resolve("assets").toFile().isDirectory() ? here.resolve("assets") : here.getParent().resolve("assets");
    }

    /** Open deck tiles clear of mounted systems. */
    static List<int[]> floorTiles(ShipGridComponent grid) {
        List<int[]> out = new ArrayList<>();
        if (grid == null) return out;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor() && t.isOpen() && t.mount == null) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /** Puts up to {@code count} crew of {@code faction} aboard {@code ship}, spread over its deck. */
    static void crew(GameWorld world, Entity ship, int count, int faction, String prefix) {
        List<int[]> floor = floorTiles(ship.getComponent(ShipGridComponent.class));
        CrewRole[] roles = CrewRole.values();
        for (int i = 0; i < count && i < floor.size(); i++) {
            int[] t = floor.get((i * floor.size()) / count);
            CrewFactory.spawn(world.crew, ship, prefix + " " + i, roles[i % roles.length], faction, t[0], t[1]);
        }
    }

    /**
     * Keeps crew on their feet: anyone standing still with nothing to do is sent to a random floor
     * tile on their own deck, so the deck worlds always have walkers in them. Returns how many walk.
     * {@code floors} caches each deck's floor tiles; pass an {@code IdentityHashMap}.
     */
    static int keepWalking(GameWorld world, RandomXS128 random, Map<ShipGridComponent, List<int[]>> floors) {
        CrewService crew = world.crew;
        Array<GridNode> path = new Array<>();
        int walking = 0;
        for (Entity e : crew.crewEntities()) {
            GridPositionComponent p = crew.position(e);
            if (p.isMoving()) {
                walking++;
                continue;
            }
            if (p.walkway != null || p.crossing != null || !crew.crew(e).isConscious()) continue;
            if (crew.crew(e).task != null) continue;
            ShipGridComponent grid = crew.grid(p.ship);
            if (grid == null) continue;
            List<int[]> floor = floors.computeIfAbsent(grid, Scenes::floorTiles);
            if (floor.isEmpty()) continue;
            int[] goal = floor.get(random.nextInt(floor.size()));
            GridPathfinder finder = crew.pathfinder(grid);
            path.clear();
            if (finder.findPath(p.tileX(), p.tileY(), goal[0], goal[1], path)) {
                p.setPath(path);
                walking++;
            }
        }
        return walking;
    }
}
