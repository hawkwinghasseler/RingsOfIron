package com.ringsofiron.interiorgrid;

/**
 * Construction weight class of a tile, edge wall or hull piece.
 *
 * The class selects a row in the {@link MaterialTable}: heavier pieces weigh more,
 * take more damage before they are destroyed (hit points) and strip more penetration
 * from incoming rounds (armor).
 */
public enum WeightClass {
    LIGHT,
    MEDIUM,
    HEAVY
}
