package com.ringsofiron.audiospatial;

import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Counts the walls a sound passes through on a ship's interior grid, by walking a Bresenham
 * line from emitter tile to listener tile.
 *
 * A barrier is entered each time the line steps from open space into an intact wall tile, a
 * sealed bulkhead tile, void (vacuum outside the hull) or off the grid; a run of wall tiles
 * counts once, so a thick wall is still one wall. Crossing an edge wall that blocks (intact,
 * with no door or a sealed one) counts one more. Destroyed walls and open doors let sound through.
 *
 * Where the line steps diagonally it could pass either side of the corner, so it takes the
 * quieter of the two orthogonal routes: sound leaks around corners.
 */
public final class GridOcclusion {
    private GridOcclusion() {}

    /** True if the tile at (x, y) stops sound: intact wall, sealed bulkhead, void or off the grid. */
    public static boolean isBarrier(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        return t == null || t.isVoid() || t.isIntactWall() || t.bulkheadSealed;
    }

    private static boolean edgeBlocks(ShipGridComponent grid, int x, int y, int nx, int ny) {
        EdgeWall e = grid.getEdgeBetween(x, y, nx, ny);
        return e != null && e.blocks();
    }

    /** Barriers met stepping orthogonally from (x, y) into (nx, ny). */
    private static int stepCost(ShipGridComponent grid, int x, int y, int nx, int ny) {
        int cost = edgeBlocks(grid, x, y, nx, ny) ? 1 : 0;
        if (isBarrier(grid, nx, ny) && !isBarrier(grid, x, y)) cost++;
        return cost;
    }

    /**
     * The number of walls between tile (x0, y0) and tile (x1, y1). The start tile itself never
     * counts, so a sound mounted inside a wall is not muffled by that wall.
     */
    public static int countWalls(ShipGridComponent grid, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0, walls = 0;
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            boolean stepX = e2 > -dy, stepY = e2 < dx;
            int nx = stepX ? x + sx : x, ny = stepY ? y + sy : y;
            if (stepX && stepY) {
                int viaX = stepCost(grid, x, y, nx, y) + stepCost(grid, nx, y, nx, ny);
                int viaY = stepCost(grid, x, y, x, ny) + stepCost(grid, x, ny, nx, ny);
                walls += Math.min(viaX, viaY);
            } else {
                walls += stepCost(grid, x, y, nx, ny);
            }
            if (stepX) err -= dy;
            if (stepY) err += dx;
            x = nx;
            y = ny;
        }
        return walls;
    }
}
