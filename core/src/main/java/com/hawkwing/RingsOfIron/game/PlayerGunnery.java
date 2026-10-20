package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * The player's hands on their ship's weapons. The ship's own fire control does the aiming: the
 * turrets slew onto the lead for the locked target, and the player decides when they fire. Point
 * defense stays under the gunners' control and shoots down incoming missiles by itself.
 */
public class PlayerGunnery {
    /** Decoy brightness against radar and thermal seekers (a ship is 1), and how long it burns. */
    public static final float DECOY_RADAR = 3f, DECOY_THERMAL = 6f, DECOY_LIFETIME = 8f;
    /** Seconds between decoys, and how fast they are thrown clear astern, m/s. */
    public static final float DECOY_COOLDOWN = 3f, DECOY_SPEED = 15f;

    private final GameWorld world;
    private final Entity ship;
    private float decoyCooldown;
    private final Vector2 pos = new Vector2();
    private final Vector2 vel = new Vector2();

    public PlayerGunnery(GameWorld world, Entity ship) {
        this.world = world;
        this.ship = ship;
        holdMainGuns(weapons());
    }

    /** The main guns wait for the player; point defense is weapons free. */
    static void holdMainGuns(WeaponsComponent weapons) {
        if (weapons == null) return;
        for (WeaponMountComponent m : weapons.mounts) m.weaponsFree = m.def != null && m.def.pointDefense;
    }

    public Entity ship() {
        return ship;
    }

    public WeaponsComponent weapons() {
        return ship == null ? null : ship.getComponent(WeaponsComponent.class);
    }

    /** The first gun that is not point defense: the one the HUD's lead reticle is for. */
    public WeaponMountComponent primary() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return null;
        for (WeaponMountComponent m : weapons.mounts) {
            if (isMainGun(m)) return m;
        }
        return null;
    }

    /** Muzzle speed of the primary gun, m/s. */
    public float muzzleSpeed() {
        WeaponMountComponent m = primary();
        ProjectileDef p = m == null || m.def.projectile == null ? null : world.combatDefs.projectiles.get(m.def.projectile);
        return p == null ? 1000f : p.muzzleSpeed;
    }

    public Entity getTarget() {
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        return sensor == null ? null : sensor.lockedTarget;
    }

    /** Locks a contact for the turrets and launchers, or null to clear the lock. */
    public void setTarget(Entity target) {
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = target;
    }

    /** Pulls the trigger on the main guns for the next tick; call every frame the key is held. */
    public void fireGuns() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return;
        for (WeaponMountComponent m : weapons.mounts) {
            if (isMainGun(m)) m.trigger = true;
        }
    }

    /** Launches from the first loaded launcher at the locked target. False if none can. */
    public boolean launchMissile() {
        if (getTarget() == null) return false;
        WeaponsComponent weapons = weapons();
        if (weapons == null) return false;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount == WeaponDef.MountType.LAUNCHER && m.cooldown <= 0f && world.weaponControl.available(m)) {
                m.trigger = true;
                return true;
            }
        }
        return false;
    }

    /** Throws a decoy clear astern. False while the last one is still reloading. */
    public boolean dropDecoy() {
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        if (decoyCooldown > 0f || pb == null || pb.body == null) return false;
        Body body = pb.body;
        float back = body.getAngle() + MathUtils.PI;
        PhysicsManager.getPosition(body, pos);
        PhysicsManager.getVelocity(body, vel).add(MathUtils.cos(back) * DECOY_SPEED, MathUtils.sin(back) * DECOY_SPEED);
        world.missiles.dropDecoy(ship, pos.x + MathUtils.cos(back) * 8f, pos.y + MathUtils.sin(back) * 8f,
                vel.x, vel.y, DECOY_RADAR, DECOY_THERMAL, DECOY_LIFETIME);
        decoyCooldown = DECOY_COOLDOWN;
        return true;
    }

    public void update(float delta) {
        decoyCooldown = Math.max(0f, decoyCooldown - delta);
    }

    /** True when the primary gun is loaded and its system works. */
    public boolean gunsReady() {
        WeaponMountComponent m = primary();
        return m != null && m.cooldown <= 0f && world.weaponControl.available(m);
    }

    /** Seconds until a launcher is loaded, 0 if one is, or -1 with no working launcher. */
    public float missileReloadTime() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return -1f;
        float best = -1f;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount != WeaponDef.MountType.LAUNCHER || !world.weaponControl.available(m)) continue;
            if (best < 0f || m.cooldown < best) best = m.cooldown;
        }
        return best;
    }

    private static boolean isMainGun(WeaponMountComponent m) {
        return m.def != null && !m.def.pointDefense && m.def.mount != WeaponDef.MountType.LAUNCHER;
    }
}
