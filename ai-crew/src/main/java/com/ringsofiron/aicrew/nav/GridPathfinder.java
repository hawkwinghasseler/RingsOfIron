package com.ringsofiron.aicrew.nav;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.Heuristic;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;

/**
 * A* pathfinding over one ship's {@link GridNavGraph}, plus a breadth-first search for
 * "the nearest walkable tile that satisfies X" (safe air, a work spot next to a fire).
 */
public class GridPathfinder {

    /** Manhattan distance: exact on a 4-connected grid with unit costs. */
    public static final Heuristic<GridNode> MANHATTAN =
            (a, b) -> Math.abs(a.x - b.x) + Math.abs(a.y - b.y);

    /** A test on a tile, for {@link #nearest}. */
    public interface TilePredicate {
        boolean test(int x, int y);
    }

    public final GridNavGraph graph;
    private final IndexedAStarPathFinder<GridNode> astar;
    private final DefaultGraphPath<GridNode> scratch = new DefaultGraphPath<>();

    public GridPathfinder(ShipGridComponent grid) {
        this.graph = new GridNavGraph(grid);
        this.astar = new IndexedAStarPathFinder<>(graph);
    }

    /**
     * Finds a path from (sx, sy) to (tx, ty), both included, into {@code out}.
     *
     * @return false if either end is not walkable or there is no route.
     */
    public boolean findPath(int sx, int sy, int tx, int ty, Array<GridNode> out) {
        out.clear();
        GridNode start = graph.node(sx, sy), goal = graph.node(tx, ty);
        if (start == null || goal == null || !graph.isWalkable(tx, ty)) return false;
        if (start == goal) {
            out.add(start);
            return true;
        }
        scratch.clear();
        if (!astar.searchNodePath(start, goal, MANHATTAN, scratch)) return false;
        out.addAll(scratch.nodes);
        return true;
    }

    /**
     * Breadth-first from (sx, sy) over walkable tiles to the closest one passing
     * {@code goal}, writing the path into {@code out}. The start tile counts. Works from
     * a tile that is not itself walkable (e.g. a crew member caught on a sealed tile) by
     * stepping off it.
     *
     * @return false if no reachable tile passes.
     */
    public boolean nearest(int sx, int sy, TilePredicate goal, Array<GridNode> out) {
        out.clear();
        ShipGridComponent grid = graph.grid;
        if (!grid.inBounds(sx, sy)) return false;
        int w = grid.width, n = w * grid.height;
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -2);
        IntArray queue = new IntArray();
        int start = sy * w + sx;
        parent[start] = -1;
        queue.add(start);
        int head = 0, found = -1;
        while (head < queue.size) {
            int i = queue.get(head++);
            int x = i % w, y = i / w;
            if (goal.test(x, y) && graph.isWalkable(x, y)) {
                found = i;
                break;
            }
            for (Side s : Side.values()) {
                int nx = x + s.dx, ny = y + s.dy;
                if (!grid.inBounds(nx, ny)) continue;
                int ni = ny * w + nx;
                if (parent[ni] != -2) continue;
                if (!graph.canStep(x, y, nx, ny)) continue;
                parent[ni] = i;
                queue.add(ni);
            }
        }
        if (found < 0) return false;
        for (int i = found; i != -1; i = parent[i]) out.add(graph.node(i % w, i / w));
        out.reverse();
        return true;
    }
}
