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
 * turrets slew onto the lead for the locked target. Crewed guns fire on hostiles by themselves
 * ({@link GunCrews}); the trigger ({@link #fireGuns}) fires them at once on the lock. Point defense
 * stays under the gunners' control and shoots down incoming missiles by itself.
 *
 * <p>Sitting at the weapons console the player has every gun and launcher in hand but point
 * defense, at a gun just that one, point defense too ({@link #inHand}): the guns follow the pointer
 * ({@link #aim}), leading a contact under it and otherwise pointing at the spot, and only the
 * trigger fires them. Point defense in hand leaves the missiles to the player; let go, it goes back
 * to shooting them down.
 *
 * <p>A missile key press launches a salvo ({@link #SALVO}) from one launcher, a missile each time it
 * is ready again, each coming in from its own side.
 */
public class PlayerGunnery {
    /** Decoy brightness against radar and thermal seekers (a ship is 1), and how long it burns. */
    public static final float DECOY_RADAR = 3f, DECOY_THERMAL = 6f, DECOY_LIFETIME = 8f;
    /** Seconds between decoys, and how fast they are thrown clear astern, m/s. */
    public static final float DECOY_COOLDOWN = 3f, DECOY_SPEED = 15f;
    /** Missiles one press of the missile key launches, while the launcher has them. */
    public static final int SALVO = 4;

    private final GameWorld world;
    private final Entity ship;
    private float decoyCooldown;
    /** The launcher throwing a salvo, and the shot count it stops at. */
    private WeaponMountComponent salvoFrom;
    private int salvoUntil;
    private final Vector2 pos = new Vector2();
    private final Vector2 vel = new Vector2();

    public PlayerGunnery(GameWorld world, Entity ship) {
        this.world = world;
        this.ship = ship;
        WeaponsComponent.holdMainGuns(weapons());
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

    /**
     * Pulls the trigger on the main guns for the next tick; call every frame the key is held. On a
     * console or a gun, only the guns in hand.
     */
    public void fireGuns() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return;
        boolean onGuns = world.bridge.playerOnGuns();
        for (WeaponMountComponent m : weapons.mounts) {
            if (onGuns ? inHand(world, m) && m.def.mount != WeaponDef.MountType.LAUNCHER : isMainGun(m)) m.trigger = true;
        }
    }

    /**
     * True if the player has {@code mount} in hand: seated at the weapons console, any gun or
     * launcher but point defense, which keeps shooting down missiles by itself; seated at a gun,
     * that gun, point defense or not.
     */
    public static boolean inHand(GameWorld world, WeaponMountComponent mount) {
        if (mount.def == null) return false;
        if (world.bridge.playerAtWeapons()) return !mount.def.pointDefense;
        Entity gun = world.bridge.seatedGun();
        return gun != null && mount.subsystem == gun;
    }

    /**
     * Points the guns in hand at the pointer, (x, y) in local meters: a contact within
     * {@code pick} meters of it is led like a target, anywhere else is aimed at straight. Call every
     * frame while seated at the guns; {@link #releaseAim} when not.
     */
    public void aim(float x, float y, float pick) {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return;
        Entity contact = contactAt(x, y, pick);
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount == WeaponDef.MountType.LAUNCHER) continue;
            if (!inHand(world, m)) {
                if (m.def.pointDefense) release(m);
                continue;
            }
            m.target = contact;
            m.manualAim = contact == null;
            m.aimPoint.set(x, y);
            // The player's now, not the missiles'
            if (m.def.pointDefense) m.weaponsFree = false;
        }
    }

    /** Hands every gun back to its crew's aim, and point defense back to the missiles. */
    public void releaseAim() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def != null && m.def.pointDefense) {
                release(m);
                continue;
            }
            if (!m.manualAim) continue;
            m.manualAim = false;
            m.target = null;
        }
    }

    /** Point defense out of the player's hand: free to pick its own missiles again. */
    private static void release(WeaponMountComponent m) {
        m.manualAim = false;
        m.target = null;
        m.weaponsFree = true;
    }

    /** The tracked contact (not a missile) nearest (x, y) within {@code pick} meters of its hull, or null. */
    private Entity contactAt(float x, float y, float pick) {
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        if (sensor == null) return null;
        Entity best = null;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < sensor.tracks.size; i++) {
            com.ringsofiron.combatsys.fire.Track t = sensor.tracks.get(i);
            if (t.target == ship || t.target.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class) != null) continue;
            t.positionAt(world.sensors.ageOf(t), pos);
            com.ringsofiron.combatsys.components.SignatureComponent sig =
                    t.target.getComponent(com.ringsofiron.combatsys.components.SignatureComponent.class);
            float d = pos.dst(x, y) - (sig == null ? 0f : sig.radius);
            if (d <= pick && d < nearest) {
                nearest = d;
                best = t.target;
            }
        }
        return best;
    }

    /**
     * Launches a salvo of up to {@link #SALVO} missiles at the locked target from the first launcher
     * that is loaded (on a console or a gun, one in hand), the first now and the rest as it reloads.
     * False if none can.
     */
    public boolean launchMissile() {
        if (getTarget() == null) return false;
        WeaponsComponent weapons = weapons();
        if (weapons == null) return false;
        boolean onGuns = world.bridge.playerOnGuns();
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount != WeaponDef.MountType.LAUNCHER || (onGuns && !inHand(world, m))) continue;
            if (m.cooldown <= 0f && hasMissiles(m) && world.weaponControl.available(m)) {
                m.trigger = true;
                salvoFrom = m;
                salvoUntil = m.shotsFired + Math.min(SALVO, m.def.magazine > 0 ? Math.max(m.ammo, 1) : SALVO);
                return true;
            }
        }
        return false;
    }

    /** Missiles left in a launcher with a magazine; -1 for one that never runs out. */
    public static int missilesLeft(WeaponMountComponent m) {
        if (m.def == null || m.def.magazine <= 0) return -1;
        return m.ammo < 0 ? m.def.magazine : m.ammo;
    }

    private static boolean hasMissiles(WeaponMountComponent m) {
        return missilesLeft(m) != 0;
    }

    /** Keeps a salvo going: the launcher fires again each time it is ready, until it has thrown it all. */
    private void continueSalvo() {
        WeaponMountComponent m = salvoFrom;
        if (m == null) return;
        if (m.shotsFired >= salvoUntil || getTarget() == null || !hasMissiles(m) || weapons() == null
                || !weapons().mounts.contains(m, true)) {
            salvoFrom = null;
            return;
        }
        if (m.cooldown <= 0f && world.weaponControl.available(m)) m.trigger = true;
    }

    /** Missiles left in all the working launchers, or -1 if none of them ever runs out. */
    public int missilesLeft() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return 0;
        int left = 0;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount != WeaponDef.MountType.LAUNCHER) continue;
            int n = missilesLeft(m);
            if (n < 0) return -1;
            left += n;
        }
        return left;
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
        continueSalvo();
    }

    /** True when the primary gun is loaded and its system works. */
    public boolean gunsReady() {
        WeaponMountComponent m = primary();
        return m != null && m.cooldown <= 0f && world.weaponControl.available(m);
    }

    /** Seconds until a launcher is loaded, 0 if one is, or -1 with no working launcher that has missiles. */
    public float missileReloadTime() {
        WeaponsComponent weapons = weapons();
        if (weapons == null) return -1f;
        float best = -1f;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def.mount != WeaponDef.MountType.LAUNCHER || !world.weaponControl.available(m) || !hasMissiles(m)) continue;
            if (best < 0f || m.cooldown < best) best = m.cooldown;
        }
        return best;
    }

    /** A gun the player fires: neither point defense nor a launcher. */
    public static boolean isMainGun(WeaponMountComponent m) {
        return m.def != null && !m.def.pointDefense && m.def.mount != WeaponDef.MountType.LAUNCHER;
    }
}
