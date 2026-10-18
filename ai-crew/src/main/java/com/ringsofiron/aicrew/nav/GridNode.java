package com.ringsofiron.aicrew.nav;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.utils.Array;

/** One tile of the interior grid as a pathfinding node. */
public final class GridNode {
    public final int x, y;
    /** {@code y * width + x}; the node's index in its {@link GridNavGraph}. */
    public final int index;
    /** Reused output buffer for {@link GridNavGraph#getConnections}. */
    final Array<Connection<GridNode>> connections = new Array<>(4);

    GridNode(int x, int y, int index) {
        this.x = x;
        this.y = y;
        this.index = index;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
