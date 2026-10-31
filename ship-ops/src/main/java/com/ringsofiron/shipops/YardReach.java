package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/** Where a station's hands can get to aboard a docked ship, for {@link StationYard}'s work. */
final class YardReach {
    private YardReach() {
    }

    public static boolean walkway(Entity ship, Entity station) {
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        if (g == null) return false;
        for (GridLink l : g.links) {
            if (l.otherShip == station && l.canCross()) return true;
        }
        return false;
    }

    /**
     * The tiles of {@code ship}'s grid crew can walk to from its open links onto {@code from}: where
     * someone coming aboard across the dock can get to.
     */
    public static boolean[][] reachable(ShipGridComponent grid, CrewService crew, Entity from) {
        boolean[][] seen = new boolean[grid.height][grid.width];
        com.ringsofiron.aicrew.nav.GridNavGraph nav = crew.pathfinder(grid).graph;
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        for (GridLink l : grid.links) {
            if (l.otherShip == from && l.canCross() && nav.isWalkable(l.x, l.y) && !seen[l.y][l.x]) {
                seen[l.y][l.x] = true;
                open.add(new int[]{l.x, l.y});
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] t = open.poll();
            for (int[] s : steps) {
                int nx = t[0] + s[0], ny = t[1] + s[1];
                if (!grid.inBounds(nx, ny) || seen[ny][nx] || !nav.isWalkable(nx, ny) || !grid.canPass(t[0], t[1], nx, ny)) continue;
                seen[ny][nx] = true;
                open.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    /** True if some tile from which {@code d} is worked (inside it or beside it) is in {@code reach}. */
    static boolean reachable(boolean[][] reach, StationYard.Damage d) {
        for (int y = d.y - 1; y <= d.y + d.h; y++) {
            for (int x = d.x - 1; x <= d.x + d.w; x++) {
                boolean corner = (x < d.x || x >= d.x + d.w) && (y < d.y || y >= d.y + d.h);
                if (corner || y < 0 || x < 0 || y >= reach.length || x >= reach[0].length) continue;
                if (reach[y][x]) return true;
            }
        }
        return false;
    }

    /** The tile in {@code reach} nearest (x, y), where a part for a mount there is set down. */
    public static int[] nearest(boolean[][] reach, int x, int y) {
        int[] best = {x, y};
        int bestD = Integer.MAX_VALUE;
        for (int ty = 0; ty < reach.length; ty++) {
            for (int tx = 0; tx < reach[ty].length; tx++) {
                int d = Math.abs(tx - x) + Math.abs(ty - y);
                if (reach[ty][tx] && d < bestD) {
                    bestD = d;
                    best = new int[]{tx, ty};
                }
            }
        }
        return best;
    }

    /** A tile of {@code piece} with an open floor tile beside it, where a technician can work it from. */
    static int[] reachableTileOf(ShipGridComponent grid, HullPiece piece) {
        int[] any = null;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).piece != piece) continue;
                if (any == null) any = new int[]{x, y};
                int[][] n = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
                for (int[] p : n) {
                    Tile t = grid.getTile(p[0], p[1]);
                    if (t != null && t.isFloor()) return new int[]{x, y};
                }
            }
        }
        return any != null ? any : new int[]{piece.originX, piece.originY};
    }
}
