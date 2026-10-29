package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * Fits a craft with its suit lockers when it is built, and an older save's craft that has none: a
 * pressure suit for each of its crew and {@link #SPARE_IVA} more, shared out between {@link #IVA_ID}
 * lockers standing beside its posts (the helm and weapons consoles first, so a hand there suits up
 * without leaving the post) and then spread through its rooms; and two EVA suits in an {@link
 * #EVA_ID} locker just inside each docking port, the ship's airlock. Lockers come full.
 *
 * <p>A locker stands on open floor with its back to something solid: a wall (a hull or interior
 * wall, or a thin wall with no door in it), or the casing of a tank, cargo bay or life support
 * beside it, as in a cramped corvette. Never in a port's opening, an escape pod's bay or over a
 * system. A craft whose rooms have no such spot goes without.
 */
public final class SuitOutfit {
    /** The furnishing id of a pressure suit locker in furnishings.toml. */
    public static final String IVA_ID = "suit_locker";
    /** The furnishing id of an EVA suit locker. */
    public static final String EVA_ID = "eva_locker";
    /** Pressure suits carried beyond one for each crew member. */
    public static final int SPARE_IVA = 1;
    /** Tiles from a post within which its locker goes, if there is room. */
    static final int NEAR_POST = 2;
    /** Steps in from a docking port's opening within which its EVA locker goes, the nearest spot first. */
    static final int NEAR_PORT = 8;

    /** The pressure suit locker when the data has none, as furnishings.toml has it. */
    public static final FurnishingType IVA_LOCKER = new FurnishingType(IVA_ID, "Suit locker", "decor/suit_locker",
            1f, 0.5f, 1f, 2400, FurnishingType.Use.STORAGE, 0, 0f, null).holds("IVA", 4);
    /** The EVA suit locker when the data has none. */
    public static final FurnishingType EVA_LOCKER = new FurnishingType(EVA_ID, "EVA locker", "decor/eva_locker",
            1.25f, 0.6f, 1.5f, 12000, FurnishingType.Use.STORAGE, 0, 0f, null).holds("EVA", 2);

    private SuitOutfit() {
    }

    /**
     * Fits {@code grid} with lockers and suits for a crew of {@code crew}, unless it already has a
     * locker. Lockers come from {@code catalog} when it has them, else the built-in ones.
     *
     * @return how many lockers went in
     */
    public static int outfit(ShipGridComponent grid, FurnishingCatalog catalog, int crew) {
        if (grid == null || SuitLockers.any(grid)) return 0;
        FurnishingType iva = type(catalog, IVA_ID, IVA_LOCKER), eva = type(catalog, EVA_ID, EVA_LOCKER);
        int placed = 0;
        // EVA lockers first, inside each port, so a pressure suit locker never takes the spot by the airlock
        for (MountFootprint m : grid.mounts) {
            if (m.kind != MountFootprint.MountKind.PORT) continue;
            Furnishing f = place(grid, eva, portTiles(grid, m), null);
            if (f == null) continue;
            for (int i = 0; i < eva.capacity; i++) f.suits.add(Suit.full(Suit.Kind.EVA));
            placed++;
        }
        int suits = Math.max(0, crew) + SPARE_IVA;
        int wanted = (suits + iva.capacity - 1) / iva.capacity;
        Array<int[]> posts = postTiles(grid);
        Array<Furnishing> lockers = new Array<>();
        while (lockers.size < wanted) {
            // Beside a post with no locker yet, then wherever is farthest from the lockers already in
            Furnishing f = null;
            while (f == null && posts.size > 0) {
                int[] post = posts.removeIndex(0);
                if (nearLocker(lockers, post[0], post[1], NEAR_POST + 1)) continue;
                f = place(grid, iva, near(grid, post[0], post[1], NEAR_POST), null);
            }
            if (f == null) f = place(grid, iva, floorTiles(grid), lockers);
            if (f == null) break;
            lockers.add(f);
            placed++;
        }
        // The suits shared out, so a post's locker has some whoever else suits up first
        for (int i = 0; i < suits && lockers.size > 0; i++) {
            Furnishing f = lockers.get(i % lockers.size);
            if (f.suits.size < iva.capacity) f.suits.add(Suit.full(Suit.Kind.IVA));
        }
        return placed;
    }

    /**
     * Stands a built-in locker for suits of {@code kind} on tile (x, y), its back to one of the
     * tile's walls, with {@code suits} full suits in it (no more than it holds): for scenes built by
     * hand. Null if it doesn't fit there.
     */
    public static Furnishing placeLocker(ShipGridComponent grid, Suit.Kind kind, int x, int y, int suits) {
        FurnishingType type = kind == Suit.Kind.EVA ? EVA_LOCKER : IVA_LOCKER;
        Furnishing f = fit(grid, type, x, y);
        if (f == null || Furnishings.place(grid, f) != null) return null;
        for (int i = 0; i < Math.min(suits, type.capacity); i++) f.suits.add(Suit.full(kind));
        return f;
    }

    private static FurnishingType type(FurnishingCatalog catalog, String id, FurnishingType fallback) {
        FurnishingType t = catalog == null ? null : catalog.get(id);
        return t != null && t.holds != null && t.capacity > 0 ? t : fallback;
    }

    /**
     * Sets a {@code type} locker down on the first of {@code tiles} with room for it against a wall;
     * with {@code spread} not null, on whichever such tile is farthest from the lockers in it.
     */
    private static Furnishing place(ShipGridComponent grid, FurnishingType type, Array<int[]> tiles, Array<Furnishing> spread) {
        Furnishing best = null;
        float bestScore = -1f;
        for (int[] t : tiles) {
            Furnishing f = fit(grid, type, t[0], t[1]);
            if (f == null) continue;
            if (spread == null) {
                best = f;
                break;
            }
            float score = Float.MAX_VALUE;
            for (Furnishing o : spread) score = Math.min(score, Math.abs(o.x() - f.x()) + Math.abs(o.y() - f.y()));
            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        if (best == null || Furnishings.place(grid, best) != null) return null;
        return best;
    }

    /** A {@code type} locker on tile (x, y) with its back to one of the tile's walls, or null if none fits. */
    static Furnishing fit(ShipGridComponent grid, FurnishingType type, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.isFloor() || t.isWall() || t.mount != null || t.furnishings.size > 0) return null;
        for (Side side : SIDES) {
            if (!wallBehind(grid, x, y, side)) continue;
            // Back against that wall: at rotation 0 the back is toward +y
            float back = 0.5f - type.depth / 2f - Furnishings.OVERLAP_SLACK;
            Furnishing f = new Furnishing(type, x, y, side.dx * back, side.dy * back, rotationFacing(side));
            if (Furnishings.refusal(grid, f, null) == null) return f;
        }
        return null;
    }

    private static final Side[] SIDES = {Side.TOP, Side.LEFT, Side.BOTTOM, Side.RIGHT};

    /** The turn that puts a furnishing's back (+y at rotation 0) toward {@code side}. */
    static float rotationFacing(Side side) {
        switch (side) {
            case TOP:    return 0f;
            case LEFT:   return 90f;
            case BOTTOM: return 180f;
            default:     return 270f;
        }
    }

    /**
     * True if tile (x, y)'s {@code side} is something solid to stand a locker against: a wall tile,
     * the hull, a thin wall with no door, or the casing of a {@link #casing} system.
     */
    static boolean wallBehind(ShipGridComponent grid, int x, int y, Side side) {
        EdgeWall e = grid.getEdge(x, y, side);
        if (e != null) return !e.hasBulkhead && !e.isDestroyed();
        Tile n = grid.getTile(x + side.dx, y + side.dy);
        if (n == null || n.isVoid()) return true;
        if (n.mount == null) return n.isWall();
        return !n.isWall() && casing(n.mount);
    }

    /** True if {@code m} holds a system whose casing a locker may stand against: a tank, cargo bay or life support. */
    static boolean casing(MountFootprint m) {
        SubsystemComponent s = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
        return s != null && (s.type == SubsystemComponent.SubsystemType.TANK || s.type == SubsystemComponent.SubsystemType.CARGO
                || s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT);
    }

    /** True if a locker in {@code lockers} stands within {@code tiles} of tile (x, y). */
    private static boolean nearLocker(Array<Furnishing> lockers, int x, int y, int tiles) {
        for (Furnishing f : lockers) if (Math.abs(f.tileX - x) <= tiles && Math.abs(f.tileY - y) <= tiles) return true;
        return false;
    }

    /** The tiles round the helm and weapons consoles: one per post, its footprint's first tile. */
    private static Array<int[]> postTiles(ShipGridComponent grid) {
        Array<int[]> out = new Array<>();
        for (SubsystemComponent.SubsystemType want : new SubsystemComponent.SubsystemType[]{
                SubsystemComponent.SubsystemType.HELM, SubsystemComponent.SubsystemType.WEAPONS}) {
            for (MountFootprint m : grid.mounts) {
                Entity e = m.installed;
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                if (s != null && s.type == want) out.add(new int[]{m.originX, m.originY});
            }
        }
        return out;
    }

    /**
     * Floor tiles within {@link #NEAR_PORT} steps of a port's opening, the nearest first: the tile
     * straight in from it too, where a locker stands against one of its sides, out of the way in.
     */
    private static Array<int[]> portTiles(ShipGridComponent grid, MountFootprint port) {
        Side out = DockingPortLayout.facing(port);
        float[] edge = new float[4];
        DockingPortLayout.outerEdge(port, edge);
        int depth = DockingPortLayout.depth(port);
        // The middle of the floor just inside the port
        int cx = (int) Math.floor(edge[0] - out.dx * (depth + 0.5f));
        int cy = (int) Math.floor(edge[1] - out.dy * (depth + 0.5f));
        return near(grid, cx, cy, NEAR_PORT);
    }

    /**
     * Floor tiles a walk of {@code reach} steps or fewer from (x, y), through doors shut or open but
     * no wall, the nearest first.
     */
    static Array<int[]> near(ShipGridComponent grid, int x, int y, int reach) {
        Array<int[]> out = new Array<>();
        Tile start = grid.getTile(x, y);
        if (start == null || !start.isOpen()) return out;
        int[] steps = new int[grid.width * grid.height];
        java.util.Arrays.fill(steps, -1);
        com.badlogic.gdx.utils.IntArray queue = new com.badlogic.gdx.utils.IntArray();
        steps[y * grid.width + x] = 0;
        queue.add(y * grid.width + x);
        for (int head = 0; head < queue.size; head++) {
            int i = queue.get(head), tx = i % grid.width, ty = i / grid.width;
            Tile t = grid.getTile(tx, ty);
            if (t.isFloor() && !t.isWall()) out.add(new int[]{tx, ty});
            if (steps[i] >= reach) continue;
            for (Side side : SIDES) {
                int nx = tx + side.dx, ny = ty + side.dy;
                Tile n = grid.getTile(nx, ny);
                if (n == null || !n.isOpen() || steps[ny * grid.width + nx] >= 0) continue;
                EdgeWall e = grid.getEdge(tx, ty, side);
                if (e != null && !e.hasBulkhead && !e.isDestroyed()) continue;
                steps[ny * grid.width + nx] = steps[i] + 1;
                queue.add(ny * grid.width + nx);
            }
        }
        return out;
    }

    /** Every floor tile, row by row. */
    private static Array<int[]> floorTiles(ShipGridComponent grid) {
        Array<int[]> out = new Array<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.isFloor() && !t.isWall()) out.add(new int[]{x, y});
            }
        }
        return out;
    }
}
