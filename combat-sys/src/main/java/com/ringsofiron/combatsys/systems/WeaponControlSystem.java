package com.ringsofiron.combatsys.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.WeaponPower;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.ShotFiredEvent;
import com.ringsofiron.combatsys.fire.FiringSolutionSolver;
import com.ringsofiron.combatsys.fire.HitChance;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Turret fire control (combat-sys/Design.md 3.3). Per mount, per tick: pick what to engage, ask
 * {@link FiringSolutionSolver} for the lead on the ship's sensor track of it, slew the barrel
 * toward that within the firing arc, and fire when loaded, the weapon's system works, and the
 * barrel's pointing error at that range is inside the target, and the ship's power grid gives
 * the shot its energy ({@link #setPower}). Gunnery skill sets aim jitter and
 * how long the gunner takes to engage a new target. Rounds inherit the hardpoint's velocity
 * (spin included) and push the ship back.
 *
 * Gunners walk their bursts in: their aim opens with a skill-scaled bias, and each shot takes
 * out part of the miss they saw on the last one.
 *
 * Point-defense mounts pick their own target: the incoming missile their ship tracks that will
 * pass closest soonest. Launchers throw a missile at the target's track when loaded; guidance
 * takes it from there. Beams point straight at the track (light needs no lead) and burn for a
 * pulse while the gunner fights to hold the spot steady; {@link BeamSystem} finds what they touch.
 *
 * A weapon's max range is a guide, not a limit (but for point defense, whose threats it picks
 * inside it). The trigger fires a gun at a target at any range the round can fly; a gunner firing
 * at will opens up only inside the mount's effective range on it ({@link #effectiveRange}), where
 * {@link HitChance} gives a shot a fair chance against the target dodging.
 *
 * A gunner firing at will holds fire while a ship or station other than the target lies on the
 * line to the aim point and {@link #setHoldFire the game's rule} says to (round 7: a pirate put
 * some 2,200 rounds into a surrendered wingmate between it and the player). A pulled trigger fires
 * regardless, but only once the barrel is on: a turret pulled out of reach of its target, or with
 * nothing to aim at, holds (a starter corvette's laser, out of range, burned off its beam into
 * empty space). Only a spinal gun with no target fires straight down its barrel, where the ship
 * points it.
 *
 * A mount aimed by hand ({@link WeaponMountComponent#manualAim}) slews onto its aim point instead
 * of a target, with no lead, and its trigger fires once the barrel is there.
 *
 * A launcher with a magazine takes a missile from it each launch and is silent once it is empty. On
 * the trigger it launches as fast as it reloads; firing at will, no faster than one every
 * {@link #AT_WILL_LAUNCH_GAP}, so a gunner doesn't empty it in one go.
 * Each missile of a run of launches is given its own approach angle
 * ({@link MissileComponent#approachAngle}), so a salvo spreads and comes in from several sides.
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}, after {@link SensorTrackingSystem}.
 */
public class WeaponControlSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY = Family.all(WeaponsComponent.class, PhysicsBodyComponent.class).get();
    /** Base seconds before a gunner engages a new target, and the extra for a green one. */
    public static final float REACTION_BASE = 0.2f, REACTION_GREEN = 1.0f;
    /** Point-defense gunners are drilled for it, and their computers do most of the work. */
    public static final float PD_REACTION_BASE = 0.05f, PD_REACTION_GREEN = 0.3f;
    /** Missiles that pass further than this from the ship are no point-defense threat, meters. */
    public static final float PD_THREAT_MISS = 300f;
    /** Size assumed for a contact with no signature, meters. */
    private static final float DEFAULT_RADIUS = 5f;
    /** A gunner's opening bias on a new target, in spreads (1 sigma) at gunnery skill 0.5. */
    public static final float BIAS_SPREADS = 4f;
    /** Share of the last shot's miss a green and a veteran gunner correct on the next. */
    public static final float WALK_GREEN = 0.3f, WALK_VETERAN = 0.7f;
    /** A pause longer than this, seconds (or three cooldowns), and the gunner starts over. */
    public static final float BURST_GAP = 2f;
    /** Reach of a beam whose data gives no max range, meters. */
    public static final float DEFAULT_BEAM_RANGE = 3000f;
    /** How fast a beam's wander changes, seconds. */
    public static final float BEAM_WANDER_TIME = 0.5f;
    /** A hand-aimed barrel this close to its aim point fires on the trigger, radians, at the least. */
    public static final float HAND_AIM_TOLERANCE = 0.01f;
    /** Seconds a launcher firing at will waits between missiles, however fast it reloads. */
    public static final float AT_WILL_LAUNCH_GAP = 8f;
    /** Seconds a mount keeps its effective range on a target before working it out again. */
    public static final float RANGE_REFRESH = 1f;
    /** Step between the approach angles of a salvo's missiles, radians: 0, +1, -1, +2, -2 steps. */
    public static final float SALVO_SPREAD = 15f * MathUtils.degreesToRadians;

    private final ComponentMapper<WeaponsComponent> weaponsMapper = ComponentMapper.getFor(WeaponsComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<SensorComponent> sensorMapper = ComponentMapper.getFor(SensorComponent.class);
    private final ComponentMapper<SignatureComponent> signatureMapper = ComponentMapper.getFor(SignatureComponent.class);
    private final ComponentMapper<MissileComponent> missileMapper = ComponentMapper.getFor(MissileComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper = ComponentMapper.getFor(SubsystemComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusterMapper = ComponentMapper.getFor(ThrusterComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> targetBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final PhysicsManager physics;
    private final CombatEvents events;
    private final CombatDefs defs;
    private final BallisticsSystem ballistics;
    private final MissileGuidanceSystem missiles;
    private final SensorTrackingSystem sensors;
    private final RandomXS128 rng;
    private final FiringSolutionSolver solver = new FiringSolutionSolver();
    private WeaponPower power;
    private HoldFire holdFire;
    private HoldFire spared;
    private ImmutableArray<Entity> ships;
    private final Entity[] blocker = new Entity[1];

    /** Whether {@code shooter}, firing at will, holds fire with {@code blocker} on its line of fire. */
    public interface HoldFire {
        boolean holds(Entity shooter, Entity blocker);
    }

    private final ShotFiredEvent shot = new ShotFiredEvent();
    private final Vector2 muzzle = new Vector2();
    private final Vector2 muzzleVel = new Vector2();
    private final Vector2 shipPos = new Vector2();
    private final Vector2 shipVel = new Vector2();
    private final Vector2 estPos = new Vector2();
    private final Vector2 estVel = new Vector2();
    private final Vector2 dir = new Vector2();
    private final Vector2 backstop = new Vector2();

    public WeaponControlSystem(PhysicsManager physics, CombatEvents events, CombatDefs defs, BallisticsSystem ballistics,
                               MissileGuidanceSystem missiles, SensorTrackingSystem sensors, long seed) {
        this.physics = physics;
        this.events = events;
        this.defs = defs;
        this.ballistics = ballistics;
        this.missiles = missiles;
        this.sensors = sensors;
        this.rng = new RandomXS128(seed);
    }

    /** Starts the fire-control noise over from {@code seed}, as if built with it (a new game's seed). */
    public void reseed(long seed) {
        rng.setSeed(seed);
    }

    /** Where weapons draw their energy; null (the default) gives them all they want. */
    public void setPower(WeaponPower power) {
        this.power = power;
    }

    /** Who holds fire for a ship in the way; null (the default) fires through anything. */
    public void setHoldFire(HoldFire holdFire) {
        this.holdFire = holdFire;
    }

    /**
     * Who a mount firing at will never takes as its target (asked as {@code holds(shooter, target)}),
     * though its trigger still fires on it; null (the default) spares nobody.
     */
    public void setSpared(HoldFire spared) {
        this.spared = spared;
    }

    /**
     * True if, firing at will, {@code ship} holds fire because a ship or station other than
     * {@code target} lies between {@code from} and {@code to}. Behind a missile, the ship that
     * launched it is fair game.
     */
    private boolean blocked(Entity ship, Entity target, Vector2 from, Vector2 to) {
        if (holdFire == null) return false;
        MissileComponent missile = target == null ? null : missileMapper.get(target);
        Entity launcher = missile == null ? null : missile.launcher;
        blocker[0] = null;
        physics.rayCast(from.x, from.y, to.x, to.y, (fixture, px, py, nx, ny, f) -> {
            short category = fixture.getFilterData().categoryBits;
            if ((category & (CollisionCategories.CATEGORY_SHIP | CollisionCategories.CATEGORY_STATION)) == 0) return -1f;
            Object owner = fixture.getBody().getUserData();
            if (!(owner instanceof Entity) || owner == ship || owner == target || owner == launcher) return -1f;
            if (!holdFire.holds(ship, (Entity) owner)) return -1f;
            blocker[0] = (Entity) owner;
            return 0f;
        });
        return blocker[0] != null;
    }

    /**
     * Where a shot fired at will must not find a ship or station in the way: the intercept point,
     * or for point defense, which misses most of its small, fast targets, as far as its rounds fly
     * (the player's misses past a missile went on into Highport and the haulers by it).
     */
    private Vector2 lineOfFire(WeaponMountComponent mount, ProjectileDef round, float maxTime) {
        if (!mount.def.pointDefense) return mount.solution.interceptPoint;
        return backstop.set(mount.solution.aimDir).nor().scl(round.muzzleSpeed * maxTime).add(muzzle);
    }

    /** Takes the energy for one shot: {@code power_draw * cooldown}, all or nothing. */
    private boolean powerShot(WeaponMountComponent mount) {
        double joules = (double) mount.def.powerDraw * mount.def.cooldown;
        return power == null || joules <= 0 || power.tryDraw(mount, joules);
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(FAMILY);
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float dt) {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            if (ship.isScheduledForRemoval()) continue;
            Body body = bodyMapper.get(ship).body;
            if (body == null) continue;
            WeaponsComponent weapons = weaponsMapper.get(ship);
            SensorComponent sensor = sensorMapper.get(ship);
            for (int m = 0; m < weapons.mounts.size; m++) {
                control(ship, body, sensor, weapons.mounts.get(m), dt);
            }
        }
    }

    /** Whether the mount can work: not under a refit, and no system or one that is operational. */
    public boolean available(WeaponMountComponent mount) {
        if (mount.underRefit) return false;
        if (mount.subsystem == null) return true;
        SubsystemComponent s = subsystemMapper.get(mount.subsystem);
        return s == null || (s.isOperational && !s.isDisabled());
    }

    private void control(Entity ship, Body body, SensorComponent sensor, WeaponMountComponent mount, float dt) {
        WeaponDef def = mount.def;
        if (def == null) return;
        mount.cooldown = Math.max(0f, mount.cooldown - dt);
        fillMagazine(mount);

        // Point defense picks its own missiles, unless it has been given a target or is aimed by hand
        boolean hand = mount.manualAim && def.mount != WeaponDef.MountType.LAUNCHER;
        boolean hunting = def.pointDefense && mount.target == null && !hand;
        Entity target = hunting ? pickMissileThreat(ship, body, sensor, def)
                : (mount.target != null ? mount.target : sensor == null ? null : sensor.lockedTarget);
        if (hand) target = null;
        if (target != null && !mount.trigger && spared != null && spared.holds(ship, target)) target = null;
        if (target != mount.lastTarget) {
            mount.engagedFor = 0f;
            mount.lastTarget = target;
            mount.biasSet = false;
        }
        mount.engaged = target;
        mount.engagedFor += dt;
        mount.rangedAge += dt;
        mount.sinceShot += dt;
        mount.beamOn = false;
        Track track = sensor == null ? null : sensor.trackFor(target);
        boolean available = available(mount);

        CombatPhysics.toWorld(body, mount.hardpoint.x, mount.hardpoint.y, muzzle);
        CombatPhysics.velocityAt(body, muzzle.x, muzzle.y, muzzleVel);
        mount.muzzle.set(muzzle);
        float bodyAngle = body.getAngle();

        if (def.mount == WeaponDef.MountType.LAUNCHER) {
            launcher(ship, body, mount, target, track, available, bodyAngle);
            mount.trigger = false;
            return;
        }
        if (def.mount == WeaponDef.MountType.BEAM) {
            beam(ship, mount, target, track, available, bodyAngle, dt, hand);
            mount.trigger = false;
            return;
        }

        ProjectileDef round = def.projectile == null ? null : defs.projectiles.get(def.projectile);
        if (round == null) return;
        mount.solution.invalidate();
        // Point defense picks its threats inside its range; any other gun aims as far as its round flies
        float maxTime = round.maxLifetime;
        if (hunting && def.maxRange > 0f) maxTime = Math.min(maxTime, def.maxRange / round.muzzleSpeed);
        if (hand) {
            aimByHand(mount, round.muzzleSpeed);
        } else if (track != null) {
            float age = sensors.ageOf(track);
            track.positionAt(age, estPos);
            track.velocityAt(age, estVel);
            solver.solve(muzzle.x, muzzle.y, muzzleVel.x, muzzleVel.y, estPos.x, estPos.y, estVel.x, estVel.y,
                    track.a.x, track.a.y, round.muzzleSpeed, maxTime, ballistics.getTidalField(), mount.solution);
        }

        // Slew toward the solution, or back to rest with nothing to shoot at.
        float want = mount.solution.valid ? mount.solution.aimDir.angleRad() - bodyAngle : mount.facing;
        if (def.mount != WeaponDef.MountType.SPINAL) slew(mount, want, dt);
        else mount.aim = mount.facing;

        float barrel = bodyAngle + mount.aim;
        boolean onTarget = false;
        if (mount.solution.valid) {
            float error = Math.abs(wrap(barrel - mount.solution.aimDir.angleRad()));
            float range = mount.solution.timeToImpact * round.muzzleSpeed;
            onTarget = hand ? error < handTolerance(def)
                    : error * range < radiusOf(target) + def.spreadRad * range;
        }
        mount.onTarget = onTarget;

        boolean atWill = !mount.trigger;
        boolean wantsFire = mount.trigger
                ? (onTarget || straightDownTheBarrel(mount, track, hand))
                : (mount.weaponsFree && !hand && onTarget && mount.engagedFor >= reactionTime(mount)
                        && (def.pointDefense || worthAShot(mount, target, muzzle.dst(mount.solution.interceptPoint))));
        mount.trigger = false;
        if (!wantsFire || !available || mount.cooldown > 0f) return;
        if (atWill && blocked(ship, target, muzzle, lineOfFire(mount, round, maxTime))) return;
        if (!powerShot(mount)) return;

        float green = 1.5f - MathUtils.clamp(mount.gunnerySkill, 0f, 1f);
        if (!mount.biasSet || mount.sinceShot > Math.max(BURST_GAP, 3f * def.cooldown)) {
            mount.aimBias = (float) rng.nextGaussian() * def.spreadRad * BIAS_SPREADS * green;
            mount.biasSet = true;
        }
        float error = mount.aimBias + (float) rng.nextGaussian() * def.spreadRad * green;
        // Burst walking: the gunner sees where this one goes and corrects part of it on the next.
        mount.aimBias -= walkShare(mount) * error;
        dir.set(MathUtils.cos(barrel + error), MathUtils.sin(barrel + error));
        float x = muzzle.x + dir.x * def.muzzleOffset, y = muzzle.y + dir.y * def.muzzleOffset;
        Entity e = ballistics.spawn(round, round.mass, x, y,
                muzzleVel.x + dir.x * round.muzzleSpeed, muzzleVel.y + dir.y * round.muzzleSpeed, ship);
        // Recoil at the hardpoint: off-centre mounts torque the ship.
        float recoil = round.mass * round.muzzleSpeed;
        CombatPhysics.applyImpulseAt(body, -dir.x * recoil, -dir.y * recoil, muzzle.x, muzzle.y);
        fired(ship, mount, e, x, y);
    }

    /**
     * A beam: aim straight at where the track says the target is now, start a pulse when the
     * barrel is on it (or on the trigger), and keep burning for the pulse while the system works.
     * The beam's own wander follows gunnery skill.
     */
    private void beam(Entity ship, WeaponMountComponent mount, Entity target, Track track, boolean available,
                      float bodyAngle, float dt, boolean hand) {
        WeaponDef def = mount.def;
        mount.solution.invalidate();
        float reach = def.maxRange > 0f ? def.maxRange : DEFAULT_BEAM_RANGE;
        float dist = 0f;
        if (hand) {
            aimByHand(mount, 0f);
            dist = mount.solution.valid ? Math.min(estPos.set(mount.aimPoint).dst(muzzle), reach) : 0f;
        } else if (track != null) {
            track.positionAt(sensors.ageOf(track), estPos);
            dir.set(estPos).sub(muzzle);
            dist = dir.len();
            // Past its reach the beam still points at the target, and burns short of it on the trigger
            if (dist > 1e-3f) {
                mount.solution.aimDir.set(dir).scl(1f / dist);
                mount.solution.interceptPoint.set(estPos);
                mount.solution.timeToImpact = 0f;
                mount.solution.valid = true;
            }
        }
        float want = mount.solution.valid ? mount.solution.aimDir.angleRad() - bodyAngle : mount.facing;
        if (def.mount != WeaponDef.MountType.SPINAL) slew(mount, want, dt);
        float barrel = bodyAngle + mount.aim;
        float error = Math.abs(wrap(barrel - mount.solution.aimDir.angleRad()));
        boolean onTarget = mount.solution.valid
                && (hand ? error < handTolerance(def) : error * dist < radiusOf(target));
        mount.onTarget = onTarget;

        if (!available) {
            mount.beamTime = 0f;
            return;
        }
        // The beam burns as hard as the power it gets this tick.
        double needed = (double) def.powerDraw * dt;
        boolean metered = power != null && needed > 0;
        if (mount.beamTime <= 0f) {
            boolean wantsFire = mount.trigger
                    ? (onTarget || straightDownTheBarrel(mount, track, hand))
                    : (mount.weaponsFree && !hand && onTarget && mount.engagedFor >= reactionTime(mount)
                            && worthAShot(mount, target, dist));
            if (!wantsFire || mount.cooldown > 0f) return;
            if (!mount.trigger && blocked(ship, target, muzzle, estPos)) return;
            // A pulse only starts with power to start it.
            double got = metered ? power.draw(mount, needed) : needed;
            if (got <= 0) return;
            mount.beamStrength = metered ? (float) (got / needed) : 1f;
            metered = false;
            mount.beamTime = def.beamDuration;
            mount.aimBias = 0f;
            dir.set(MathUtils.cos(barrel), MathUtils.sin(barrel));
            fired(ship, mount, null, muzzle.x, muzzle.y);
        }
        // Holding a beam steady is a skill: its spot wanders, and a green gunner's wanders more.
        float green = 1.5f - MathUtils.clamp(mount.gunnerySkill, 0f, 1f);
        float k = Math.min(1f, dt / BEAM_WANDER_TIME);
        mount.aimBias += -mount.aimBias * k
                + (float) rng.nextGaussian() * def.spreadRad * green * (float) Math.sqrt(2f * k);
        float a = barrel + mount.aimBias;
        mount.beamDir.set(MathUtils.cos(a), MathUtils.sin(a));
        if (metered) mount.beamStrength = (float) (power.draw(mount, needed) / needed);
        else if (power == null || needed <= 0) mount.beamStrength = 1f;
        mount.beamOn = true;
        mount.beamTime = Math.max(0f, mount.beamTime - dt);
    }

    private void launcher(Entity ship, Body body, WeaponMountComponent mount, Entity target, Track track,
                          boolean available, float bodyAngle) {
        WeaponDef def = mount.def;
        MissileDef missile = def.missile == null ? null : defs.missiles.get(def.missile);
        mount.solution.invalidate();
        mount.onTarget = false;
        if (missile == null || missiles == null) return;
        boolean inRange = false;
        if (track != null) {
            track.positionAt(sensors.ageOf(track), estPos);
            float reach = def.maxRange > 0f ? def.maxRange : missile.seeker.range;
            inRange = estPos.dst2(muzzle) < reach * reach;
            mount.solution.interceptPoint.set(estPos);
            mount.solution.valid = true;
        }
        // At will a gunner spaces its missiles out; the trigger throws them as fast as the launcher reloads
        boolean wantsFire = mount.trigger
                ? track != null
                : (mount.weaponsFree && inRange && mount.engagedFor >= reactionTime(mount)
                        && mount.sinceShot >= AT_WILL_LAUNCH_GAP);
        boolean loaded = def.magazine <= 0 || mount.ammo > 0;
        if (!wantsFire || !available || !loaded || mount.cooldown > 0f || !powerShot(mount)) return;

        // Throw it clear toward the target (or along the rail), within the launcher's arc.
        float want = track != null ? dir.set(estPos).sub(muzzle).angleRad() - bodyAngle : mount.facing;
        mount.aim = mount.facing + clampToArc(wrap(want - mount.facing), def);
        float heading = bodyAngle + mount.aim;
        dir.set(MathUtils.cos(heading), MathUtils.sin(heading));
        float x = muzzle.x + dir.x * def.muzzleOffset, y = muzzle.y + dir.y * def.muzzleOffset;
        float vx = muzzleVel.x + dir.x * missile.ejectSpeed, vy = muzzleVel.y + dir.y * missile.ejectSpeed;
        Entity e = missiles.launch(missile, ship, mount.subsystem, target, x, y, vx, vy, heading);
        if (def.magazine > 0) mount.ammo--;
        // One of a salvo: each missile comes in on its own side of the line to the target
        MissileComponent flying = e == null ? null : missileMapper.get(e);
        if (flying != null) flying.approachAngle = approachAngle(mount.shotsFired);
        float kick = (missile.dryMass + missile.propellantMass) * missile.ejectSpeed;
        CombatPhysics.applyImpulseAt(body, -dir.x * kick, -dir.y * kick, muzzle.x, muzzle.y);
        fired(ship, mount, e, x, y);
    }

    private void fired(Entity ship, WeaponMountComponent mount, Entity round, float x, float y) {
        mount.cooldown = mount.def.cooldown;
        mount.shotsFired++;
        mount.sinceShot = 0f;
        shot.shooter = ship;
        shot.round = round;
        shot.weaponDefId = mount.def.id;
        shot.target = mount.engaged;
        ProjectileComponent p = round == null ? null : round.getComponent(ProjectileComponent.class);
        if (p != null) {
            p.aimedAt = mount.engaged;
            p.pointDefense = mount.def.pointDefense;
        }
        shot.muzzlePoint.set(x, y);
        shot.direction.set(dir);
        events.shots.dispatch(shot);
    }

    /**
     * The tracked missile, not this ship's own, that will pass within {@link #PD_THREAT_MISS}
     * soonest, inside the mount's reach. Null when nothing threatens.
     */
    private Entity pickMissileThreat(Entity ship, Body body, SensorComponent sensor, WeaponDef def) {
        if (sensor == null) return null;
        PhysicsManager.getPosition(body, shipPos);
        PhysicsManager.getVelocity(body, shipVel);
        ProjectileDef round = def.projectile == null ? null : defs.projectiles.get(def.projectile);
        float reach = def.maxRange > 0f ? def.maxRange
                : round == null ? 0f : round.muzzleSpeed * round.maxLifetime;
        Entity best = null;
        float bestTime = Float.MAX_VALUE;
        for (int i = 0; i < sensor.tracks.size; i++) {
            Track t = sensor.tracks.get(i);
            MissileComponent m = missileMapper.get(t.target);
            if (m == null || m.launcher == ship) continue;
            float age = sensors.ageOf(t);
            t.positionAt(age, estPos).sub(shipPos);
            t.velocityAt(age, estVel).sub(shipVel);
            float dist = estPos.len();
            if (dist > reach) continue;
            float v2 = estVel.len2();
            float tca = v2 > 1e-6f ? -estPos.dot(estVel) / v2 : 0f;
            if (tca < 0f) continue; // already going away
            float missX = estPos.x + estVel.x * tca, missY = estPos.y + estVel.y * tca;
            if (missX * missX + missY * missY > PD_THREAT_MISS * PD_THREAT_MISS && dist > PD_THREAT_MISS) continue;
            if (tca < bestTime) {
                bestTime = tca;
                best = t.target;
            }
        }
        return best;
    }

    /** Fills a launcher's magazine the first time it is seen (a new mount, or a save from before magazines). */
    static void fillMagazine(WeaponMountComponent mount) {
        int magazine = mount.def.magazine;
        if (magazine <= 0) return;
        if (mount.ammo < 0 || mount.ammo > magazine) mount.ammo = magazine;
    }

    /** Refills a launcher to its magazine, as a station's yard does. Returns how many it took. */
    public static int restock(WeaponMountComponent mount, int most) {
        if (mount.def == null || mount.def.magazine <= 0) return 0;
        fillMagazine(mount);
        int add = Math.min(most, mount.def.magazine - mount.ammo);
        mount.ammo += add;
        return add;
    }

    /**
     * The approach angle of the {@code n}th missile a launcher throws, radians off the line to the
     * target: 0, then a step to one side, then the other, then two steps each way, and round again.
     */
    public static float approachAngle(int n) {
        switch (n % 5) {
            case 1: return SALVO_SPREAD;
            case 2: return -SALVO_SPREAD;
            case 3: return 2f * SALVO_SPREAD;
            case 4: return -2f * SALVO_SPREAD;
            default: return 0f;
        }
    }

    /**
     * Points a hand-aimed mount's solution at its aim point, with no lead. {@code speed} gives the
     * time to impact for a gun; 0 (a beam) gives none.
     */
    private void aimByHand(WeaponMountComponent mount, float speed) {
        dir.set(mount.aimPoint).sub(mount.muzzle);
        float dist = dir.len();
        if (dist < 1e-3f) return;
        mount.solution.aimDir.set(dir).scl(1f / dist);
        mount.solution.interceptPoint.set(mount.aimPoint);
        mount.solution.timeToImpact = speed > 0f ? dist / speed : 0f;
        mount.solution.valid = true;
    }

    private static float handTolerance(WeaponDef def) {
        return Math.max(HAND_AIM_TOLERANCE, 3f * def.spreadRad);
    }

    /**
     * A pulled trigger with nothing to aim at fires only a spinal gun, along the ship's nose. A
     * turret holds: its barrel rests to the side, and would throw the shot away.
     */
    private static boolean straightDownTheBarrel(WeaponMountComponent mount, Track track, boolean hand) {
        return mount.def.mount == WeaponDef.MountType.SPINAL && track == null && !hand;
    }

    /** Share of the last shot's miss the gunner corrects on the next. */
    static float walkShare(WeaponMountComponent mount) {
        return MathUtils.lerp(WALK_GREEN, WALK_VETERAN, MathUtils.clamp(mount.gunnerySkill, 0f, 1f));
    }

    private float reactionTime(WeaponMountComponent mount) {
        float green = 1f - MathUtils.clamp(mount.gunnerySkill, 0f, 1f);
        return mount.def.pointDefense ? PD_REACTION_BASE + PD_REACTION_GREEN * green
                : REACTION_BASE + REACTION_GREEN * green;
    }

    /** Size of {@code target}, meters: its signature's, or a guess with none. */
    public float radiusOf(Entity target) {
        SignatureComponent s = target == null ? null : signatureMapper.get(target);
        return s == null ? DEFAULT_RADIUS : s.radius;
    }

    /**
     * How hard {@code target} can dodge, m/s^2: its strongest thrust over its mass, 0 out of fuel,
     * {@link HitChance#DEFAULT_EVASION} with its drive unknown.
     */
    public float evasionOf(Entity target) {
        ThrusterComponent t = target == null ? null : thrusterMapper.get(target);
        PhysicsBodyComponent pb = target == null ? null : targetBodyMapper.get(target);
        if (t == null || pb == null || pb.body == null) return HitChance.DEFAULT_EVASION;
        if (t.isOutOfFuel()) return 0f;
        float mass = PhysicsManager.getMass(pb.body);
        if (mass <= 0f) return HitChance.DEFAULT_EVASION;
        return Math.max(t.forwardThrust, Math.max(t.reverseThrust, t.lateralThrust)) / mass;
    }

    /** The round {@code def} fires, or null for a beam or launcher. */
    public ProjectileDef roundOf(WeaponDef def) {
        return def == null || def.projectile == null ? null : defs.projectiles.get(def.projectile);
    }

    /** How far a beam of {@code def} reaches, meters. */
    public static float beamReach(WeaponDef def) {
        return def.maxRange > 0f ? def.maxRange : DEFAULT_BEAM_RANGE;
    }

    /** The chance one shot of {@code mount}'s, by its gunner, hits {@code target} at {@code range}, dodging or holding still. */
    public float hitChance(WeaponMountComponent mount, Entity target, float range, boolean dodging) {
        WeaponDef def = mount.def;
        return HitChance.perShot(def, roundOf(def), beamReach(def), mount.gunnerySkill, range,
                radiusOf(target), dodging ? evasionOf(target) : 0f);
    }

    /**
     * How far {@code mount} opens up on {@code target} firing at will: where a shot's chance on it
     * dodging ({@link HitChance#rated}) falls to {@link HitChance#OPEN_FIRE}. Worked out once per target and kept for
     * {@link #RANGE_REFRESH}, as the gunner's skill or the target's drive may change.
     */
    public float effectiveRange(WeaponMountComponent mount, Entity target) {
        if (target == null) return 0f;
        if (mount.rangedFor != target || mount.rangedAge > RANGE_REFRESH) {
            mount.effectiveRange = openingRange(mount, target);
            mount.rangedFor = target;
            mount.rangedAge = 0f;
        }
        return mount.effectiveRange;
    }

    /** {@link #effectiveRange} worked out afresh, and not kept: for weighing a target before taking it. */
    public float openingRange(WeaponMountComponent mount, Entity target) {
        WeaponDef def = mount.def;
        return HitChance.effectiveRange(def, roundOf(def), beamReach(def), mount.gunnerySkill,
                radiusOf(target), evasionOf(target), HitChance.OPEN_FIRE);
    }

    private boolean worthAShot(WeaponMountComponent mount, Entity target, float range) {
        return range <= effectiveRange(mount, target);
    }

    /** Turns the barrel toward body-frame angle {@code want} at the slew rate, inside the arc. */
    static void slew(WeaponMountComponent mount, float want, float dt) {
        WeaponDef def = mount.def;
        float goal = clampToArc(wrap(want - mount.facing), def);
        float now = clampToArc(wrap(mount.aim - mount.facing), def);
        float max = def.slewRateDeg * MathUtils.degreesToRadians * dt;
        // Inside a restricted arc the barrel cannot swing through the dead zone behind it.
        float delta = def.firingArcDeg >= 360f ? wrap(goal - now) : goal - now;
        now += MathUtils.clamp(delta, -max, max);
        mount.aim = wrap(mount.facing + now);
    }

    static float clampToArc(float rel, WeaponDef def) {
        if (def.firingArcDeg >= 360f) return rel;
        float half = 0.5f * def.firingArcDeg * MathUtils.degreesToRadians;
        return MathUtils.clamp(rel, -half, half);
    }

    /** Wraps an angle into [-pi, pi). */
    static float wrap(float a) {
        a = (a + MathUtils.PI) % MathUtils.PI2;
        if (a < 0f) a += MathUtils.PI2;
        return a - MathUtils.PI;
    }
}
