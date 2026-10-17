package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;

/**
 * Simulates the spread of fire and vacuum across the ship's interior grid using
 * a cellular automata approach. Runs on a slow fixed tick (1 second).
 *
 * Fire behaviour:
 *   - Consumes oxygen in the tile it occupies.
 *   - Spreads to adjacent flammable floor tiles that are not separated by sealed bulkheads
 *     or closed edge walls.
 *   - Extinguishes automatically when oxygenLevel reaches 0.
 *
 * Vacuum behaviour:
 *   - Vacuum spreads from hull breaches (destroyed hull tiles) or void borders.
 *   - Oxygen is rapidly equalised from pressurised tiles into vacuum tiles.
 *   - Sealed bulkheads and closed edge walls block spread.
 *
 * Walls only block while they stand: an internal wall or edge wall at integrity 0 lets
 * fire and air through, and a hull wall at integrity 0 is a breach to space.
 */
public class GridHazardSystem extends IteratingSystem {

    private static final float HAZARD_TICK = 1.0f; // seconds between cellular automata steps
    private static final float FIRE_OXYGEN_CONSUMPTION = 0.15f;
    private static final float FIRE_SPREAD_THRESHOLD = 0.2f; // min oxygen for fire to spread
    private static final float OXYGEN_BLEED_RATE = 0.25f; // oxygen lost per tick when exposed to vacuum

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);

    private float accumulator = 0f;

    public GridHazardSystem() {
        super(Family.all(ShipGridComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator >= HAZARD_TICK) {
            accumulator -= HAZARD_TICK;
            super.update(HAZARD_TICK); // process each entity
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipGridComponent grid = gridMapper.get(entity);
        processVacuum(grid);
        processFire(grid);
    }

    // -----------------------------------------------------------------------
    // Vacuum Spread
    // -----------------------------------------------------------------------

    private void processVacuum(ShipGridComponent grid) {
        // First pass: mark tiles that should have vacuum
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                if (tile == null || !tile.isOpen()) continue;

                if (isAdjacentToSpace(grid, x, y)) {
                    // This floor tile is directly adjacent to space — bleed oxygen
                    if (!tile.bulkheadSealed) {
                        tile.oxygenLevel = Math.max(0f, tile.oxygenLevel - OXYGEN_BLEED_RATE);
                        if (tile.oxygenLevel <= 0f) {
                            tile.hasVacuum = true;
                            tile.hasFire = false; // fire can't exist in vacuum
                        }
                    }
                }

                // Spread vacuum from neighbouring vacuum tiles
                if (tile.hasVacuum || tile.oxygenLevel < 0.05f) {
                    spreadVacuumToNeighbours(grid, x, y);
                }
            }
        }
    }

    private boolean isAdjacentToSpace(ShipGridComponent grid, int x, int y) {
        int[][] neighbors = {{x+1,y},{x-1,y},{x,y+1},{x,y-1}};
        for (int[] n : neighbors) {
            // A closed edge wall seals this side, even against open space
            if (grid.isEdgeBlocked(x, y, n[0], n[1])) continue;
            Tile neighbor = grid.getTile(n[0], n[1]);
            // Out-of-bounds counts as space, and void tiles count as space
            if (neighbor == null || neighbor.isSpaceExposed() || neighbor.hasVacuum) {
                return true;
            }
        }
        return false;
    }

    private void spreadVacuumToNeighbours(ShipGridComponent grid, int srcX, int srcY) {
        Tile src = grid.getTile(srcX, srcY);
        if (src == null || src.bulkheadSealed) return;

        int[][] neighbors = {{srcX+1,srcY},{srcX-1,srcY},{srcX,srcY+1},{srcX,srcY-1}};
        for (int[] n : neighbors) {
            if (!grid.canPass(srcX, srcY, n[0], n[1])) continue;
            Tile neighbor = grid.getTile(n[0], n[1]);
            if (neighbor.bulkheadSealed) continue;
            // Equalise oxygen
            if (neighbor.oxygenLevel > src.oxygenLevel) {
                float bleed = Math.min(OXYGEN_BLEED_RATE, neighbor.oxygenLevel - src.oxygenLevel);
                neighbor.oxygenLevel -= bleed;
                if (neighbor.oxygenLevel <= 0f) {
                    neighbor.oxygenLevel = 0f;
                    neighbor.hasVacuum = true;
                    neighbor.hasFire = false;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Fire Spread
    // -----------------------------------------------------------------------

    private void processFire(ShipGridComponent grid) {
        // Collect tiles that need to catch fire this tick (avoid modifying during iteration)
        Array<int[]> newFires = new Array<>();

        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                if (tile == null || !tile.hasFire) continue;

                // Fire consumes oxygen
                tile.oxygenLevel = Math.max(0f, tile.oxygenLevel - FIRE_OXYGEN_CONSUMPTION);
                if (tile.oxygenLevel <= 0f) {
                    tile.hasFire = false; // Starved of oxygen
                    continue;
                }

                // Damage tile integrity
                tile.setIntegrity(tile.getIntegrity() - 0.1f);

                // Spread to adjacent flammable floor tiles with enough oxygen
                int[][] neighbors = {{x+1,y},{x-1,y},{x,y+1},{x,y-1}};
                for (int[] n : neighbors) {
                    if (!grid.canPass(x, y, n[0], n[1])) continue;
                    Tile neighbor = grid.getTile(n[0], n[1]);
                    if (neighbor.hasFire || neighbor.bulkheadSealed) continue;
                    if (neighbor.isFlammable && neighbor.oxygenLevel >= FIRE_SPREAD_THRESHOLD) {
                        newFires.add(new int[]{n[0], n[1]});
                    }
                }
            }
        }

        for (int[] pos : newFires) {
            Tile t = grid.getTile(pos[0], pos[1]);
            if (t != null) t.hasFire = true;
        }
    }
}
