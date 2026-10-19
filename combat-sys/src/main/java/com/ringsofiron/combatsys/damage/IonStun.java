package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * An ion bolt landing on a hull (combat-sys/Design.md 5.11): every system with a tile within the
 * bolt's {@link ProjectileDef.StunDef#radiusTiles} of where it struck is stunned for a time drawn
 * between its minimum and maximum. A stun never adds up past the longest; nothing is damaged. Cargo
 * bays, tanks and docking ports have nothing to stun.
 */
public final class IonStun {
    private IonStun() {
    }

    /**
     * Stuns the systems near tile-space point ({@code tileX}, {@code tileY}) of {@code ship}.
     * Returns how many systems it stunned, and marks the grid for a reroute when any.
     */
    public static int stun(Entity ship, float tileX, float tileY, ProjectileDef.StunDef stun, RandomXS128 rng) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (grid == null || registry == null || stun == null) return 0;
        float r = stun.radiusTiles;
        int n = 0;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s == null || !stunnable(s.type)) continue;
            MountFootprint f = s.footprint;
            // Distance from the point to the nearest tile of the footprint, in tiles
            float dx = Math.max(0f, Math.max(f.originX - tileX, tileX - (f.originX + f.width())));
            float dy = Math.max(0f, Math.max(f.originY - tileY, tileY - (f.originY + f.height())));
            if (dx * dx + dy * dy > r * r) continue;
            float seconds = stun.minSeconds + rng.nextFloat() * Math.max(0f, stun.maxSeconds - stun.minSeconds);
            s.stunned = Math.max(s.stunned, seconds);
            n++;
        }
        if (n > 0) grid.gridDirty = true;
        return n;
    }

    /** True if any system aboard {@code ship} is stunned now. */
    public static boolean anyStunned(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.isStunned()) return true;
        }
        return false;
    }

    static boolean stunnable(SubsystemComponent.SubsystemType type) {
        switch (type) {
            case CARGO:
            case TANK:
            case DOCKING_PORT:
                return false;
            default:
                return true;
        }
    }
}
