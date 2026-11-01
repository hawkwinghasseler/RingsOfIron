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
    /** Power conduits along horizontal edges, indexed like {@link #edgeH}. Null means none. */
    public final EdgeConduit[][] conduitH;
    /** Power conduits along vertical edges, indexed like {@link #edgeV}. Null means none. */
    public final EdgeConduit[][] conduitV;
    /** Power conduits across tiles, [y][x] like {@link #tiles}. Null means none. See {@link TileConduit}. */
    public final TileConduit[][] tileConduits;
    /** Pipes across the middle of tiles, [y][x] like {@link #tiles}. Null means none. See {@link Pipe}. */
    public final Pipe[][] pipes;
    /** Pipes along horizontal edges, indexed like {@link #edgeH}. Null means none. */
    public final Pipe[][] pipeH;
    /** Pipes along vertical edges, indexed like {@link #edgeV}. Null means none. */
    public final Pipe[][] pipeV;
    public final Array<HullPiece> hullPieces = new Array<>();
    public final Array<MountFootprint> mounts = new Array<>();
    /**
     * The lights hung on its walls, as the layout places them; null when the layout does not
     * say, and the game works them out ({@link WallLightLayout}).
     */
    public Array<WallLight> wallLights;
    /** RCS thruster blocks on the outside of its hull; empty for a craft with none. */
    public final Array<RcsThruster> rcsThrusters = new Array<>();
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
    /** The ship's connected pipe runs, rebuilt by {@link PipeRouting}. Not saved. */
    public final transient Array<PipeNetwork> pipeNetworks = new Array<>();
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
    /**
     * Id of the chassis this grid was built from (chassis.toml), which names its exterior art;
     * null for a grid made in code.
     */
    public String chassis;
    /**
     * Hull patches the ship carries for damage control: each one lets the crew stop a leaking
     * wall, deck plate or thin wall in the field (see {@link LeakPatches}). A yard restocks them.
     */
    public int patchKits = LeakPatches.DEFAULT_KITS;
    /** Medical stores a ship carries, and a yard restocks them to. */
    public static final int DEFAULT_MEDICAL_STORES = 6;
    /**
     * Medical stores aboard: each wound treated a level down uses one. With none left the wounded
     * are treated slowly. A yard restocks them.
     */
    public int medicalStores = DEFAULT_MEDICAL_STORES;
    /**
     * Short circuits and tank bursts aboard that nothing has reported yet, oldest first (see
     * {@link DamageStates}). The game drains them into notices for the player's ship. Not saved.
     */
    public final transient Array<DamageStates.Incident> incidents = new Array<>();
    /**
     * Furnishings a fire has burned away since the game last looked, oldest first (see
     * {@link GridHazardSystem}). The game drains them into notices for the player's ship. Not saved.
     */
    public final transient Array<Furnishing> burntFurnishings = new Array<>();
    /** Short circuits and tank bursts aboard since the grid was built or loaded, for surveys. Not saved. */
    public transient int shortCount, burstCount;

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
        this.conduitH = new EdgeConduit[height + 1][width];
        this.conduitV = new EdgeConduit[height][width + 1];
        this.tileConduits = new TileConduit[height][width];
        this.pipes = new Pipe[height][width];
        this.pipeH = new Pipe[height + 1][width];
        this.pipeV = new Pipe[height][width + 1];
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

    /** Returns the edge conduit on the given side of tile (x, y), or null if none / out of bounds. */
    public EdgeConduit getConduit(int x, int y, Side side) {
        if (!inBounds(x, y)) return null;
        switch (side) {
            case BOTTOM: return conduitH[y][x];
            case TOP:    return conduitH[y + 1][x];
            case LEFT:   return conduitV[y][x];
            default:     return conduitV[y][x + 1];
        }
    }

    /** Lays (or with null, removes) the edge conduit on the given side of tile (x, y). */
    public void setConduit(int x, int y, Side side, EdgeConduit conduit) {
        if (!inBounds(x, y)) throw new IllegalArgumentException("Tile out of bounds: " + x + "," + y);
        switch (side) {
            case BOTTOM: conduitH[y][x] = conduit; break;
            case TOP:    conduitH[y + 1][x] = conduit; break;
            case LEFT:   conduitV[y][x] = conduit; break;
            default:     conduitV[y][x + 1] = conduit; break;
        }
    }

    /** The power conduit across tile (x, y), or null if none / out of bounds. */
    public TileConduit getTileConduit(int x, int y) {
        return inBounds(x, y) ? tileConduits[y][x] : null;
    }

    /** Lays (or with null, takes up) the power conduit across tile (x, y). */
    public void setTileConduit(int x, int y, TileConduit conduit) {
        if (!inBounds(x, y)) throw new IllegalArgumentException("Tile out of bounds: " + x + "," + y);
        tileConduits[y][x] = conduit;
    }

    /**
     * True if tile (x, y) has a power conduit on its {@code side} edge or across the tile beyond
     * it: what a conduit across the tile reaches toward.
     */
    public boolean hasConduitToward(int x, int y, Side side) {
        return getConduit(x, y, side) != null || getTileConduit(x + side.dx, y + side.dy) != null;
    }

    /** True if any power conduit across a tile or along an edge has been shorted. */
    public boolean hasShortedConduit() {
        for (TileConduit[] row : tileConduits) for (TileConduit c : row) if (c != null && c.shorted) return true;
        return false;
    }

    /** The pipe across the middle of tile (x, y), or null if none / out of bounds. */
    public Pipe getPipe(int x, int y) {
        return inBounds(x, y) ? pipes[y][x] : null;
    }

    /** Lays (or with null, takes up) the pipe across the middle of tile (x, y). */
    public void setPipe(int x, int y, Pipe pipe) {
        if (!inBounds(x, y)) throw new IllegalArgumentException("Tile out of bounds: " + x + "," + y);
        pipes[y][x] = pipe;
    }

    /** Returns the pipe on the given side of tile (x, y), or null if none / out of bounds. */
    public Pipe getEdgePipe(int x, int y, Side side) {
        if (!inBounds(x, y)) return null;
        switch (side) {
            case BOTTOM: return pipeH[y][x];
            case TOP:    return pipeH[y + 1][x];
            case LEFT:   return pipeV[y][x];
            default:     return pipeV[y][x + 1];
        }
    }

    /** Lays (or with null, takes up) the pipe on the given side of tile (x, y). */
    public void setEdgePipe(int x, int y, Side side, Pipe pipe) {
        if (!inBounds(x, y)) throw new IllegalArgumentException("Tile out of bounds: " + x + "," + y);
        switch (side) {
            case BOTTOM: pipeH[y][x] = pipe; break;
            case TOP:    pipeH[y + 1][x] = pipe; break;
            case LEFT:   pipeV[y][x] = pipe; break;
            default:     pipeV[y][x + 1] = pipe; break;
        }
    }

    /**
     * True if tile (x, y) has a pipe on its {@code side} edge or across the neighbor beyond it:
     * what a pipe across the tile's middle reaches toward.
     */
    public boolean hasPipeToward(int x, int y, Side side) {
        return getEdgePipe(x, y, side) != null || getPipe(x + side.dx, y + side.dy) != null;
    }

    /** True if the grid has any pipe at all. */
    public boolean hasPipes() {
        for (Pipe[][] a : new Pipe[][][]{pipes, pipeH, pipeV})
            for (Pipe[] row : a) for (Pipe p : row) if (p != null) return true;
        return false;
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

    /**
     * True while the ship as a whole loses air to space faster than {@link #VENTING_HAZARD_FLOW}:
     * a sealed or patched leak, a trickle, or a leak from a room already empty does not count.
     */
    public boolean isVenting() {
        return ventingRate() > VENTING_HAZARD_FLOW;
    }

    /** Air leaving the ship for space through every opening, mol/s, from the last hazard tick. */
    public float ventingRate() {
        float total = 0f;
        for (int i = 0; i < airflow.size; i++) {
            AirflowEdge e = airflow.get(i);
            if (e.other == null && e.flow > 0f) total += e.flow;
        }
        return total;
    }

    /**
     * Venting below this rate (mol/s, the whole ship) does not count as a hazard. Life support
     * makes it up without noticing: a standard room holds thousands of moles.
     */
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
     * Checks a mount for the system that installs into it: systems mounted through the hull have
     * their own rules (an engine outside it, see {@link EngineLayout}; an escape pod through it, see
     * {@link EscapePodLayout}); the rest use {@link #validateMount(MountFootprint)}.
     *
     * @return null if legal, otherwise a description of the problem.
     */
    public String validateMount(MountFootprint m, SubsystemComponent.SubsystemType type) {
        if (type == SubsystemComponent.SubsystemType.ENGINE) return EngineLayout.validate(this, m);
        if (type == SubsystemComponent.SubsystemType.ESCAPE_POD) return EscapePodLayout.validate(this, m);
        return validateMount(m);
    }

    /**
     * Places a mount. A docking port also builds its frame, opening and door into the tiles. An
     * engine or escape pod goes through {@link #placeMount(MountFootprint, SubsystemComponent.SubsystemType)}.
     *
     * @throws IllegalArgumentException if {@link #validateMount} rejects the footprint.
     */
    public void placeMount(MountFootprint m) {
        placeMount(m, null);
    }

    /**
     * Places a mount for a system of {@code type}. A docking port also builds its frame, opening and
     * door into the tiles, and an engine its casing.
     *
     * @throws IllegalArgumentException if {@link #validateMount(MountFootprint, SubsystemComponent.SubsystemType)}
     *         rejects the footprint.
     */
    public void placeMount(MountFootprint m, SubsystemComponent.SubsystemType type) {
        String problem = validateMount(m, type);
        if (problem != null) throw new IllegalArgumentException("Illegal " + m.kind + " " + m.size + " mount: " + problem);
        if (type == SubsystemComponent.SubsystemType.ENGINE) EngineLayout.build(this, m);
        else if (m.kind == MountFootprint.MountKind.PORT) DockingPortLayout.build(this, m);
        register(m);
    }

    private void register(MountFootprint m) {
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

    /**
     * Fraction of a full tile's mass that an edge wall of the same class weighs: a bulkhead plate
     * 8 to 12 mm thick across the deck height, 200 kg in medium steel. Mass only; combat keeps its
     * own share of the tile's hit points and armor.
     */
    public static final float EDGE_WALL_MASS_FRACTION = 0.1f;
    /**
     * Kilograms one open tile of deck weighs, floor or conduit: about 8 to 10 mm of deck plate over
     * 1 m^2 with the framing under it and the ceiling services over it. The systems standing on a
     * tile weigh what they weigh on their own ({@link ComponentMass}).
     */
    public static final float DECK_MASS = 150f;
    /**
     * Kilograms of power cabling in a metre of conduit, on a conduit tile or along an edge: a main
     * bus carrying a few MW at about 1 kV (~3,000 A, ~13 kg of copper a metre) with its insulation
     * and tray. Only the cable; the deck it runs over is {@link #DECK_MASS}.
     */
    public static final float CONDUIT_CABLE_MASS = 20f;

    /** Structural mass, centre of mass (body-local) and moment of inertia about it. */
    public static class MassProperties {
        public float mass;
        public final Vector2 center = new Vector2();
        public float inertia;
    }

    /**
     * Computes the structural mass properties of the hull, walls, decks and power cabling, for
     * feeding the ship's Box2D body. Walls weigh by their material and weight class; open tiles
     * weigh {@link #DECK_MASS}, plus {@link #CONDUIT_CABLE_MASS} on a conduit, and every edge
     * conduit adds its cable. An engine's casing tiles weigh nothing here, since the engine
     * carries its own mass. Installed systems are not included; {@link ComponentMass} adds them.
     */
    public MassProperties computeMassProperties(MaterialTable materials) {
        // Accumulate as point masses: sum m, sum m*p, sum (m*|p|^2 + I_own)
        float m = 0f, mx = 0f, my = 0f, i0 = 0f;
        float tileOwnI = tileSize * tileSize / 6f; // square plate: m*(s^2 + s^2)/12
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile t = tiles[y][x];
                if (t.isVoid()) continue;
                float tm;
                if (t.isWall()) {
                    if (EngineLayout.isCasing(this, x, y)) continue;
                    tm = materials.get(t.material, t.weightClass).mass;
                    if (t.piece != null) tm *= t.piece.fillFraction();
                } else {
                    tm = DECK_MASS + (t.isPowerConduit() ? CONDUIT_CABLE_MASS : 0f);
                }
                Vector2 p = tileToLocal(x, y);
                m += tm; mx += tm * p.x; my += tm * p.y;
                i0 += tm * (p.len2() + tileOwnI);
            }
        }
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                float em = 0f;
                EdgeWall e = edgeH[y][x];
                if (e != null) em += materials.get(e.material, e.weightClass).mass * EDGE_WALL_MASS_FRACTION;
                if (conduitH[y][x] != null) em += CONDUIT_CABLE_MASS;
                if (em == 0f) continue;
                float px = gridOriginLocal.x + (x + 0.5f) * tileSize, py = gridOriginLocal.y + y * tileSize;
                m += em; mx += em * px; my += em * py; i0 += em * (px * px + py * py);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x <= width; x++) {
                float em = 0f;
                EdgeWall e = edgeV[y][x];
                if (e != null) em += materials.get(e.material, e.weightClass).mass * EDGE_WALL_MASS_FRACTION;
                if (conduitV[y][x] != null) em += CONDUIT_CABLE_MASS;
                if (em == 0f) continue;
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
