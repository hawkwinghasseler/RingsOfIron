package com.ringsofiron.interiorgrid;

/**
 * Shared rules for systems mounted through the outer hull, such as escape pods: which way a mount
 * faces, its rows along the hull, and whether the space beyond it is clear.
 *
 * <p>A mount faces {@link Side#TOP} (+y) at rotation 0 and turns counter-clockwise with it: 1 faces
 * {@link Side#LEFT}, 2 {@link Side#BOTTOM}, 3 {@link Side#RIGHT}, as a docking port does.
 */
public final class HullMounts {

    /** Tiles of open space a hull mount needs beyond its outer edge: an escape pod's launch path. */
    public static final int CLEARANCE = 5;

    private HullMounts() {}

    /** The side of the grid a mount faces, from its rotation. */
    public static Side facing(MountFootprint f) {
        return DockingPortLayout.facing(f);
    }

    /** True if tile (x, y) of the footprint lies in its row (or column) along {@code side}. */
    public static boolean onEdge(MountFootprint f, int x, int y, Side side) {
        switch (side) {
            case TOP:    return y == f.originY + f.height() - 1;
            case BOTTOM: return y == f.originY;
            case LEFT:   return x == f.originX;
            default:     return x == f.originX + f.width() - 1;
        }
    }

    /** True if (x, y) is open space: void or off the grid, with no mount on it. */
    public static boolean isSpace(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        return t == null || (t.isVoid() && t.mount == null);
    }

    /**
     * What lies in the band {@code depth} tiles deep just past the footprint's {@code side} edge (as
     * wide as the footprint): the first tile that is not open space, as "x,y", or "the RCS block on
     * x,y" when one stands on that edge. Null when the band is clear.
     */
    public static String blockedBeyond(ShipGridComponent grid, MountFootprint f, Side side, int depth) {
        for (RcsThruster r : grid.rcsThrusters) {
            if (r.side == side && f.covers(r.x, r.y) && onEdge(f, r.x, r.y, side)) return "the RCS block on " + r.x + "," + r.y;
        }
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (!onEdge(f, x, y, side)) continue;
                for (int d = 1; d <= depth; d++) {
                    int bx = x + side.dx * d, by = y + side.dy * d;
                    if (!isSpace(grid, bx, by)) return bx + "," + by;
                }
            }
        }
        return null;
    }
}
