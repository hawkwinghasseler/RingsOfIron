package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * The player's spinal gun: fires one weapon's projectile straight off the nose, with the ship's
 * velocity added. Stands in until combat-sys has player-aimed turrets.
 */
public class PlayerGunnery {
    private final BallisticsSystem ballistics;
    private final WeaponDef weapon;
    private final ProjectileDef projectile;
    private final Vector2 pos = new Vector2();
    private final Vector2 vel = new Vector2();
    private float cooldown;

    /** {@code weaponId} from the combat tables, or null for the first weapon with a projectile. */
    public PlayerGunnery(BallisticsSystem ballistics, CombatDefs defs, String weaponId) {
        this.ballistics = ballistics;
        WeaponDef w = weaponId == null ? null : defs.weapons.get(weaponId);
        if (w == null) {
            for (WeaponDef d : defs.weapons.values()) {
                if (d.projectile != null && defs.projectiles.containsKey(d.projectile)) {
                    w = d;
                    break;
                }
            }
        }
        weapon = w;
        projectile = w == null ? null : defs.projectiles.get(w.projectile);
    }

    public WeaponDef weapon() {
        return weapon;
    }

    /** Muzzle speed in m/s, for the HUD's lead reticle. */
    public float muzzleSpeed() {
        return projectile == null ? 1000f : projectile.muzzleSpeed;
    }

    public void update(float delta) {
        cooldown = Math.max(0f, cooldown - delta);
    }

    public boolean ready() {
        return projectile != null && cooldown <= 0f;
    }

    /** Fires if loaded. Returns the projectile, or null. */
    public Entity fire(Entity ship) {
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        if (!ready() || pb == null || pb.body == null) return null;
        Body body = pb.body;
        float angle = body.getAngle();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        // Clear of the hull: the body's reach plus the weapon's muzzle offset.
        float reach = reach(body) + weapon.muzzleOffset;
        PhysicsManager.getPosition(body, pos).add(cos * reach, sin * reach);
        PhysicsManager.getVelocity(body, vel).add(cos * projectile.muzzleSpeed, sin * projectile.muzzleSpeed);
        cooldown = weapon.cooldown;
        return ballistics.spawn(projectile, projectile.mass, pos.x, pos.y, vel.x, vel.y, ship);
    }

    private static float reach(Body body) {
        float r = 0f;
        Vector2 v = new Vector2();
        for (com.badlogic.gdx.physics.box2d.Fixture f : body.getFixtureList()) {
            com.badlogic.gdx.physics.box2d.Shape s = f.getShape();
            if (s instanceof com.badlogic.gdx.physics.box2d.PolygonShape) {
                com.badlogic.gdx.physics.box2d.PolygonShape p = (com.badlogic.gdx.physics.box2d.PolygonShape) s;
                for (int i = 0; i < p.getVertexCount(); i++) {
                    p.getVertex(i, v);
                    r = Math.max(r, v.len());
                }
            } else {
                r = Math.max(r, s.getRadius());
            }
        }
        return r * PhysicsManager.METERS_PER_UNIT;
    }
}
