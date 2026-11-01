package com.ringsofiron.interiorgrid;

/**
 * A power conduit run across a tile, stored per tile in {@link ShipGridComponent#tileConduits}
 * the way a {@link Pipe} is. It lies on top of the tile, floor or wall, and takes no room on it:
 * a system can be mounted over it and crew walk across it.
 *
 * It joins the tile conduits on the four tiles beside it (through any thin wall or door between
 * them) and the {@link EdgeConduit}s on its own tile's edges. A system draws on it when it lies on
 * one of the system's footprint tiles. See {@link PowerRoutingSystem}.
 *
 * It has a condition of its own, apart from the tile's: a hit can cut the conduit without wrecking
 * the deck under it, and the other way round. A cut or shorted conduit carries no power.
 */
public class TileConduit {

    /** Condition, 0.0 (cut) to 1.0 (intact). */
    public float integrity = 1f;
    /**
     * Faulted by a short circuit (see {@link DamageStates}): it carries no power until the crew
     * reset it or a yard repairs it. Saved.
     */
    public boolean shorted;
    /** Seconds it has been shorted (see {@link DamageStates#AUTO_RESET}). Not saved: a loaded short starts its wait again. */
    public float shortedFor;
    /** Whether it is connected to a powered reactor. Set by {@link PowerRoutingSystem}. */
    public boolean isPowered;

    /** True while it can carry power: not cut and not shorted. */
    public boolean isLive() {
        return integrity > 0f && !shorted;
    }

    /** True once it has been cut. */
    public boolean isCut() {
        return integrity <= 0f;
    }
}
