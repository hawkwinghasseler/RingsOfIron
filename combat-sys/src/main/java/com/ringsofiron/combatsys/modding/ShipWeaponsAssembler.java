package com.ringsofiron.combatsys.modding;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.ComponentAssembler;
import com.ringsofiron.moddingdata.DefEntry;
import com.ringsofiron.moddingdata.EntityFactory;
import com.ringsofiron.moddingdata.defs.ShipDef;

/**
 * Gives every ship spawned from a {@code [ship.*]} def its combat parts: sensors, a signature
 * the size of its hull, and one {@link WeaponMountComponent} per hardpoint. Each mount is wired
 * to the nearest turret mount or weapons console in the ship's interior, so damage to that
 * system silences the gun. Register after interior-grid's assembler, which builds the systems.
 *
 * <pre>
 * bootstrap.beforeScripts(ctx -> ShipWeaponsAssembler.register(ctx.factory, combatDefs));
 * </pre>
 */
public class ShipWeaponsAssembler implements ComponentAssembler {
    private final CombatDefs defs;

    public ShipWeaponsAssembler(CombatDefs defs) {
        this.defs = defs;
    }

    /** {@code defs} is read at spawn time, so data merged into it after loading is seen. */
    public static void register(EntityFactory factory, CombatDefs defs) {
        factory.registerAssembler("ship", new ShipWeaponsAssembler(defs));
    }

    @Override
    public void assemble(Entity entity, DefEntry def, EntityFactory factory) {
        ShipDef ship = factory.defs().convert(def, ShipDef.class);
        entity.add(new SensorComponent());
        entity.add(new SignatureComponent(1f, 1f, hullRadius(ship)));

        WeaponsComponent weapons = new WeaponsComponent();
        for (ShipDef.Hardpoint hp : ship.hardpoints) {
            WeaponDef weapon = hp.weapon == null ? null : defs.weapons.get(hp.weapon);
            if (weapon == null) continue; // the loader has already warned about it
            WeaponMountComponent mount = new WeaponMountComponent(weapon, hp.x, hp.y,
                    hp.rotation * MathUtils.degreesToRadians);
            mount.subsystem = nearestWeaponSystem(entity, hp.x, hp.y);
            weapons.add(mount);
        }
        if (weapons.mounts.size > 0) entity.add(weapons);
    }

    static float hullRadius(ShipDef ship) {
        float r2 = 0f;
        for (float[] v : ship.hull) {
            if (v != null && v.length >= 2) r2 = Math.max(r2, v[0] * v[0] + v[1] * v[1]);
        }
        return r2 > 0f ? (float) Math.sqrt(r2) : 5f;
    }

    /** The turret mount or weapons console closest to a body-local point, or null if there is none. */
    static Entity nearestWeaponSystem(Entity ship, float x, float y) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (grid == null || registry == null) return null;
        Entity best = null;
        float bestD2 = Float.MAX_VALUE;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null || (s.type != SubsystemComponent.SubsystemType.TURRET_MOUNT
                    && s.type != SubsystemComponent.SubsystemType.WEAPONS)) continue;
            MountFootprint m = s.footprint;
            float cx = grid.gridOriginLocal.x + (m.originX + 0.5f * m.width()) * grid.tileSize;
            float cy = grid.gridOriginLocal.y + (m.originY + 0.5f * m.height()) * grid.tileSize;
            float d2 = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }
}
