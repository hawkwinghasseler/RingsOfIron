package com.ringsofiron.interiorgrid;

/**
 * Where a docking port's parts sit in its {@link MountFootprint.MountKind#PORT} footprint.
 *
 * <p>A port is an airlock built into the hull edge. Its footprint is {@code W} tiles along the hull
 * and {@code D} deep (Small 3 x 1, Medium 5 x 2, Large 7 x 3). The tile at each end of the width
 * is frame (hull); the {@code W - 2} tiles between them, through the whole depth, are the opening:
 * floor the crew walk through. The opening's outer edge carries the port door, a sealed bulkhead,
 * and faces space. The latch collar is not on the grid: it sticks out half a tile past the outer
 * edge and is the docking module's business.
 *
 * <p>The footprint's rotation sets the side it faces: 0 is {@link Side#TOP} (+y), 1
 * {@link Side#LEFT}, 2 {@link Side#BOTTOM}, 3 {@link Side#RIGHT}, counter-clockwise like every
 * other mount.
 */
public final class DockingPortLayout {

    private DockingPortLayout() {}

    /** The side of the grid the port faces, from its rotation. */
    public static Side facing(MountFootprint port) {
        switch (port.rotation) {
            case 0:  return Side.TOP;
            case 1:  return Side.LEFT;
            case 2:  return Side.BOTTOM;
            default: return Side.RIGHT;
        }
    }

    /** The rotation that makes a port face {@code side}. */
    public static int rotationFacing(Side side) {
        switch (side) {
            case TOP:    return 0;
            case LEFT:   return 1;
            case BOTTOM: return 2;
            default:     return 3;
        }
    }

    /** Tiles along the hull: 3, 5 or 7. */
    public static int width(MountFootprint port) {
        return port.size.baseWidth(MountFootprint.MountKind.PORT);
    }

    /** Tiles into the ship: 1, 2 or 3. */
    public static int depth(MountFootprint port) {
        return port.size.baseHeight(MountFootprint.MountKind.PORT);
    }

    /** Width of the opening in tiles: 1, 3 or 5. */
    public static int openingWidth(MountFootprint port) {
        return width(port) - 2;
    }

    /**
     * Position of footprint tile (x, y) across the port: {@code out[0]} along the hull (0 to W - 1)
     * and {@code out[1]} the depth (0 at the outer edge).
     */
    public static void along(MountFootprint port, int x, int y, int[] out) {
        int d = depth(port);
        switch (facing(port)) {
            case TOP:
                out[0] = port.originX + width(port) - 1 - x;
                out[1] = port.originY + d - 1 - y;
                break;
            case BOTTOM:
                out[0] = x - port.originX;
                out[1] = y - port.originY;
                break;
            case LEFT:
                out[0] = port.originY + width(port) - 1 - y;
                out[1] = x - port.originX;
                break;
            default:
                out[0] = y - port.originY;
                out[1] = port.originX + d - 1 - x;
                break;
        }
    }

    /** True for the opening's tiles: floor between the two frame columns. */
    public static boolean isOpening(MountFootprint port, int x, int y) {
        if (!port.covers(x, y)) return false;
        int[] a = new int[2];
        along(port, x, y, a);
        return a[0] >= 1 && a[0] <= width(port) - 2;
    }

    /** True for the outer row of the opening, whose outer edge is the port door. */
    public static boolean isDoorTile(MountFootprint port, int x, int y) {
        if (!isOpening(port, x, y)) return false;
        int[] a = new int[2];
        along(port, x, y, a);
        return a[1] == 0;
    }

    /**
     * The outer edge's middle and outward normal in grid units (tiles from the grid's bottom-left
     * corner): {@code out = {x, y, nx, ny}}.
     */
    public static void outerEdge(MountFootprint port, float[] out) {
        float w = width(port), d = depth(port);
        switch (facing(port)) {
            case TOP:
                out[0] = port.originX + w / 2f; out[1] = port.originY + d; out[2] = 0f; out[3] = 1f;
                break;
            case BOTTOM:
                out[0] = port.originX + w / 2f; out[1] = port.originY; out[2] = 0f; out[3] = -1f;
                break;
            case LEFT:
                out[0] = port.originX; out[1] = port.originY + w / 2f; out[2] = -1f; out[3] = 0f;
                break;
            default:
                out[0] = port.originX + d; out[1] = port.originY + w / 2f; out[2] = 1f; out[3] = 0f;
                break;
        }
    }

