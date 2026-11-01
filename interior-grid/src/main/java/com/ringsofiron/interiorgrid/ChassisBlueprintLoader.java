package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Loads and validates a {@link ChassisBlueprint} into a {@link ShipGridComponent}.
 *
 * The rows hold tile types only: each character is looked up in the blueprint's
 * {@link ShipPartCatalog}, and one that is not a tile type is an error. The built-in ones:
 *   '#' = Outer hull wall, medium (immovable, WallType.HULL)
 *   '1' / '2' / '3' = Outer hull wall, light / medium / heavy
 *   'I' = Full-tile internal wall, medium (WallType.INTERNAL)
 *   '.' = Interior floor tile (steel deck)
 *   ' ' = Void (space, not part of the ship)
 *
 * Every other layer is laid over the tiles from the blueprint's lists: thin walls and their
 * doors, power conduits across tiles ({@link TileConduit}) and along edges ({@link EdgeConduit}),
 * pipes, floor coverings, hull pieces, lights, RCS blocks, and the mounts with the systems
 * installed in them. A mount's size must be one its component comes in, and the tiles under it
 * are as the rows draw them: floor under an interior system, hull under a docking port's frame
 * and an escape pod's hatch end, void under an engine outside the hull (see {@link EngineLayout},
 * {@link DockingPortLayout} and {@link EscapePodLayout}). Conduits and pipes take no room, so they
 * may run under any mount.
 *
 * This class is intentionally independent of the ECS; it simply produces a
 * populated ShipGridComponent and a list of SubsystemPlacement data objects.
 */
public class ChassisBlueprintLoader {

    /**
     * A system and the mount it installs into, discovered during blueprint parsing.
     * The mount is already placed on the grid; the caller creates the system entity
     * and sets {@link MountFootprint#installed}.
     */
    public static class SubsystemPlacement {
        public final MountFootprint footprint;
        public final SubsystemComponent.SubsystemType type;
        /** The component installed here; {@link ShipPartCatalog.ComponentType#configure} sets the system up from it. */
        public final ShipPartCatalog.ComponentType component;

        public SubsystemPlacement(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
            this(footprint, ShipPartCatalog.builtIn().standardComponent(type));
        }

        public SubsystemPlacement(MountFootprint footprint, ShipPartCatalog.ComponentType component) {
            this.footprint = footprint;
            this.type = component.behavior;
            this.component = component;
        }
    }

    /**
     * The result of loading a blueprint: the grid and the discovered subsystem locations.
     */
    public static class LoadResult {
        public final ShipGridComponent grid;
        public final Array<SubsystemPlacement> subsystems;

        public LoadResult(ShipGridComponent grid, Array<SubsystemPlacement> subsystems) {
            this.grid = grid;
            this.subsystems = subsystems;
        }
    }

    /** Builds a bare grid of tile types: no walls, conduits, pipes or mounts. */
    public static LoadResult load(String[] blueprint, float tileSize) {
        return load(new ChassisBlueprint(blueprint).tileSize(tileSize));
    }

    /**
     * Builds the grid from a blueprint.
     *
     * @throws IllegalArgumentException if a mount, hull piece or edge wall is illegal.
     */
    public static LoadResult load(ChassisBlueprint blueprint) {
        String[] rows = blueprint.rows;
        ShipPartCatalog catalog = blueprint.catalog;
        int height = rows.length;
        // Find the widest row
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }

        ShipGridComponent grid = new ShipGridComponent(width, height, blueprint.tileSize,
                blueprint.originLocalX, blueprint.originLocalY);
        Array<SubsystemPlacement> subsystems = new Array<>();

