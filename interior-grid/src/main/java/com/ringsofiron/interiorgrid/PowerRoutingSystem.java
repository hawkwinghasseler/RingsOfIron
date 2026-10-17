package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;

/**
 * Calculates power connectivity across the ship's interior grid using Breadth-First Search.
 *
 * Starting from all REACTOR tiles (SubsystemComponent.SubsystemType.REACTOR), the system
 * floods outward across connected POWER_CONDUIT and FLOOR tiles. Any subsystem whose
 * anchor tile is reachable is marked as powered.
 *
 * Runs once per second to reflect structural changes.
 */
public class PowerRoutingSystem extends IteratingSystem {

    private static final float ROUTING_TICK = 1.0f;

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> registryMapper =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper =
            ComponentMapper.getFor(SubsystemComponent.class);

    private float accumulator = 0f;

    public PowerRoutingSystem() {
        super(Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator >= ROUTING_TICK) {
            accumulator -= ROUTING_TICK;
            super.update(ROUTING_TICK);
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipGridComponent grid = gridMapper.get(entity);
        ShipSubsystemRegistryComponent registry = registryMapper.get(entity);

        // Step 1: Clear all power flags on tiles
        clearAllPower(grid);

        // Step 2: Find all reactor locations as BFS starting points
        Array<int[]> queue = new Array<>();
        for (Entity subsysEntity : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(subsysEntity);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) {
                queue.add(new int[]{sub.gridX, sub.gridY});
                Tile reactorTile = grid.getTile(sub.gridX, sub.gridY);
                if (reactorTile != null) reactorTile.isPowered = true;
            }
        }

        // Step 3: BFS flood across floor and conduit tiles
        boolean[][] visited = new boolean[grid.height][grid.width];
        for (int[] start : queue) {
            visited[start[1]][start[0]] = true;
        }

        int head = 0;
        while (head < queue.size) {
            int[] current = queue.get(head++);
            int cx = current[0], cy = current[1];

            int[][] neighbors = {{cx+1,cy},{cx-1,cy},{cx,cy+1},{cx,cy-1}};
            for (int[] n : neighbors) {
                int nx = n[0], ny = n[1];
                if (!grid.inBounds(nx, ny) || visited[ny][nx]) continue;
                Tile neighbor = grid.getTile(nx, ny);
                if (neighbor == null || neighbor.isWall() || neighbor.isVoid()) continue;
                // Power flows through conduits and floors
                visited[ny][nx] = true;
                neighbor.isPowered = true;
                queue.add(new int[]{nx, ny});
            }
        }

        // Step 4: Update subsystem powered status based on tile connectivity
        for (Entity subsysEntity : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(subsysEntity);
            if (sub == null) continue;
            Tile tile = grid.getTile(sub.gridX, sub.gridY);
            sub.powered = (tile != null) && tile.isPowered;
            sub.isOperational = sub.powered && sub.health > 0f;
        }
    }

    private void clearAllPower(ShipGridComponent grid) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null) t.isPowered = false;
            }
        }
    }
}
