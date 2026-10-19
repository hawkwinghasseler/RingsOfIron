package com.ringsofiron.audiospatial;

import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * How sound from one source tile spreads through a ship's rooms: the indirect, "through the
 * rooms" path, as opposed to the straight line {@link GridOcclusion} traces.
 *
 * It is a shortest-path flood over the tile grid from the source. Moving through open air
 * costs distance only; every boundary crossed costs a fixed loss in decibels: a little for an
 * open doorway, more for a sealed door, most for a wall (plus a little per extra tile of wall
 * thickness). So the loss grows with the number of rooms between source and listener, and
 * open doors let more through. The route chosen is the one with the least total loss, where
 * each meter of travel also costs {@link #DB_PER_METER} so sound prefers short routes.
 *
 * Because the field changes by at most one tile's travel between neighbouring tiles, the
 * result is smooth as the listener walks, unlike a single ray.
 *
 * The field depends only on the source tile and the grid layout, so it is cached and rebuilt
 * only when either changes.
 */
public class SoundField {

    /** Loss through an open doorway, dB. */
    public static final float OPEN_DOOR_DB = 3f;
    /** Loss through a sealed door, dB. */
    public static final float SEALED_DOOR_DB = 15f;
    /** Loss through a wall, dB. */
    public static final float WALL_DB = 20f;
    /** Extra loss for each further tile of a thick wall, dB. */
    public static final float WALL_THICKNESS_DB = 4f;
    /** Cost of each meter of travel when choosing a route, dB. Rolloff itself is applied separately. */
    public static final float DB_PER_METER = 0.3f;

    private static final float DIAGONAL = (float) Math.sqrt(2);

    public final int width, height;
    /** Source tile this field was built from. */
    public final int sourceX, sourceY;
    /** The grid layout signature this field was built for. */
    public final int layoutSignature;
    /** Boundary losses along the best route to each tile, dB. Infinite where sound cannot reach. */
    final float[] lossDb;
    /** Part of {@link #lossDb} from walls and sealed doors, which muffle; open doorways do not. */
    final float[] muffleDb;
    /** Length of the best route to each tile, meters. */
    final float[] length;

    private SoundField(int width, int height, int sourceX, int sourceY, int layoutSignature) {
        this.width = width;
        this.height = height;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.layoutSignature = layoutSignature;
        int n = width * height;
        lossDb = new float[n];
        muffleDb = new float[n];
        length = new float[n];
        Arrays.fill(lossDb, Float.POSITIVE_INFINITY);
    }

    public boolean reaches(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && lossDb[y * width + x] != Float.POSITIVE_INFINITY;
    }

    public float lossDb(int x, int y) { return lossDb[y * width + x]; }

    public float muffleDb(int x, int y) { return muffleDb[y * width + x]; }

    /** Route length in meters (tiles times the tile size). */
    public float length(int x, int y) { return length[y * width + x]; }

    /** True if the field is still valid for this source and layout. */
    public boolean matches(int sourceX, int sourceY, int layoutSignature) {
        return this.sourceX == sourceX && this.sourceY == sourceY && this.layoutSignature == layoutSignature;
    }

    /** Builds the field from source tile (sx, sy). */
    public static SoundField build(ShipGridComponent grid, int sx, int sy, int layoutSignature) {
        SoundField f = new SoundField(grid.width, grid.height, sx, sy, layoutSignature);
        if (!grid.inBounds(sx, sy) || grid.getTile(sx, sy).isSpaceExposed()) return f;
        float tile = grid.tileSize;
        int w = grid.width;
        float[] key = new float[w * grid.height];
        Arrays.fill(key, Float.POSITIVE_INFINITY);
        PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[0]), Float.intBitsToFloat((int) b[0])));
        int s = sy * w + sx;
        f.lossDb[s] = 0f;
        key[s] = 0f;
        open.add(new long[]{Float.floatToIntBits(0f), s});
        while (!open.isEmpty()) {
            long[] top = open.poll();
            int i = (int) top[1];
            if (Float.intBitsToFloat((int) top[0]) > key[i]) continue; // stale entry
            int x = i % w, y = i / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (!grid.inBounds(nx, ny) || grid.getTile(nx, ny).isSpaceExposed()) continue;
                    float stepLoss, stepMuffle, stepLen;
                    if (dx != 0 && dy != 0) {
                        // Diagonal steps only through open air, so sound never cuts a wall's corner
                        if (crossing(grid, x, y, nx, y, null) != 0f || crossing(grid, x, y, x, ny, null) != 0f
                                || crossing(grid, nx, y, nx, ny, null) != 0f || crossing(grid, x, ny, nx, ny, null) != 0f) continue;
                        stepLoss = 0f;
                        stepMuffle = 0f;
                        stepLen = DIAGONAL * tile;
                    } else {
                        float[] muffle = new float[1];
                        stepLoss = crossing(grid, x, y, nx, ny, muffle);
                        stepMuffle = muffle[0];
                        stepLen = tile;
                    }
                    int n = ny * w + nx;
                    float loss = f.lossDb[i] + stepLoss;
                    float len = f.length[i] + stepLen;
                    float k = loss + DB_PER_METER * len;
                    if (k < key[n]) {
                        key[n] = k;
                        f.lossDb[n] = loss;
                        f.muffleDb[n] = f.muffleDb[i] + stepMuffle;
                        f.length[n] = len;
                        open.add(new long[]{Float.floatToIntBits(k), n});
                    }
                }
            }
        }
        return f;
    }

    /**
     * Loss in dB stepping orthogonally from (x, y) to (nx, ny): the edge between them plus
     * entering or continuing through a wall tile. The muffling part goes in {@code muffle[0]}.
     */
    private static float crossing(ShipGridComponent grid, int x, int y, int nx, int ny, float[] muffle) {
        float[] edge = new float[1];
        float loss = edgeLossDb(grid.getEdgeBetween(x, y, nx, ny), edge), muff = edge[0];
        boolean fromSolid = isSolid(grid.getTile(x, y)), toSolid = isSolid(grid.getTile(nx, ny));
        if (toSolid) {
            float l = fromSolid ? WALL_THICKNESS_DB : WALL_DB;
            loss += l;
            muff += l;
        }
        if (muffle != null) muffle[0] = muff;
        return loss;
    }

    /**
     * Loss in dB through one edge wall: none for no wall (or a destroyed one), a little for an open
     * door, more for a sealed one, most for a plain wall. The muffling part goes in {@code muffle[0]}.
     */
    public static float edgeLossDb(EdgeWall e, float[] muffle) {
        float loss = 0f, muff = 0f;
        if (e != null && !e.isDestroyed()) {
            if (!e.hasBulkhead) {
                loss = muff = WALL_DB;
            } else if (e.bulkheadSealed) {
                loss = muff = SEALED_DOOR_DB;
            } else {
                loss = OPEN_DOOR_DB;
            }
        }
        if (muffle != null) muffle[0] = muff;
        return loss;
    }

    /** A tile sound must pass through rather than travel in: an intact wall or a sealed bulkhead. */
    static boolean isSolid(Tile t) {
        return t != null && (t.isIntactWall() || t.bulkheadSealed);
    }

    /**
     * A hash of everything that changes how sound spreads on {@code grid}: wall tiles, sealed
     * tiles, breaches and every edge wall's state. Cheap enough to take once per frame per ship.
     */
    public static int layoutSignature(ShipGridComponent grid) {
        int h = 17;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                h = h * 31 + (t.isSpaceExposed() ? 2 : isSolid(t) ? 1 : 0);
            }
        }
        h = hashEdges(grid.edgeH, h);
        h = hashEdges(grid.edgeV, h);
        return h;
    }

    private static int hashEdges(EdgeWall[][] edges, int h) {
        for (EdgeWall[] row : edges) {
            for (EdgeWall e : row) {
                int v = e == null || e.isDestroyed() ? 0 : !e.hasBulkhead ? 1 : e.bulkheadSealed ? 2 : 3;
                h = h * 31 + v;
            }
        }
        return h;
    }
}
