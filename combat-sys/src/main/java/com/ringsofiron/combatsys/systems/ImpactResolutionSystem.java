package com.ringsofiron.combatsys.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.PenetrationResolver;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WarheadDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.combatsys.events.DetonationEvent;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.events.ImpactEvent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;

/**
 * Drains each tick's impacts and detonations in a fixed order and resolves them
 * (combat-sys/Design.md section 5): hull hits into the interior grid through
 * {@link PenetrationResolver}, ricochets and over-penetrating rounds back into space,
 * warheads, and momentum transfer to the struck body.
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}. Add it to the engine after
 * {@link BallisticsSystem} so it resolves hits in the same tick they were found.
 */
public class ImpactResolutionSystem extends EntitySystem implements PhysicsManager.StepListener {
    /** How long a glancing or exiting round ignores the ship it just left, seconds. */
    public static final float EXIT_IGNORE_TIME = 0.1f;
    /** How far outside the hull a ricochet or exiting round reappears, meters. */
    public static final float EXIT_CLEARANCE = 0.5f;
    /** Energy, J, that counts as severity 1 for HUD flashes. */
    public static final float SEVERITY_SCALE = 1e7f;

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final PhysicsManager physics;
    private final CombatEvents events;
    private final CombatDefs defs;
    private final BallisticsSystem ballistics;
    private final PenetrationResolver resolver;
    private final long seed;
    private final ObjectMap<Entity, RandomXS128> rngs = new ObjectMap<>();
    private final ObjectMap<String, ProjectileDef> fragmentDefs = new ObjectMap<>();

    private final Listener<FastPassImpactEvent> fastPassListener = this::onFastPass;
    private ToFloatFunction<Entity> transientMass;
    /** How a ramming body tears into a hull: a blunt kinetic penetrator that never glances off. */
    private final ProjectileDef ramDef = new ProjectileDef();

    private final Array<ImpactEvent> batch = new Array<>();
    private final Array<DetonationEvent> detonationBatch = new Array<>();
    private final PenetrationResolver.Result result = new PenetrationResolver.Result();
    private final DamageReport report = new DamageReport();
    private final HullHitEvent hullHit = new HullHitEvent();
    private final Vector2 localPoint = new Vector2();
    private final Vector2 localNormal = new Vector2();
    private final Vector2 localRel = new Vector2();
    private final Vector2 vOut = new Vector2();
    private final Vector2 tmp = new Vector2();
    private final Vector2 surfaceVel = new Vector2();

    public ImpactResolutionSystem(PhysicsManager physics, CombatEvents events, CombatDefs defs,
                                  BallisticsSystem ballistics, MaterialTable materials, long seed) {
        this.physics = physics;
        this.events = events;
        this.defs = defs;
        this.ballistics = ballistics;
        this.resolver = new PenetrationResolver(materials);
        this.seed = seed;
        ramDef.id = "fast_pass.ram";
        ramDef.damageType = DamageType.KINETIC;
        ramDef.penetrationFactor = 0.5f;
        ramDef.ricochetAngleDeg = 89f;
        ramDef.ricochetRestitution = 0f;
    }

    /** Where crew on a struck tile are found and hurt. Null (the default) means no crew are modelled. */
    public void setCrew(CrewDamageBridge crew) {
        resolver.setCrew(crew);
    }

    /** Hull and armor stats damage is resolved against, e.g. the table loaded from data. */
    public void setMaterials(MaterialTable materials) {
        resolver.setMaterials(materials);
    }

    public PenetrationResolver getResolver() {
        return resolver;
    }

