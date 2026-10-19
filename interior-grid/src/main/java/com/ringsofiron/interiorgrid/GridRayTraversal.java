package com.ringsofiron.interiorgrid;

/**
 * Amanatides &amp; Woo grid traversal (DDA) over a tile grid.
 *
 * Works in continuous tile space: tile (x, y) covers [x, x+1) by [y, y+1), so a body-local
 * point maps to {@code (local - gridOriginLocal) / tileSize}. Unlike Bresenham it visits every
 * tile the ray passes through, including ones it only clips at a corner, and it reports each
 * tile boundary it crosses so callers can resolve {@link EdgeWall}s before entering the next tile.
 *
 * Where the ray passes exactly through a tile corner it steps in x first, then in y, so it
 * visits one of the two diagonal neighbours and reports both boundaries it crosses. That way a
 * corner can never be slipped through without touching a wall on either side.
 *
 * The traversal is unbounded: tiles outside the grid are still reported, and the visitor
 * decides what to do with them. Shared by combat (penetration rays) and crew (line of sight).
 */
public final class GridRayTraversal {

    /** Receives the tiles and boundaries a ray crosses, in order. Return false to stop. */
    public interface Visitor {
        /**
         * The ray is about to enter tile (x, y) at parameter {@code tEnter} and would leave it at
         * {@code tExit} (both clamped to the ray's length).
         */
        boolean tile(int x, int y, float tEnter, float tExit);

        /**
         * The ray crosses the boundary from tile (fromX, fromY) into the orthogonally adjacent
         * tile (toX, toY) at parameter {@code t}. Called before {@link #tile} for the new tile.
         */
        boolean edge(int fromX, int fromY, int toX, int toY, float t);
    }

