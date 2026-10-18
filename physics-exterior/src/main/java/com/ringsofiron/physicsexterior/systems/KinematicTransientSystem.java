package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.IdentityMap;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Moves fast-pass bodies ({@link KinematicTransientComponent}) along their true trajectories in
 * the bubble frame and sweeps them against the Box2D world for collisions.
 *
 * Runs right after each Box2D step. The position comes straight from the trajectory at the new
 * sim time minus the anchor, in doubles, so nothing is integrated. The sweep casts rays from last
 * tick's position to this tick's: one along the center line, plus two at +/- the radius for wide
 * bodies. Targets are tested at their post-step pose; a transient moves at least 1,000 m/s and a
 * Box2D body at most 1,200 m/s, so the error is under one tick of the target's motion. Each body
 * passed through fires one {@link FastPassImpactEvent} on {@link PhysicsManager#fastPassImpacts}.
 */
public class KinematicTransientSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY = Family.all(KinematicTransientComponent.class).get();

    private final ComponentMapper<KinematicTransientComponent> transientMapper =
            ComponentMapper.getFor(KinematicTransientComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<BodyShapeComponent> shapeMapper = ComponentMapper.getFor(BodyShapeComponent.class);

    private final PhysicsManager physics;
    private final double[] trueState = new double[4];
    private final float[] local = new float[4];
    private final IdentityMap<Body, Hit> hits = new IdentityMap<>();
    private final Sweep sweep = new Sweep();
    private final Listener<WorldShiftEvent> shiftListener = this::onWorldShift;
    private ImmutableArray<Entity> entities;

    public KinematicTransientSystem(PhysicsManager physics) {
        this.physics = physics;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
        physics.addStepListener(this);
        physics.worldShifts.add(shiftListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.worldShifts.remove(shiftListener);
        entities = null;
    }

    @Override
    public void beforeStep(float deltaTime) {
    }

    @Override
    public void afterStep(float deltaTime) {
        for (Entity entity : entities) {
            KinematicTransientComponent kt = transientMapper.get(entity);
            if (kt.trajectory == null) continue;

            kt.trajectory.stateAt(physics.getSimSeconds(), trueState);
            physics.trueToLocal(trueState, local);
            kt.x = local[0];
            kt.y = local[1];
            kt.vx = local[2];
            kt.vy = local[3];
            kt.relativeSpeed = Vector2.len(kt.vx, kt.vy);

            if (kt.hasPrevious) sweep(entity, kt);
            kt.previousX = kt.x;
            kt.previousY = kt.y;
            kt.hasPrevious = true;

            TransformComponent transform = transformMapper.get(entity);
            if (transform != null) {
                transform.x = kt.x;
                transform.y = kt.y;
            }
        }
    }

    private void sweep(Entity entity, KinematicTransientComponent kt) {
        float dx = kt.x - kt.previousX;
        float dy = kt.y - kt.previousY;
        float length = Vector2.len(dx, dy);
        if (length == 0f) return;

        sweep.transientEntity = entity;
        physics.rayCast(kt.previousX, kt.previousY, kt.x, kt.y, sweep);
        if (kt.radius > 0f) {
            float ox = -dy / length * kt.radius;
            float oy = dx / length * kt.radius;
            physics.rayCast(kt.previousX + ox, kt.previousY + oy, kt.x + ox, kt.y + oy, sweep);
            physics.rayCast(kt.previousX - ox, kt.previousY - oy, kt.x - ox, kt.y - oy, sweep);
        }

        for (IdentityMap.Entry<Body, Hit> entry : hits) {
            Body body = entry.key;
            Hit hit = entry.value;
            Object key = body.getUserData() instanceof Entity ? body.getUserData() : body;
            if (!kt.alreadyHit.add(key)) continue;
            physics.fastPassImpacts.dispatch(toEvent(entity, kt, body, hit));
        }
        hits.clear();
    }

    private FastPassImpactEvent toEvent(Entity entity, KinematicTransientComponent kt, Body body, Hit hit) {
        FastPassImpactEvent event = new FastPassImpactEvent();
        event.transientEntity = entity;
        event.transientMass = massOf(entity, kt);
        event.target = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
        event.pointX = hit.x;
        event.pointY = hit.y;
        event.normalX = hit.normalX;
        event.normalY = hit.normalY;

        Vector2 targetVelocity = body.getLinearVelocityFromWorldPoint(
                new Vector2(hit.x, hit.y).scl(PhysicsManager.UNITS_PER_METER));
        float rvx = kt.vx - targetVelocity.x * PhysicsManager.METERS_PER_UNIT;
        float rvy = kt.vy - targetVelocity.y * PhysicsManager.METERS_PER_UNIT;
        float speed = Vector2.len(rvx, rvy);
        event.relativeSpeed = speed;
        if (speed > 0f) {
            event.dirX = rvx / speed;
            event.dirY = rvy / speed;
        }
        return event;
    }

    /** The transient's own mass, else the mass it had as a Box2D body, else 0 (unknown). */
    private float massOf(Entity entity, KinematicTransientComponent kt) {
        if (kt.mass > 0f) return kt.mass;
        BodyShapeComponent shape = shapeMapper.get(entity);
        return shape != null ? shape.mass : 0f;
    }

    private void onWorldShift(Signal<WorldShiftEvent> signal, WorldShiftEvent shift) {
        for (Entity entity : entities) {
            KinematicTransientComponent kt = transientMapper.get(entity);
            kt.x -= shift.deltaX;
            kt.y -= shift.deltaY;
            kt.vx -= shift.deltaVx;
            kt.vy -= shift.deltaVy;
            // Same frame change as PhysicsManager applies to previous body poses.
            kt.previousX += shift.deltaVx * PhysicsManager.TIME_STEP - shift.deltaX;
            kt.previousY += shift.deltaVy * PhysicsManager.TIME_STEP - shift.deltaY;
        }
    }

    private static class Hit {
        float fraction = Float.MAX_VALUE;
        float x, y, normalX, normalY;
    }

    /** Keeps the earliest hit on each body across all rays of one sweep. */
    private class Sweep implements PhysicsManager.RayHitCallback {
        Entity transientEntity;

        @Override
        public float hit(Fixture fixture, float pointX, float pointY, float normalX, float normalY, float fraction) {
            if (fixture.isSensor()) return -1f;
            Body body = fixture.getBody();
            if (body.getUserData() == transientEntity) return -1f;
            Hit hit = hits.get(body);
            if (hit == null) {
                hit = new Hit();
                hits.put(body, hit);
            }
            if (fraction < hit.fraction) {
                hit.fraction = fraction;
                hit.x = pointX;
                hit.y = pointY;
                hit.normalX = normalX;
                hit.normalY = normalY;
            }
            return 1f;
        }
    }
}
