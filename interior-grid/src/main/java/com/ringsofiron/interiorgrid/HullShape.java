package com.ringsofiron.interiorgrid;

import java.util.ArrayList;
import java.util.List;

/**
 * The outside of a craft as its layout draws it: every tile that is not void, with each wedge
 * hull piece as its triangle rather than its square footprint. Given as a few convex polygons,
 * so physics can collide on it as one body with several fixtures:
 * <ul>
 *   <li>boxes, made of runs of solid tiles along each row merged with the identical runs of the
 *   rows above them;</li>
 *   <li>a triangle for each wedge.</li>
 * </ul>
 * Polygons are {x0, y0, x1, y1, ...} in body-local meters, counter-clockwise, 3 or 4 vertices.
 */
public final class HullShape {
    private HullShape() {
    }

    /** The convex pieces covering {@code grid}, or an empty list for a grid with no solid tile. */
    public static List<float[]> pieces(ShipGridComponent grid) {
        List<float[]> out = new ArrayList<>();
        int w = grid.width, h = grid.height;
        boolean[][] used = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (used[y][x] || !boxed(grid, x, y)) {
                    x++;
                    continue;
                }
                int x1 = x;
                while (x1 < w && !used[y][x1] && boxed(grid, x1, y)) x1++;
                int y1 = y + 1;
                while (y1 < h && runIsSolid(grid, used, x, x1, y1)) y1++;
                for (int yy = y; yy < y1; yy++) for (int xx = x; xx < x1; xx++) used[yy][xx] = true;
                out.add(box(grid, x, y, x1, y1));
                x = x1;
            }
        }
        for (HullPiece p : grid.hullPieces) {
            if (p.shape == HullPiece.Shape.WEDGE) out.add(wedge(grid, p));
        }
        return out;
    }

    /** True if tile (x, y) is part of the hull's boxes: solid, and not under a wedge. */
    private static boolean boxed(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        return t != null && !t.isVoid() && (t.piece == null || t.piece.shape != HullPiece.Shape.WEDGE);
    }

    /** True if tiles {@code x0..x1-1} of row {@code y} are all boxed and unused, and the run ends there. */
    private static boolean runIsSolid(ShipGridComponent grid, boolean[][] used, int x0, int x1, int y) {
        for (int x = x0; x < x1; x++) {
            if (used[y][x] || !boxed(grid, x, y)) return false;
        }
        // Only merge runs of the same span, so the row's own run is not cut short
        boolean leftOpen = x0 == 0 || used[y][x0 - 1] || !boxed(grid, x0 - 1, y);
        boolean rightOpen = x1 == grid.width || used[y][x1] || !boxed(grid, x1, y);
        return leftOpen && rightOpen;
    }

    private static float[] box(ShipGridComponent grid, int x0, int y0, int x1, int y1) {
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        float l = ox + x0 * s, r = ox + x1 * s, b = oy + y0 * s, t = oy + y1 * s;
        return new float[]{l, b, r, b, r, t, l, t};
    }

    /**
     * A wedge's triangle: at rotation 0 its legs run along the footprint's left and bottom edges,
     * and each quarter turn turns it counter-clockwise about the footprint's middle.
     */
    private static float[] wedge(ShipGridComponent grid, HullPiece p) {
        float s = grid.tileSize;
        float cx = grid.gridOriginLocal.x + (p.originX + p.width() * 0.5f) * s;
        float cy = grid.gridOriginLocal.y + (p.originY + p.height() * 0.5f) * s;
        float hw = p.baseWidth * s * 0.5f, hh = p.baseHeight * s * 0.5f;
        float[] v = {-hw, -hh, hw, -hh, -hw, hh};
        for (int i = 0; i < v.length; i += 2) {
            float x = v[i], y = v[i + 1];
            for (int k = 0; k < p.rotation; k++) {
                float t = x;
                x = -y;
                y = t;
            }
            v[i] = cx + x;
            v[i + 1] = cy + y;
        }
        return v;
    }
}
