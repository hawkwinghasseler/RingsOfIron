package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

import java.util.EnumSet;

/**
 * What crew cannot walk through on a deck besides walls: the machinery of the big systems, and
 * goods stacked in the cargo bays. One place for the rule, so the pathfinding and the crew's
 * bodies agree, and changing what blocks is a change here.
 *
 * <p>A system in {@link #SOLID} fills its footprint, wrecked or not: crew go round it and work it
 * from a tile beside it. Consoles (helm, weapons), airlocks, escape pods and docking ports stay walkable, since
 * crew stand at or pass through them. A cargo bay tile blocks while goods are stacked on it past
 * {@link #CARGO_FILL}; an empty bay is floor.
 */
public final class CrewObstacles {
    /** The systems whose footprint crew cannot walk over. */
    public static final EnumSet<SubsystemComponent.SubsystemType> SOLID = EnumSet.of(
            SubsystemComponent.SubsystemType.REACTOR,
            SubsystemComponent.SubsystemType.SHIELD_EMITTER,
            SubsystemComponent.SubsystemType.TURRET_MOUNT,
            SubsystemComponent.SubsystemType.LIFE_SUPPORT);
    /** A bay tile filled past this (0-1 of a full stack) blocks; 0 means any goods at all. */
    public static final float CARGO_FILL = 0f;

    private CrewObstacles() {
    }

    /**
     * True if something stands on tile (x, y) of {@code grid} that crew cannot walk through: a
     * {@link #SOLID} system, or goods in {@code hold} (null for a craft whose cargo is not known).
     */
    public static boolean blocks(ShipGridComponent grid, CargoHoldComponent hold, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null) return false;
        if (t.mount != null && solid(t.mount)) return true;
        return hold != null && hold.stackAt(x, y) != null && hold.fillAt(x, y) > CARGO_FILL;
    }

    /** True if the system installed in {@code mount} is {@link #SOLID}. */
    public static boolean solid(MountFootprint mount) {
        Entity e = mount.installed;
        SubsystemComponent sub = e == null ? null : e.getComponent(SubsystemComponent.class);
        return sub != null && sub.type != null && SOLID.contains(sub.type);
    }
}
