package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

/**
 * Holds the 2D interior tile grid for a ship or station entity.
 *
 * All coordinates within this component are in LOCAL space relative to the
 * entity's TransformComponent origin. The tile at [0][0] is the top-left.
 */
public class ShipGridComponent implements Component {

    public final int width;
    public final int height;
    /** Tile size in world units (meters). */
    public final float tileSize;
    /** The flat 2D grid stored in row-major order: tiles[y][x] */
    public final Tile[][] tiles;

    public ShipGridComponent(int width, int height, float tileSize) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.tiles = new Tile[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new Tile();
            }
        }
    }

    /**
     * Returns the tile at grid coordinates (x, y), or null if out of bounds.
     */
    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return tiles[y][x];
    }

    /**
     * Converts local tile indices to a local-space position (the center of the tile).
     */
    public Vector2 tileToLocal(int tileX, int tileY) {
        float lx = tileX * tileSize + tileSize * 0.5f;
        float ly = tileY * tileSize + tileSize * 0.5f;
        return new Vector2(lx, ly);
    }

    /**
     * Converts a local-space position to tile indices. Returns (-1, -1) if out of bounds.
     */
    public int[] localToTile(float localX, float localY) {
        int tx = (int) (localX / tileSize);
        int ty = (int) (localY / tileSize);
        if (tx < 0 || tx >= width || ty < 0 || ty >= height) {
            return new int[]{-1, -1};
        }
        return new int[]{tx, ty};
    }

    /**
     * Returns true if the given tile coordinates are within the grid bounds.
     */
    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
}