        char[][] chars = toGrid(rows);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char ch = chars[y][x];
                ShipPartCatalog.TileType tileType = catalog.tileFor(ch);
                if (tileType == null) {
                    throw new IllegalArgumentException("'" + ch + "' at " + x + "," + y + " is not a tile type");
                }
                tileType.apply(grid.getTile(x, y), grid.tileSize);
            }
        }

        for (ChassisBlueprint.EdgeWallDef def : blueprint.edgeWalls) {
            grid.setEdge(def.x, def.y, def.side, new EdgeWall(def.weightClass, def.material, def.hasBulkhead));
        }

        // Conduits across tiles lie on top of them, floor or wall, under anything mounted there
        for (ChassisBlueprint.ConduitDef def : blueprint.conduits) {
            if (!grid.inBounds(def.x, def.y)) {
                throw new IllegalArgumentException("Conduit at " + def.x + "," + def.y + " is off the grid");
            }
            if (grid.getTile(def.x, def.y).isVoid()) {
                throw new IllegalArgumentException("Conduit at " + def.x + "," + def.y
                        + " lies out in space: a conduit lies on a floor or wall tile");
            }
            if (grid.getTileConduit(def.x, def.y) == null) grid.setTileConduit(def.x, def.y, new TileConduit());
        }

        // Conduits along edges may run inside any wall, full-tile or thin
        for (ChassisBlueprint.EdgeConduitDef def : blueprint.edgeConduits) {
            grid.setConduit(def.x, def.y, def.side, new EdgeConduit());
        }

        // Pipes, like conduits, may run along any edge; across a tile they lie under the deck
        for (ChassisBlueprint.PipeDef def : blueprint.pipes) {
            if (def.side == null) grid.setPipe(def.x, def.y, new Pipe());
            else grid.setEdgePipe(def.x, def.y, def.side, new Pipe());
        }

        for (ChassisBlueprint.CoveringDef def : blueprint.coverings) {
            for (int y = def.y; y < def.y + def.height; y++) {
                for (int x = def.x; x < def.x + def.width; x++) {
                    Tile t = grid.getTile(x, y);
                    if (t != null && t.isFloor()) t.setCovering(def.covering, grid.tileSize);
                }
            }
        }

        for (HullPiece piece : blueprint.hullPieces) {
            grid.placeHullPiece(piece);
        }

        if (blueprint.wallLights != null) grid.wallLights = new Array<>(blueprint.wallLights);
        grid.rcsThrusters.addAll(blueprint.rcsThrusters);

        Array<ChassisBlueprint.MountDef> mounts = new Array<>();
        for (ChassisBlueprint.MountDef def : blueprint.mounts) mounts.add(resolve(def, catalog));
        for (ChassisBlueprint.MountDef def : mounts) {
            if (def.type == SubsystemComponent.SubsystemType.ENGINE) EngineLayout.shape(grid, def.footprint);
        }
        for (ChassisBlueprint.MountDef def : mounts) {
            ShipPartCatalog.ComponentType component = def.component != null ? def.component
                    : catalog.standardComponent(def.type);
            if (def.footprint.kind != component.mountKind()) {
                throw new IllegalArgumentException(component.name + " needs a " + component.mountKind() + " mount");
            }
            if (!component.sizes.contains(def.footprint.size)) {
                throw new IllegalArgumentException(component.name + " does not come in size " + def.footprint.size);
            }
            grid.placeMount(def.footprint, component.behavior);
            encase(grid, def.footprint, component);
            subsystems.add(new SubsystemPlacement(def.footprint, component));
        }

        // Every part is in place now: nothing may stand in an engine's exhaust or a pod's launch path
        for (SubsystemPlacement p : subsystems) {
            if (p.type == SubsystemComponent.SubsystemType.ENGINE) {
                String blocked = EngineLayout.exhaustBlocked(grid, p.footprint);
                if (blocked != null) throw new IllegalArgumentException("Illegal engine at " + p.footprint.originX + ","
                        + p.footprint.originY + ": " + blocked);
            } else if (p.type == SubsystemComponent.SubsystemType.ESCAPE_POD) {
                String problem = EscapePodLayout.clearance(grid, p.footprint);
                if (problem != null) throw new IllegalArgumentException("Illegal escape pod: " + problem);
            }
        }

        return new LoadResult(grid, subsystems);
    }

    /** Rebuilds an engine's casing as the hull its component names, when it names one. */
    private static void encase(ShipGridComponent grid, MountFootprint m, ShipPartCatalog.ComponentType component) {
        if (component.behavior == SubsystemComponent.SubsystemType.ENGINE && component.casing != null) {
            EngineLayout.build(grid, m, component.casing);
        }
    }

    /** A mount with its footprint, looking a component given by id up in {@code catalog}. */
    private static ChassisBlueprint.MountDef resolve(ChassisBlueprint.MountDef def, ShipPartCatalog catalog) {
        if (def.footprint != null) return def;
        ShipPartCatalog.ComponentType component = catalog.component(def.componentId);
        if (component == null) throw new IllegalArgumentException("Unknown component '" + def.componentId + "'");
        if (!def.size.exists(component.mountKind())) {
            throw new IllegalArgumentException("There is no " + def.size + " " + component.mountKind() + " mount");
        }
        return new ChassisBlueprint.MountDef(new MountFootprint(component.mountKind(), def.size, def.x, def.y,
                def.rotation), component);
    }

    /**
     * The character map as a grid: {@code [y][x]}, row 0 at the bottom. Text row 0 is the top
     * line, so text row r is stored at gridY = height - 1 - r. Short rows are padded with void.
     */
    public static char[][] toGrid(String[] rows) {
        int height = rows.length, width = 0;
        for (String row : rows) width = Math.max(width, row.length());
        char[][] chars = new char[height][width];
        for (int r = 0; r < height; r++) {
            int gridY = height - 1 - r;
            for (int x = 0; x < width; x++) {
                chars[gridY][x] = x < rows[r].length() ? rows[r].charAt(x) : ' ';
            }
        }
        return chars;
    }

    /**
     * Validates that a player-placed single-tile module is on open interior floor
     * that no mount already covers.
     *
     * @return true if placement is legal.
     */
    public static boolean isPlacementValid(ShipGridComponent grid, int x, int y) {
        Tile tile = grid.getTile(x, y);
        if (tile == null) return false;
        return tile.isFloor() && !tile.isWall() && tile.mount == null;
    }

    /** Validates a mount footprint against the grid. See {@link ShipGridComponent#validateMount}. */
    public static boolean isPlacementValid(ShipGridComponent grid, MountFootprint footprint) {
        return grid.validateMount(footprint) == null;
    }
}
