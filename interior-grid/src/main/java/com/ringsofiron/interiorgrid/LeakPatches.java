package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Damage control in the field: where a ship leaks air to space through something a crew can
 * patch, and patching it. A damaged hull or wall tile, deck plate or thin wall below
 * {@link Tile#LEAK_START} leaks without being a breach; a patch from the ship's stores
 * ({@link ShipGridComponent#patchKits}) stops the leak where it is, at the condition it is in.
 * The piece stays damaged until a yard repairs it, and a new hit breaks the patch (see
 * {@link Tile#isPatched(float, float)}). A breach (condition 0) is sealed with an emergency
 * bulkhead instead, which needs no stores.
 *
 * <p>Leaks are found from {@link ShipGridComponent#airflow}, so they reflect the last hazard tick.
 */
public final class LeakPatches {

    /** Patches a ship carries from the yard. */
    public static final int DEFAULT_KITS = 8;
    /** Patch kits it takes to brace each wrecked airlock door or hatch ({@link #brace}). */
    public static final int BRACE_KITS = 1;
    /** A brace takes this many times a patch's work. */
    public static final float BRACE_WORK = 10f;
    /** Condition a braced airlock door or hatch is left at: it seals and works, but only a yard makes it whole. */
    public static final float BRACED = 0.25f;

    private LeakPatches() {}

    /**
     * One patchable leak to space. With {@link #side} null it is the tile (x, y) itself, a wall
     * or a deck plate; otherwise it is the thin wall on that side of air tile (x, y).
     */
    public static final class Leak {
        public final int x, y;
        public final Side side;
        /** Air leaving through it at the last hazard tick, mol/s. */
        public float flow;

        Leak(int x, int y, Side side) {
            this.x = x;
            this.y = y;
            this.side = side;
        }

        public boolean same(int ox, int oy, Side oside) {
            return x == ox && y == oy && side == oside;
        }
    }

    /** Every leak to space aboard that a patch would stop, one entry per leaking piece. */
    public static Array<Leak> leaks(ShipGridComponent grid) {
        Array<Leak> out = new Array<>();
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.other != null) continue;
            Leak leak = leakBehind(grid, e);
            if (leak == null) continue;
            Leak known = null;
            for (Leak l : out) if (l.same(leak.x, leak.y, leak.side) || samePiece(grid, l, leak)) known = l;
            if (known == null) out.add(known = leak);
            known.flow += Math.max(0f, e.flow);
        }
        return out;
    }

    /** The patchable piece an opening to space leaks through, or null (a breach, an open edge). */
    static Leak leakBehind(ShipGridComponent grid, AirflowEdge e) {
        if (e.side == null) {
            Tile t = grid.getTile(e.x, e.y);
            return t != null && t.isFloor() && t.leakFactor() > 0f ? new Leak(e.x, e.y, null) : null;
        }
        EdgeWall wall = grid.getEdge(e.x, e.y, e.side);
        if (wall != null && wall.blocks()) return wall.leakFactor() > 0f ? new Leak(e.x, e.y, e.side) : null;
        int nx = e.x + e.side.dx, ny = e.y + e.side.dy;
        Tile nb = grid.getTile(nx, ny);
        return nb != null && nb.isIntactWall() && nb.leakFactor() > 0f ? new Leak(nx, ny, null) : null;
    }

    private static boolean samePiece(ShipGridComponent grid, Leak a, Leak b) {
        if (a.side != null || b.side != null) return false;
        Tile ta = grid.getTile(a.x, a.y), tb = grid.getTile(b.x, b.y);
        return ta.piece != null && ta.piece == tb.piece;
    }

    /** True while the piece at (x, y, side) leaks and a patch would stop it. */
    public static boolean leaks(ShipGridComponent grid, int x, int y, Side side) {
        if (side != null) {
            EdgeWall wall = grid.getEdge(x, y, side);
            return wall != null && wall.blocks() && wall.leakFactor() > 0f;
        }
        Tile t = grid.getTile(x, y);
        if (t == null || t.bulkheadSealed) return false;
        if (t.isIntactWall()) return t.leakFactor() > 0f;
        return t.isFloor() && t.leakFactor() > 0f;
    }

    /**
     * Patches the piece at (x, y, side) from the ship's stores.
     *
     * @return false, and nothing changes, if it no longer leaks or the stores are empty
     */
    public static boolean patch(ShipGridComponent grid, int x, int y, Side side) {
        if (side != null && braceable(grid, x, y, side)) return brace(grid, x, y, side);
        if (grid.patchKits <= 0 || !leaks(grid, x, y, side)) return false;
        if (side != null) {
            EdgeWall wall = grid.getEdge(x, y, side);
            wall.patchedAt = Tile.patchMark(wall.integrity);
        } else {
            grid.getTile(x, y).patch();
        }
        grid.patchKits--;
        return true;
    }

    /** True if some leak to space aboard could be patched but the stores are empty. */
    public static boolean outOfKits(ShipGridComponent grid) {
        return grid.patchKits <= 0 && leaks(grid).size > 0;
    }

    // -----------------------------------------------------------------------
    // Bracing a wrecked airlock door
    // -----------------------------------------------------------------------

    /**
     * True if the {@code side} edge of tile (x, y) is a docking port's outer door or inner hatch,
     * wrecked, that the crew could brace from the stores ({@link #BRACE_KITS} of them): a lone
     * port's door wrecked by the hit that jammed its collar would otherwise leave nobody a way out.
     */
    public static boolean braceable(ShipGridComponent grid, int x, int y, Side side) {
        if (grid.patchKits < BRACE_KITS) return false;
        EdgeWall w = grid.getEdge(x, y, side);
        if (w == null || !w.isDestroyed() || !w.hasBulkhead) return false;
        Tile t = grid.getTile(x, y);
        MountFootprint m = t == null ? null : t.mount;
        if (m == null || m.kind != MountFootprint.MountKind.PORT) return false;
        return DockingPortLayout.door(grid, m, x, y) == w || DockingPortLayout.hatch(grid, m, x, y) == w;
    }

    /** Every wrecked airlock door and hatch aboard the stores could brace, as a leak on its edge from the opening tile. */
    public static Array<Leak> braces(ShipGridComponent grid) {
        Array<Leak> out = new Array<>();
        if (grid.patchKits < BRACE_KITS) return out;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint m = grid.mounts.get(i);
            if (m.kind != MountFootprint.MountKind.PORT || Airlock.docked(grid, m)) continue;
            Side out0 = DockingPortLayout.facing(m), in0 = out0.opposite();
            for (int y = m.originY; y < m.originY + m.height(); y++) {
                for (int x = m.originX; x < m.originX + m.width(); x++) {
                    if (DockingPortLayout.isDoorTile(m, x, y) && braceable(grid, x, y, out0)) out.add(new Leak(x, y, out0));
                    if (DockingPortLayout.isHatchTile(m, x, y) && braceable(grid, x, y, in0)) out.add(new Leak(x, y, in0));
                }
            }
        }
        return out;
    }

    /**
     * Braces the wrecked airlock door or hatch on the {@code side} edge of (x, y) from the ship's
     * stores: it goes back up at {@link #BRACED}, patched so it holds its air, the door shut and the
     * hatch open as an airlock at rest has them. It stays that damaged, and a new hit breaks it, until
     * a yard repairs it.
     *
     * @return false, and nothing changes, if it isn't wrecked any more or the stores are short
     */
    public static boolean brace(ShipGridComponent grid, int x, int y, Side side) {
        if (!braceable(grid, x, y, side)) return false;
        EdgeWall w = grid.getEdge(x, y, side);
        Tile t = grid.getTile(x, y);
        boolean door = DockingPortLayout.door(grid, t.mount, x, y) == w;
        w.integrity = BRACED;
        w.patchedAt = Tile.patchMark(BRACED);
        w.bulkheadSealed = door;
        grid.patchKits -= BRACE_KITS;
        grid.gridDirty = true;
        return true;
    }

    /** True if {@code port}'s airlock works, or would once the crew braced its wrecked door and hatch from the stores aboard. */
    public static boolean worksOnceBraced(ShipGridComponent grid, MountFootprint port) {
        if (Airlock.works(grid, port)) return true;
        if (port.kind != MountFootprint.MountKind.PORT || Airlock.docked(grid, port)) return false;
        Side out0 = DockingPortLayout.facing(port), in0 = out0.opposite();
        int needed = 0;
        boolean any = false;
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                if (DockingPortLayout.isDoorTile(port, x, y)) {
                    EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                    if (d == null) return false;
                    any = true;
                    if (d.isDestroyed()) {
                        if (!d.hasBulkhead) return false;
                        needed++;
                    }
                }
                if (DockingPortLayout.isHatchTile(port, x, y)) {
                    EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                    if (h == null) return false;
                    if (h.isDestroyed()) needed++;
                }
            }
        }
        return any && grid.patchKits >= needed * BRACE_KITS;
    }
}
