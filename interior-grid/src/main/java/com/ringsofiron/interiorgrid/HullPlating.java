package com.ringsofiron.interiorgrid;

/**
 * Damage control from outside the hull, on EVA: a hull tile wrecked to nothing (integrity 0) whose
 * outer face is open to space is framed back up to {@link RcsThruster#FIELD_REPAIR} and plated over
 * from the ship's stores ({@link ShipGridComponent#patchKits}), so it stands again and holds air.
 * From inside a breach can only be sealed with an emergency bulkhead, which stops the air but leaves
 * the hull as broken as it was; the plate puts some of its strength back. A breach already sealed
 * keeps its seal under the plate; with no kits left the frame alone goes up, leaking. A tile crushed
 * flat is yard work, and an RCS block's tile is a job of its own ({@link RcsThruster#fieldRepairable}).
 */
public final class HullPlating {

    private HullPlating() {
    }

    /** True if hull tile (x, y) is wrecked, not crushed, carries no RCS block and has a side open to space. */
    public static boolean plateable(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.wallType != Tile.WallType.HULL || t.getIntegrity() > 0f || t.isCrushed()) return false;
        return !RcsThruster.fieldRepairable(grid, x, y) && facesSpace(grid, x, y);
    }

    /** True if a side of tile (x, y) faces empty space or the grid's edge: it can be got at from outside. */
    public static boolean facesSpace(ShipGridComponent grid, int x, int y) {
        for (Side s : Side.values()) {
            Tile n = grid.getTile(x + s.dx, y + s.dy);
            if (n == null || n.isVoid()) return true;
        }
        return false;
    }

    /** Frames hull tile (x, y) back up to {@link RcsThruster#FIELD_REPAIR} and, while there are kits, plates it airtight. */
    public static void plate(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        t.setIntegrity(Math.max(t.getIntegrity(), RcsThruster.FIELD_REPAIR));
        if (grid.patchKits > 0 && t.leakFactor() > 0f) {
            t.patch();
            grid.patchKits--;
        }
        grid.gridDirty = true;
    }
}
