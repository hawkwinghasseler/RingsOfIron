package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.IntArray;

/**
 * How a main engine ({@link SubsystemComponent.SubsystemType#ENGINE}) mounts on a ship: on the
 * outside of the outer hull, bolted on by the flange at the end away from its nozzle.
 *
 * <p>The footprint is an auxiliary slot whose rotation sets the way the nozzle faces: 0 is
 * {@link Side#TOP} (+y), 1 {@link Side#LEFT}, 2 {@link Side#BOTTOM}, 3 {@link Side#RIGHT}. The row
 * at the other end is the flange. Either the flange sits just outside the hull, against an outer
 * wall, or it takes the place of one tile of that wall (the engine reaches one tile into the wall,
 * and no further). Everything else lies out in space, and the {@link #clearance} tiles past the
 * nozzle, as wide as the engine, stay clear for its exhaust.
 *
 * <p>Built, an engine's tiles are hull: its casing ({@link #isCasing}) is light hull outside the
 * ship, so it is struck, collides and is crushed like the rest of the hull, and a flange set into
 * the wall keeps that wall's place. The crew and the ship's power and pipes reach the engine from
 * its feed tiles ({@link #isFeedTile}): the floor just inside the wall behind its flange.
 */
public final class EngineLayout {

    private EngineLayout() {}

    /** Tiles past the nozzle that must be open space, for each tile of the engine's width. */
    public static final int CLEARANCE = 5;
    /** The casing outside the hull: light hull. */
    public static final WeightClass CASING = WeightClass.LIGHT;

    /** The side the nozzle faces, from the footprint's rotation. */
    public static Side nozzle(MountFootprint m) {
        switch (m.rotation) {
            case 0:  return Side.TOP;
            case 1:  return Side.LEFT;
            case 2:  return Side.BOTTOM;
            default: return Side.RIGHT;
        }
    }

    /** The rotation that points the nozzle at {@code side}. */
    public static int rotationFacing(Side side) {
        return DockingPortLayout.rotationFacing(side);
    }

    /** Tiles from the nozzle end to the flange: the slot's long side. */
    public static int length(MountFootprint m) {
        Side n = nozzle(m);
        return n.dx != 0 ? m.width() : m.height();
    }

    /** Tiles across the nozzle end: the slot's short side. */
    public static int breadth(MountFootprint m) {
        Side n = nozzle(m);
        return n.dx != 0 ? m.height() : m.width();
    }

    /**
     * Tiles past the nozzle, as wide as the engine, that must be open space: {@link #CLEARANCE} for
     * each tile of its breadth, so 5 for a Small engine and 25 for an Extra Large one.
     */
    public static int clearance(MountFootprint m) {
        return CLEARANCE * breadth(m);
    }

    /** True if footprint tile (x, y) is in the flange row, the end away from the nozzle. */
    public static boolean inFlange(MountFootprint m, int x, int y) {
        if (!m.covers(x, y)) return false;
        switch (nozzle(m)) {
            case TOP:    return y == m.originY;
            case BOTTOM: return y == m.originY + m.height() - 1;
            case RIGHT:  return x == m.originX;
            default:     return x == m.originX + m.width() - 1;
        }
    }

