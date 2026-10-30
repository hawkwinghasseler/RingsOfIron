package com.hawkwing.RingsOfIron.wiring;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.pilot.ThreatSensor;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/** Lets ai-crew's combat pilot see combat-sys missiles homing on its ship. */
public class CombatThreatSensor implements ThreatSensor {
    private static final Family MISSILES = Family.all(MissileComponent.class, ProjectileComponent.class).get();

    private final ImmutableArray<Entity> missiles;
    private final Vector2 shipPos = new Vector2();

    public CombatThreatSensor(Engine engine) {
        missiles = engine.getEntitiesFor(MISSILES);
    }

    @Override
    public boolean nearestInbound(Entity ship, Vector2 outPosition, Vector2 outVelocity) {
        PhysicsBodyComponent body = ship.getComponent(PhysicsBodyComponent.class);
        if (body == null || body.body == null) return false;
        PhysicsManager.getPosition(body.body, shipPos);
        ProjectileComponent nearest = null;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < missiles.size(); i++) {
            Entity m = missiles.get(i);
            if (m.getComponent(MissileComponent.class).target != ship) continue;
            ProjectileComponent p = m.getComponent(ProjectileComponent.class);
            if (p.spent) continue;
            float d = p.pos.dst2(shipPos);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return false;
        outPosition.set(nearest.pos);
        outVelocity.set(nearest.vel);
        return true;
    }
}
