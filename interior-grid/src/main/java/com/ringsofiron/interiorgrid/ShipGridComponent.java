package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Holds the 2D interior tile grid for a ship or station entity.
 *
 * The grid uses Box2D's orientation: tile [0][0] is the BOTTOM-LEFT, +x is the body's
 * local +x and +y points up. Grid coordinates are body-local coordinates offset by
 * {@link #gridOriginLocal} and divided by {@link #tileSize}, with no axis flip.
 *
 * Besides tiles, the grid stores thin walls on tile edges, multi-tile hull pieces and
 * system mount footprints.
 */
public class ShipGridComponent implements Component {

    public final int width;
    public final int height;
    /** Tile size in world units (meters). */
    public final float tileSize;
    /** Body-local position (meters) of the bottom-left corner of tile [0][0]. */
    public final Vector2 gridOriginLocal = new Vector2();
    /** The flat 2D grid stored in row-major order: tiles[y][x] */
    public final Tile[][] tiles;
    /**
     * Horizontal edge walls: edgeH[y][x] lies on the bottom edge of tile (x, y), so
     * edgeH[height][x] is the top edge of the top row. Null means no wall.
     */
    public final EdgeWall[][] edgeH;
    /**
     * Vertical edge walls: edgeV[y][x] lies on the left edge of tile (x, y), so
     * edgeV[y][width] is the right edge of the right column. Null means no wall.
     */
    public final EdgeWall[][] edgeV;
    public final Array<HullPiece> hullPieces = new Array<>();
    public final Array<MountFootprint> mounts = new Array<>();
    /**
     * The airtight volumes of the interior, rebuilt by {@link CompartmentBuilder} whenever
     * walls, doors or seals change. Each tile that holds air points at its compartment.
     */
    public final Array<Compartment> compartments = new Array<>();
    /** Air moving through every opening, from the last hazard tick. See {@link AirflowEdge}. */
    public final Array<AirflowEdge> airflow = new Array<>();
    /**
     * Passages from this grid's outer edges onto other craft's grids, while docked. See
     * {@link GridLink}. Not saved: the docking code makes them again.
     */
    public final transient Array<GridLink> links = new Array<>();
    /** The ship's power circuits, rebuilt by {@link PowerRoutingSystem}. Not saved. */
    public final transient Array<PowerCircuit> circuits = new Array<>();
    /** The layout the compartments were last built from; null before the first build. */
    boolean[] compartmentLayout;
    /**
     * Bumped every time the compartments are rebuilt, which happens on the first hazard
     * tick after a wall, door, seal or tile opens or closes. Pathfinding and other caches
     * keyed on the layout can compare it against the value they were built at.
     */
    public int layoutRevision = 0;
    /**
     * Set by damage code after structural changes so power reroutes on the next frame
     * instead of waiting for the routing tick. Cleared by {@link PowerRoutingSystem}.
     */
    public boolean gridDirty = false;

    public ShipGridComponent(int width, int height, float tileSize) {
        this(width, height, tileSize, 0f, 0f);
    }

    public ShipGridComponent(int width, int height, float tileSize, float originLocalX, float originLocalY) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.gridOriginLocal.set(originLocalX, originLocalY);
        this.tiles = new Tile[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = new Tile();
            }
        }
        this.edgeH = new EdgeWall[height + 1][width];
        this.edgeV = new EdgeWall[height][width + 1];
    }

    /**
     * Returns the tile at grid coordinates (x, y), or null if out of bounds.
     */
    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return tiles[y][x];
    }

    /**
     * Converts local tile indices to a local-space position (the center of the tile).
     */
    public Vector2 tileToLocal(int tileX, int tileY) {
        float lx = gridOriginLocal.x + tileX * tileSize + tileSize * 0.5f;
        float ly = gridOriginLocal.y + tileY * tileSize + tileSize * 0.5f;
        return new Vector2(lx, ly);
    }

    /**
     * Converts a local-space position to tile indices. Returns (-1, -1) if out of bounds.
     */
    public int[] localToTile(float localX, float localY) {
        int tx = MathUtils.floor((localX - gridOriginLocal.x) / tileSize);
        int ty = MathUtils.floor((localY - gridOriginLocal.y) / tileSize);
        if (tx < 0 || tx >= width || ty < 0 || ty >= height) {
            return new int[]{-1, -1};
        }
        return new int[]{tx, ty};
    }

    /**
     * Returns true if the given tile coordinates are within the grid bounds.
     */
    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // -----------------------------------------------------------------------
    // Edge walls
    // -----------------------------------------------------------------------

    /** Returns the edge wall on the given side of tile (x, y), or null if none / out of bounds. */
    public EdgeWall getEdge(int x, int y, Side side) {
        if (!inBounds(x, y)) return null;
        switch (side) {
            case BOTTOM: return edgeH[y][x];
            case TOP:    return edgeH[y + 1][x];
            case LEFT:   return edgeV[y][x];
            default:     return edgeV[y][x + 1];
        }
    }

    /** Places (or with null, removes) the edge wall on the given side of tile (x, y). */
    public void setEdge(int x, int y, Side side, EdgeWall wall) {
        if (!inBounds(x, y)) throw new IllegalArgumentException("Tile out of bounds: " + x + "," + y);
        switch (side) {
            case BOTTOM: edgeH[y][x] = wall; break;
            case TOP:    edgeH[y + 1][x] = wall; break;
            case LEFT:   edgeV[y][x] = wall; break;
            default:     edgeV[y][x + 1] = wall; break;
        }
    }

    /**
     * Returns the edge wall between two orthogonally adjacent tiles, or null if there
     * is none. Either tile may be out of bounds (the grid's outer edges).
     */
    public EdgeWall getEdgeBetween(int x, int y, int nx, int ny) {
        Side side = Side.between(x, y, nx, ny);
        if (side == null) return null;
        if (inBounds(x, y)) return getEdge(x, y, side);
        return getEdge(nx, ny, side.opposite());
    }

    /** The link across the {@code side} edge of tile (x, y) onto another grid, or null. */
    public GridLink linkAt(int x, int y, Side side) {
        for (int i = 0; i < links.size; i++) {
            GridLink l = links.get(i);
            if (l.x == x && l.y == y && l.side == side) return l;
        }
        return null;
    }

    /** True if an intact, closed edge wall separates the two adjacent tiles. */
    public boolean isEdgeBlocked(int x, int y, int nx, int ny) {
        EdgeWall wall = getEdgeBetween(x, y, nx, ny);
        return wall != null && wall.blocks();
    }

    /**
     * True if crew, air and fire can cross from (x, y) into the adjacent (nx, ny): both
     * tiles are open (see {@link Tile#isOpen()}) and no closed edge wall stands between them.
     */
    public boolean canPass(int x, int y, int nx, int ny) {
        Tile a = getTile(x, y), b = getTile(nx, ny);
        if (a == null || b == null || !a.isOpen() || !b.isOpen()) return false;
        return !isEdgeBlocked(x, y, nx, ny);
    }

    /**
     * True if air, smoke and fire can cross from (x, y) into the adjacent (nx, ny): both
     * tiles hold air (open and not sealed, see {@link Tile#holdsAir()}) and no closed edge
     * wall stands between them.
     */
    public boolean canFlow(int x, int y, int nx, int ny) {
        Tile a = getTile(x, y), b = getTile(nx, ny);
        if (a == null || b == null || !a.holdsAir() || !b.holdsAir()) return false;
        return !isEdgeBlocked(x, y, nx, ny);
    }

    /** The compartment whose air fills tile (x, y), or null for walls, sealed tiles and space. */
    public Compartment compartmentAt(int x, int y) {
        Tile t = getTile(x, y);
        return t == null ? null : t.compartment;
    }

    /** Pressure relative to standard at tile (x, y); 0 where there is no air. */
    public float pressureRatioAt(int x, int y) {
        Compartment c = compartmentAt(x, y);
        return c == null ? 0f : c.pressureRatio();
    }

    /** Oxygen partial pressure relative to standard at tile (x, y); 0 where there is no air. */
    public float oxygenRatioAt(int x, int y) {
        Compartment c = compartmentAt(x, y);
        return c == null ? 0f : c.oxygenRatio();
    }

    /**
     * True while a fire is burning anywhere aboard or air is venting to space faster than
     * {@link #VENTING_HAZARD_FLOW} mol/s. Time warp is refused while this holds. Venting
     * is read from {@link #airflow}, so it reflects the last hazard tick.
     */
    public boolean hasActiveHazard() {
        return hasFire() || isVenting();
    }

    /** True while any tile is burning. */
    public boolean hasFire() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[y][x].isBurning()) return true;
            }
        }
        return false;
    }

    /** True while air leaves through some opening to space faster than {@link #VENTING_HAZARD_FLOW}. */
    public boolean isVenting() {
        for (int i = 0; i < airflow.size; i++) {
            AirflowEdge e = airflow.get(i);
            if (e.other == null && e.flow > VENTING_HAZARD_FLOW) return true;
        }
        return false;
    }

    /** Venting below this rate (mol/s through one opening) does not count as a hazard. */
    public static final float VENTING_HAZARD_FLOW = 0.01f;

    // -----------------------------------------------------------------------
    // Hull pieces and mounts
    // -----------------------------------------------------------------------

    /**
     * Places a multi-tile hull piece. Its tiles must be in bounds and either void or
     * plain hull, and not already part of another piece or a mount.
     *
     * @throws IllegalArgumentException if the placement is illegal.
     */
    public void placeHullPiece(HullPiece piece) {
        for (int y = piece.originY; y < piece.originY + piece.height(); y++) {
            for (int x = piece.originX; x < piece.originX + piece.width(); x++) {
                Tile t = getTile(x, y);
                if (t == null) throw new IllegalArgumentException("Hull piece out of bounds at " + x + "," + y);
                if (t.piece != null || t.mount != null)
                    throw new IllegalArgumentException("Hull piece overlaps another piece or mount at " + x + "," + y);
                if (!t.isVoid() && t.wallType != Tile.WallType.HULL)
                    throw new IllegalArgumentException("Hull piece must cover void or hull tiles, not " + x + "," + y);
            }
        }
        for (int y = piece.originY; y < piece.originY + piece.height(); y++) {
            for (int x = piece.originX; x < piece.originX + piece.width(); x++) {
                Tile t = getTile(x, y);
                t.wallType = Tile.WallType.HULL;
                t.floorType = Tile.FloorType.NONE;
                t.setCovering(FloorCovering.NONE, tileSize);
                t.weightClass = piece.weightClass;
                t.material = piece.material;
                t.piece = piece;
            }
        }
        hullPieces.add(piece);
    }

    /**
     * Checks whether a mount footprint can be placed: every tile in bounds, open floor,
     * not in another mount, and no edge wall cutting through the footprint. A docking
     * port has its own rules instead; see {@link DockingPortLayout}.
     *
     * @return null if legal, otherwise a description of the problem.
     */
    public String validateMount(MountFootprint m) {
        if (m.kind == MountFootprint.MountKind.PORT) return DockingPortLayout.validate(this, m);
        int x0 = m.originX, y0 = m.originY, x1 = x0 + m.width(), y1 = y0 + m.height();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                Tile t = getTile(x, y);
                if (t == null) return "out of bounds at " + x + "," + y;
                if (!t.isFloor() || t.isWall()) return "not interior floor at " + x + "," + y;
                if (t.mount != null) return "overlaps another mount at " + x + "," + y;
                if (x + 1 < x1 && getEdge(x, y, Side.RIGHT) != null) return "edge wall inside footprint at " + x + "," + y;
                if (y + 1 < y1 && getEdge(x, y, Side.TOP) != null) return "edge wall inside footprint at " + x + "," + y;
            }
        }
        return null;
    }

    /**
     * Places a mount. A docking port also builds its frame, opening and door into the tiles.
     *
     * @throws IllegalArgumentException if {@link #validateMount} rejects the footprint.
     */
    public void placeMount(MountFootprint m) {
        String problem = validateMount(m);
        if (problem != null) throw new IllegalArgumentException("Illegal " + m.kind + " " + m.size + " mount: " + problem);
        if (m.kind == MountFootprint.MountKind.PORT) DockingPortLayout.build(this, m);
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                getTile(x, y).mount = m;
            }
        }
        mounts.add(m);
    }

    /** The system entity installed over tile (x, y), or null. This is the tile→subsystem index. */
    public Entity subsystemAt(int x, int y) {
        Tile t = getTile(x, y);
        return (t == null || t.mount == null) ? null : t.mount.installed;
    }

    /**
     * Points every mount at the system entity installed in it, matching each system's
     * footprint by position. Call after loading a save, since the link is not saved.
     */
    public void relinkMounts(Iterable<Entity> subsystems) {
        for (MountFootprint m : mounts) m.installed = null;
        for (Entity e : subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub == null) continue;
            for (MountFootprint m : mounts) {
                if (m.originX == sub.footprint.originX && m.originY == sub.footprint.originY && m.kind == sub.footprint.kind) {
                    m.installed = e;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mass properties
    // -----------------------------------------------------------------------

    /** Fraction of a full tile's mass that an edge wall of the same class weighs. */
    public static final float EDGE_WALL_MASS_FRACTION = 0.25f;

    /** Structural mass, centre of mass (body-local) and moment of inertia about it. */
    public static class MassProperties {
        public float mass;
        public final Vector2 center = new Vector2();
        public float inertia;
    }

    /**
     * Computes the structural mass properties of the hull, walls and decks from their
     * weight classes, for feeding the ship's Box2D body. Installed systems are not
     * included; they carry their own mass.
     */
    public MassProperties computeMassProperties(MaterialTable materials) {
        // Accumulate as point masses: sum m, sum m*p, sum (m*|p|^2 + I_own)
        float m = 0f, mx = 0f, my = 0f, i0 = 0f;
        float tileOwnI = tileSize * tileSize / 6f; // square plate: m*(s^2 + s^2)/12
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile t = tiles[y][x];
                if (t.isVoid()) continue;
                float tm = materials.get(t.material, t.weightClass).mass;
                if (t.piece != null) tm *= t.piece.fillFraction();
                Vector2 p = tileToLocal(x, y);
                m += tm; mx += tm * p.x; my += tm * p.y;
                i0 += tm * (p.len2() + tileOwnI);
            }
        }
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                EdgeWall e = edgeH[y][x];
                if (e == null) continue;
                float em = materials.get(e.material, e.weightClass).mass * EDGE_WALL_MASS_FRACTION;
                float px = gridOriginLocal.x + (x + 0.5f) * tileSize, py = gridOriginLocal.y + y * tileSize;
                m += em; mx += em * px; my += em * py; i0 += em * (px * px + py * py);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x <= width; x++) {
                EdgeWall e = edgeV[y][x];
                if (e == null) continue;
                float em = materials.get(e.material, e.weightClass).mass * EDGE_WALL_MASS_FRACTION;
                float px = gridOriginLocal.x + x * tileSize, py = gridOriginLocal.y + (y + 0.5f) * tileSize;
                m += em; mx += em * px; my += em * py; i0 += em * (px * px + py * py);
            }
        }
        MassProperties out = new MassProperties();
        out.mass = m;
        if (m > 0f) {
            out.center.set(mx / m, my / m);
            // Parallel axis theorem: move inertia from the local origin to the centre of mass
            out.inertia = i0 - m * out.center.len2();
        }
        return out;
    }
}
