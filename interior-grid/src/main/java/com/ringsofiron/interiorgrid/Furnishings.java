package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Setting furnishings down on a deck and finding them again. A furnishing goes anywhere on open
 * floor at any angle: its middle on a floor tile, its whole footprint over floor (no wall, void or
 * system under any of it, no thin wall or door across it) and clear of every other furnishing,
 * except that anything can stand on a {@link FurnishingType#flat flat} one such as a rug.
 */
public final class Furnishings {
    /** How far two furnishings may overlap at their edges and still both fit, tiles. */
    public static final float OVERLAP_SLACK = 0.02f;
    /** Spacing of the points its footprint is checked at, tiles. */
    private static final float SAMPLE = 0.2f;

    private Furnishings() {
    }

    /** Every furnishing on {@code grid}, row by row from the bottom-left tile. */
    public static Array<Furnishing> all(ShipGridComponent grid) {
        Array<Furnishing> out = new Array<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) out.addAll(grid.tiles[y][x].furnishings);
        }
        return out;
    }

    /** Sets {@code f} down on its tile if it fits there ({@link #refusal}); returns why not, or null once it is down. */
    public static String place(ShipGridComponent grid, Furnishing f) {
        String no = refusal(grid, f, null);
        if (no != null) return no;
        grid.getTile(f.tileX, f.tileY).furnishings.add(f);
        return null;
    }

    /** Puts {@code f} on its tile without asking whether it fits, as a save says it was. */
    public static void restore(ShipGridComponent grid, Furnishing f) {
        Tile t = grid.getTile(f.tileX, f.tileY);
        if (t != null) t.furnishings.add(f);
    }

    /** Takes {@code f} off the deck; false if it wasn't there. */
    public static boolean remove(ShipGridComponent grid, Furnishing f) {
        Tile t = grid.getTile(f.tileX, f.tileY);
        if (t == null || !t.furnishings.removeValue(f, true)) return false;
        f.users.clear();
        return true;
    }

    /** The furnishing whose footprint covers ({@code x}, {@code y}), tiles from the grid's corner, or null. */
    public static Furnishing at(ShipGridComponent grid, float x, float y) {
        int tx = MathUtils.floor(x), ty = MathUtils.floor(y);
        float[] c = new float[8];
        // Big pieces reach over the tiles around their own
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                Tile t = grid.getTile(tx + dx, ty + dy);
                if (t == null) continue;
                for (Furnishing f : t.furnishings) if (contains(f.corners(0f, c), x, y)) return f;
            }
        }
        return null;
    }

    /**
     * Why {@code f} can't go where it says on {@code grid}, or null if it fits. {@code pending}, if
     * not null, are furnishings on their way there that it must keep clear of too.
     */
    public static String refusal(ShipGridComponent grid, Furnishing f, Iterable<Furnishing> pending) {
        Tile home = grid.getTile(f.tileX, f.tileY);
        if (home == null || !home.isFloor() || home.isWall()) return "That isn't on the deck";
        float[] c = f.corners(-OVERLAP_SLACK, new float[8]);
        // Every point over the footprint is over open floor with no system on it...
        int n = Math.max(2, MathUtils.ceil(Math.max(f.type.width, f.type.depth) / SAMPLE) + 1);
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float u = i / (float) (n - 1), v = j / (float) (n - 1);
                // Bilinear over the corners: (0,0) back left, (1,0) back right... any order covers it
                float px = lerp(lerp(c[0], c[6], u), lerp(c[2], c[4], u), v);
                float py = lerp(lerp(c[1], c[7], u), lerp(c[3], c[5], u), v);
                int tx = MathUtils.floor(px), ty = MathUtils.floor(py);
                Tile t = grid.getTile(tx, ty);
                if (t == null || !t.isFloor() || t.isWall() || t.isCrushed()) return "It doesn't fit: there's a wall in the way";
                if (t.mount != null) return "It doesn't fit: there's a system in the way";
                // ...and no thin wall or door runs between it and the next point
                if (j > 0 && (tx != lastX || ty != lastY) && crossesEdge(grid, lastX, lastY, tx, ty)) {
                    return "It doesn't fit: there's a wall in the way";
                }
                lastX = tx;
                lastY = ty;
            }
            lastX = lastY = Integer.MIN_VALUE;
        }
        // Across too, for a thin wall running along its length
        for (int j = 0; j < n; j++) {
            lastX = lastY = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                float u = i / (float) (n - 1), v = j / (float) (n - 1);
                float px = lerp(lerp(c[0], c[6], u), lerp(c[2], c[4], u), v);
                float py = lerp(lerp(c[1], c[7], u), lerp(c[3], c[5], u), v);
                int tx = MathUtils.floor(px), ty = MathUtils.floor(py);
                if (i > 0 && (tx != lastX || ty != lastY) && crossesEdge(grid, lastX, lastY, tx, ty)) {
                    return "It doesn't fit: there's a wall in the way";
                }
                lastX = tx;
                lastY = ty;
            }
        }
        float[] o = new float[8];
        int reach = MathUtils.ceil(Math.max(f.type.width, f.type.depth)) + 2;
        for (int y = f.tileY - reach; y <= f.tileY + reach; y++) {
            for (int x = f.tileX - reach; x <= f.tileX + reach; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) continue;
                for (Furnishing other : t.furnishings) {
                    if (other != f && blocks(f, other) && overlaps(c, other.corners(0f, o))) {
                        return "It doesn't fit: " + other.type.the() + " is in the way";
                    }
                }
            }
        }
        if (pending != null) {
            for (Furnishing other : pending) {
                if (other != f && blocks(f, other) && overlaps(c, other.corners(0f, o))) {
                    return "It doesn't fit: " + other.type.the() + " is going there";
                }
            }
        }
        return null;
    }

    /** Whether two furnishings can't share floor: anything can stand on a flat one. */
    private static boolean blocks(Furnishing a, Furnishing b) {
        return !a.type.flat && !b.type.flat;
    }

    /** True if a thin wall or door stands on any edge stepped over going from tile (x, y) to (nx, ny). */
    private static boolean crossesEdge(ShipGridComponent grid, int x, int y, int nx, int ny) {
        // Step along x then y, and y then x: a diagonal step must be clear both ways round
        return stepBlocked(grid, x, y, nx, y) || stepBlocked(grid, nx, y, nx, ny)
                || stepBlocked(grid, x, y, x, ny) || stepBlocked(grid, x, ny, nx, ny);
    }

    private static boolean stepBlocked(ShipGridComponent grid, int x, int y, int nx, int ny) {
        while (x != nx || y != ny) {
            int sx = x + Integer.signum(nx - x), sy = y + Integer.signum(ny - y);
            if (sx != x && sy != y) sy = y; // one axis at a time
            if (grid.getEdgeBetween(x, y, sx, sy) != null) return true;
            x = sx;
            y = sy;
        }
        return false;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Whether two convex quadrilaterals ({x0, y0 ... x3, y3}) overlap: no axis of either separates them. */
    static boolean overlaps(float[] a, float[] b) {
        return !separated(a, b) && !separated(b, a);
    }

    private static boolean separated(float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float nx = a[2 * j + 1] - a[2 * i + 1], ny = a[2 * i] - a[2 * j];
            float aMin = Float.MAX_VALUE, aMax = -Float.MAX_VALUE, bMin = Float.MAX_VALUE, bMax = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                float pa = a[2 * k] * nx + a[2 * k + 1] * ny, pb = b[2 * k] * nx + b[2 * k + 1] * ny;
                aMin = Math.min(aMin, pa);
                aMax = Math.max(aMax, pa);
                bMin = Math.min(bMin, pb);
                bMax = Math.max(bMax, pb);
            }
            if (aMax <= bMin || bMax <= aMin) return true;
        }
        return false;
    }

    /** Whether ({@code x}, {@code y}) is inside the convex quadrilateral {@code q}. */
    static boolean contains(float[] q, float x, float y) {
        boolean pos = false, neg = false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float cross = (q[2 * j] - q[2 * i]) * (y - q[2 * i + 1]) - (q[2 * j + 1] - q[2 * i + 1]) * (x - q[2 * i]);
            if (cross > 0f) pos = true;
            if (cross < 0f) neg = true;
        }
        return !(pos && neg);
    }
}
