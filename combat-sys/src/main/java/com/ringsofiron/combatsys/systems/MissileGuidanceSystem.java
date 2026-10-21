package com.ringsofiron.combatsys.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.combatsys.ContactState;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.DetonationEvent;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Flies missiles and decoys (combat-sys/Design.md section 4).
 *
 * A missile is a {@link ProjectileComponent} that {@link BallisticsSystem} integrates and sweeps
 * for contact hits like any round, plus a {@link MissileComponent} this system steers. Each tick,
 * once its motor has armed, it gets a target estimate (its own seeker if the target is inside the
 * seeker's range and cone with a clear line of sight, else the launcher's track by datalink while
 * the launcher's weapon system works, else its seeker's memory for a while) and steers on it with
 * zero-effort-miss guidance: the predicted miss at time-to-go, perpendicular to the line of sight,
 * times the navigation gain, plus a closing burn while fuel above the terminal reserve lasts. It
 * turns its nose toward the commanded acceleration at its turn rate and burns only as well as the
 * nose is aligned. Mass falls as propellant burns. With no fuel or no estimate it coasts, armed.
 *
 * A proximity fuse fires when the missile will pass within its radius of what the seeker locked
 * during the coming tick; the warhead goes off as a {@link DetonationEvent}.
 *
 * Seekers lock the strongest signature in their cone, with the intended target's weighted up by
 * the seeker's discrimination, so a bright decoy can steal the lock.
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}, after {@link SensorTrackingSystem} and
 * before {@link BallisticsSystem} and {@link ImpactResolutionSystem}.
 */
