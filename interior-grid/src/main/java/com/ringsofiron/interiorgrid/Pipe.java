package com.ringsofiron.interiorgrid;

/**
 * A run of pipe that carries life support gases and propellant from tanks to the systems that use
 * them. Like a power conduit, it lies either across the middle of a tile (under the deck, taking
 * no room) or along a tile edge, where it may run inside a thin wall or along a full-tile wall.
 * See {@link PipeRouting} for how pipes join and what they connect.
 */
public class Pipe {

    /** The network it belongs to, or null when it is cut. Set by {@link PipeRouting}; not saved. */
    public transient PipeNetwork network;

    /** True when pipe joins it to at least one tank. */
    public boolean isFed() {
        return network != null && network.tanks.size > 0;
    }
}