    /**
     * The outer edge's middle in body-local meters and its outward normal:
     * {@code out = {x, y, nx, ny}}.
     */
    public static void outerEdgeLocal(ShipGridComponent grid, MountFootprint port, float[] out) {
        outerEdge(port, out);
        out[0] = grid.gridOriginLocal.x + out[0] * grid.tileSize;
        out[1] = grid.gridOriginLocal.y + out[1] * grid.tileSize;
    }

    /**
     * Checks a port footprint: in bounds, clear of other mounts and hull pieces, no edge wall
     * through it, nothing but space beyond its outer edge, and floor behind every opening tile.
     *
     * @return null if legal, otherwise a description of the problem
     */
    static String validate(ShipGridComponent grid, MountFootprint port) {
        Side out = facing(port);
        int[] a = new int[2];
        int x0 = port.originX, y0 = port.originY, x1 = x0 + port.width(), y1 = y0 + port.height();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) return "out of bounds at " + x + "," + y;
                if (t.mount != null) return "overlaps another mount at " + x + "," + y;
                if (t.piece != null) return "overlaps a hull piece at " + x + "," + y;
                if (x + 1 < x1 && grid.getEdge(x, y, Side.RIGHT) != null) return "edge wall inside footprint at " + x + "," + y;
                if (y + 1 < y1 && grid.getEdge(x, y, Side.TOP) != null) return "edge wall inside footprint at " + x + "," + y;
                along(port, x, y, a);
                if (a[1] == 0) {
                    Tile beyond = grid.getTile(x + out.dx, y + out.dy);
                    if (beyond != null && !beyond.isVoid()) return "does not face space at " + x + "," + y;
                }
                boolean opening = a[0] >= 1 && a[0] <= width(port) - 2;
                if (opening && a[1] == depth(port) - 1) {
                    Tile inside = grid.getTile(x - out.dx, y - out.dy);
                    if (inside == null || !inside.isFloor()) return "opening leads nowhere at " + x + "," + y;
                }
            }
        }
        return null;
    }

    /**
     * Builds the port into the grid: frame tiles become hull, opening tiles steel floor, and the
     * opening's outer edge gets the port door, a sealed medium bulkhead. Call after
     * {@link #validate} passed; does not register the mount.
     */
    static void build(ShipGridComponent grid, MountFootprint port) {
        Side out = facing(port);
        int[] a = new int[2];
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                Tile t = grid.getTile(x, y);
                along(port, x, y, a);
                boolean opening = a[0] >= 1 && a[0] <= width(port) - 2;
                if (opening) {
                    t.wallType = Tile.WallType.NONE;
                    if (!t.isFloor()) t.floorType = Tile.FloorType.FLOOR;
                    if (a[1] == 0) {
                        EdgeWall door = new EdgeWall(WeightClass.MEDIUM, MaterialTable.DEFAULT_MATERIAL, true);
                        door.bulkheadSealed = true;
                        grid.setEdge(x, y, out, door);
                    }
                } else {
                    if (t.wallType != Tile.WallType.HULL) t.weightClass = WeightClass.MEDIUM;
                    t.wallType = Tile.WallType.HULL;
                    t.floorType = Tile.FloorType.NONE;
                    t.setCovering(FloorCovering.NONE, grid.tileSize);
                }
            }
        }
    }

    /** The port door on opening tile (x, y), or null if that tile is not on the port's outer row. */
    public static EdgeWall door(ShipGridComponent grid, MountFootprint port, int x, int y) {
        return isDoorTile(port, x, y) ? grid.getEdge(x, y, facing(port)) : null;
    }
}
