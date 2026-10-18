package com.ringsofiron.interiorgrid;

/**
 * A combustible layer laid on top of a tile's steel floor. Fire burns the covering
 * away and leaves the steel floor behind; the floor itself does not burn.
 */
public enum FloorCovering {
    /** Bare steel deck. Nothing to burn. */
    NONE(0f),
    /** Carpet and underlay. */
    CARPET(1.5f);

    /** Combustible mass of a fresh covering, kg per square meter of floor. */
    public final float fuelPerArea;

    FloorCovering(float fuelPerArea) {
        this.fuelPerArea = fuelPerArea;
    }
}
