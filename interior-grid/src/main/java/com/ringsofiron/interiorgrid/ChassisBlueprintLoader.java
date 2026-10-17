package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Loads and validates a ship chassis blueprint into a {@link ShipGridComponent}.
 *
 * A chassis blueprint is defined as a 2D array of characters:
 *   '#' = Outer hull wall (immovable, WallType.HULL)
 *   '.' = Interior floor tile
 *   '+' = Power conduit tile
 *   'R' = Reactor anchor tile (floor, flagged for SubsystemType.REACTOR)
 *   'H' = Helm anchor tile
 *   'L' = Life support anchor tile
 *   ' ' = Void (space, not part of the ship)
 *
 * This class is intentionally independent of the ECS; it simply produces a
 * populated ShipGridComponent and a list of SubsystemPlacement data objects.
 */
public class ChassisBlueprintLoader {

    /**
     * A simple data object representing a subsystem anchor discovered during blueprint parsing.
     */
    public static class SubsystemPlacement {
        public final int x;
        public final int y;
        public final SubsystemComponent.SubsystemType type;

        public SubsystemPlacement(int x, int y, SubsystemComponent.SubsystemType type) {
            this.x = x;
            this.y = y;
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

    /**
     * Parse a ship blueprint from a string array (one string per row, top-to-bottom).
     *
     * @param blueprint  Rows of the ship layout using the documented character convention.
     * @param tileSize   World-unit size of each tile (e.g. 1.0f for 1 metre per tile).
     * @return           A {@link LoadResult} containing the populated grid and subsystem list.
     */
    public static LoadResult load(String[] blueprint, float tileSize) {
        int height = blueprint.length;
        // Find the widest row
        int width = 0;
        for (String row : blueprint) {
            width = Math.max(width, row.length());
        }

        ShipGridComponent grid = new ShipGridComponent(width, height, tileSize);
        Array<SubsystemPlacement> subsystems = new Array<>();

        for (int y = 0; y < height; y++) {
            String row = blueprint[y];
            // In our layout, row 0 is the BOTTOM of the ship (LibGDX Y-up convention).
            // We flip so that blueprint row 0 = top visually, stored as y=(height-1-y) in grid.
            int gridY = height - 1 - y;

            for (int x = 0; x < row.length(); x++) {
                char ch = row.charAt(x);
                Tile tile = grid.getTile(x, gridY);
                if (tile == null) continue;

                switch (ch) {
                    case '#':
                        tile.wallType = Tile.WallType.HULL;
                        tile.floorType = Tile.FloorType.NONE;
                        tile.isFlammable = false;
                        break;
                    case '+':
                        tile.floorType = Tile.FloorType.POWER_CONDUIT;
                        tile.isFlammable = false;
                        break;
                    case 'R':
                        tile.floorType = Tile.FloorType.FLOOR;
                        subsystems.add(new SubsystemPlacement(x, gridY, SubsystemComponent.SubsystemType.REACTOR));
                        break;
                    case 'H':
                        tile.floorType = Tile.FloorType.FLOOR;
                        subsystems.add(new SubsystemPlacement(x, gridY, SubsystemComponent.SubsystemType.HELM));
                        break;
                    case 'L':
                        tile.floorType = Tile.FloorType.FLOOR;
                        subsystems.add(new SubsystemPlacement(x, gridY, SubsystemComponent.SubsystemType.LIFE_SUPPORT));
                        break;
                    case '.':
                        tile.floorType = Tile.FloorType.FLOOR;
                        break;
                    case ' ':
                    default:
                        // Void - already default
                        break;
                }
            }
        }

        return new LoadResult(grid, subsystems);
    }

    /**
     * Validates that a player-placed module is within the valid interior (non-hull, non-void)
     * bounds of the grid and does not overlap an existing wall or hull tile.
     *
     * @return true if placement is legal.
     */
    public static boolean isPlacementValid(ShipGridComponent grid, int x, int y) {
        Tile tile = grid.getTile(x, y);
        if (tile == null) return false;
        return tile.isFloor() && !tile.isWall();
    }
}
