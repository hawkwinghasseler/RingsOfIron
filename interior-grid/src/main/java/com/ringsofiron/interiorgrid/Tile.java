package com.ringsofiron.interiorgrid;

/**
 * Represents a single tile in a ship's interior grid.
 * All state is stored here as plain data (no ECS components needed per tile).
 *
 * Walls come in two kinds. A full-tile wall is a tile whose {@link #wallType} is set;
 * a thin wall between two tiles is an {@link EdgeWall} stored on the grid's edges.
 * A tile can also belong to a multi-tile {@link HullPiece} or lie inside a system
 * {@link MountFootprint}.
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
    /** Construction class; sets mass, hit points and armor through the material table. */
    public WeightClass weightClass = WeightClass.MEDIUM;
    /** Material id looked up in the {@link MaterialTable}. */
    public String material = MaterialTable.DEFAULT_MATERIAL;

    /** Whether this tile is currently on fire. */
    public boolean hasFire = false;
    /** Whether this tile is exposed to vacuum (no atmosphere). */
    public boolean hasVacuum = false;
    /** Oxygen level 0.0 (vacuum) to 1.0 (full pressure). */
    public float oxygenLevel = 1.0f;
    /**
     * Structural integrity 0.0 (destroyed) to 1.0 (intact) for a tile that is not part
     * of a {@link HullPiece}. Read and write through {@link #getIntegrity()} and
     * {@link #setIntegrity(float)} so pieces share their pool.
     */
    public float integrity = 1.0f;
    /** Whether this tile's internal bulkhead is sealed (blocks fire/vacuum spread). */
    public boolean bulkheadSealed = false;
    /** Whether this tile is connected to a powered reactor via power conduits. */
    public boolean isPowered = false;
    /** Whether this tile is flammable (floor tiles generally are). */
    public boolean isFlammable = true;

    /** The multi-tile hull piece covering this tile, or null. */
    public HullPiece piece;
    /** The system mount whose footprint covers this tile, or null. */
    public MountFootprint mount;

    public Tile() {}

    /** Integrity of this tile, or of its whole hull piece if it has one. */
    public float getIntegrity() {
        return piece != null ? piece.integrity : integrity;
    }

    public void setIntegrity(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (piece != null) piece.integrity = clamped;
        else integrity = clamped;
    }

    public boolean isWall() {
        return wallType != WallType.NONE;
    }

    /** A wall that still stands. A wall at integrity 0 no longer blocks anything. */
    public boolean isIntactWall() {
        return isWall() && getIntegrity() > 0f;
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
        // A hull tile is breached when its integrity (or its piece's) is 0
        return wallType == WallType.HULL && getIntegrity() <= 0f;
    }

    public boolean isSpaceExposed() {
        return isVoid() || isHullBreach();
    }

    /**
     * True for tiles that hold air and that crew, fire and gas can move through:
     * floors, and internal walls that have been destroyed.
     */
    public boolean isOpen() {
        if (isSpaceExposed()) return false;
        if (isWall()) return wallType == WallType.INTERNAL && getIntegrity() <= 0f;
        return isFloor();
    }

    @Override
    public String toString() {
        if (isVoid()) return " ";
        if (isWall()) return getIntegrity() <= 0 ? "X" : "#";
        if (hasFire) return "F";
        if (hasVacuum) return "V";
        return ".";
    }
}
