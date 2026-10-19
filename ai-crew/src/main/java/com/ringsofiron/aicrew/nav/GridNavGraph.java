package com.ringsofiron.aicrew.nav;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.DefaultConnection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;

/**
 * The walkable tiles of a ship's interior grid as a gdx-ai {@link IndexedGraph}.
 *
 * Two orthogonally adjacent open tiles are connected when {@link ShipGridComponent#canPass}
 * allows it (walls, edge walls, doors, destroyed walls). Mounted systems don't block: crew
 * walk over every mount footprint until systems get colliders of their own. Connections are computed when asked for, straight from the grid, so
 * doors opening or closing and walls being destroyed take effect on the next search
 * with no rebuild step.
 *
 * A search can be given a {@link DoorKey}: then a shut bulkhead door between two open tiles is
 * a way through for whoever holds the key, at {@link #DOOR_COST} extra, since they stop to open
 * it. {@link #canStep} itself never counts shut doors: it is whether the way is open now.
 */
public class GridNavGraph implements IndexedGraph<GridNode> {

    /** Extra cost of a step through a shut door, in tiles walked: the time spent opening it. */
    public static final float DOOR_COST = 2f;

    /** Which shut doors a crew member may open, for a search on their behalf. */
    public interface DoorKey {
        /** True if the searcher may open the shut door between (x, y) and its neighbour (nx, ny), going that way. */
        boolean mayOpen(int x, int y, int nx, int ny);
    }

    public final ShipGridComponent grid;
    private final GridNode[] nodes;
    /** The key of the search under way, or null; set by {@link GridPathfinder} around each search. */
    DoorKey key;

    public GridNavGraph(ShipGridComponent grid) {
        this.grid = grid;
        nodes = new GridNode[grid.width * grid.height];
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                int i = y * grid.width + x;
                nodes[i] = new GridNode(x, y, i);
            }
        }
    }

    /** The node for tile (x, y), or null if out of bounds. */
    public GridNode node(int x, int y) {
        return grid.inBounds(x, y) ? nodes[y * grid.width + x] : null;
    }

    /** True if crew can stand on tile (x, y): open, mounted system or not. */
    public boolean isWalkable(int x, int y) {
        Tile t = grid.getTile(x, y);
        return t != null && t.isOpen();
    }

    /** True if crew can step from (x, y) to the orthogonal neighbour (nx, ny). */
    public boolean canStep(int x, int y, int nx, int ny) {
        return isWalkable(nx, ny) && grid.canPass(x, y, nx, ny);
    }

    /**
     * The shut bulkhead door between two adjacent tiles, or null: an intact edge wall with a sealed
     * door in it. Only doors between two tiles of this grid; a port door faces out of it.
     */
    public EdgeWall shutDoor(int x, int y, int nx, int ny) {
        if (!grid.inBounds(x, y) || !grid.inBounds(nx, ny)) return null;
        EdgeWall wall = grid.getEdgeBetween(x, y, nx, ny);
        return wall != null && wall.hasBulkhead && wall.blocks() ? wall : null;
    }

    /**
     * True if the step from (x, y) to (nx, ny) is a shut door that {@code key} opens, with open
     * tiles on both sides.
     */
    public boolean opensDoor(int x, int y, int nx, int ny, DoorKey key) {
        if (key == null || !isWalkable(x, y) || !isWalkable(nx, ny)) return false;
        return shutDoor(x, y, nx, ny) != null && key.mayOpen(x, y, nx, ny);
    }

    /** True if crew searching with the current key can step from (x, y) to (nx, ny): open now, or through a door they may open. */
    boolean canTraverse(int x, int y, int nx, int ny) {
        return canStep(x, y, nx, ny) || opensDoor(x, y, nx, ny, key);
    }

    @Override
    public Array<Connection<GridNode>> getConnections(GridNode from) {
        Array<Connection<GridNode>> out = from.connections;
        out.clear();
        for (Side s : Side.values()) {
            int nx = from.x + s.dx, ny = from.y + s.dy;
            if (canStep(from.x, from.y, nx, ny)) {
                out.add(new DefaultConnection<>(from, nodes[ny * grid.width + nx]));
            } else if (opensDoor(from.x, from.y, nx, ny, key)) {
                out.add(new DoorConnection(from, nodes[ny * grid.width + nx]));
            }
        }
        return out;
    }

    /** A step through a shut door: dearer than walking, for the time it takes to open. */
    private static final class DoorConnection extends DefaultConnection<GridNode> {
        DoorConnection(GridNode from, GridNode to) {
            super(from, to);
        }

        @Override
        public float getCost() {
            return 1f + DOOR_COST;
        }
    }

    @Override
    public int getIndex(GridNode node) {
        return node.index;
    }

    @Override
    public int getNodeCount() {
        return nodes.length;
    }
}
