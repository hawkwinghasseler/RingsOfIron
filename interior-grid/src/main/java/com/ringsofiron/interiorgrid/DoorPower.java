package com.ringsofiron.interiorgrid;

/**
 * Whether a bulkhead door has the power to work its lock: an edge conduit in its wall powered, or
 * a powered conduit across a tile on either side of it; an airlock's door or hatch also from its
 * docking port's own supply ({@link Airlock#powered}). A locked door without power can't be
 * unlocked, only cut open ({@link #cutOpen}).
 */
public final class DoorPower {

    private DoorPower() {}

    /** True if the door on the {@code side} edge of tile (x, y) has power to work its lock. */
    public static boolean powered(ShipGridComponent grid, int x, int y, Side side) {
        EdgeConduit e = grid.getConduit(x, y, side);
        if (e != null && e.isPowered) return true;
        int nx = x + side.dx, ny = y + side.dy;
        TileConduit a = grid.getTileConduit(x, y), b = grid.getTileConduit(nx, ny);
        if (a != null && a.isPowered || b != null && b.isPowered) return true;
        for (int k = 0; k < 2; k++) {
            Tile t = grid.getTile(k == 0 ? x : nx, k == 0 ? y : ny);
            MountFootprint m = t == null ? null : t.mount;
            if (m != null && m.kind == MountFootprint.MountKind.PORT && Airlock.of(grid, m).powered(grid)) return true;
        }
        return false;
    }

    /** Cuts the door open: wrecked, so it blocks nothing until a yard rebuilds it. */
    public static void cutOpen(ShipGridComponent grid, EdgeWall door) {
        door.integrity = 0f;
        door.bulkheadSealed = false;
        door.locked = false;
        door.unlock();
        door.patchedAt = 0f;
        grid.gridDirty = true;
    }
}