    /** A visitor that only cares about tiles. */
    public abstract static class TileVisitor implements Visitor {
        @Override
        public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
            return true;
        }
    }

    /** Safety cap so a degenerate ray can never loop forever. */
    public static final int MAX_STEPS = 100_000;

    private GridRayTraversal() {
    }

    /**
     * Walks the ray {@code start + dir * t} for {@code t} in [0, maxT], in tile units. {@code dir}
     * need not be normalized; {@code t} is measured in multiples of it.
     *
     * @return true if the walk reached {@code maxT}, false if the visitor stopped it.
     */
    public static boolean traverse(float startX, float startY, float dirX, float dirY, float maxT, Visitor visitor) {
        int x = (int) Math.floor(startX);
        int y = (int) Math.floor(startY);

        int stepX = dirX > 0f ? 1 : (dirX < 0f ? -1 : 0);
        int stepY = dirY > 0f ? 1 : (dirY < 0f ? -1 : 0);
        // Parameter at which the ray crosses the next vertical / horizontal boundary,
        // and how far t advances per whole tile.
        float tMaxX = stepX == 0 ? Float.POSITIVE_INFINITY
                : ((stepX > 0 ? (x + 1) - startX : startX - x) / Math.abs(dirX));
        float tMaxY = stepY == 0 ? Float.POSITIVE_INFINITY
                : ((stepY > 0 ? (y + 1) - startY : startY - y) / Math.abs(dirY));
        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : 1f / Math.abs(dirX);
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : 1f / Math.abs(dirY);

        float tEnter = 0f;
        for (int steps = 0; steps < MAX_STEPS; steps++) {
            float tExit = Math.min(Math.min(tMaxX, tMaxY), maxT);
            if (!visitor.tile(x, y, tEnter, tExit)) return false;
            if (tExit >= maxT) return true;

            if (tMaxX <= tMaxY) {
                // On an exact corner (tMaxX == tMaxY) this steps x now and y on the next pass,
                // with a zero-length visit to the tile in between.
                if (!visitor.edge(x, y, x + stepX, y, tMaxX)) return false;
                x += stepX;
                tEnter = tMaxX;
                tMaxX += tDeltaX;
            } else {
                if (!visitor.edge(x, y, x, y + stepY, tMaxY)) return false;
                y += stepY;
                tEnter = tMaxY;
                tMaxY += tDeltaY;
            }
        }
        return false;
    }

    /**
     * True if nothing blocks a straight line between the centres of tiles (fromX, fromY) and
     * (toX, toY): no intact wall tile and no closed edge wall lies between them. The two end
     * tiles themselves are not tested, so a crew member standing in a doorway can still see out.
     */
    public static boolean hasLineOfSight(ShipGridComponent grid, int fromX, int fromY, int toX, int toY) {
        return hasLineOfSight(grid, fromX + 0.5f, fromY + 0.5f, toX + 0.5f, toY + 0.5f);
    }

    /** {@link #hasLineOfSight(ShipGridComponent, int, int, int, int)} between two points in tile space. */
    public static boolean hasLineOfSight(final ShipGridComponent grid, float fromX, float fromY, float toX, float toY) {
        final int endX = (int) Math.floor(toX);
        final int endY = (int) Math.floor(toY);
        final int startX = (int) Math.floor(fromX);
        final int startY = (int) Math.floor(fromY);
        return traverse(fromX, fromY, toX - fromX, toY - fromY, 1f, new Visitor() {
            @Override
            public boolean tile(int x, int y, float tEnter, float tExit) {
                if ((x == startX && y == startY) || (x == endX && y == endY)) return true;
                return seeThrough(grid.getTile(x, y));
            }

            @Override
            public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                return !grid.isEdgeBlocked(fromX, fromY, toX, toY);
            }
        });
    }

    private static boolean seeThrough(Tile t) {
        return t != null && !t.isIntactWall() && !t.isVoid();
    }

    /**
     * True if nothing blocks a straight line from tile (fromX, fromY) on {@code grid} to tile
     * (toX, toY) on {@code other}, a craft docked to it: out of {@code grid} over the edge of a
     * {@link GridLink} onto {@code other} that can be crossed (both port doors open), across the
     * collars and in over the far edge, with no wall or shut door on either craft in the way. On
     * the same grid it is {@link #hasLineOfSight(ShipGridComponent, int, int, int, int)}.
     */
    public static boolean hasLineOfSight(ShipGridComponent grid, int fromX, int fromY, ShipGridComponent other, int toX, int toY) {
        if (other == grid) return hasLineOfSight(grid, fromX, fromY, toX, toY);
        float[] p = new float[2];
        for (int i = 0; i < grid.links.size; i++) {
            GridLink l = grid.links.get(i);
            if (l.other != other || !l.canCross()) continue;
            // The far tile's centre in this grid's tile space: the craft are held rigidly port to port
            l.mate.carryAcross(toX + 0.5f, toY + 0.5f, p);
            if (sightAcross(grid, fromX + 0.5f, fromY + 0.5f, p[0], p[1], other, toX, toY)) return true;
        }
        return false;
    }

    /**
     * The ray from (fx, fy) to (tx, ty), both in {@code grid}'s tile space (the far end mapped from
     * {@code other}): through {@code grid} to its edge, out through a crossable link onto
     * {@code other}, and on through {@code other} to tile (endX, endY).
     */
    private static boolean sightAcross(final ShipGridComponent grid, float fx, float fy, float tx, float ty,
                                       final ShipGridComponent other, int endX, int endY) {
        final int startX = (int) Math.floor(fx), startY = (int) Math.floor(fy);
        final GridLink[] out = new GridLink[1];
        final float[] leave = new float[1];
        float dx = tx - fx, dy = ty - fy;
        traverse(fx, fy, dx, dy, 1f, new Visitor() {
            @Override
            public boolean tile(int x, int y, float tEnter, float tExit) {
                if (x == startX && y == startY) return true;
                return seeThrough(grid.getTile(x, y));
            }

            @Override
            public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                if (grid.getTile(toX, toY) != null) return !grid.isEdgeBlocked(fromX, fromY, toX, toY);
                // Out over the edge: only through an open link onto the other craft
                GridLink l = grid.linkAt(fromX, fromY, Side.between(fromX, fromY, toX, toY));
                if (l != null && l.other == other && l.canCross()) {
                    out[0] = l;
                    leave[0] = t;
                }
                return false;
            }
        });
        GridLink l = out[0];
        if (l == null) return false;
        // Across the collars: the ray reaches the far edge {@code gap} tiles past this one
        float outward = dx * l.side.dx + dy * l.side.dy;
        if (outward <= 0f) return false;
        float t = leave[0] + l.gap / outward;
        if (t > 1f) return false;
        float[] e = new float[2], f = new float[2];
        l.carryAcross(fx + dx * t, fy + dy * t, e);
        l.carryAcross(tx, ty, f);
        // A hair inside the far edge, so the ray starts on the far craft's tile
        Side in = l.otherSide;
        float ex = e[0] - in.dx * 1e-4f, ey = e[1] - in.dy * 1e-4f;
        int ix = (int) Math.floor(ex), iy = (int) Math.floor(ey);
        GridLink back = other.linkAt(ix, iy, in);
        if (back == null || back.other != grid || !back.canCross()) return false;
        if (ix == endX && iy == endY) return true;
        return hasLineOfSight(other, ex, ey, endX + 0.5f, endY + 0.5f) && seeThrough(other.getTile(ix, iy));
    }
}
