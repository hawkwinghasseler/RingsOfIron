package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Loads and validates a {@link ChassisBlueprint} into a {@link ShipGridComponent}.
 *
 * Characters are looked up in the blueprint's {@link ShipPartCatalog}: tile types build a tile,
 * component letters mark mounts, and any other character is an error. The built-in ones:
 *   '#' = Outer hull wall, medium (immovable, WallType.HULL)
 *   '1' / '2' / '3' = Outer hull wall, light / medium / heavy
 *   'I' = Full-tile internal wall, medium (WallType.INTERNAL)
 *   '.' = Interior floor tile (steel deck)
 *   '+' = Power conduit tile (steel deck)
 *   ' ' = Void (space, not part of the ship)
 *
 * System letters mark floor tiles covered by a mount. Each solid rectangle of one
 * letter becomes one mount, and its size must be a legal mount for that system's kind
 * (in either orientation for rectangular slots). Adjacent mounts of the same type
 * cannot be told apart in the map; list those in {@link ChassisBlueprint#mounts}.
 *   Primary (square):        'R' reactor, 'S' shield emitter, 'T' turret mount
 *   Auxiliary (rectangular): 'L' life support, 'H' helm, 'W' weapons console,
 *                            'C' cargo, 'A' airlock
 *   Docking port:            'D', drawn over the hull edge: 3 x 1 Small, 5 x 2 Medium,
 *                            7 x 3 Large. It faces the side where nothing but space lies
 *                            beyond it; a port that could face either way goes in
 *                            {@link ChassisBlueprint#mounts} with its rotation. See
 *                            {@link DockingPortLayout} for its frame, opening and door.
 *
 * Floor coverings such as carpet are laid over the steel deck from
 * {@link ChassisBlueprint#coverings}, on every floor tile inside each listed rectangle.
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

    /** Parses a character map with no edge walls, hull pieces or explicit mounts. */
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
                if (tileType != null) {
                    tileType.apply(grid.getTile(x, y), grid.tileSize);
                } else if (catalog.componentFor(ch) != null) {
                    grid.getTile(x, y).floorType = Tile.FloorType.FLOOR;
                } else {
                    throw new IllegalArgumentException("'" + ch + "' at " + x + "," + y
                            + " is not a tile type or component symbol");
                }
            }
        }

        for (ChassisBlueprint.EdgeWallDef def : blueprint.edgeWalls) {
            grid.setEdge(def.x, def.y, def.side, new EdgeWall(def.weightClass, def.material, def.hasBulkhead));
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

        for (SubsystemPlacement drawn : drawnMounts(chars, catalog, true)) {
            grid.placeMount(drawn.footprint);
            subsystems.add(drawn);
        }

        for (ChassisBlueprint.MountDef def : blueprint.mounts) {
            ShipPartCatalog.ComponentType component = def.component != null ? def.component
                    : catalog.standardComponent(def.type);
            if (def.footprint.kind != component.mountKind()) {
                throw new IllegalArgumentException(component.name + " needs a " + component.mountKind() + " mount");
            }
            if (!component.sizes.contains(def.footprint.size)) {
                throw new IllegalArgumentException(component.name + " does not come in size " + def.footprint.size);
            }
            grid.placeMount(def.footprint);
            subsystems.add(new SubsystemPlacement(def.footprint, component));
        }

        return new LoadResult(grid, subsystems);
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
     * Finds the mounts drawn as letter rectangles in a character grid (see {@link #toGrid}): each
     * solid rectangle of one component's letter is one mount, and a docking port faces the side
     * with only space beyond it. Nothing is placed on a grid.
     *
     * @param strict throw on a rectangle that is not a legal mount; otherwise skip it
     */
    public static Array<SubsystemPlacement> drawnMounts(char[][] chars, ShipPartCatalog catalog, boolean strict) {
        int height = chars.length, width = height == 0 ? 0 : chars[0].length;
        // Void tiles, for telling which way a docking port faces
        boolean[][] space = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                ShipPartCatalog.TileType t = catalog.tileFor(chars[y][x]);
                space[y][x] = t != null && t.base == ShipPartCatalog.TileBase.VOID;
            }
        }
        Array<SubsystemPlacement> out = new Array<>();
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                ShipPartCatalog.ComponentType component = catalog.componentFor(chars[y][x]);
                if (component == null || visited[y][x]) continue;
                SubsystemComponent.SubsystemType type = component.behavior;
                char c = chars[y][x];
                int w = 1;
                while (x + w < width && chars[y][x + w] == c && !visited[y][x + w]) w++;
                int h = 1;
                while (y + h < height && rowMatches(chars[y + h], visited[y + h], x, w, c)) h++;
                for (int yy = y; yy < y + h; yy++)
                    for (int xx = x; xx < x + w; xx++) visited[yy][xx] = true;

                MountFootprint footprint = MountFootprint.fromSize(type.mountKind, x, y, w, h);
                if (footprint == null || !component.sizes.contains(footprint.size)) {
                    if (!strict) continue;
                    throw new IllegalArgumentException("'" + c + "' at " + x + "," + y + " is " + w + "x" + h
                            + ", which is not a size " + component.name + " comes in");
                }
                if (type.mountKind == MountFootprint.MountKind.PORT) {
                    try {
                        footprint = facingSpace(space, footprint);
                    } catch (IllegalArgumentException e) {
                        if (strict) throw e;
                        continue;
                    }
                }
                out.add(new SubsystemPlacement(footprint, component));
            }
        }
        return out;
    }

    /**
     * Turns a port drawn in the map to face the side with only space beyond it: a horizontal port
     * faces up or down, a vertical one left or right.
     */
    private static MountFootprint facingSpace(boolean[][] space, MountFootprint port) {
        Side[] sides = port.rotation % 2 == 0 ? new Side[]{Side.TOP, Side.BOTTOM} : new Side[]{Side.LEFT, Side.RIGHT};
        Side found = null;
        for (Side side : sides) {
            if (!spaceBeyond(space, port, side)) continue;
            if (found != null) {
                throw new IllegalArgumentException("Docking port at " + port.originX + "," + port.originY
                        + " could face " + found + " or " + side + "; list it under mounts with a rotation");
            }
            found = side;
        }
        if (found == null) {
            throw new IllegalArgumentException("Docking port at " + port.originX + "," + port.originY
                    + " has no side facing space");
        }
        return new MountFootprint(port.kind, port.size, port.originX, port.originY, DockingPortLayout.rotationFacing(found));
    }

    /** True if every tile just beyond one side of the footprint is void or off the map. */
    private static boolean spaceBeyond(boolean[][] space, MountFootprint m, Side side) {
        int height = space.length, width = height == 0 ? 0 : space[0].length;
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                int bx = x + side.dx, by = y + side.dy;
                if (m.covers(bx, by)) continue;
                if (bx >= 0 && bx < width && by >= 0 && by < height && !space[by][bx]) return false;
            }
        }
        return true;
    }

    private static boolean rowMatches(char[] row, boolean[] visitedRow, int x, int w, char c) {
        for (int xx = x; xx < x + w; xx++) {
            if (row[xx] != c || visitedRow[xx]) return false;
        }
        return true;
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
