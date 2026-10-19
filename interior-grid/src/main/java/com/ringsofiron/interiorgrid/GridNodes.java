package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.IntArray;

/**
 * Numbers the places on a grid that power conduits and pipes run through, so a flood fill can
 * treat tiles and tile edges alike: tiles first ({@code y * width + x}), then the horizontal
 * edges (indexed like {@link ShipGridComponent#edgeH}), then the vertical edges (indexed like
 * {@link ShipGridComponent#edgeV}). Used by {@link PowerRoutingSystem} and {@link PipeRouting}.
 */
final class GridNodes {

    private GridNodes() {
    }

    static int count(ShipGridComponent grid) {
        return grid.width * grid.height + (grid.height + 1) * grid.width + grid.height * (grid.width + 1);
    }

    static int tiles(ShipGridComponent grid) {
        return grid.width * grid.height;
    }

    /** First vertical edge node; nodes from {@link #tiles} up to this are horizontal edges. */
    static int firstV(ShipGridComponent grid) {
        return grid.width * grid.height + (grid.height + 1) * grid.width;
    }

    static int hNode(ShipGridComponent grid, int x, int y) {
        return grid.width * grid.height + y * grid.width + x;
    }

    static int vNode(ShipGridComponent grid, int x, int y) {
        return firstV(grid) + y * (grid.width + 1) + x;
    }

    static int tileNode(ShipGridComponent grid, int x, int y) {
        return y * grid.width + x;
    }

    /** The node of the {@code side} edge of tile (x, y). */
    static int edgeNode(ShipGridComponent grid, int x, int y, Side side) {
        switch (side) {
            case BOTTOM: return hNode(grid, x, y);
            case TOP:    return hNode(grid, x, y + 1);
            case LEFT:   return vNode(grid, x, y);
            default:     return vNode(grid, x + 1, y);
        }
    }

    /** The array index ([y][x]) an edge node stands for, in edgeH-style arrays for horizontal edges. */
    static int edgeX(ShipGridComponent grid, int n) {
        int h0 = tiles(grid), v0 = firstV(grid);
        return n < v0 ? (n - h0) % grid.width : (n - v0) % (grid.width + 1);
    }

    static int edgeY(ShipGridComponent grid, int n) {
        int h0 = tiles(grid), v0 = firstV(grid);
        return n < v0 ? (n - h0) / grid.width : (n - v0) / (grid.width + 1);
    }

    static boolean isHorizontal(ShipGridComponent grid, int n) {
        return n >= tiles(grid) && n < firstV(grid);
    }

    /** The tiles beside edge node {@code n} and the edges that meet it at its two ends. */
    static void edgeNeighbors(ShipGridComponent grid, int n, IntArray out) {
        int x = edgeX(grid, n), y = edgeY(grid, n);
        if (isHorizontal(grid, n)) {
            // From vertex (x, y) to (x + 1, y), between tiles (x, y - 1) and (x, y)
            if (y < grid.height) out.add(tileNode(grid, x, y));
            if (y > 0) out.add(tileNode(grid, x, y - 1));
            vertexEdges(grid, x, y, out);
            vertexEdges(grid, x + 1, y, out);
        } else {
            // From vertex (x, y) to (x, y + 1), between tiles (x - 1, y) and (x, y)
            if (x < grid.width) out.add(tileNode(grid, x, y));
            if (x > 0) out.add(tileNode(grid, x - 1, y));
            vertexEdges(grid, x, y, out);
            vertexEdges(grid, x, y + 1, out);
        }
    }

    /** The up to four edges that meet at grid vertex (vx, vy). */
    static void vertexEdges(ShipGridComponent grid, int vx, int vy, IntArray out) {
        if (vx > 0 && vy <= grid.height) out.add(hNode(grid, vx - 1, vy));
        if (vx < grid.width && vy <= grid.height) out.add(hNode(grid, vx, vy));
        if (vy > 0 && vx <= grid.width) out.add(vNode(grid, vx, vy - 1));
        if (vy < grid.height && vx <= grid.width) out.add(vNode(grid, vx, vy));
    }

    /**
     * True when the wall an edge run lies in has been destroyed, cutting it: the thin wall on the
     * edge, or a full-tile wall on either side.
     */
    static boolean edgeCut(ShipGridComponent grid, int n) {
        int x = edgeX(grid, n), y = edgeY(grid, n);
        EdgeWall wall;
        Tile a, b;
        if (isHorizontal(grid, n)) {
            wall = grid.edgeH[y][x];
            a = grid.getTile(x, y);
            b = grid.getTile(x, y - 1);
        } else {
            wall = grid.edgeV[y][x];
            a = grid.getTile(x, y);
            b = grid.getTile(x - 1, y);
        }
        return (wall != null && wall.isDestroyed()) || wrecked(a) || wrecked(b);
    }

    /** A full-tile wall that has been destroyed, cutting any run along its edges. */
    static boolean wrecked(Tile t) {
        return t != null && t.isWall() && t.getIntegrity() <= 0f;
    }
}
