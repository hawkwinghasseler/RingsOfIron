package com.ringsofiron.interiorgrid;

/**
 * A power conduit run along the boundary between two tiles instead of across a tile's middle,
 * stored per edge in {@link ShipGridComponent} like an {@link EdgeWall}. It takes no room on
 * either tile, and it can run inside a wall: a thin wall on its edge, or a full-tile wall on
 * either side of it.
 *
 * It carries power to the conduits it meets at either end of its edge and to the open tiles on
 * both sides, through any wall, so it can feed two rooms. It is cut when the wall it runs in is
 * destroyed. See {@link PowerRoutingSystem}.
 */
public class EdgeConduit {

    /** Whether it is connected to a powered reactor. Set by {@link PowerRoutingSystem}. */
    public boolean isPowered = false;
}
