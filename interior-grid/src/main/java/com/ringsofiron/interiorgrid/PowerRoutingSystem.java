package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;

/**
 * Calculates power connectivity across the ship's interior grid using Breadth-First Search.
 *
 * Starting from every footprint tile of each REACTOR that is switched on and not
 * destroyed, the system floods
 * outward across connected POWER_CONDUIT and FLOOR tiles. A subsystem is powered when
 * any tile of its footprint is reachable.
 *
 * Power does not pass through intact full-tile walls, through closed edge walls (except
 * where a conduit runs on both sides of the wall), or through tiles at integrity 0:
 * a destroyed conduit stops conducting.
 *
 * Runs once per second to reflect structural changes, or on the next frame when
 * {@link ShipGridComponent#gridDirty} is set.
 */
public class PowerRoutingSystem extends IteratingSystem {

    private static final float ROUTING_TICK = 1.0f;

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> registryMapper =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper =
            ComponentMapper.getFor(SubsystemComponent.class);

    private float accumulator = 0f;
    private boolean tickDue = false;

    public PowerRoutingSystem() {
        super(Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        tickDue = accumulator >= ROUTING_TICK;
        if (tickDue) accumulator -= ROUTING_TICK;
        // Always iterate so dirty grids reroute immediately; processEntity skips the rest
        super.update(ROUTING_TICK);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipGridComponent grid = gridMapper.get(entity);
        if (!tickDue && !grid.gridDirty) return;
        grid.gridDirty = false;
        ShipSubsystemRegistryComponent registry = registryMapper.get(entity);

        // Step 1: Clear all power flags on tiles
        clearAllPower(grid);

        // Step 2: Seed the BFS with every footprint tile of each working reactor
        Array<int[]> queue = new Array<>();
        boolean[][] visited = new boolean[grid.height][grid.width];
        for (Entity subsysEntity : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(subsysEntity);
            // Seed from the reactor's own state, never from last tick's isOperational, so a
            // reactor that was off can come back on
            if (sub == null || sub.type != SubsystemComponent.SubsystemType.REACTOR
                    || !sub.switchedOn || sub.isDestroyed()) continue;
            MountFootprint f = sub.footprint;
            for (int y = f.originY; y < f.originY + f.height(); y++) {
                for (int x = f.originX; x < f.originX + f.width(); x++) {
                    Tile t = grid.getTile(x, y);
                    if (t == null || visited[y][x]) continue;
                    // A reactor always powers its own footprint; only intact tiles pass power on
                    visited[y][x] = true;
                    t.isPowered = true;
                    if (conducts(t)) queue.add(new int[]{x, y});
                }
            }
        }

        // Step 3: BFS flood across floor and conduit tiles
        int head = 0;
        while (head < queue.size) {
            int[] current = queue.get(head++);
            int cx = current[0], cy = current[1];
            Tile currentTile = grid.getTile(cx, cy);

            int[][] neighbors = {{cx+1,cy},{cx-1,cy},{cx,cy+1},{cx,cy-1}};
            for (int[] n : neighbors) {
                int nx = n[0], ny = n[1];
                if (!grid.inBounds(nx, ny) || visited[ny][nx]) continue;
                Tile neighbor = grid.getTile(nx, ny);
                if (!conducts(neighbor)) continue;
                if (grid.isEdgeBlocked(cx, cy, nx, ny)
                        && !(currentTile.isPowerConduit() && neighbor.isPowerConduit())) continue;
                // Power flows through conduits and floors
                visited[ny][nx] = true;
                neighbor.isPowered = true;
                queue.add(new int[]{nx, ny});
            }
        }

        // Step 4: A subsystem is powered if any tile of its footprint is powered
        for (Entity subsysEntity : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(subsysEntity);
            if (sub == null) continue;
            sub.powered = false;
            MountFootprint f = sub.footprint;
            for (int y = f.originY; y < f.originY + f.height() && !sub.powered; y++) {
                for (int x = f.originX; x < f.originX + f.width(); x++) {
                    Tile tile = grid.getTile(x, y);
                    if (tile != null && tile.isPowered) {
                        sub.powered = true;
                        break;
                    }
                }
            }
            sub.isOperational = sub.switchedOn && sub.powered && !sub.isDestroyed();
        }
    }

    /** Floors and conduits carry power until they are destroyed; walls never do. */
    private static boolean conducts(Tile t) {
        return t.isFloor() && !t.isWall() && t.getIntegrity() > 0f;
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