    @Override
    public void addedToEngine(Engine engine) {
        physics.addStepListener(this);
        physics.fastPassImpacts.add(fastPassListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.fastPassImpacts.remove(fastPassListener);
        rngs.clear();
    }

    /**
     * Sets how heavy a fast-pass body is, kg, for ramming damage. Null (the default) treats it as
     * heavy as whatever it hits, so the struck body's mass decides (design 1.4).
     */
    public void setTransientMass(ToFloatFunction<Entity> mass) {
        this.transientMass = mass;
    }

    /** A per-entity mass lookup. */
    public interface ToFloatFunction<T> {
        float apply(T value);
    }

    /**
     * A kinematic transient (a fast pass, over 1 km/s relative) swept through a body. It becomes a
     * ram impact with the lighter body's mass, resolved like a very large slug on the next tick.
     */
    private void onFastPass(Signal<FastPassImpactEvent> signal, FastPassImpactEvent pass) {
        Entity target = pass.target;
        PhysicsBodyComponent bc = target == null ? null : bodyMapper.get(target);
        if (bc == null || bc.body == null || pass.relativeSpeed <= 0f) return;
        float mass = PhysicsManager.getMass(bc.body);
        if (transientMass != null) mass = Math.min(mass, transientMass.apply(pass.transientEntity));
        if (!(mass > 0f)) return;
        ImpactEvent hit = new ImpactEvent();
        hit.target = target;
        hit.owner = pass.transientEntity;
        hit.worldPoint.set(pass.pointX, pass.pointY);
        hit.worldNormal.set(pass.normalX, pass.normalY);
        hit.relVelocity.set(pass.dirX, pass.dirY).scl(pass.relativeSpeed);
        CombatPhysics.velocityAt(bc.body, pass.pointX, pass.pointY, tmp);
        hit.velocity.set(hit.relVelocity).add(tmp);
        hit.mass = mass;
        hit.def = ramDef;
        hit.ram = true;
        hit.tick = events.tick;
        events.pendingImpacts.add(hit);
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float deltaTime) {
        // Resolving can queue more events (fragments hitting next tick go through ballistics),
        // so take this tick's batch first.
        batch.clear();
        batch.addAll(events.pendingImpacts);
        events.pendingImpacts.clear();
        batch.sort((a, b) -> {
            if (a.tick != b.tick) return Long.compare(a.tick, b.tick);
            return uuidOf(a.target).compareTo(uuidOf(b.target));
        });
        for (int i = 0; i < batch.size; i++) {
            resolve(batch.get(i));
            events.impacts.dispatch(batch.get(i));
        }
        batch.clear();

        detonationBatch.clear();
        detonationBatch.addAll(events.pendingDetonations);
        events.pendingDetonations.clear();
        for (int i = 0; i < detonationBatch.size; i++) {
            DetonationEvent d = detonationBatch.get(i);
            throwFragments(d.warhead, d.worldPoint, d.velocity, d.target, d.owner, null);
            events.detonations.dispatch(d);
        }
        detonationBatch.clear();
    }

    private String uuidOf(Entity e) {
        UuidComponent u = e == null ? null : uuidMapper.get(e);
        return u == null ? "" : u.id;
    }

    /** The per-ship RNG every random draw for damage to that ship comes from. */
    public RandomXS128 rngFor(Entity ship) {
        RandomXS128 rng = rngs.get(ship);
        if (rng == null) {
            UuidComponent u = uuidMapper.get(ship);
            long s = seed * 31 + (u != null ? u.id.hashCode() : rngs.size);
            rng = new RandomXS128(s);
            rngs.put(ship, rng);
        }
        return rng;
    }

    // ------------------------------------------------------------------

    private void resolve(ImpactEvent hit) {
        Entity target = hit.target;
        PhysicsBodyComponent bodyComponent = target == null ? null : bodyMapper.get(target);
        Body body = bodyComponent == null ? null : bodyComponent.body;
        if (body == null) return;

        ShipGridComponent grid = gridMapper.get(target);
        float speed = hit.relVelocity.len();
        vOut.setZero();

        if (grid != null && speed > 0f) {
            resolveIntoGrid(hit, body, grid, speed);
        }

        // Momentum: m * (v_in - v_out), relative velocities, at the hit point (section 5.7).
        float ix = hit.mass * (hit.relVelocity.x - vOut.x);
        float iy = hit.mass * (hit.relVelocity.y - vOut.y);
        CombatPhysics.applyImpulseAt(body, ix, iy, hit.worldPoint.x, hit.worldPoint.y);
    }

    private void resolveIntoGrid(ImpactEvent hit, Body body, ShipGridComponent grid, float speed) {
        ProjectileDef def = hit.def;
        Entity ship = hit.target;
        CombatPhysics.toBodyLocal(body, hit.worldPoint.x, hit.worldPoint.y, localPoint);
        CombatPhysics.vectorToBodyLocal(body, hit.worldNormal.x, hit.worldNormal.y, localNormal);
        CombatPhysics.vectorToBodyLocal(body, hit.relVelocity.x, hit.relVelocity.y, localRel);
        float budget = hit.penetration >= 0f ? hit.penetration
                : 0.5f * hit.mass * speed * speed * def.penetrationFactor;

        RandomXS128 rng = rngFor(ship);
        report.clear();
        report.ship = ship;
        resolver.resolveHit(grid, ship, localPoint, localNormal, localRel, budget, def.damageType, def.fuse,
                def.spall, def.ricochetAngleDeg, def.ricochetRestitution, rng, report, result);

        CombatPhysics.velocityAt(body, hit.worldPoint.x, hit.worldPoint.y, surfaceVel);
        HullHitEvent.Outcome outcome;
        switch (result.outcome) {
            case RICOCHET: {
                CombatPhysics.vectorToWorld(body, result.ricochetDir.x, result.ricochetDir.y, vOut)
                        .scl(speed * result.ricochetSpeedScale);
                tmp.set(hit.worldNormal).scl(EXIT_CLEARANCE).add(hit.worldPoint);
                respawn(hit, tmp.x, tmp.y, hit.mass);
                outcome = HullHitEvent.Outcome.RICOCHET;
                break;
            }
            case OVER_PENETRATED: {
                float keep = result.startBudget > 0f ? (float) Math.sqrt(result.exitBudget / result.startBudget) : 0f;
                vOut.set(hit.relVelocity).scl(keep);
                // Exit point from tile space back to the world, nudged clear of the hull.
                float lx = grid.gridOriginLocal.x + result.exitPoint.x * grid.tileSize;
                float ly = grid.gridOriginLocal.y + result.exitPoint.y * grid.tileSize;
                CombatPhysics.toWorld(body, lx, ly, tmp);
                tmp.mulAdd(vOut, EXIT_CLEARANCE / Math.max(vOut.len(), 1e-3f));
                respawn(hit, tmp.x, tmp.y, hit.mass);
                outcome = HullHitEvent.Outcome.OVER_PENETRATED;
                break;
            }
            case PENETRATED:
                outcome = HullHitEvent.Outcome.PENETRATED;
                break;
            default:
                outcome = HullHitEvent.Outcome.STOPPED;
                break;
        }

        float absorbed = result.absorbed;
        WarheadDef warhead = defs.warhead(def.warhead);
        if (warhead != null && result.detonationX >= 0) {
            if (result.outcome == PenetrationResolver.Result.Outcome.STOPPED) {
                // Went off on the outer hull: vacuum, so fragments only, thrown into the hull.
                tmp.set(hit.worldNormal).scl(EXIT_CLEARANCE).add(hit.worldPoint);
                throwFragments(warhead, tmp, surfaceVel, ship, hit.owner, hit.worldNormal);
            } else {
                absorbed += resolver.detonateInside(grid, ship, result.detonationX, result.detonationY,
                        warhead, rng, report);
            }
        }

        if (report.structureChanged) grid.gridDirty = true;

        hullHit.ship = ship;
        hullHit.tileX = result.entryX;
        hullHit.tileY = result.entryY;
        hullHit.worldPoint.set(hit.worldPoint);
        hullHit.outcome = outcome;
        hullHit.energyAbsorbed = absorbed;
        hullHit.severity = MathUtils.clamp(absorbed / SEVERITY_SCALE, 0f, 1f);
        hullHit.tick = hit.tick;
        if (result.outcome != PenetrationResolver.Result.Outcome.MISSED) events.hullHits.dispatch(hullHit);
        if (!report.isEmpty()) events.damage.dispatch(report);
    }

    /** Puts a ricocheting or exiting round back in flight with velocity surface + vOut. */
    private void respawn(ImpactEvent hit, float x, float y, float mass) {
        if (hit.ram) return;
        Entity e = ballistics.spawn(hit.def, mass, x, y, surfaceVel.x + vOut.x, surfaceVel.y + vOut.y, hit.owner);
        ProjectileComponent p = e.getComponent(ProjectileComponent.class);
        p.ignore = hit.target;
        p.ignoreUntil = EXIT_IGNORE_TIME;
        // Already flew; let it hit its own ship again (it may be a ricochet off a friendly hull).
        p.age = 0f;
    }

    /**
     * Throws a warhead's fragments as rounds. They fly toward {@code target} in the warhead's
     * cone (or along {@code inward} reversed, for a hull-contact burst) and resolve as ordinary
     * hits when they land. Vacuum has no overpressure, so this is all the damage a warhead
     * outside a hull does.
     */
    private void throwFragments(WarheadDef warhead, Vector2 point, Vector2 baseVel, Entity target, Entity owner,
                                Vector2 outwardNormal) {
        if (warhead == null || warhead.fragmentCount <= 0) return;
        float aim;
        if (outwardNormal != null) {
            aim = MathUtils.atan2(-outwardNormal.y, -outwardNormal.x);
        } else if (target != null && bodyMapper.get(target) != null && bodyMapper.get(target).body != null) {
            PhysicsManager.getPosition(bodyMapper.get(target).body, tmp);
            aim = MathUtils.atan2(tmp.y - point.y, tmp.x - point.x);
        } else {
            aim = MathUtils.atan2(baseVel.y, baseVel.x);
        }
        ProjectileDef fragment = fragmentDef(warhead);
        RandomXS128 rng = target != null ? rngFor(target) : rngFor(owner != null ? owner : new Entity());
        float half = 0.5f * warhead.fragmentConeDeg * MathUtils.degreesToRadians;
        for (int i = 0; i < warhead.fragmentCount; i++) {
            float a = aim + (rng.nextFloat() * 2f - 1f) * half;
            float vx = baseVel.x + MathUtils.cos(a) * warhead.fragmentSpeed;
            float vy = baseVel.y + MathUtils.sin(a) * warhead.fragmentSpeed;
            ballistics.spawn(fragment, warhead.fragmentMass, point.x, point.y, vx, vy, owner);
        }
    }

    private ProjectileDef fragmentDef(WarheadDef warhead) {
        ProjectileDef def = fragmentDefs.get(warhead.id);
        if (def == null) {
            def = new ProjectileDef();
            def.id = warhead.id + ".fragment";
            def.mass = warhead.fragmentMass;
            def.muzzleSpeed = warhead.fragmentSpeed;
            def.damageType = DamageType.KINETIC;
            def.penetrationFactor = 1f;
            def.ricochetAngleDeg = 60f;
            def.ricochetRestitution = 0.3f;
            def.maxLifetime = 5f;
            def.armingTime = 0f;
            fragmentDefs.put(warhead.id, def);
        }
        return def;
    }
}
