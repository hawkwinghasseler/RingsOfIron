package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Arrays;

/**
 * The tile edges of a deck that stop light, as line segments in the grid's local meters: the
 * faces of standing walls and hull, the deck's outer edge, and shut doors and edge walls between
 * two lit tiles. Open doors, holes blown in a wall and docked ports open to walk through let it
 * pass. Mounted systems do not cast shadows, since their own lights sit inside them.
 */
public final class LightBarriers {
    private final ShipGridComponent grid;
    /** Which edges stop light: vertical edges (x from 0 to width, then y), then horizontal ones. */
    private boolean[] edges = new boolean[0];
    private boolean[] scratch = new boolean[0];
    private final FloatArray segments = new FloatArray();

    public LightBarriers(ShipGridComponent grid) {
        this.grid = grid;
    }

    /** Re-reads the grid; true if the barriers changed since the last call (always the first time). */
    public boolean update() {
        int w = grid.width, h = grid.height;
        int n = (w + 1) * h + w * (h + 1);
        boolean first = edges.length != n;
        if (scratch.length != n) scratch = new boolean[n];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x <= w; x++) scratch[i++] = blocks(x - 1, y, x, y, Side.RIGHT);
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x < w; x++) scratch[i++] = blocks(x, y - 1, x, y, Side.TOP);
        }
        if (!first && Arrays.equals(scratch, edges)) return false;
        boolean[] t = edges;
        edges = scratch;
        scratch = t.length == n ? t : new boolean[n];
        build();
        return true;
    }

    /** The barriers as segments, x1 y1 x2 y2 each, in the grid's local meters; runs along a grid line are merged. */
    public FloatArray segments() {
        return segments;
    }

    /** True if light reaches over tile (x, y): anything but empty space and standing walls. */
    static boolean transparent(Tile t) {
        return t != null && !t.isVoid() && !t.isIntactWall();
    }

    private boolean blocks(int ax, int ay, int bx, int by, Side side) {
        Tile a = grid.getTile(ax, ay), b = grid.getTile(bx, by);
        boolean aLit = transparent(a), bLit = transparent(b);
        if (aLit && bLit) return grid.isEdgeBlocked(ax, ay, bx, by);
        if (aLit) return !openPort(ax, ay, side);
        if (bLit) return !openPort(bx, by, side.opposite());
        return false;
    }

    private boolean openPort(int x, int y, Side side) {
        if (grid.links.size == 0) return false;
        GridLink link = grid.linkAt(x, y, side);
        return link != null && link.canCross();
    }

    private void build() {
        segments.clear();
        int w = grid.width, h = grid.height;
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x <= w; x++) {
                if (edges[i + x] && (y == 0 || !edges[i + x - (w + 1)])) {
                    int end = y;
                    while (end + 1 < h && edges[i + x + (end + 1 - y) * (w + 1)]) end++;
                    segments.addAll(ox + x * s, oy + y * s, ox + x * s, oy + (end + 1) * s);
                }
            }
            i += w + 1;
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x < w; x++) {
                if (edges[i + x] && (x == 0 || !edges[i + x - 1])) {
                    int end = x;
                    while (end + 1 < w && edges[i + end + 1]) end++;
                    segments.addAll(ox + x * s, oy + y * s, ox + (end + 1) * s, oy + y * s);
                }
            }
            i += w;
        }
    }
}
