package com.ringsofiron.interiorgrid;

/**
 * A thin wall on the boundary between two tiles, stored per edge in
 * {@link ShipGridComponent}. It divides space without spending a tile on the wall.
 *
 * An edge wall may carry a bulkhead door. An open door lets crew, air and fire
 * through; a sealed door blocks them like solid wall. A wall at integrity 0 is
 * destroyed and blocks nothing.
 */
public class EdgeWall {

    public WeightClass weightClass;
    public String material;
    /** Structural integrity 0.0 (destroyed) to 1.0 (intact). */
    public float integrity = 1.0f;
    /** Whether this wall section contains a bulkhead door. */
    public boolean hasBulkhead;
    /** For walls with a bulkhead: whether the door is currently sealed. */
    public boolean bulkheadSealed;

    public EdgeWall(WeightClass weightClass) {
        this(weightClass, MaterialTable.DEFAULT_MATERIAL, false);
    }

    public EdgeWall(WeightClass weightClass, String material, boolean hasBulkhead) {
        this.weightClass = weightClass;
        this.material = material;
        this.hasBulkhead = hasBulkhead;
    }

    public boolean isDestroyed() {
        return integrity <= 0f;
    }

    /** True when this edge stops movement, atmosphere and fire. */
    public boolean blocks() {
        if (isDestroyed()) return false;
        return !hasBulkhead || bulkheadSealed;
    }
}
