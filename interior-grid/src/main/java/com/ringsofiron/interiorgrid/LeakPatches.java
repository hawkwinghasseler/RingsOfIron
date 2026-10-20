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
}
