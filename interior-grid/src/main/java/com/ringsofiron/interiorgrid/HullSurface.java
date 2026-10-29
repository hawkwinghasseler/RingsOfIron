package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * The outside of a craft's hull, where a crew member on EVA crawls: the ring of empty tiles hugging
 * its silhouette (each empty tile, or tile past the grid's edge, beside a tile of the craft, corners
 * included). A crew member on one of them clings to the hull at its {@link #hug} point, {@link
 * #STANDOFF} out from the hull, and crawls from tile to tile round the craft's outline. Seen from
 * above, that is the craft's side: the figure shows just outside its silhouette, inside and out.
 *
 * <p>Coordinates are tiles from the grid's corner, as on deck; ring tiles run from -1 to the grid's
 * width and height, one past each edge. Built from the grid as it is; {@link #of} builds a fresh
 * one when the layout changed.
 */
public final class HullSurface {
    /** How far a clinging crew member's middle is from the hull, tiles. */
    public static final float STANDOFF = 0.3f;

    private final ShipGridComponent grid;
    /** Width and height of the ring's box, one tile past the grid each way. */
    private final int w, h;
    private final boolean[] ring;
    private final int layout;

    private HullSurface(ShipGridComponent grid) {
        this.grid = grid;
        w = grid.width + 2;
        h = grid.height + 2;
        ring = new boolean[w * h];
        layout = grid.layoutRevision;
        for (int y = -1; y <= grid.height; y++) {
            for (int x = -1; x <= grid.width; x++) {
                if (solid(x, y)) continue;
                boolean beside = false;
                for (int dy = -1; dy <= 1 && !beside; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && solid(x + dx, y + dy)) {
                            beside = true;
                            break;
                        }
                    }
                }
                ring[index(x, y)] = beside;
            }
        }
    }

    /** {@code grid}'s hull surface: {@code cached} if the layout hasn't changed since it was built, else a fresh one. */
    public static HullSurface of(ShipGridComponent grid, HullSurface cached) {
        return cached != null && cached.grid == grid && cached.layout == grid.layoutRevision ? cached : new HullSurface(grid);
    }

    /** The craft's tile size, meters. */
    public float tileSize() {
        return grid.tileSize;
    }

    /** True if tile (x, y) is part of the craft: anything but empty space, past the grid's edge included. */
    public boolean solid(int x, int y) {
        Tile t = grid.getTile(x, y);
        return t != null && !t.isVoid();
    }

    /** True if tile (x, y) is on the hull's outside: empty, beside the craft. */
    public boolean isRing(int x, int y) {
        return x >= -1 && y >= -1 && x <= grid.width && y <= grid.height && ring[index(x, y)];
    }

    private int index(int x, int y) {
        return (y + 1) * w + (x + 1);
    }

    /**
     * Where a crew member on ring tile (x, y) clings, tiles: {@link #STANDOFF} out from the hull
     * beside it, against the side of the craft it lies along, or off the corner it rounds.
     */
    public Vector2 hug(int x, int y, Vector2 out) {
        float px = x + 0.5f, py = y + 0.5f;
        boolean side = false;
        if (solid(x - 1, y)) { px = x + STANDOFF; side = true; }
        if (solid(x + 1, y)) { px = x + 1 - STANDOFF; side = true; }
        if (solid(x, y - 1)) { py = y + STANDOFF; side = true; }
        if (solid(x, y + 1)) { py = y + 1 - STANDOFF; side = true; }
        if (side) return out.set(px, py);
        // Round a corner: off it along the diagonal
        float cx = 0f, cy = 0f;
        int n = 0;
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                if (!solid(x + dx, y + dy)) continue;
                cx += x + (dx > 0 ? 1 : 0);
                cy += y + (dy > 0 ? 1 : 0);
                n++;
            }
        }
        if (n == 0) return out.set(px, py);
        cx /= n;
        cy /= n;
        float ox = px - cx, oy = py - cy, len = (float) Math.sqrt(ox * ox + oy * oy);
        return out.set(cx + ox / len * STANDOFF, cy + oy / len * STANDOFF);
    }

    /**
     * The ring tile outside the {@code side} edge of tile (x, y), if that edge faces space: where a crew
     * member works on something on that edge (an RCS block) or comes out through it (a port door).
     * Null if the edge doesn't face space.
     */
    public int[] outside(int x, int y, Side side) {
        int ox = x + side.dx, oy = y + side.dy;
        return isRing(ox, oy) ? new int[]{ox, oy} : null;
    }

    /**
     * The way round the hull from ring tile (sx, sy) to ring tile (tx, ty), tile by tile, the start
     * left out: the shorter way round, a step to each side or end to end. False if there is none (the
     * two on different outlines, an enclosed yard's and the outside's).
     */
    public boolean path(int sx, int sy, int tx, int ty, Array<int[]> out) {
        out.clear();
        if (!isRing(sx, sy) || !isRing(tx, ty)) return false;
        if (sx == tx && sy == ty) return true;
        int[] from = new int[w * h];
        java.util.Arrays.fill(from, -1);
        IntArray queue = new IntArray();
        int start = index(sx, sy), goal = index(tx, ty);
        from[start] = start;
        queue.add(start);
        for (int head = 0; head < queue.size && from[goal] < 0; head++) {
            int i = queue.get(head), x = i % w - 1, y = i / w - 1;
            for (Side s : Side.values()) {
                int nx = x + s.dx, ny = y + s.dy;
                if (!isRing(nx, ny)) continue;
                int j = index(nx, ny);
                if (from[j] >= 0) continue;
                from[j] = i;
                queue.add(j);
            }
        }
        if (from[goal] < 0) return false;
        for (int i = goal; i != start; i = from[i]) out.add(new int[]{i % w - 1, i / w - 1});
        out.reverse();
        return true;
    }

    /** True if the straight line between points (ax, ay) and (bx, by), tiles, stays off the craft. */
    public boolean clear(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy) / 0.1f));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            if (solid((int) Math.floor(ax + dx * t), (int) Math.floor(ay + dy * t))) return false;
        }
        return true;
    }

    /** The ring tile nearest point (x, y), tiles, or null if the craft has none. */
    public int[] nearest(float x, float y) {
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        Vector2 at = new Vector2();
        for (int ty = -1; ty <= grid.height; ty++) {
            for (int tx = -1; tx <= grid.width; tx++) {
                if (!isRing(tx, ty)) continue;
                hug(tx, ty, at);
                float d = at.dst2(x, y);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{tx, ty};
                }
            }
        }
        return best;
    }

    /**
     * The point on the hull's outside nearest (x, y) and its outward normal, tiles: {@code out = {x, y,
     * nx, ny}}, where a crew member there would cling. From the nearest solid tile's square, looking
     * no more than three tiles off; false (and {@code out} meaningless) if there is none that near.
     */
    public boolean surface(float x, float y, float[] out) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        float bestD = Float.MAX_VALUE;
        for (int r = 0; r <= 3 && bestD == Float.MAX_VALUE; r++) {
            for (int ty = cy - r; ty <= cy + r; ty++) {
                for (int tx = cx - r; tx <= cx + r; tx++) {
                    if (!solid(tx, ty)) continue;
                    float qx = Math.max(tx, Math.min(tx + 1, x)), qy = Math.max(ty, Math.min(ty + 1, y));
                    float d = (qx - x) * (qx - x) + (qy - y) * (qy - y);
                    if (d < bestD) {
                        bestD = d;
                        out[0] = qx;
                        out[1] = qy;
                    }
                }
            }
        }
        if (bestD == Float.MAX_VALUE) return false;
        float nx = x - out[0], ny = y - out[1], len = (float) Math.sqrt(nx * nx + ny * ny);
        if (len < 1e-4f) {
            out[2] = 0f;
            out[3] = 1f;
        } else {
            out[2] = nx / len;
            out[3] = ny / len;
        }
        return true;
    }

    /**
     * Pushes point (x, y), tiles, out of the solid tile it is in across the nearest edge of that tile
     * with open space beyond it: {@code out = {x, y}}. False (and {@code out} left alone) if it is not
     * in a solid tile, or is buried in one with solid tiles all round.
     */
    public boolean pushOut(float x, float y, float[] out) {
        int tx = (int) Math.floor(x), ty = (int) Math.floor(y);
        if (!solid(tx, ty)) return false;
        final float eps = 0.01f;
        float best = Float.MAX_VALUE, bx = x, by = y;
        if (!solid(tx - 1, ty) && x - tx < best) { best = x - tx; bx = tx - eps; by = y; }
        if (!solid(tx + 1, ty) && tx + 1 - x < best) { best = tx + 1 - x; bx = tx + 1 + eps; by = y; }
        if (!solid(tx, ty - 1) && y - ty < best) { best = y - ty; bx = x; by = ty - eps; }
        if (!solid(tx, ty + 1) && ty + 1 - y < best) { best = ty + 1 - y; bx = x; by = ty + 1 + eps; }
        if (best == Float.MAX_VALUE) return false;
        out[0] = bx;
        out[1] = by;
        return true;
    }
}
