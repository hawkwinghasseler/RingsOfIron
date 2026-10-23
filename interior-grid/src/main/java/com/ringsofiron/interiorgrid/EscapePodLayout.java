package com.ringsofiron.interiorgrid;

/**
 * Where an escape pod sits: through the outer hull, which it launches out of.
 *
 * <p>The pod's hatch end is its outer edge, the end it faces ({@link HullMounts#facing}: +y at
 * rotation 0). That row of the footprint overlaps the outer hull and stays hull, so the ship keeps
 * its air; the rest of the pod lies inside, on interior floor the crew walk into. Beyond the hatch
 * end, {@link HullMounts#CLEARANCE} tiles of open space (no hull, no other mount, no RCS block on
 * the hatch end) give the pod a clear path away from the ship.
 */
public final class EscapePodLayout {

    private EscapePodLayout() {}

    /**
     * Checks the tiles under the footprint: the hatch end on outer hull, the rest open interior floor,
     * none of it in another mount. Null if legal, otherwise the problem. See {@link #clearance} for
     * the launch path, which is checked once every mount is placed.
     */
    public static String validate(ShipGridComponent grid, MountFootprint f) {
        Side out = HullMounts.facing(f);
        int x0 = f.originX, y0 = f.originY, x1 = x0 + f.width(), y1 = y0 + f.height();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) return "out of bounds at " + x + "," + y;
                if (t.mount != null) return "overlaps another mount at " + x + "," + y;
                if (HullMounts.onEdge(f, x, y, out)) {
                    if (t.wallType != Tile.WallType.HULL) {
                        return "an escape pod's hatch end (" + out + ") overlaps the outer hull, but " + x + "," + y + " is not hull";
                    }
                    continue;
                }
                if (!t.isFloor() || t.isWall()) return "an escape pod lies inside the hull, but " + x + "," + y + " is not interior floor";
                if (x + 1 < x1 && grid.getEdge(x, y, Side.RIGHT) != null) return "edge wall inside footprint at " + x + "," + y;
                if (y + 1 < y1 && grid.getEdge(x, y, Side.TOP) != null) return "edge wall inside footprint at " + x + "," + y;
            }
        }
        return null;
    }

    /** Null if the pod has its launch path clear, otherwise the problem. */
    public static String clearance(ShipGridComponent grid, MountFootprint f) {
        Side out = HullMounts.facing(f);
        String blocked = HullMounts.blockedBeyond(grid, f, out, HullMounts.CLEARANCE);
        if (blocked == null) return null;
        return "the escape pod at " + f.originX + "," + f.originY + " needs " + HullMounts.CLEARANCE
                + " tiles of open space " + out + " of it to launch, but " + blocked + " is in the way";
    }

    /** A tile of the pod inside the hull, for crew to walk to: the middle of the row behind the hatch end. */
    public static int[] bayTile(MountFootprint f) {
        Side out = HullMounts.facing(f);
        int cx = f.originX + (f.width() - 1) / 2, cy = f.originY + (f.height() - 1) / 2;
        switch (out) {
            case TOP:    return new int[]{cx, f.originY + f.height() - 2};
            case BOTTOM: return new int[]{cx, f.originY + 1};
            case LEFT:   return new int[]{f.originX + 1, cy};
            default:     return new int[]{f.originX + f.width() - 2, cy};
        }
    }
}
