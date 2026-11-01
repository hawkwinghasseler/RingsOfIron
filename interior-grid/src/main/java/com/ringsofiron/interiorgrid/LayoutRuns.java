package com.ringsofiron.interiorgrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs: how a chassis layout writes conduits, pipes and thin walls (see {@code ChassisDef}). A run
 * is a list of points, each step from one to the next straight across or straight up and down.
 *
 * <ul>
 *   <li>A <em>tile run</em> goes from tile to tile and lies on every tile it passes.</li>
 *   <li>An <em>edge run</em> goes from corner to corner along tile edges and lies on every edge it
 *       passes. Corner (x, y) is the bottom-left corner of tile (x, y), so a grid {@code width}
 *       wide has corners 0 to {@code width} across.</li>
 * </ul>
 * A run of one point is a single tile; an edge run needs two points at least.
 */
public final class LayoutRuns {

    private LayoutRuns() {
    }

    /** One tile edge: the {@code side} edge of tile (x, y), always a tile on the grid. */
    public static final class Edge {
        public final int x, y;
        public final Side side;

        public Edge(int x, int y, Side side) {
            this.x = x;
            this.y = y;
            this.side = side;
        }
    }

    /**
     * The tiles a tile run lies on, as {x, y}, in order (a tile where two steps meet is listed once).
     *
     * @throws IllegalArgumentException if the run is empty or a step is not straight
     */
    public static List<int[]> tiles(int[][] path) {
        check(path, 1, "tile");
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{path[0][0], path[0][1]});
        for (int i = 1; i < path.length; i++) {
            int[] a = path[i - 1], b = path[i];
            int dx = Integer.signum(b[0] - a[0]), dy = Integer.signum(b[1] - a[1]);
            for (int x = a[0], y = a[1]; x != b[0] || y != b[1]; ) {
                x += dx;
                y += dy;
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /**
     * The edges an edge run lies on, each named by a tile of a {@code width} by {@code height} grid.
     *
     * @throws IllegalArgumentException if the run has under two points, a step is not straight, or
     *                                  it leaves the grid's corners
     */
    public static List<Edge> edges(int[][] path, int width, int height) {
        check(path, 2, "edge");
        List<Edge> out = new ArrayList<>();
        for (int i = 1; i < path.length; i++) {
            int[] a = path[i - 1], b = path[i];
            int dx = Integer.signum(b[0] - a[0]), dy = Integer.signum(b[1] - a[1]);
            for (int x = a[0], y = a[1]; x != b[0] || y != b[1]; x += dx, y += dy) {
                // The edge from this corner one step on
                int ex = dx < 0 ? x - 1 : x, ey = dy < 0 ? y - 1 : y;
                out.add(dx != 0 ? horizontal(ex, ey, width, height) : vertical(ex, ey, width, height));
            }
        }
        return out;
    }

    /** The horizontal edge from corner (x, y) to (x + 1, y). */
    private static Edge horizontal(int x, int y, int width, int height) {
        if (x < 0 || x >= width || y < 0 || y > height) throw off(x, y);
        return y < height ? new Edge(x, y, Side.BOTTOM) : new Edge(x, y - 1, Side.TOP);
    }

    /** The vertical edge from corner (x, y) to (x, y + 1). */
    private static Edge vertical(int x, int y, int width, int height) {
        if (y < 0 || y >= height || x < 0 || x > width) throw off(x, y);
        return x < width ? new Edge(x, y, Side.LEFT) : new Edge(x - 1, y, Side.RIGHT);
    }

    private static IllegalArgumentException off(int x, int y) {
        return new IllegalArgumentException("Edge run leaves the grid at corner " + x + "," + y);
    }

    private static void check(int[][] path, int min, String what) {
        if (path == null || path.length < min) {
            throw new IllegalArgumentException((what.startsWith("e") ? "An " : "A ") + what + " run needs at least " + min
                    + " point" + (min > 1 ? "s" : ""));
        }
        for (int[] p : path) {
            if (p == null || p.length != 2) throw new IllegalArgumentException("A run's points are [x, y] pairs");
        }
        for (int i = 1; i < path.length; i++) {
            int[] a = path[i - 1], b = path[i];
            if (a[0] != b[0] && a[1] != b[1]) {
                throw new IllegalArgumentException("Run step " + a[0] + "," + a[1] + " to " + b[0] + "," + b[1]
                        + " is not straight across or up and down");
            }
        }
    }

    // ---- Writing runs ----

    /**
     * The fewest straight runs that lie on every set tile ({@code set[y][x]}) and no other: rows of
     * two or more first, then columns that reach a tile no row has, then the lone tiles. Runs may
     * cross where a row meets a column.
     */
    public static List<int[][]> tileRuns(boolean[][] set) {
        int h = set.length, w = h == 0 ? 0 : set[0].length;
        boolean[][] covered = new boolean[h][w];
        List<int[][]> out = new ArrayList<>();
        for (int y = h - 1; y >= 0; y--) {
            int x = 0;
            while (x < w) {
                if (!set[y][x]) {
                    x++;
                    continue;
                }
                int s = x;
                while (x < w && set[y][x]) x++;
                if (x - s >= 2) {
                    out.add(new int[][]{{s, y}, {x - 1, y}});
                    for (int i = s; i < x; i++) covered[y][i] = true;
                }
            }
        }
        for (int x = 0; x < w; x++) {
            int y = h - 1;
            while (y >= 0) {
                if (!set[y][x]) {
                    y--;
                    continue;
                }
                int top = y;
                boolean fresh = false;
                while (y >= 0 && set[y][x]) {
                    fresh |= !covered[y][x];
                    y--;
                }
                int bottom = y + 1;
                if (top > bottom && fresh) {
                    out.add(new int[][]{{x, bottom}, {x, top}});
                    for (int i = bottom; i <= top; i++) covered[i][x] = true;
                }
            }
        }
        for (int y = h - 1; y >= 0; y--)
            for (int x = 0; x < w; x++)
                if (set[y][x] && !covered[y][x]) out.add(new int[][]{{x, y}});
        return out;
    }

    /**
     * The straight runs, corner to corner, that lie on every set edge and no other.
     *
     * @param h horizontal edges, {@code h[y][x]} from corner (x, y) to (x + 1, y)
     * @param v vertical edges, {@code v[y][x]} from corner (x, y) to (x, y + 1)
     */
    public static List<int[][]> edgeRuns(boolean[][] h, boolean[][] v) {
        List<int[][]> out = new ArrayList<>();
        for (int y = 0; y < h.length; y++) {
            int x = 0, w = h[y].length;
            while (x < w) {
                if (!h[y][x]) {
                    x++;
                    continue;
                }
                int s = x;
                while (x < w && h[y][x]) x++;
                out.add(new int[][]{{s, y}, {x, y}});
            }
        }
        int rows = v.length, w = rows == 0 ? 0 : v[0].length;
        for (int x = 0; x < w; x++) {
            int y = 0;
            while (y < rows) {
                if (!v[y][x]) {
                    y++;
                    continue;
                }
                int s = y;
                while (y < rows && v[y][x]) y++;
                out.add(new int[][]{{x, s}, {x, y}});
            }
        }
        return out;
    }

    /** Marks the {@code side} edge of tile (x, y) in edge arrays shaped as {@link #edgeRuns} takes them. */
    public static void mark(boolean[][] h, boolean[][] v, int x, int y, Side side) {
        switch (side) {
            case BOTTOM: h[y][x] = true; break;
            case TOP:    h[y + 1][x] = true; break;
            case LEFT:   v[y][x] = true; break;
            default:     v[y][x + 1] = true; break;
        }
    }
}
