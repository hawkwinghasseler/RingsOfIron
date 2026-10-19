package com.ringsofiron.combatsys.modding;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
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
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

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
        entity.add(new SignatureComponent(1f, 1f, hullRadius(entity, ship)));

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

    /**
     * How far the hull reaches from the body origin: the farthest corner of the body's polygons
     * when it has a body (its shape may come from the layout rather than the def), else of the
     * def's {@code hull}, else 5 m.
     */
    static float hullRadius(Entity entity, ShipDef ship) {
        float r2 = 0f;
        PhysicsBodyComponent pb = entity.getComponent(PhysicsBodyComponent.class);
        if (pb != null && pb.body != null) {
            Vector2 v = new Vector2();
            for (Fixture f : pb.body.getFixtureList()) {
                if (!(f.getShape() instanceof PolygonShape)) continue;
                PolygonShape poly = (PolygonShape) f.getShape();
                for (int i = 0; i < poly.getVertexCount(); i++) {
                    poly.getVertex(i, v);
                    r2 = Math.max(r2, v.len2() * PhysicsManager.METERS_PER_UNIT * PhysicsManager.METERS_PER_UNIT);
                }
            }
            if (r2 > 0f) return (float) Math.sqrt(r2);
        }
        for (float[] v : ship.hull) {
            if (v != null && v.length >= 2) r2 = Math.max(r2, v[0] * v[0] + v[1] * v[1]);
        }
        return r2 > 0f ? (float) Math.sqrt(r2) : 5f;
    }

    /** The turret mount or weapons console closest to a body-local point, or null if there is none. */
    public static Entity nearestWeaponSystem(Entity ship, float x, float y) {
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
