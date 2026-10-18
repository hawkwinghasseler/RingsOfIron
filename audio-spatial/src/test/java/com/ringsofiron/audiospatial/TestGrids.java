package com.ringsofiron.audiospatial;

import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

/** Small grids for tests. Tiles are 1 m with the grid origin at ship-local (0, 0). */
final class TestGrids {
    private TestGrids() {}

    /** A w x h block of plain floor. */
    static ShipGridComponent floor(int w, int h) {
        ShipGridComponent g = new ShipGridComponent(w, h, 1f);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                g.getTile(x, y).floorType = Tile.FloorType.FLOOR;
        return g;
    }

    static void wallTile(ShipGridComponent g, int x, int y) {
        g.getTile(x, y).wallType = Tile.WallType.INTERNAL;
    }

    /** A thin wall on the right edge of every tile in column x. Returns the walls. */
    static EdgeWall[] edgeColumn(ShipGridComponent g, int x) {
        EdgeWall[] walls = new EdgeWall[g.height];
        for (int y = 0; y < g.height; y++) {
            walls[y] = new EdgeWall(WeightClass.MEDIUM);
            g.setEdge(x, y, Side.RIGHT, walls[y]);
        }
        return walls;
    }
}
