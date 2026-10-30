package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.ContactState;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.uihud.providers.ThreatProvider;

/**
 * ui-hud's missile overlay from combat-sys: every missile in flight, whose it is, and for one with a
 * target, where and when it meets that target if both hold their course. Missiles still accelerate,
 * so the time is an upper bound that shrinks as the motor burns.
 */
public class CombatThreats implements ThreatProvider {
    /** How far ahead to draw a missile with no target, seconds. */
    static final float COAST_LOOKAHEAD = 4f;
    /** Longest path drawn toward a target, seconds. */
    static final float MAX_LOOKAHEAD = 60f;

    private static final ComponentMapper<MissileComponent> MISSILES = ComponentMapper.getFor(MissileComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJECTILES = ComponentMapper.getFor(ProjectileComponent.class);

    private final GameContext ctx;
    private final ImmutableArray<Entity> missiles;
    private final Vector2 targetPos = new Vector2(), targetVel = new Vector2();

    public CombatThreats(GameContext ctx) {
        this.ctx = ctx;
        this.missiles = ctx.engine.getEcsEngine().getEntitiesFor(Family.all(MissileComponent.class, ProjectileComponent.class).get());
    }

    @Override
    public void collect(ThreatFrame out) {
        Entity player = ctx.player();
        for (Entity e : missiles) {
            MissileComponent m = MISSILES.get(e);
            ProjectileComponent p = PROJECTILES.get(e);
            MissileTrack t = out.add();
            t.side = m.launcher != null && m.launcher == player ? Side.OWN
                    : player != null && (m.target == player || m.seekerLock == player) ? Side.INCOMING : Side.OTHER;
            t.x = p.pos.x;
            t.y = p.pos.y;
            t.burning = m.throttle > 0f;
            Entity target = m.seekerLock != null ? m.seekerLock : m.target;
            float tca = ContactState.read(target, targetPos, targetVel)
                    ? closestApproach(p.pos.x - targetPos.x, p.pos.y - targetPos.y, p.vel.x - targetVel.x, p.vel.y - targetVel.y)
                    : Float.NaN;
            if (!Float.isNaN(tca) && tca <= MAX_LOOKAHEAD) {
                t.timeToImpact = tca;
                t.pathX = targetPos.x + targetVel.x * tca;
                t.pathY = targetPos.y + targetVel.y * tca;
            } else {
                t.pathX = p.pos.x + p.vel.x * COAST_LOOKAHEAD;
                t.pathY = p.pos.y + p.vel.y * COAST_LOOKAHEAD;
            }
        }
    }

    /** Seconds until closest approach for relative position (rx, ry) and velocity (vx, vy); NaN if opening. */
    static float closestApproach(float rx, float ry, float vx, float vy) {
        float v2 = vx * vx + vy * vy;
        if (v2 < 1e-6f) return Float.NaN;
        float t = -(rx * vx + ry * vy) / v2;
        return t > 0f ? t : Float.NaN;
    }
}