public class MissileGuidanceSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family MISSILES = Family.all(MissileComponent.class, ProjectileComponent.class).get();
    private static final Family DECOYS = Family.all(DecoyComponent.class).get();
    private static final Family CONTACTS = Family.all(SignatureComponent.class).get();
    /** Base and full-burn thermal signature of a missile. */
    public static final float MISSILE_RADAR = 0.05f, MISSILE_THERMAL = 0.3f, PLUME_THERMAL = 3f;
    /** Below this closing speed, time-to-go comes from how long the motor takes to get there, m/s. */
    private static final float MIN_CLOSING = 30f;

    private final ComponentMapper<MissileComponent> missileMapper = ComponentMapper.getFor(MissileComponent.class);
    private final ComponentMapper<ProjectileComponent> projectileMapper = ComponentMapper.getFor(ProjectileComponent.class);
    private final ComponentMapper<DecoyComponent> decoyMapper = ComponentMapper.getFor(DecoyComponent.class);
    private final ComponentMapper<SignatureComponent> signatureMapper = ComponentMapper.getFor(SignatureComponent.class);
    private final ComponentMapper<SensorComponent> sensorMapper = ComponentMapper.getFor(SensorComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper = ComponentMapper.getFor(SubsystemComponent.class);

    private final PhysicsManager physics;
    private final CombatEvents events;
    private final CombatDefs defs;
    private final BallisticsSystem ballistics;
    private final SensorTrackingSystem sensors;
    private final ObjectMap<String, ProjectileDef> bodyDefs = new ObjectMap<>();
    private Engine engine;
    private ImmutableArray<Entity> missiles;
    private ImmutableArray<Entity> decoys;
    private ImmutableArray<Entity> contacts;
    private final Array<Entity> doomed = new Array<>();
    private final Listener<WorldShiftEvent> shiftListener = this::onWorldShift;

    private final Vector2 estPos = new Vector2();
    private final Vector2 estVel = new Vector2();
    private final Vector2 estAcc = new Vector2();
    private final Vector2 gravity = new Vector2();
    private final Vector2 pos = new Vector2();
    private final Vector2 vel = new Vector2();
    private final Vector2 d = new Vector2();
    private final Vector2 zem = new Vector2();
    private final Vector2 cmd = new Vector2();
    private final boolean[] blocked = new boolean[1];
    /** Ships a launcher's seekers leave alone unless the missile was fired at them; null spares none. */
    private WeaponControlSystem.HoldFire spared;

    public MissileGuidanceSystem(PhysicsManager physics, CombatEvents events, CombatDefs defs,
                                 BallisticsSystem ballistics, SensorTrackingSystem sensors) {
        this.physics = physics;
        this.events = events;
        this.defs = defs;
        this.ballistics = ballistics;
        this.sensors = sensors;
    }

    /**
     * Ships whose seekers pass over: a missile from {@code shooter} never locks a ship this spares
     * unless it was fired at that ship (round 10: a heavy missile fired at a raider locked a fallen
     * base's battery instead and burst on it). The game wires the same rule as the player's guns.
     */
    public void setSpared(WeaponControlSystem.HoldFire spared) {
        this.spared = spared;
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        missiles = engine.getEntitiesFor(MISSILES);
        decoys = engine.getEntitiesFor(DECOYS);
        contacts = engine.getEntitiesFor(CONTACTS);
        physics.addStepListener(this);
        physics.worldShifts.add(shiftListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.worldShifts.remove(shiftListener);
        missiles = decoys = contacts = null;
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    /**
     * Puts a missile in flight, motor dark until it arms. Position and velocity are local meters
     * and m/s, the launcher's motion and ejection already included. {@code datalinkSubsystem} is the
     * launcher's weapon system (null for an always-on datalink).
     */
    public Entity launch(MissileDef def, Entity launcher, Entity datalinkSubsystem, Entity target,
                         float x, float y, float vx, float vy, float heading) {
        Entity e = ballistics.spawn(bodyDef(def), def.dryMass + def.propellantMass, x, y, vx, vy, launcher);
        // A missile never strikes the ship that launched it: thrown forward from a beam launcher aft
        // on a long hull (the frigate's), it armed before it was clear and blew up on its own bow
        // (round 6, part 4: two harpoons took out both of a frigate's railguns with their first salvo)
        ProjectileComponent body = e.getComponent(ProjectileComponent.class);
        if (body != null) {
            body.ignore = launcher;
            body.ignoreUntil = Float.MAX_VALUE;
        }
        MissileComponent m = new MissileComponent();
        m.def = def;
        m.heading = heading;
        m.propellant = def.propellantMass;
        m.hitPoints = def.hitPoints;
        m.launcher = launcher;
        m.datalinkSubsystem = datalinkSubsystem;
        m.target = target;
        e.add(m);
        e.add(new SignatureComponent(MISSILE_RADAR, MISSILE_THERMAL, def.hitRadius));
        return e;
    }

    /**
     * Cuts the guidance of every missile {@code launcher} has in flight: each forgets its target,
     * its motor goes dark and it coasts on as a ballistic slug, so it no longer homes on anyone or
     * counts as inbound. Returns how many.
     */
    public int goBallistic(Entity launcher) {
        if (launcher == null || missiles == null) return 0;
        int n = 0;
        for (int i = 0; i < missiles.size(); i++) {
            MissileComponent m = missileMapper.get(missiles.get(i));
            if (m.launcher != launcher || m.guidanceCut) continue;
            m.guidanceCut = true;
            m.target = null;
            m.seekerLock = null;
            m.propellant = 0f;
            m.throttle = 0f;
            m.phase = MissileComponent.Phase.COAST;
            n++;
        }
        return n;
    }

    /** Drops a decoy at a point with a velocity; it shines for {@code lifetime} seconds. */
    public Entity dropDecoy(Entity owner, float x, float y, float vx, float vy, float radar, float thermal, float lifetime) {
        Entity e = new Entity();
        DecoyComponent decoy = new DecoyComponent();
        decoy.pos.set(x, y);
        decoy.vel.set(vx, vy);
        decoy.lifetime = lifetime;
        decoy.owner = owner;
        e.add(decoy);
        e.add(new SignatureComponent(radar, thermal, 1f));
        e.add(new TransientComponent());
        engine.addEntity(e);
        return e;
    }

    /** The round the ballistics model flies for a missile: its contact hit and warhead. */
    private ProjectileDef bodyDef(MissileDef def) {
        ProjectileDef body = bodyDefs.get(def.id);
        if (body == null) {
            body = new ProjectileDef();
            body.id = def.id + ".airframe";
            body.mass = def.dryMass + def.propellantMass;
            body.muzzleSpeed = 0f;
            body.damageType = DamageType.EXPLOSIVE;
            body.penetrationFactor = 0.3f;
            // A missile that strikes the hull goes off on it rather than glancing away.
            body.ricochetAngleDeg = 89f;
            body.ricochetRestitution = 0.1f;
            body.fuse = Fuse.contact();
            body.warhead = def.warhead;
            body.maxLifetime = def.maxLifetime;
            body.armingTime = def.armingDelay;
            bodyDefs.put(def.id, body);
        }
        return body;
    }

    @Override
    public void beforeStep(float dt) {
        for (int i = 0; i < decoys.size(); i++) {
            Entity e = decoys.get(i);
            if (e.isScheduledForRemoval()) continue;
            DecoyComponent decoy = decoyMapper.get(e);
            decoy.age += dt;
            ballistics.getTidalField().accelAt(decoy.pos.x, decoy.pos.y, gravity);
            decoy.vel.mulAdd(gravity, dt);
            decoy.pos.mulAdd(decoy.vel, dt);
            if (decoy.age >= decoy.lifetime) doomed.add(e);
        }
        for (int i = 0; i < missiles.size(); i++) {
            Entity e = missiles.get(i);
            if (e.isScheduledForRemoval()) continue;
            if (fly(e, missileMapper.get(e), projectileMapper.get(e), dt)) doomed.add(e);
        }
        for (int i = 0; i < doomed.size; i++) engine.removeEntity(doomed.get(i));
        doomed.clear();
    }

    /** One tick of one missile. Returns true if it detonated. */
    private boolean fly(Entity e, MissileComponent m, ProjectileComponent p, float dt) {
        MissileDef def = m.def;
        m.throttle = 0f;
        p.mass = m.mass();
        if (p.age < def.armingDelay && !m.guidanceCut) {
            m.phase = MissileComponent.Phase.LAUNCH;
            return false;
        }
        if (m.guidanceCut) {
            m.phase = MissileComponent.Phase.COAST;
            updateSignature(e, m);
            return false;
        }

        boolean estimate = seek(e, m, p, dt);
        if (estimate) {
            m.phase = MissileComponent.Phase.TERMINAL;
        } else if (datalink(m)) {
            m.phase = MissileComponent.Phase.MIDCOURSE;
            estimate = true;
        } else if (m.seekerTrack.fixes > 0 && m.sinceSeekerFix < def.memoryTime) {
            m.seekerTrack.positionAt(m.sinceSeekerFix, estPos);
            m.seekerTrack.velocityAt(m.sinceSeekerFix, estVel);
            estAcc.set(m.seekerTrack.a);
            m.phase = MissileComponent.Phase.TERMINAL;
            estimate = true;
        } else {
            m.phase = MissileComponent.Phase.COAST;
        }

        if (proximityFuse(m, p, dt)) return true;
        if (!estimate || m.propellant <= 0f) {
            if (m.propellant <= 0f) m.phase = MissileComponent.Phase.COAST;
            updateSignature(e, m);
            return false;
        }
        steer(m, p, dt);
        updateSignature(e, m);
        return false;
    }

    /**
     * Looks for the strongest signature inside the seeker's cone and range with a clear line of
     * sight, and folds a fix into the seeker's track. Returns true and fills the estimate on a lock.
     */
    private boolean seek(Entity self, MissileComponent m, ProjectileComponent p, float dt) {
        MissileDef.SeekerDef seeker = m.def.seeker;
        m.sinceSeekerFix += dt;
        if (seeker == null || seeker.type == MissileDef.SeekerType.COMMAND) return false;
        float range2 = seeker.range * seeker.range;
        float halfFov = 0.5f * seeker.fovDeg * MathUtils.degreesToRadians;
        Entity best = null;
        float bestScore = 0f;
        // A lock on a ship it spares is let go, so the proximity fuse never fires on it
        if (m.seekerLock != null && m.seekerLock != m.target && spares(m, m.seekerLock)) {
            m.seekerLock = null;
            m.seekerTrack.reset();
        }
        for (int i = 0; i < contacts.size(); i++) {
            Entity c = contacts.get(i);
            if (c == self || c == m.launcher || c.isScheduledForRemoval() || missileMapper.has(c)) continue;
            if (c != m.target && spares(m, c)) continue;
            DecoyComponent decoy = decoyMapper.get(c);
            if (decoy != null && decoy.owner == m.launcher) continue;
            if (!ContactState.read(c, pos, vel)) continue;
            d.set(pos).sub(p.pos);
            if (d.len2() > range2) continue;
            if (Math.abs(WeaponControlSystem.wrap(d.angleRad() - m.heading)) > halfFov) continue;
            SignatureComponent sig = signatureMapper.get(c);
            float score = seeker.type == MissileDef.SeekerType.THERMAL ? sig.thermal : sig.radar;
            if (c == m.target) score *= 1f + seeker.discrimination;
            if (score <= bestScore || !clearLineOfSight(p.pos, pos)) continue;
            best = c;
            bestScore = score;
        }
        if (best == null) return false;
        if (best != m.seekerLock) {
            m.seekerLock = best;
            m.seekerTrack.reset();
        }
        ContactState.read(best, pos, vel);
        float since = m.seekerTrack.fixes == 0 ? dt : Math.max(m.sinceSeekerFix, 1e-3f);
        m.seekerTrack.update(pos.x, pos.y, since, sensors.getClock(), 0f);
        m.sinceSeekerFix = 0f;
        estPos.set(m.seekerTrack.p);
        estVel.set(m.seekerTrack.v);
        estAcc.set(m.seekerTrack.a);
        return true;
    }

    private boolean spares(MissileComponent m, Entity c) {
        return spared != null && m.launcher != null && spared.holds(m.launcher, c);
    }

    /** Asteroids block seekers; ships do not (the seeker would lock them instead). */
    private boolean clearLineOfSight(Vector2 from, Vector2 to) {
        blocked[0] = false;
        physics.rayCast(from.x, from.y, to.x, to.y, (Fixture fixture, float px, float py, float nx, float ny, float f) -> {
            if ((fixture.getFilterData().categoryBits & CollisionCategories.CATEGORY_ASTEROID) == 0) return -1f;
            blocked[0] = true;
            return 0f;
        });
        return !blocked[0];
    }

    /** The launcher's own track on the target, while its weapon system works. */
    private boolean datalink(MissileComponent m) {
        if (m.launcher == null || m.launcher.isScheduledForRemoval() || m.target == null) return false;
        if (m.datalinkSubsystem != null) {
            SubsystemComponent s = subsystemMapper.get(m.datalinkSubsystem);
            if (s != null && (!s.isOperational || s.isDisabled())) return false;
        }
        SensorComponent sensor = sensorMapper.get(m.launcher);
        Track track = sensor == null ? null : sensor.trackFor(m.target);
        if (track == null) return false;
        float age = sensors.ageOf(track);
        track.positionAt(age, estPos);
        track.velocityAt(age, estVel);
        estAcc.set(track.a);
        return true;
    }

    /** Fires the warhead if the missile will pass within fuse range of its lock this tick. */
    private boolean proximityFuse(MissileComponent m, ProjectileComponent p, float dt) {
        Fuse fuse = m.def.fuse;
        if (fuse == null || fuse.type != Fuse.Type.PROXIMITY || m.def.warhead == null) return false;
        Entity victim = m.seekerLock != null && !m.seekerLock.isScheduledForRemoval() ? m.seekerLock : m.target;
        if (victim == null || victim.isScheduledForRemoval() || !ContactState.read(victim, pos, vel)) return false;
        SignatureComponent sig = signatureMapper.get(victim);
        float reach = fuse.radius + (sig == null ? 0f : sig.radius);
        float rx = pos.x - p.pos.x, ry = pos.y - p.pos.y;
        float wx = vel.x - p.vel.x, wy = vel.y - p.vel.y;
        float w2 = wx * wx + wy * wy;
        float t = w2 > 1e-6f ? MathUtils.clamp(-(rx * wx + ry * wy) / w2, 0f, dt) : 0f;
        float mx = rx + wx * t, my = ry + wy * t;
        if (mx * mx + my * my > reach * reach) return false;

        DetonationEvent boom = new DetonationEvent();
        boom.worldPoint.set(p.pos).mulAdd(p.vel, t);
        boom.velocity.set(p.vel);
        boom.warhead = defs.warhead(m.def.warhead);
        boom.owner = m.launcher;
        boom.target = victim;
        boom.aimedAt = p.aimedAt;
        boom.pointDefense = p.pointDefense;
        boom.tick = events.tick;
        boom.sinceFired = ballistics.getClock() - p.firedAt;
        events.pendingDetonations.add(boom);
        return true;
    }

    private void steer(MissileComponent m, ProjectileComponent p, float dt) {
        MissileDef def = m.def;
        float mass = m.mass();
        float aMax = def.maxThrust / mass;
        ballistics.getTidalField().accelAt(p.pos.x, p.pos.y, gravity);

        d.set(estPos).sub(p.pos);
        float dist = Math.max(d.len(), 1e-3f);
        float ux = estVel.x - p.vel.x, uy = estVel.y - p.vel.y;
        float closing = -(d.x * ux + d.y * uy) / dist;
        float tGo = closing > MIN_CLOSING ? dist / closing : (float) Math.sqrt(2f * dist / Math.max(aMax, 1e-3f));
        tGo = Math.max(tGo, 0.05f);

        // Zero-effort miss, and the part of it across the line of sight.
        float kx = estAcc.x - gravity.x, ky = estAcc.y - gravity.y;
        zem.set(d.x + ux * tGo + 0.5f * kx * tGo * tGo, d.y + uy * tGo + 0.5f * ky * tGo * tGo);
        float lx = d.x / dist, ly = d.y / dist;
        float along = zem.x * lx + zem.y * ly;
        float gain = def.navGain / (tGo * tGo);
        cmd.set((zem.x - along * lx) * gain, (zem.y - along * ly) * gain);
        // Close along the line of sight while fuel above the reserve lasts, or always if opening.
        boolean reserve = m.propellant > def.terminalReserve * def.propellantMass;
        if (reserve || closing < 0f) cmd.add(lx * def.closingBurn, ly * def.closingBurn);
        float want = cmd.len();
        if (want < 1e-3f) return;

        float desired = cmd.angleRad();
        float turn = def.turnRateDeg * MathUtils.degreesToRadians * dt;
        m.heading = WeaponControlSystem.wrap(m.heading
                + MathUtils.clamp(WeaponControlSystem.wrap(desired - m.heading), -turn, turn));
        float align = Math.max(0f, MathUtils.cos(m.heading - desired));
        m.throttle = Math.min(want / aMax, 1f) * align;
        if (m.throttle <= 0f) return;

        float thrust = m.throttle * def.maxThrust;
        float burn = thrust / def.exhaustVelocity * dt;
        if (burn > m.propellant) {
            thrust *= m.propellant / burn;
            m.throttle *= m.propellant / burn;
            burn = m.propellant;
        }
        float dv = thrust / mass * dt;
        p.vel.add(MathUtils.cos(m.heading) * dv, MathUtils.sin(m.heading) * dv);
        m.propellant -= burn;
        p.mass = m.mass();
    }

    private void updateSignature(Entity e, MissileComponent m) {
        SignatureComponent sig = signatureMapper.get(e);
        if (sig != null) sig.thermal = MISSILE_THERMAL + PLUME_THERMAL * m.throttle;
    }

    /** Seeker tracks and decoys are in the bubble frame; ballistics moves the missiles themselves. */
    private void onWorldShift(Signal<WorldShiftEvent> signal, WorldShiftEvent shift) {
        if (missiles == null) return;
        for (int i = 0; i < missiles.size(); i++) {
            Track t = missileMapper.get(missiles.get(i)).seekerTrack;
            t.p.sub(shift.deltaX, shift.deltaY);
            t.v.sub(shift.deltaVx, shift.deltaVy);
        }
        for (int i = 0; i < decoys.size(); i++) {
            DecoyComponent decoy = decoyMapper.get(decoys.get(i));
            decoy.pos.sub(shift.deltaX, shift.deltaY);
            decoy.vel.sub(shift.deltaVx, shift.deltaVy);
        }
        for (int i = 0; i < events.pendingDetonations.size; i++) {
            DetonationEvent boom = events.pendingDetonations.get(i);
            boom.worldPoint.sub(shift.deltaX, shift.deltaY);
            boom.velocity.sub(shift.deltaVx, shift.deltaVy);
        }
    }
}
