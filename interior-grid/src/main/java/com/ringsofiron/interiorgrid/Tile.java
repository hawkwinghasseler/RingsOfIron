package com.ringsofiron.interiorgrid;

/**
 * Represents a single tile in a ship's interior grid.
 * All state is stored here as plain data (no ECS components needed per tile).
 */
public class Tile {

    public enum WallType {
        NONE,       // Not a wall - free floor space
        HULL,       // Outer hull wall - immovable, created by chassis
        INTERNAL    // Internal wall / bulkhead - can be opened/closed
    }

    public enum FloorType {
        NONE,       // Void/space - impassable and not part of the ship
        FLOOR,      // Standard walkable floor
        POWER_CONDUIT // A power conduit tile, also walkable
    }

    public WallType wallType = WallType.NONE;
    public FloorType floorType = FloorType.NONE;

    /** Whether this tile is currently on fire. */
    public boolean hasFire = false;
    /** Whether this tile is exposed to vacuum (no atmosphere). */
    public boolean hasVacuum = false;
    /** Oxygen level 0.0 (vacuum) to 1.0 (full pressure). */
    public float oxygenLevel = 1.0f;
    /** Structural integrity 0.0 (destroyed) to 1.0 (intact). */
    public float integrity = 1.0f;
    /** Whether this tile's internal bulkhead is sealed (blocks fire/vacuum spread). */
    public boolean bulkheadSealed = false;
    /** Whether this tile is connected to a powered reactor via power conduits. */
    public boolean isPowered = false;
    /** Whether this tile is flammable (floor tiles generally are). */
    public boolean isFlammable = true;

    public Tile() {}

    public boolean isWall() {
        return wallType != WallType.NONE;
    }

    public boolean isFloor() {
        return floorType != FloorType.NONE;
    }

    public boolean isVoid() {
        return floorType == FloorType.NONE && wallType == WallType.NONE;
    }

    public boolean isPowerConduit() {
        return floorType == FloorType.POWER_CONDUIT;
    }

    public boolean isHullBreach() {
        // A hull tile is breached when its integrity is 0
        return wallType == WallType.HULL && integrity <= 0f;
    }

    public boolean isSpaceExposed() {
        return isVoid() || isHullBreach();
    }

    @Override
    public String toString() {
        if (isVoid()) return " ";
        if (isWall()) return integrity <= 0 ? "X" : "#";
        if (hasFire) return "F";
        if (hasVacuum) return "V";
        return ".";
    }
}
