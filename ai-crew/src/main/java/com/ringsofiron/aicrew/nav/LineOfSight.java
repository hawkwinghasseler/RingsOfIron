package com.ringsofiron.aicrew.nav;

import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Line of sight across the interior grid by supercover traversal (Amanatides &amp; Woo):
 * every tile the segment touches is visited, so sight cannot slip between two
 * diagonal walls. Intact full-tile walls, sealed bulkhead tiles and closed edge walls
 * block. Edge walls are checked each time the ray crosses a tile boundary. A ray that
 * passes exactly through a tile corner touches both tiles beside the corner, so it is
 * clear only if both of them and all four edges around the corner are clear.
 *
 * Private copy until combat-sys's shared {@code GridRayTraversal} lands.
 */
public final class LineOfSight {

    private LineOfSight() {}

    /** True if a tile stops sight. */
    public static boolean blocksSight(Tile t) {
        return t == null || t.isIntactWall() || t.bulkheadSealed || t.isVoid();
    }

    /**
     * True if the centre of tile (x0, y0) can see the centre of tile (x1, y1). The end
     * tiles themselves are not tested for blocking, only what lies between and the
     * walls on their edges.
     */
    public static boolean canSee(ShipGridComponent grid, int x0, int y0, int x1, int y1) {
        return canSee(grid, x0 + 0.5f, y0 + 0.5f, x1 + 0.5f, y1 + 0.5f);
    }

    /** As {@link #canSee(ShipGridComponent, int, int, int, int)} from and to points in tile units. */
    public static boolean canSee(ShipGridComponent grid, float ax, float ay, float bx, float by) {
        int x = (int) Math.floor(ax), y = (int) Math.floor(ay);
        int ex = (int) Math.floor(bx), ey = (int) Math.floor(by);
        float dx = bx - ax, dy = by - ay;
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        float tDeltaX = stepX != 0 ? Math.abs(1f / dx) : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dy) : Float.POSITIVE_INFINITY;
        float tMaxX = stepX > 0 ? (x + 1 - ax) * tDeltaX : (stepX < 0 ? (ax - x) * tDeltaX : Float.POSITIVE_INFINITY);
        float tMaxY = stepY > 0 ? (y + 1 - ay) * tDeltaY : (stepY < 0 ? (ay - y) * tDeltaY : Float.POSITIVE_INFINITY);
        final float eps = 1e-6f;
        int guard = grid.width + grid.height + 4;
        while ((x != ex || y != ey) && guard-- > 0) {
            if (Math.abs(tMaxX - tMaxY) < eps) {
                // Through a corner: the ray touches both side tiles, so both must be clear,
                // and so must every edge on the two ways around the corner.
                int nx = x + stepX, ny = y + stepY;
                if (!clearStep(grid, x, y, nx, y) || !clearStep(grid, nx, y, nx, ny)
                        || !clearStep(grid, x, y, x, ny) || !clearStep(grid, x, ny, nx, ny)) return false;
                if (!isEnd(nx, y, ex, ey) && blocksSight(grid.getTile(nx, y))) return false;
                if (!isEnd(x, ny, ex, ey) && blocksSight(grid.getTile(x, ny))) return false;
                x = nx;
                y = ny;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            } else if (tMaxX < tMaxY) {
                if (!clearStep(grid, x, y, x + stepX, y)) return false;
                x += stepX;
                tMaxX += tDeltaX;
            } else {
                if (!clearStep(grid, x, y, x, y + stepY)) return false;
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (!isEnd(x, y, ex, ey) && blocksSight(grid.getTile(x, y))) return false;
        }
        return true;
    }

    private static boolean isEnd(int x, int y, int ex, int ey) {
        return x == ex && y == ey;
    }

    private static boolean clearStep(ShipGridComponent grid, int x, int y, int nx, int ny) {
        return !grid.isEdgeBlocked(x, y, nx, ny);
    }
}
