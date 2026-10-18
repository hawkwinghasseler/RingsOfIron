package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Where any combat contact really is: a Box2D ship, a fast-pass kinematic transient, a missile or
 * round in flight, or a decoy. Sensors and fuses read the truth through here; everything that
 * aims works from tracks instead. Local (bubble) meters and m/s.
 */
public final class ContactState {
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<KinematicTransientComponent> TRANSIENTS = ComponentMapper.getFor(KinematicTransientComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJECTILES = ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<DecoyComponent> DECOYS = ComponentMapper.getFor(DecoyComponent.class);

    private ContactState() {
    }

    /** Writes the entity's position and velocity; returns false if it has neither. */
    public static boolean read(Entity e, Vector2 pos, Vector2 vel) {
        if (e == null) return false;
        KinematicTransientComponent k = TRANSIENTS.get(e);
        if (k != null) {
            pos.set(k.x, k.y);
            vel.set(k.vx, k.vy);
            return true;
        }
        PhysicsBodyComponent b = BODIES.get(e);
        if (b != null && b.body != null) {
            PhysicsManager.getPosition(b.body, pos);
            PhysicsManager.getVelocity(b.body, vel);
            return true;
        }
        ProjectileComponent p = PROJECTILES.get(e);
        if (p != null) {
            pos.set(p.pos);
            vel.set(p.vel);
            return true;
        }
        DecoyComponent d = DECOYS.get(e);
        if (d != null) {
            pos.set(d.pos);
            vel.set(d.vel);
            return true;
        }
        return false;
    }
}
