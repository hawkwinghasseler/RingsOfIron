package com.ringsofiron.aicrew.nav;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.DefaultConnection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.utils.Array;
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
 */
public class GridNavGraph implements IndexedGraph<GridNode> {

    public final ShipGridComponent grid;
    private final GridNode[] nodes;

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

    @Override
    public Array<Connection<GridNode>> getConnections(GridNode from) {
        Array<Connection<GridNode>> out = from.connections;
        out.clear();
        for (Side s : Side.values()) {
            int nx = from.x + s.dx, ny = from.y + s.dy;
            if (canStep(from.x, from.y, nx, ny)) out.add(new DefaultConnection<>(from, nodes[ny * grid.width + nx]));
        }
        return out;
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
