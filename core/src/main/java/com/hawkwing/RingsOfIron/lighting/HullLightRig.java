package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * The lights on the outside of a craft, from its hull art's emissive layer: navigation lights,
 * bridge glazing, reactor and shield glow. A light over a system's footprint is on while that
 * system runs; the rest (navigation lights) while the craft has power. Hull art is in the body
 * frame at 64 units a meter, so its lights land straight on the body.
 */
public final class HullLightRig {
    /** How far a hull light reaches, meters: at least this... */
    static final float REACH_MIN = 2.5f;
    /** ...or this many times the glowing part's size. */
    static final float REACH_PER_SIZE = 10f;

    private final EmissiveLights art;

    public HullLightRig(EmissiveLights art) {
        this.art = art;
    }

    /** Adds the lights of {@code chassis}'s hull art to {@code out}; {@code grid} decides which are on. */
    public void collect(String chassis, ShipGridComponent grid, LightSpots out) {
        EmissiveLights.Art a = chassis == null ? null : art.get("hulls/" + chassis);
        if (a == null) return;
        boolean power = grid == null || DeckLightRig.hasPower(grid);
        for (EmissiveLights.Source src : a.lights) {
            LightSpot s = out.add();
            s.x = src.x / EmissiveLights.UNITS_PER_TILE;
            s.y = src.y / EmissiveLights.UNITS_PER_TILE;
            s.glow = src.size() / EmissiveLights.UNITS_PER_TILE;
            s.reach = Math.max(REACH_MIN, REACH_PER_SIZE * s.glow);
            s.color.set(src.color);
            s.color.a = src.alpha;
            s.on = power && systemRunning(grid, s.x, s.y);
        }
    }

    /** False only when the point is over a system that is not running. */
    private static boolean systemRunning(ShipGridComponent grid, float x, float y) {
        if (grid == null) return true;
        int[] t = grid.localToTile(x, y);
        Tile tile = t[0] < 0 ? null : grid.getTile(t[0], t[1]);
        Entity e = tile == null || tile.mount == null ? null : tile.mount.installed;
        SubsystemComponent sub = e == null ? null : e.getComponent(SubsystemComponent.class);
        return sub == null || sub.isOperational;
    }
}
