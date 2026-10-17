package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Loads and validates a {@link ChassisBlueprint} into a {@link ShipGridComponent}.
 *
 * Character map convention:
 *   '#' = Outer hull wall, medium (immovable, WallType.HULL)
 *   '1' / '2' / '3' = Outer hull wall, light / medium / heavy
 *   'I' = Full-tile internal wall, medium (WallType.INTERNAL)
 *   '.' = Interior floor tile
 *   '+' = Power conduit tile
 *   ' ' = Void (space, not part of the ship)
 *
 * System letters mark floor tiles covered by a mount. Each solid rectangle of one
 * letter becomes one mount, and its size must be a legal mount for that system's kind
 * (in either orientation for rectangular slots). Adjacent mounts of the same type
 * cannot be told apart in the map; list those in {@link ChassisBlueprint#mounts}.
 *   Primary (square):        'R' reactor, 'S' shield emitter, 'T' turret mount
 *   Auxiliary (rectangular): 'L' life support, 'H' helm, 'W' weapons console,
 *                            'C' cargo, 'A' airlock
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

        public SubsystemPlacement(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
            this.footprint = footprint;
            this.type = type;
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
        int height = rows.length;
        // Find the widest row
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }

        ShipGridComponent grid = new ShipGridComponent(width, height, blueprint.tileSize,
                blueprint.originLocalX, blueprint.originLocalY);
        Array<SubsystemPlacement> subsystems = new Array<>();

        // Grid row 0 is the BOTTOM (Box2D Y-up), but text row 0 is the top line,
        // so text row r is stored at gridY = height - 1 - r.
        char[][] chars = new char[height][width];
        for (int r = 0; r < height; r++) {
            int gridY = height - 1 - r;
            for (int x = 0; x < width; x++) {
                chars[gridY][x] = x < rows[r].length() ? rows[r].charAt(x) : ' ';
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                applyTileChar(grid.getTile(x, y), chars[y][x]);
            }
        }

        for (ChassisBlueprint.EdgeWallDef def : blueprint.edgeWalls) {
            grid.setEdge(def.x, def.y, def.side, new EdgeWall(def.weightClass, def.material, def.hasBulkhead));
        }

        for (HullPiece piece : blueprint.hullPieces) {
            grid.placeHullPiece(piece);
        }

        // Mounts drawn as letter rectangles in the map
        boolean[][] visited = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SubsystemComponent.SubsystemType type = systemFor(chars[y][x]);
                if (type == null || visited[y][x]) continue;
                char c = chars[y][x];
                int w = 1;
                while (x + w < width && chars[y][x + w] == c && !visited[y][x + w]) w++;
                int h = 1;
                while (y + h < height && rowMatches(chars[y + h], visited[y + h], x, w, c)) h++;
                for (int yy = y; yy < y + h; yy++)
                    for (int xx = x; xx < x + w; xx++) visited[yy][xx] = true;

                MountFootprint footprint = MountFootprint.fromSize(type.mountKind, x, y, w, h);
                if (footprint == null) {
                    throw new IllegalArgumentException("'" + c + "' at " + x + "," + y + " is " + w + "x" + h
                            + ", which is not a legal " + type.mountKind + " mount size");
                }
                grid.placeMount(footprint);
                subsystems.add(new SubsystemPlacement(footprint, type));
            }
        }

        for (ChassisBlueprint.MountDef def : blueprint.mounts) {
            if (def.footprint.kind != def.type.mountKind) {
                throw new IllegalArgumentException(def.type + " needs a " + def.type.mountKind + " mount");
            }
            grid.placeMount(def.footprint);
            subsystems.add(new SubsystemPlacement(def.footprint, def.type));
        }

        return new LoadResult(grid, subsystems);
    }

    private static void applyTileChar(Tile tile, char ch) {
        switch (ch) {
            case '#':
            case '2':
                makeHull(tile, WeightClass.MEDIUM);
                break;
            case '1':
                makeHull(tile, WeightClass.LIGHT);
                break;
            case '3':
                makeHull(tile, WeightClass.HEAVY);
                break;
            case 'I':
                tile.wallType = Tile.WallType.INTERNAL;
                tile.floorType = Tile.FloorType.NONE;
                tile.isFlammable = false;
                break;
            case '+':
                tile.floorType = Tile.FloorType.POWER_CONDUIT;
                tile.isFlammable = false;
                break;
            case ' ':
                // Void - already default
                break;
            default:
                // '.' and system letters are floor
                if (ch == '.' || systemFor(ch) != null) tile.floorType = Tile.FloorType.FLOOR;
                break;
        }
    }

    private static void makeHull(Tile tile, WeightClass weightClass) {
        tile.wallType = Tile.WallType.HULL;
        tile.floorType = Tile.FloorType.NONE;
        tile.isFlammable = false;
        tile.weightClass = weightClass;
    }

    private static boolean rowMatches(char[] row, boolean[] visitedRow, int x, int w, char c) {
        for (int xx = x; xx < x + w; xx++) {
            if (row[xx] != c || visitedRow[xx]) return false;
        }
        return true;
    }

    private static SubsystemComponent.SubsystemType systemFor(char ch) {
        switch (ch) {
            case 'R': return SubsystemComponent.SubsystemType.REACTOR;
            case 'S': return SubsystemComponent.SubsystemType.SHIELD_EMITTER;
            case 'T': return SubsystemComponent.SubsystemType.TURRET_MOUNT;
            case 'L': return SubsystemComponent.SubsystemType.LIFE_SUPPORT;
            case 'H': return SubsystemComponent.SubsystemType.HELM;
            case 'W': return SubsystemComponent.SubsystemType.WEAPONS;
            case 'C': return SubsystemComponent.SubsystemType.CARGO;
            case 'A': return SubsystemComponent.SubsystemType.AIRLOCK;
            default:  return null;
        }
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
