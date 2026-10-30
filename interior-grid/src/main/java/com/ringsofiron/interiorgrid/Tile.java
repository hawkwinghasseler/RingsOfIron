package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Represents a single tile in a ship's interior grid.
 * All state is stored here as plain data (no ECS components needed per tile).
 *
 * Walls come in two kinds. A full-tile wall is a tile whose {@link #wallType} is set;
 * a thin wall between two tiles is an {@link EdgeWall} stored on the grid's edges.
 * A tile can also belong to a multi-tile {@link HullPiece} or lie inside a system
 * {@link MountFootprint}.
 *
 * Every floor is a steel deck plate. A {@link FloorCovering} such as carpet can be laid
 * on top of it; that covering is the fuel a fire burns, along with any {@link Furnishing}s set
 * down on the tile. Air does not live on tiles: it belongs to the tile's {@link Compartment}.
 */
public class Tile {

    public enum WallType {
        NONE,       // Not a wall - free floor space
        HULL,       // Outer hull wall - immovable, created by chassis
        INTERNAL    // Internal wall / bulkhead - can be opened/closed
    }

    public enum FloorType {
        NONE,       // Void/space - impassable and not part of the ship
        FLOOR,      // Standard walkable steel floor
        POWER_CONDUIT // A steel floor carrying a power conduit, also walkable
    }

    /** How big a fire is, for display and crew decisions. */
    public enum FireSize {
        NONE, SMALL, MEDIUM, LARGE
    }

    /** Fire intensity a freshly ignited tile starts at. */
    public static final float IGNITION_INTENSITY = 0.05f;

    public WallType wallType = WallType.NONE;
    public FloorType floorType = FloorType.NONE;
    /** Construction class; sets mass, hit points and armor through the material table. */
    public WeightClass weightClass = WeightClass.MEDIUM;
    /** Material id looked up in the {@link MaterialTable}. */
    public String material = MaterialTable.DEFAULT_MATERIAL;

    /** Combustible layer on top of the steel floor. Burns away to {@link FloorCovering#NONE}. */
    public FloorCovering covering = FloorCovering.NONE;
    /** Combustible mass left in the covering, kg. Fire consumes it. */
    public float fuel = 0f;
    /**
     * Furniture and decor whose middle is over this tile, each at its own offset and angle (see
     * {@link Furnishing}). A fire here burns them with the covering.
     */
    public final Array<Furnishing> furnishings = new Array<>(0);
    /**
     * Fire on this tile: 0 is no fire, a new fire starts at {@link #IGNITION_INTENSITY}
     * and grows toward 1 (fully involved) while it has fuel and oxygen.
     */
    public float fireIntensity = 0f;
    /** Temperature of the tile (its surfaces and the air right over it), K. */
    public float temperature = Compartment.AMBIENT_TEMPERATURE;
    /**
     * Structural condition 0.0 (destroyed) to 1.0 (intact) for a tile that is not part
     * of a {@link HullPiece}. Read and write through {@link #getIntegrity()} and
     * {@link #setIntegrity(float)} so pieces share their pool. Below
     * {@link #LEAK_START} a wall or floor starts to leak (see {@link #leakFactor(float)}).
     */
    public float integrity = 1.0f;
    /**
     * Whether this tile is sealed by an emergency bulkhead. A sealed tile is airtight
     * and blocks air, fire and heat convection like an intact wall. Sealing a breached
     * hull tile patches the breach.
     */
    public boolean bulkheadSealed = false;
    /**
     * The condition at which the crew patched this tile in the field, or 0 for no patch. Read
     * and write through {@link #isPatched()}, {@link #patch()} and {@link #clearPatch()} so hull
     * pieces share it. See {@link #isPatched(float, float)}.
     */
    public float patchedAt = 0f;
    /**
     * A power conduit tile a short circuit has faulted (see {@link DamageStates}): it carries no
     * power until the crew reset it or a yard repairs it. Saved.
     */
    public boolean shorted = false;
    /** Seconds it has been shorted (see {@link DamageStates#AUTO_RESET}). Not saved: a loaded short starts its wait again. */
    public float shortedFor = 0f;
    /** Whether this tile is connected to a powered reactor via power conduits. */
    public boolean isPowered = false;

    /** The compartment whose air fills this tile, or null. Set by {@link CompartmentBuilder}. */
    public Compartment compartment;
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

    /**
     * A hull tile (or piece) at integrity 0 that has not been patched with a bulkhead seal.
     */
    public boolean isHullBreach() {
        return wallType == WallType.HULL && getIntegrity() <= 0f && !bulkheadSealed;
    }

    public boolean isSpaceExposed() {
        return isVoid() || isHullBreach();
    }

    /**
     * True for tiles that crew, fire and gas can move through: floors, and internal walls
     * that have been destroyed.
     */
    public boolean isOpen() {
        if (isSpaceExposed()) return false;
        if (isWall()) return wallType == WallType.INTERNAL && getIntegrity() <= 0f;
        return isFloor();
    }

    /** True for open tiles that are not sealed off: these are the tiles that hold air. */
    public boolean holdsAir() {
        return isOpen() && !bulkheadSealed;
    }

    /** True if there is something here a fire can burn. */
    public boolean isFlammable() {
        return fuel + furnishingFuel() > 0f && holdsAir();
    }

    /** Combustible mass of the furnishings on this tile, kg. */
    public float furnishingFuel() {
        float kg = 0f;
        for (int i = 0; i < furnishings.size; i++) kg += furnishings.get(i).fuel;
        return kg;
    }

    public boolean isBurning() {
        return fireIntensity > 0f;
    }

    public FireSize fireSize() {
        if (fireIntensity <= 0f) return FireSize.NONE;
        if (fireIntensity < 0.34f) return FireSize.SMALL;
        if (fireIntensity < 0.67f) return FireSize.MEDIUM;
        return FireSize.LARGE;
    }

    /**
     * Starts a small fire if there is fuel and no fire yet. Oxygen is checked by
     * {@link GridHazardSystem}: a fire started in thin air dies on its next tick.
     *
     * @return true if a new fire was started.
     */
    public boolean ignite() {
        if (isBurning() || !isFlammable()) return false;
        fireIntensity = IGNITION_INTENSITY;
        return true;
    }

    public void extinguish() {
        fireIntensity = 0f;
    }

    /** Lays a fresh covering over the steel floor (NONE strips it). */
    public void setCovering(FloorCovering covering, float tileSize) {
        this.covering = covering;
        this.fuel = covering.fuelPerArea * tileSize * tileSize;
    }

    // -----------------------------------------------------------------------
    // Leaks
    // -----------------------------------------------------------------------

    /** Condition below which walls, hull and floors start to leak. */
    public static final float LEAK_START = 0.5f;
    /** Condition at which the leak reaches its cap. */
    public static final float LEAK_CAP_CONDITION = 0.25f;
    /** Leak at the cap, as a share of the flow through a fully open tile edge. */
    public static final float LEAK_CAP = 0.5f;

    /**
     * How much air a damaged wall, hull or floor lets through, as a share of the flow
     * through an opening one tile wide with no wall at all. Zero at or above
     * {@link #LEAK_START}, rising linearly to {@link #LEAK_CAP} at
     * {@link #LEAK_CAP_CONDITION} and held there down to (but not including) 0. At 0 the
     * piece is destroyed and is a full opening (1.0).
     */
    public static float leakFactor(float condition) {
        if (condition <= 0f) return 1f;
        if (condition >= LEAK_START) return 0f;
        if (condition <= LEAK_CAP_CONDITION) return LEAK_CAP;
        return LEAK_CAP * (LEAK_START - condition) / (LEAK_START - LEAK_CAP_CONDITION);
    }

    /** {@link #leakFactor(float)} at this tile's condition. A sealed or patched tile does not leak. */
    public float leakFactor() {
        return bulkheadSealed || isPatched() ? 0f : leakFactor(getIntegrity());
    }

    // -----------------------------------------------------------------------
    // Field patches
    // -----------------------------------------------------------------------

    /**
     * True while a field patch put on at condition {@code patchedAt} still holds at
     * {@code condition}: the piece leaks (below {@link #LEAK_START}) and has taken no new damage
     * since. A patch stops the leak but does not restore condition; a yard repair past
     * {@link #LEAK_START} or a hit that lowers the condition ends it. A deck plate wrecked to 0
     * still takes a patch (see {@link #patchMark}): one that didn't hold there used up every kit aboard.
     */
    public static boolean isPatched(float patchedAt, float condition) {
        return patchedAt > 0f && condition < LEAK_START && condition >= patchedAt - PATCH_SLACK;
    }

    /** What a patch put on at {@code condition} records: above 0 even on a wrecked piece, as 0 means no patch. */
    public static float patchMark(float condition) {
        return Math.max(condition, PATCH_SLACK);
    }

    /** How far condition may read below the patch's own before the patch counts as broken. */
    private static final float PATCH_SLACK = 1e-4f;

    /** Whether this tile (or its hull piece) carries a field patch that still holds. */
    public boolean isPatched() {
        return isPatched(piece != null ? piece.patchedAt : patchedAt, getIntegrity());
    }

    /** Patches this tile (or its whole hull piece) at its present condition. */
    public void patch() {
        if (piece != null) piece.patchedAt = patchMark(piece.integrity);
        else patchedAt = patchMark(integrity);
    }

    public void clearPatch() {
        if (piece != null) piece.patchedAt = 0f;
        else patchedAt = 0f;
    }

    @Override
    public String toString() {
        if (isVoid()) return " ";
        if (isWall()) return getIntegrity() <= 0 ? "X" : "#";
        if (isBurning()) return "F";
        return ".";
    }
}
