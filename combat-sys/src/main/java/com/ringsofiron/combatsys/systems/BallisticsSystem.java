package com.ringsofiron.combatsys.systems;

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
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.TidalAccel;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.damage.SegmentShapeCast;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.ImpactEvent;
import com.ringsofiron.combatsys.events.InterceptEvent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Flies raycast-model rounds and finds what they hit (combat-sys/Design.md section 2.3).
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}, once per fixed tick, so it sees the
 * bodies exactly where the last step left them. Each tick it first sweeps the segment every
 * round flew during that step against each nearby ship in that ship's own frame: the start
 * against the ship's pose before the step and the end against its pose after it. Then it
 * integrates the round through the coming step with semi-implicit Euler under the tidal field. That catches hits on fast-moving or spinning hulls that a
 * plain world-space ray would miss. Hits are queued on {@link CombatEvents#pendingImpacts}.
 *
 * Missiles are rounds too (their {@code MissileComponent} adds guidance), and ordinary rounds
 * are also swept against each missile's {@code hitRadius} in relative motion: that is point
 * defense. A hit takes the round's relative energy off the missile's hit points.
 *
 * Every round is on the raycast model for now. Slow defs that the design gives Box2D bullet
 * bodies fly the same way here; both models emit the same {@link ImpactEvent}.
 */
public class BallisticsSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY = Family.all(ProjectileComponent.class).get();
    /** Categories a round can hit. */
    public static final short TARGET_MASK = (short) (CollisionCategories.CATEGORY_SHIP
            | CollisionCategories.CATEGORY_ASTEROID | CollisionCategories.CATEGORY_STATION);
    /** Broadphase padding for how far a ship may move in a tick, meters. */
    private static final float SHIP_MOTION_MARGIN = PhysicsManager.MAX_SPEED * PhysicsManager.TIME_STEP;

    private static final Family MISSILES = Family.all(MissileComponent.class, ProjectileComponent.class).get();

    private final ComponentMapper<ProjectileComponent> projectileMapper = ComponentMapper.getFor(ProjectileComponent.class);
    private final ComponentMapper<MissileComponent> missileMapper = ComponentMapper.getFor(MissileComponent.class);

    private final PhysicsManager physics;
    private final CombatEvents events;
    private TidalAccel tidal = TidalAccel.ZERO;
    private float bubbleExitRadius = CombatPhysics.BUBBLE_EXIT_RADIUS;

    private Engine engine;
    private ImmutableArray<Entity> projectiles;
    private ImmutableArray<Entity> missiles;
    private final Array<Entity> doomed = new Array<>();
    private final ObjectMap<Body, float[]> previousPose = new ObjectMap<>();
    private final Array<Body> bodyBuffer = new Array<>();
    private final Array<Body> candidates = new Array<>();
    private final Listener<WorldShiftEvent> shiftListener = this::onWorldShift;
    private float clock;

    private final Vector2 accel = new Vector2();
    private final Vector2 normal = new Vector2();
    private final Vector2 bestNormal = new Vector2();
    private final Vector2 tmp = new Vector2();
    private final Transform prevTransform = new Transform();

    public BallisticsSystem(PhysicsManager physics, CombatEvents events) {
        this.physics = physics;
        this.events = events;
    }

    public void setTidalField(TidalAccel tidal) {
        this.tidal = tidal == null ? TidalAccel.ZERO : tidal;
    }

    public TidalAccel getTidalField() {
        return tidal;
    }

    public void setBubbleExitRadius(float meters) {
        this.bubbleExitRadius = meters;
    }

    /** Seconds of fixed-tick time this system has run. */
    public float getClock() {
        return clock;
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        projectiles = engine.getEntitiesFor(FAMILY);
        missiles = engine.getEntitiesFor(MISSILES);
        physics.addStepListener(this);
        physics.worldShifts.add(shiftListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.worldShifts.remove(shiftListener);
        previousPose.clear();
        projectiles = null;
        this.engine = null;
    }

    /**
     * Puts a round in flight. Position and velocity are local (bubble) meters and m/s; the
     * caller adds the shooter's velocity. The entity is transient and never saved.
     */
    public Entity spawn(ProjectileDef def, float mass, float x, float y, float vx, float vy, Entity owner) {
        Entity e = new Entity();
        ProjectileComponent p = new ProjectileComponent();
        p.def = def;
        p.mass = mass;
        p.pos.set(x, y);
        p.prevPos.set(x, y);
        p.vel.set(vx, vy);
        p.owner = owner;
        e.add(p);
        e.add(new TransientComponent());
        engine.addEntity(e);
        return e;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float dt) {
        events.tick++;
        clock += dt;

        // The bodies are where the last step left them, so first test the segment every round
        // flew during that step, then fly the survivors on through the step about to run. Two
        // passes, so a point-defense round and the missile it meets are tested on the same step.
        for (int i = 0; i < projectiles.size(); i++) {
            Entity e = projectiles.get(i);
            ProjectileComponent p = projectileMapper.get(e);
            if (p.spent || e.isScheduledForRemoval()) continue;
            if (sweep(e, p)) spend(e, p);
        }
        for (int i = 0; i < projectiles.size(); i++) {
            Entity e = projectiles.get(i);
            ProjectileComponent p = projectileMapper.get(e);
            if (p.spent || e.isScheduledForRemoval()) continue;
            if (step(p, dt)) spend(e, p);
        }
        for (int i = 0; i < doomed.size; i++) engine.removeEntity(doomed.get(i));
        doomed.clear();

        recordPoses();
    }

    private void spend(Entity e, ProjectileComponent p) {
        p.spent = true;
        doomed.add(e);
    }

    /** Flies one round through the coming step; returns true if it expired or left the bubble. */
    private boolean step(ProjectileComponent p, float dt) {
        p.age += dt;
        tidal.accelAt(p.pos.x, p.pos.y, accel);
        p.prevPos.set(p.pos);
        p.vel.mulAdd(accel, dt);
        p.pos.mulAdd(p.vel, dt);
        if (p.age >= p.def.maxLifetime) return true;
        return p.pos.len2() > bubbleExitRadius * bubbleExitRadius;
    }

    /** Swept hit test from prevPos to pos. Queues an impact and returns true on a hit. */
    private boolean sweep(Entity self, final ProjectileComponent p) {
        float ax = p.prevPos.x, ay = p.prevPos.y, bx = p.pos.x, by = p.pos.y;
        float pad = SHIP_MOTION_MARGIN;
        float u = PhysicsManager.UNITS_PER_METER;
        candidates.clear();
        physics.getWorld().QueryAABB(new QueryCallback() {
            @Override
            public boolean reportFixture(Fixture fixture) {
                if ((fixture.getFilterData().categoryBits & TARGET_MASK) == 0) return true;
                Body body = fixture.getBody();
                if (!candidates.contains(body, true)) candidates.add(body);
                return true;
            }
        }, (Math.min(ax, bx) - pad) * u, (Math.min(ay, by) - pad) * u,
                (Math.max(ax, bx) + pad) * u, (Math.max(ay, by) + pad) * u);

        float bestT = 2f;
        Body bestBody = null;
        for (int i = 0; i < candidates.size; i++) {
            Body body = candidates.get(i);
            Entity target = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
            if (target != null && target == p.owner && p.age < p.def.armingTime) continue;
            if (target != null && target == p.ignore && p.age < p.ignoreUntil) continue;

            // Segment in the body's frame, Box2D units: start vs previous pose, end vs current.
            Transform now = body.getTransform();
            float[] prev = previousPose.get(body);
            if (prev != null) prevTransform.setPosition(tmp.set(prev[0], prev[1]));
            else prevTransform.setPosition(body.getPosition());
            prevTransform.setRotation(prev != null ? prev[2] : body.getAngle());
            Vector2 la = inverse(prevTransform, ax * u, ay * u, new Vector2());
            Vector2 lb = inverse(now, bx * u, by * u, new Vector2());

            Array<Fixture> fixtures = body.getFixtureList();
            for (int f = 0; f < fixtures.size; f++) {
                Fixture fixture = fixtures.get(f);
                if ((fixture.getFilterData().categoryBits & TARGET_MASK) == 0) continue;
                float t = SegmentShapeCast.cast(fixture.getShape(), la.x, la.y, lb.x, lb.y, normal);
                if (t >= 0f && t < bestT) {
                    bestT = t;
                    bestBody = body;
                    bestNormal.set(normal);
                }
            }
        }
        // Point defense: a round (not a missile) against each missile's circle, in relative motion.
        if (!missileMapper.has(self)) {
            Entity struck = null;
            for (int i = 0; i < missiles.size(); i++) {
                Entity me = missiles.get(i);
                ProjectileComponent mp = projectileMapper.get(me);
                MissileComponent m = missileMapper.get(me);
                if (mp == p || mp.spent || me.isScheduledForRemoval() || m.launcher == p.owner) continue;
                float t = segmentCircle(ax - mp.prevPos.x, ay - mp.prevPos.y,
                        bx - mp.pos.x, by - mp.pos.y, m.def.hitRadius);
                if (t >= 0f && t < bestT) {
                    bestT = t;
                    struck = me;
                }
            }
            if (struck != null) {
                intercept(p, struck, bestT);
                return true;
            }
        }
        if (bestBody == null) return false;

        // Hit point on the body's current pose, back in local meters.
        Transform now = bestBody.getTransform();
        Vector2 la = inverse(previousOrNow(bestBody), ax * u, ay * u, new Vector2());
        Vector2 lb = inverse(now, bx * u, by * u, new Vector2());
        Vector2 localHit = la.lerp(lb, bestT);
        Vector2 worldHit = bestBody.getWorldPoint(localHit).cpy().scl(PhysicsManager.METERS_PER_UNIT);

        ImpactEvent hit = new ImpactEvent();
        hit.target = bestBody.getUserData() instanceof Entity ? (Entity) bestBody.getUserData() : null;
        hit.owner = p.owner;
        hit.worldPoint.set(worldHit);
        hit.worldNormal.set(bestNormal).rotateRad(bestBody.getAngle());
        hit.velocity.set(p.vel);
        CombatPhysics.velocityAt(bestBody, worldHit.x, worldHit.y, tmp);
        hit.relVelocity.set(p.vel).sub(tmp);
        hit.mass = p.mass;
        hit.def = p.def;
        hit.penetration = p.penetration;
        hit.tick = events.tick;
        if (hit.target != null) events.pendingImpacts.add(hit);
        return true;
    }

    /**
     * A round strikes a missile at fraction {@code t} of this step: its energy relative to the
     * missile comes off the missile's hit points, and at zero the missile breaks up.
     */
    private void intercept(ProjectileComponent p, Entity missile, float t) {
        ProjectileComponent mp = projectileMapper.get(missile);
        MissileComponent m = missileMapper.get(missile);
        tmp.set(p.vel).sub(mp.vel);
        float energy = p.penetration >= 0f ? p.penetration
                : 0.5f * p.mass * tmp.len2() * p.def.penetrationFactor;
        m.hitPoints -= energy;
        InterceptEvent hit = new InterceptEvent();
        hit.missile = missile;
        hit.shooter = p.owner;
        hit.point.set(mp.prevPos).lerp(mp.pos, t);
        hit.energy = energy;
        hit.destroyed = m.hitPoints <= 0f;
        hit.tick = events.tick;
        if (hit.destroyed) spend(missile, mp);
        events.intercepts.dispatch(hit);
    }

    /**
     * Earliest fraction in [0, 1] at which the segment a-b enters the circle of radius r at the
     * origin, 0 if it starts inside, or -1 if it misses.
     */
    public static float segmentCircle(float ax, float ay, float bx, float by, float r) {
        float c = ax * ax + ay * ay - r * r;
        if (c <= 0f) return 0f;
        float dx = bx - ax, dy = by - ay;
        float a = dx * dx + dy * dy;
        if (a < 1e-12f) return -1f;
        float b = ax * dx + ay * dy;
        float disc = b * b - a * c;
        if (disc < 0f) return -1f;
        float t = (-b - (float) Math.sqrt(disc)) / a;
        return t >= 0f && t <= 1f ? t : -1f;
    }

    private Transform previousOrNow(Body body) {
        float[] prev = previousPose.get(body);
        if (prev == null) return body.getTransform();
        prevTransform.setPosition(tmp.set(prev[0], prev[1]));
        prevTransform.setRotation(prev[2]);
        return prevTransform;
    }

    private static Vector2 inverse(Transform t, float x, float y, Vector2 out) {
        Vector2 pos = t.getPosition();
        float c = t.vals[Transform.COS], s = t.vals[Transform.SIN];
        float dx = x - pos.x, dy = y - pos.y;
        return out.set(c * dx + s * dy, -s * dx + c * dy);
    }

    /** Remembers every body's pose for next tick's swept tests. */
    private void recordPoses() {
        physics.getWorld().getBodies(bodyBuffer);
        // Rebuilt every tick so destroyed bodies never linger as keys.
        previousPose.clear();
        for (int i = 0; i < bodyBuffer.size; i++) {
            Body body = bodyBuffer.get(i);
            float[] pose = new float[3];
            previousPose.put(body, pose);
            Vector2 pos = body.getPosition();
            pose[0] = pos.x;
            pose[1] = pos.y;
            pose[2] = body.getAngle();
        }
        bodyBuffer.clear();
    }

    /** Moves combat-owned state with the floating origin (design 1.1, "Rebase"). */
    private void onWorldShift(Signal<WorldShiftEvent> signal, WorldShiftEvent shift) {
        if (projectiles != null) {
            for (int i = 0; i < projectiles.size(); i++) {
                ProjectileComponent p = projectileMapper.get(projectiles.get(i));
                p.pos.sub(shift.deltaX, shift.deltaY);
                p.prevPos.sub(shift.deltaX, shift.deltaY);
                p.vel.sub(shift.deltaVx, shift.deltaVy);
            }
        }
        float u = PhysicsManager.UNITS_PER_METER;
        for (float[] pose : previousPose.values()) {
            pose[0] -= shift.deltaX * u;
            pose[1] -= shift.deltaY * u;
        }
        for (int i = 0; i < events.pendingImpacts.size; i++) {
            ImpactEvent hit = events.pendingImpacts.get(i);
            hit.worldPoint.sub(shift.deltaX, shift.deltaY);
            hit.velocity.sub(shift.deltaVx, shift.deltaVy);
        }
    }
}