    /**
     * Shapes the tiles a map draws the engine on, before it is checked and built: the map's letters
     * say nothing of what lies under them. The flange is set into the wall (hull) where it backs onto
     * the inside of the ship, and stands outside (void) where it backs onto the hull; every other tile
     * is space.
     */
    static void shape(ShipGridComponent grid, MountFootprint m) {
        Side back = nozzle(m).opposite();
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) continue;
                t.floorType = Tile.FloorType.NONE;
                t.wallType = Tile.WallType.NONE;
                t.setCovering(FloorCovering.NONE, grid.tileSize);
                if (!inFlange(m, x, y)) continue;
                Tile behind = grid.getTile(x + back.dx, y + back.dy);
                if (behind != null && !behind.isVoid() && behind.wallType != Tile.WallType.HULL) {
                    t.wallType = Tile.WallType.HULL;
                    t.weightClass = WeightClass.MEDIUM;
                }
            }
        }
    }

    /**
     * Checks an engine's footprint on a grid it is not built into yet: in bounds and clear of other
     * mounts, hull pieces and thin walls; every tile but the flange's out in space, and no deck beside
     * any of them but behind a flange in the wall; the flange either wholly outside against the outer
     * hull or wholly set into it; and nothing but space for {@link #clearance} tiles past the nozzle.
     *
     * @return null if legal, otherwise a description of the problem
     */
    static String validate(ShipGridComponent grid, MountFootprint m) {
        Side back = nozzle(m).opposite();
        int x0 = m.originX, y0 = m.originY, x1 = x0 + m.width(), y1 = y0 + m.height();
        Boolean flangeInWall = null;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) return "out of bounds at " + x + "," + y;
                if (t.mount != null) return "overlaps another mount at " + x + "," + y;
                if (t.piece != null) return "overlaps a hull piece at " + x + "," + y;
                if (x + 1 < x1 && grid.getEdge(x, y, Side.RIGHT) != null) return "edge wall inside footprint at " + x + "," + y;
                if (y + 1 < y1 && grid.getEdge(x, y, Side.TOP) != null) return "edge wall inside footprint at " + x + "," + y;
                // Deck beside it, but for the deck behind a flange set into the wall, means it is inside the ship
                for (Side side : Side.values()) {
                    if (m.covers(x + side.dx, y + side.dy) || (side == back && inFlange(m, x, y))) continue;
                    Tile n = grid.getTile(x + side.dx, y + side.dy);
                    if (n != null && n.isFloor()) return "an engine mounts outside the hull, reaching at most one tile into it, at " + x + "," + y;
                }
                if (!inFlange(m, x, y)) {
                    if (!t.isVoid()) return "an engine mounts outside the hull, reaching at most one tile into it, at " + x + "," + y;
                    continue;
                }
                boolean inWall = t.wallType == Tile.WallType.HULL;
                if (!inWall && !t.isVoid()) return "an engine's flange goes on or into the outer hull, at " + x + "," + y;
                if (flangeInWall != null && flangeInWall != inWall) return "an engine's flange is half in the hull at " + x + "," + y;
                flangeInWall = inWall;
                Tile behind = grid.getTile(x + back.dx, y + back.dy);
                if (inWall ? behind == null || behind.isVoid() : behind == null || behind.wallType != Tile.WallType.HULL) {
                    return "an engine's flange must be against the outer hull at " + x + "," + y;
                }
            }
        }
        return exhaustBlocked(grid, m);
    }

    /**
     * Builds the engine into the grid: its casing becomes light hull, and a flange in the wall stays
     * the wall. Call after {@link #validate} passed; does not register the mount.
     */
    static void build(ShipGridComponent grid, MountFootprint m) {
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL) continue;
                t.wallType = Tile.WallType.HULL;
                t.floorType = Tile.FloorType.NONE;
                t.weightClass = CASING;
            }
        }
    }

    /**
     * The first tile, as "x,y", within {@link #clearance} tiles past the nozzle (as wide as the
     * engine) that is not open space, or null when the exhaust is clear. Off the grid is space.
     */
    public static String exhaustBlocked(ShipGridComponent grid, MountFootprint m) {
        Side out = nozzle(m);
        int clear = clearance(m);
        for (int d = 1; d <= clear; d++) {
            for (int y = m.originY; y < m.originY + m.height(); y++) {
                for (int x = m.originX; x < m.originX + m.width(); x++) {
                    // Only the nozzle row's tiles look out past the footprint
                    if (m.covers(x + out.dx, y + out.dy)) continue;
                    int bx = x + out.dx * d, by = y + out.dy * d;
                    Tile t = grid.getTile(bx, by);
                    if (t != null && (!t.isVoid() || t.mount != null)) {
                        return "an engine needs " + clear + " clear tiles past its nozzle; blocked at " + bx + "," + by;
                    }
                }
            }
        }
        return null;
    }

    /**
     * True if the built engine's flange is set into the hull wall, as opposed to standing against
     * its outside: the tile behind the flange is not a wall.
     */
    public static boolean flangeInWall(ShipGridComponent grid, MountFootprint m) {
        Side back = nozzle(m).opposite();
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                if (!inFlange(m, x, y)) continue;
                Tile behind = grid.getTile(x + back.dx, y + back.dy);
                return behind != null && !behind.isWall();
            }
        }
        return false;
    }

    /** True for a tile of the built engine that lies outside the ship's hull: all but a flange set into the wall. */
    public static boolean isCasing(ShipGridComponent grid, MountFootprint m, int x, int y) {
        if (!m.covers(x, y)) return false;
        return !inFlange(m, x, y) || !flangeInWall(grid, m);
    }

    /** True if tile (x, y) is the casing of an engine installed on the grid ({@link #isCasing(ShipGridComponent, MountFootprint, int, int)}). */
    public static boolean isCasing(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        MountFootprint m = t == null ? null : t.mount;
        if (m == null || m.installed == null || m.kind != MountFootprint.MountKind.AUXILIARY) return false;
        SubsystemComponent s = m.installed.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemComponent.SubsystemType.ENGINE && isCasing(grid, m, x, y);
    }

    /**
     * The feed tiles of a built engine, packed as {@code y * width + x}: for each flange tile, the
     * first floor tile inward through at most one more wall tile. Power, pipes and the crew reach the
     * engine there.
     */
    public static void feedTiles(ShipGridComponent grid, MountFootprint m, IntArray out) {
        Side back = nozzle(m).opposite();
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                if (!inFlange(m, x, y)) continue;
                for (int d = 1; d <= 2; d++) {
                    Tile t = grid.getTile(x + back.dx * d, y + back.dy * d);
                    if (t == null || t.isVoid()) break;
                    if (t.isFloor() && !t.isWall()) {
                        out.add((y + back.dy * d) * grid.width + x + back.dx * d);
                        break;
                    }
                    if (t.mount != null) break;
                }
            }
        }
    }

    /** True if (x, y) is one of the engine's feed tiles ({@link #feedTiles}). */
    public static boolean isFeedTile(ShipGridComponent grid, MountFootprint m, int x, int y) {
        IntArray feed = new IntArray(m.width() + m.height());
        feedTiles(grid, m, feed);
        return feed.contains(y * grid.width + x);
    }
}
