package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.LeadSolver;

/**
 * Stand-in fire control until combat-sys provides one: a manually chosen target and a fixed
 * projectile speed, solved with {@link LeadSolver}.
 */
public class SimpleTargetingProvider implements TargetingProvider {
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final Entity shooter;
    public float projectileSpeed;
    private Entity target;
    private float timeToImpact = Float.NaN;
    private final Vector2 shooterVel = new Vector2();
    private final Vector2 targetVel = new Vector2();

    public SimpleTargetingProvider(Entity shooter, float projectileSpeed) {
        this.shooter = shooter;
        this.projectileSpeed = projectileSpeed;
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    @Override
    public Entity getLockedTarget() {
        return target;
    }

    @Override
    public boolean getLeadPoint(Vector2 out) {
        timeToImpact = Float.NaN;
        if (target == null || shooter == null) return false;
        TransformComponent s = TRANSFORMS.get(shooter);
        TransformComponent t = TRANSFORMS.get(target);
        if (s == null || t == null) return false;
        velocityOf(shooter, shooterVel);
        velocityOf(target, targetVel);
        float time = LeadSolver.interceptTime(t.x - s.x, t.y - s.y,
                targetVel.x - shooterVel.x, targetVel.y - shooterVel.y, projectileSpeed);
        if (Float.isNaN(time)) return false;
        timeToImpact = time;
        // Aim point drawn in the bubble frame: where the target will be relative to us after `time`.
        out.set(t.x + (targetVel.x - shooterVel.x) * time, t.y + (targetVel.y - shooterVel.y) * time);
        return true;
    }

    @Override
    public float getTimeToImpact() {
        return timeToImpact;
    }

    static void velocityOf(Entity entity, Vector2 out) {
        PhysicsBodyComponent body = BODIES.get(entity);
        if (body != null && body.body != null) PhysicsManager.getVelocity(body.body, out);
        else out.setZero();
    }
}
