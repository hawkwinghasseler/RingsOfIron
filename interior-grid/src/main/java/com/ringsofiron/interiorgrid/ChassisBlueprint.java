package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Data describing a ship chassis, consumed by {@link ChassisBlueprintLoader}.
 *
 * A layout is built in layers. Tile types come from a character map ({@link #rows}, written
 * top-to-bottom as it reads on screen), which holds nothing else. Everything on top of or between
 * the tiles (mounts and the systems in them, power conduits, pipes, thin walls and their doors,
 * multi-tile hull pieces, floor coverings, lights and RCS blocks) is listed separately in grid
 * coordinates, where (0, 0) is the bottom-left tile and +y points up, matching Box2D. Conduits,
 * pipes and walls can be given as runs (see {@link LayoutRuns}).
 */
public class ChassisBlueprint {

    /** A power conduit along one side of a tile (see {@link EdgeConduit}). */
    public static class EdgeConduitDef {
        public final int x, y;
        public final Side side;

        public EdgeConduitDef(int x, int y, Side side) {
            this.x = x;
            this.y = y;
            this.side = side;
        }
    }

    /** A power conduit across tile (x, y) (see {@link TileConduit}). */
    public static class ConduitDef {
        public final int x, y;

        public ConduitDef(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A pipe across tile (x, y), or along its {@code side} edge when that is not null (see {@link Pipe}). */
    public static class PipeDef {
        public final int x, y;
        public final Side side;

        public PipeDef(int x, int y, Side side) {
            this.x = x;
            this.y = y;
            this.side = side;
        }
    }

    /** An edge wall on one side of a tile. */
    public static class EdgeWallDef {
        public final int x, y;
        public final Side side;
        public final WeightClass weightClass;
        public final String material;
        public final boolean hasBulkhead;

        public EdgeWallDef(int x, int y, Side side, WeightClass weightClass, String material, boolean hasBulkhead) {
            this.x = x;
            this.y = y;
            this.side = side;
            this.weightClass = weightClass;
            this.material = material;
            this.hasBulkhead = hasBulkhead;
        }
    }

    /** A floor covering laid over every floor tile in a rectangle. */
    public static class CoveringDef {
        public final FloorCovering covering;
        public final int x, y, width, height;

        public CoveringDef(FloorCovering covering, int x, int y, int width, int height) {
            this.covering = covering;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** A system installed in a mount. */
    public static class MountDef {
        /** The mount, or null until it is resolved from {@link #componentId} against the catalog. */
        public final MountFootprint footprint;
        public final SubsystemComponent.SubsystemType type;
        /** The component installed, or null for the type's standard one in the blueprint's catalog. */
        public final ShipPartCatalog.ComponentType component;
        /** A component id to look up in the blueprint's catalog when it loads, or null. */
        public final String componentId;
        /** The mount's size, corner and rotation, for a mount given by {@link #componentId}. */
        public final MountFootprint.MountSize size;
        public final int x, y, rotation;

        public MountDef(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
            this.footprint = footprint;
            this.type = type;
            this.component = null;
            this.componentId = null;
            this.size = footprint.size;
            this.x = footprint.originX;
            this.y = footprint.originY;
            this.rotation = footprint.rotation;
        }

        public MountDef(MountFootprint footprint, ShipPartCatalog.ComponentType component) {
            this.footprint = footprint;
            this.type = component.behavior;
            this.component = component;
            this.componentId = component.id;
            this.size = footprint.size;
            this.x = footprint.originX;
            this.y = footprint.originY;
            this.rotation = footprint.rotation;
        }

        /** A mount for the component with id {@code componentId}, looked up when the blueprint loads. */
        public MountDef(String componentId, MountFootprint.MountSize size, int x, int y, int rotation) {
            this.footprint = null;
            this.type = null;
            this.component = null;
            this.componentId = componentId;
            this.size = size;
            this.x = x;
            this.y = y;
            this.rotation = rotation;
        }
    }

    /** Character map of tile types, one string per row, top row first. See {@link ChassisBlueprintLoader}. */
    public final String[] rows;
    /** World-unit size of each tile (e.g. 1.0f for 1 metre per tile). */
    public float tileSize = 1f;
    /** Body-local position (meters) of the bottom-left corner of tile (0, 0). */
    public float originLocalX = 0f, originLocalY = 0f;

    public final Array<EdgeWallDef> edgeWalls = new Array<>();
    public final Array<ConduitDef> conduits = new Array<>();
    public final Array<EdgeConduitDef> edgeConduits = new Array<>();
    public final Array<PipeDef> pipes = new Array<>();
    public final Array<HullPiece> hullPieces = new Array<>();
    public final Array<MountDef> mounts = new Array<>();
    public final Array<CoveringDef> coverings = new Array<>();
    /** Lights hung on walls; null when the layout does not place any (see {@link ShipGridComponent#wallLights}). */
    public Array<WallLight> wallLights;
    /** RCS thruster blocks on the outside of the hull. */
    public final Array<RcsThruster> rcsThrusters = new Array<>();
    /** The tile types and components the map's characters stand for. */
    public ShipPartCatalog catalog = ShipPartCatalog.builtIn();

    public ChassisBlueprint(String... rows) {
        this.rows = rows;
    }

    public ChassisBlueprint tileSize(float tileSize) {
        this.tileSize = tileSize;
        return this;
    }

    public ChassisBlueprint catalog(ShipPartCatalog catalog) {
        this.catalog = catalog;
        return this;
    }

    public ChassisBlueprint origin(float localX, float localY) {
        this.originLocalX = localX;
        this.originLocalY = localY;
        return this;
    }

    public ChassisBlueprint edgeWall(int x, int y, Side side, WeightClass weightClass, boolean hasBulkhead) {
        edgeWalls.add(new EdgeWallDef(x, y, side, weightClass, MaterialTable.DEFAULT_MATERIAL, hasBulkhead));
        return this;
    }

    /** Builds a thin wall of {@code weightClass} along an edge run, given as corner x, y pairs (see {@link LayoutRuns}). */
    public ChassisBlueprint edgeWallRun(WeightClass weightClass, int... corners) {
        for (LayoutRuns.Edge e : LayoutRuns.edges(points(corners), width(), rows.length)) {
            edgeWalls.add(new EdgeWallDef(e.x, e.y, e.side, weightClass, MaterialTable.DEFAULT_MATERIAL, false));
        }
        return this;
    }

    /**
     * Puts a door in the thin wall on the {@code side} edge of tile (x, y), which must be listed
     * already.
     */
    public ChassisBlueprint door(int x, int y, Side side) {
        for (int i = 0; i < edgeWalls.size; i++) {
            EdgeWallDef w = edgeWalls.get(i);
            if (sameEdge(w.x, w.y, w.side, x, y, side)) {
                edgeWalls.set(i, new EdgeWallDef(w.x, w.y, w.side, w.weightClass, w.material, true));
                return this;
            }
        }
        throw new IllegalArgumentException("No edge wall for the door at " + x + "," + y + " " + side);
    }

    /** Lays a power conduit across tile (x, y). */
    public ChassisBlueprint conduit(int x, int y) {
        conduits.add(new ConduitDef(x, y));
        return this;
    }

    /** Lays power conduit along a tile run, given as tile x, y pairs (see {@link LayoutRuns}). */
    public ChassisBlueprint conduitRun(int... tiles) {
        for (int[] t : LayoutRuns.tiles(points(tiles))) conduits.add(new ConduitDef(t[0], t[1]));
        return this;
    }

    /** Lays power conduit along an edge run, given as corner x, y pairs (see {@link LayoutRuns}). */
    public ChassisBlueprint edgeConduitRun(int... corners) {
        for (LayoutRuns.Edge e : LayoutRuns.edges(points(corners), width(), rows.length)) {
            edgeConduits.add(new EdgeConduitDef(e.x, e.y, e.side));
        }
        return this;
    }

    /** Lays pipe along a tile run, given as tile x, y pairs. */
    public ChassisBlueprint pipeRun(int... tiles) {
        for (int[] t : LayoutRuns.tiles(points(tiles))) pipes.add(new PipeDef(t[0], t[1], null));
        return this;
    }

    /** Lays pipe along an edge run, given as corner x, y pairs. */
    public ChassisBlueprint edgePipeRun(int... corners) {
        for (LayoutRuns.Edge e : LayoutRuns.edges(points(corners), width(), rows.length)) {
            pipes.add(new PipeDef(e.x, e.y, e.side));
        }
        return this;
    }

    /** The widest row: the grid's width. */
    public int width() {
        int w = 0;
        for (String row : rows) w = Math.max(w, row.length());
        return w;
    }

    private static int[][] points(int[] xy) {
        if (xy.length % 2 != 0) throw new IllegalArgumentException("A run's points are x, y pairs");
        int[][] out = new int[xy.length / 2][];
        for (int i = 0; i < out.length; i++) out[i] = new int[]{xy[2 * i], xy[2 * i + 1]};
        return out;
    }

    /** True if the two tile sides name the same edge. */
    static boolean sameEdge(int x, int y, Side side, int x2, int y2, Side side2) {
        if (x == x2 && y == y2 && side == side2) return true;
        return x + side.dx == x2 && y + side.dy == y2 && side.opposite() == side2;
    }

    /** Runs a power conduit along the {@code side} edge of tile (x, y). */
    public ChassisBlueprint edgeConduit(int x, int y, Side side) {
        edgeConduits.add(new EdgeConduitDef(x, y, side));
        return this;
    }

    /** Lays a pipe across the middle of tile (x, y). */
    public ChassisBlueprint pipe(int x, int y) {
        pipes.add(new PipeDef(x, y, null));
        return this;
    }

    /** Lays a pipe along the {@code side} edge of tile (x, y). */
    public ChassisBlueprint pipe(int x, int y, Side side) {
        pipes.add(new PipeDef(x, y, side));
        return this;
    }

    public ChassisBlueprint hullPiece(HullPiece piece) {
        hullPieces.add(piece);
        return this;
    }

    /** Lays a covering on every floor tile in the rectangle with bottom-left tile (x, y). */
    public ChassisBlueprint covering(FloorCovering covering, int x, int y, int width, int height) {
        coverings.add(new CoveringDef(covering, x, y, width, height));
        return this;
    }

    public ChassisBlueprint mount(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
        mounts.add(new MountDef(footprint, type));
        return this;
    }

    public ChassisBlueprint mount(MountFootprint footprint, ShipPartCatalog.ComponentType component) {
        mounts.add(new MountDef(footprint, component));
        return this;
    }

    /** Installs the component with id {@code component} in a mount of {@code size} with bottom-left tile (x, y). */
    public ChassisBlueprint mount(String component, MountFootprint.MountSize size, int x, int y) {
        return mount(component, size, x, y, 0);
    }

    /** As {@link #mount(String, MountFootprint.MountSize, int, int)}, turned {@code rotation} quarter turns. */
    public ChassisBlueprint mount(String component, MountFootprint.MountSize size, int x, int y, int rotation) {
        mounts.add(new MountDef(component, size, x, y, rotation));
        return this;
    }
}
