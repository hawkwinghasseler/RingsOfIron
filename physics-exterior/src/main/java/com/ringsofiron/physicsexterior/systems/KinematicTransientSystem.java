package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.events.AnchorChangeEvent;
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
 *
 * <p>A transient the {@link BodyFactory} gives a body (a station) is not swept: its kinematic body
 * is put exactly on the trajectory after each step and given the velocity that carries it to the
 * next tick's point, so Box2D sees it move smoothly and joints to it hold. The system owns those
 * bodies: one is destroyed when its component or entity goes.
 */
public class KinematicTransientSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY = Family.all(KinematicTransientComponent.class).get();
    /** Corrections smaller than this, in meters, are left for the next tick's velocity to absorb. */
    private static final float PLACE_TOLERANCE = 1e-3f;

    /** Decides which transients get a kinematic body, and builds it. */
    @FunctionalInterface
    public interface BodyFactory {
        /**
         * A body for this transient, with its fixtures in body-local meters and the entity as user
         * data, anywhere in the world (the system places it and makes it kinematic); or null to
         * sweep the transient instead.
         */
        Body build(Entity entity, KinematicTransientComponent transientBody);
    }

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
    private final Listener<AnchorChangeEvent> anchorListener = this::onAnchorChange;
    // Remembered per entity because the component is already gone when a removal is reported.
    private final ObjectMap<Entity, Body> trackedBodies = new ObjectMap<>();
    private final EntityListener bodyLifecycle = new BodyLifecycle();
    private final double[] anchorState = new double[4];
    private BodyFactory bodyFactory;
    private ImmutableArray<Entity> entities;

    public KinematicTransientSystem(PhysicsManager physics) {
        this.physics = physics;
    }

    /** Gives transients bodies from now on; transients already in the engine get theirs now. */
    public void setBodyFactory(BodyFactory factory) {
        this.bodyFactory = factory;
        if (entities == null) return;
        for (Entity entity : entities) {
            if (!trackedBodies.containsKey(entity)) bodyLifecycle.entityAdded(entity);
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
        physics.addStepListener(this);
        physics.worldShifts.add(shiftListener);
        physics.anchorChanges.add(anchorListener);
        engine.addEntityListener(FAMILY, bodyLifecycle);
        for (Entity entity : entities) bodyLifecycle.entityAdded(entity);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.worldShifts.remove(shiftListener);
        physics.anchorChanges.remove(anchorListener);
        engine.removeEntityListener(bodyLifecycle);
        for (Body body : trackedBodies.values()) physics.destroyBody(body);
        trackedBodies.clear();
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

            locate(kt, physics.getOriginTime());
            if (kt.body != null) {
                place(kt);
            } else if (kt.hasPrevious) {
                sweep(entity, kt);
            }
            kt.previousX = kt.x;
            kt.previousY = kt.y;
            kt.hasPrevious = true;

            TransformComponent transform = transformMapper.get(entity);
            if (transform != null) {
                transform.x = kt.x;
                transform.y = kt.y;
                transform.rotation = kt.angle * MathUtils.radiansToDegrees;
            }
        }
    }

    /** Bubble-frame position and velocity at sim second {@code t}, which must be the origin's time. */
    private void locate(KinematicTransientComponent kt, double t) {
        kt.trajectory.stateAt(t, trueState);
        physics.trueToLocal(trueState, local);
        kt.x = local[0];
        kt.y = local[1];
        kt.vx = local[2];
        kt.vy = local[3];
        kt.relativeSpeed = Vector2.len(kt.vx, kt.vy);
    }

    /**
     * Puts the transient's body on its point now ({@link KinematicTransientComponent#x}, y) and
     * gives it the velocity that reaches next tick's point, both in the bubble frame.
     */
    private void place(KinematicTransientComponent kt) {
        Body body = kt.body;
        Vector2 p = body.getPosition();
        float px = p.x * PhysicsManager.METERS_PER_UNIT, py = p.y * PhysicsManager.METERS_PER_UNIT;
        if (Math.abs(px - kt.x) > PLACE_TOLERANCE || Math.abs(py - kt.y) > PLACE_TOLERANCE
                || body.getAngle() != kt.angle) {
            PhysicsManager.setPosition(body, kt.x, kt.y, kt.angle);
        }
        double next = physics.getOriginTime() + PhysicsManager.TIME_STEP;
        kt.trajectory.stateAt(next, trueState);
        physics.getAnchorModel().stateAt(next, anchorState);
        double nx = trueState[0] - anchorState[0];
        double ny = trueState[1] - anchorState[1];
        // The step is taken in doubles: from floats a kilometre out, a tick's step rounds to the
        // nearest ulp, and the velocity jumps by 7 mm/s from tick to tick, a jolt that a heavy ship
        // latched on takes through its weld (at a gunship's mass, enough to tear it)
        kt.trajectory.stateAt(physics.getOriginTime(), trueState);
        double cx = trueState[0] - physics.getOriginX(), cy = trueState[1] - physics.getOriginY();
        PhysicsManager.setVelocity(body, (float) ((nx - cx) / PhysicsManager.TIME_STEP), (float) ((ny - cy) / PhysicsManager.TIME_STEP));
        body.setAngularVelocity(0f);
    }

    /** A new anchor (rails warp rebuilt the bubble, a save loaded) moves the frame: re-place every body. */
    private void onAnchorChange(Signal<AnchorChangeEvent> signal, AnchorChangeEvent change) {
        if (change.kind != AnchorChangeEvent.Kind.SET || entities == null) return;
        for (Entity entity : entities) {
            KinematicTransientComponent kt = transientMapper.get(entity);
            if (kt.body == null || kt.trajectory == null) continue;
            locate(kt, physics.getOriginTime());
            place(kt);
            kt.hasPrevious = false;
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
            // Out to the skin a hull collides on, past its vertices
            float edge = kt.radius + PolygonSkin.SKIN;
            float ox = -dy / length * edge;
            float oy = dx / length * edge;
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

    private class BodyLifecycle implements EntityListener {
        @Override
        public void entityAdded(Entity entity) {
            KinematicTransientComponent kt = transientMapper.get(entity);
            if (bodyFactory == null || kt == null || kt.body != null) return;
            Body body = bodyFactory.build(entity, kt);
            if (body == null) return;
            body.setType(BodyDef.BodyType.KinematicBody);
            if (body.getUserData() == null) body.setUserData(entity);
            kt.body = body;
            trackedBodies.put(entity, body);
            if (kt.trajectory != null) {
                locate(kt, physics.getOriginTime());
                place(kt);
            }
        }

        @Override
        public void entityRemoved(Entity entity) {
            Body body = trackedBodies.remove(entity);
            if (body == null) return;
            KinematicTransientComponent kt = transientMapper.get(entity);
            if (kt != null && kt.body == body) kt.body = null;
            physics.destroyBody(body);
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
