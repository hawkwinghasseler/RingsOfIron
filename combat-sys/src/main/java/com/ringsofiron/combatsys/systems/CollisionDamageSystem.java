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
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.combatsys.damage.PenetrationResolver;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.HullShape;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.events.CrashEvent;

import java.util.List;
import java.util.function.Function;

/**
 * Hull-on-hull collisions (combat-sys/Design.md section 5.12). Two bodies meeting faster than
 * {@link #NO_DAMAGE_SPEED} along the contact normal crash: physics lets them pass into each other
 * for that tick ({@link PhysicsManager#setCrashHandling}) and this system settles it.
 *
 * <ul>
 *   <li><b>Impulse.</b> The crash is plastic: it takes the closing speed down to
 *   {@link #NO_DAMAGE_SPEED} with one impulse {@code J = mu (v - v0)} along the normal at the
 *   contact, where {@code mu} is the pair's effective mass there (spin included). What is left of
 *   the hulls then meets as Box2D would. A body with no say in its motion (a station, kinematic)
 *   counts as infinitely heavy.</li>
 *   <li><b>Energy.</b> That impulse takes {@code E = mu (v^2 - v0^2) / 2} out of their motion.
 *   {@link #DAMAGE_SHARE} of it crushes structure; the rest is the hulls' frames flexing, heat,
 *   noise and spin. The two hulls split it by how hard they are where they met: the softer one
 *   takes the bigger share, and a body with no grid counts as medium steel.</li>
 *   <li><b>Crush.</b> Each hull's share spreads from the contact over the tiles round it
 *   ({@link PenetrationResolver#crush}); what a tile can't take pushes inward and crushes it
 *   flat. Crushed tiles leave the hull's collision shape, which is rebuilt before the next step,
 *   so the hulls end up inside where they used to be.</li>
 *   <li><b>Jolt.</b> Everyone aboard is thrown about by the ship's change of speed {@code J / m}:
 *   {@link #JOLT_WOUND_PER_MPS} injury for each m/s past {@link #JOLT_SAFE_SPEED}.</li>
 * </ul>
 *
 * Between two starter corvettes (195 t) head on: 25 m/s does nothing; 50 m/s takes 30% off the
 * tiles where the bows met and less off some twenty round them, with no breach and nobody hurt;
 * 100 m/s crushes three tiles on each, wrecks the bow and lightly wounds everyone aboard; 200 m/s
 * crushes a third of each and leaves the crews critical.
 */
public class CollisionDamageSystem extends EntitySystem implements PhysicsManager.StepListener {
    /** Closing speed along the contact normal at or under which hulls just bump, m/s. */
    public static final float NO_DAMAGE_SPEED = 25f;
    /** Share of the energy a crash takes out of the hulls' motion that goes into crushing them. */
    public static final float DAMAGE_SHARE = 0.2f;
    /** Width of the patch of tiles a crash spreads over, tiles (the kernel's sigma). */
    public static final float SPREAD_TILES = 1.25f;
    /** A ship's change of speed in a crash that anyone aboard rides out unhurt, m/s. */
    public static final float JOLT_SAFE_SPEED = 10f;
    /** Injury to everyone aboard for each m/s of jolt past {@link #JOLT_SAFE_SPEED}. */
    public static final float JOLT_WOUND_PER_MPS = 1.5f;

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<KinematicTransientComponent> transientMapper = ComponentMapper.getFor(KinematicTransientComponent.class);

    private final PhysicsManager physics;
    private final CombatEvents events;
    private final ImpactResolutionSystem impacts;
    private final Listener<CrashEvent> crashListener = this::onCrash;
    private Function<Entity, List<float[]>> hullSource = e -> {
        ShipGridComponent grid = e.getComponent(ShipGridComponent.class);
        return grid == null ? null : HullShape.pieces(grid);
    };
    private ImmutableArray<Entity> grids;

    private final PenetrationResolver.CrushResult crushResult = new PenetrationResolver.CrushResult();
    private final DamageReport report = new DamageReport();
    private final HullHitEvent hullHit = new HullHitEvent();
    private final Array<Entity> occupants = new Array<>();
    private final Vector2 worldA = new Vector2(), worldB = new Vector2();
    private final Vector2 local = new Vector2(), localDir = new Vector2();
    private final Vector2 center = new Vector2();
    private final MassData keptMass = new MassData();

    /** What the last crash did, for tests, demos and the console. */
    public final Outcome last = new Outcome();

    /** One crash, settled. */
    public static final class Outcome {
        public Entity entityA, entityB;
        public float approachSpeed;
        /** Impulse traded along the normal, N s. */
        public float impulse;
        /** Energy taken out of the pair's motion, and the part of it that went into the hulls, J. */
        public float energy, damageEnergy;
        /** Tiles each hull lost integrity on and had crushed. */
        public int damagedA, damagedB, crushedA, crushedB;
        /** Most integrity any one tile of each hull lost. */
        public float worstA, worstB;
    }

    public CollisionDamageSystem(PhysicsManager physics, CombatEvents events, ImpactResolutionSystem impacts) {
        this.physics = physics;
        this.events = events;
        this.impacts = impacts;
    }

    /**
     * Where a hull's collision shape comes from once a crash has changed it: by default its
     * layout less its crushed tiles ({@link HullShape}). The game's source also knows the hull art.
     */
    public void setHullSource(Function<Entity, List<float[]>> source) {
        this.hullSource = source;
    }

    @Override
    public void addedToEngine(Engine engine) {
        grids = engine.getEntitiesFor(Family.all(ShipGridComponent.class).get());
        physics.addStepListener(this);
        physics.crashes.add(crashListener);
        physics.setCrashHandling(NO_DAMAGE_SPEED, this::canCrash);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.crashes.remove(crashListener);
        physics.setCrashHandling(Float.POSITIVE_INFINITY, null);
        grids = null;
    }

    /** Two bodies crash when at least one is a craft with a grid; rocks and debris just bounce off each other. */
    private boolean canCrash(Body a, Body b) {
        return gridOf(a) != null || gridOf(b) != null;
    }

    private ShipGridComponent gridOf(Body body) {
        Object owner = body.getUserData();
        return owner instanceof Entity ? gridMapper.get((Entity) owner) : null;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick.
    }

    /** Reshapes every hull a crash (or a repair) changed, before the world steps with it. */
    @Override
    public void beforeStep(float deltaTime) {
        if (grids == null) return;
        for (int i = 0; i < grids.size(); i++) {
            Entity e = grids.get(i);
            ShipGridComponent grid = gridMapper.get(e);
            if (!grid.hullShapeDirty) continue;
            grid.hullShapeDirty = false;
            reshape(e);
        }
    }

    /** Gives {@code craft}'s body the shape of its hull as it now stands, keeping its mass. */
    public boolean reshape(Entity craft) {
        Body body = bodyOf(craft);
        List<float[]> pieces = body == null ? null : hullSource.apply(craft);
        if (pieces == null || pieces.isEmpty()) return false;
        MassData mass = body.getMassData();
        keptMass.mass = mass.mass;
        keptMass.center.set(mass.center);
        keptMass.I = mass.I;
        if (!PhysicsManager.replacePolygonFixtures(body, pieces)) return false;
        if (body.getType() == BodyDef.BodyType.DynamicBody) body.setMassData(keptMass);
        return true;
    }

    private Body bodyOf(Entity e) {
        PhysicsBodyComponent pb = bodyMapper.get(e);
        if (pb != null && pb.body != null) return pb.body;
        KinematicTransientComponent kt = transientMapper.get(e);
        return kt == null ? null : kt.body;
    }

    private void onCrash(Signal<CrashEvent> signal, CrashEvent crash) {
        Body a = crash.bodyA, b = crash.bodyB;
        float v = crash.approachSpeed;
        if (v <= NO_DAMAGE_SPEED) return;
        // Where they met, now: each body moved on through the other for the tick
        CombatPhysics.toWorld(a, crash.localPointA.x, crash.localPointA.y, worldA);
        CombatPhysics.toWorld(b, crash.localPointB.x, crash.localPointB.y, worldB);
        float nx = crash.normal.x, ny = crash.normal.y;
        float kA = inverseMassAlong(a, worldA, nx, ny), kB = inverseMassAlong(b, worldB, nx, ny);
        if (!(kA + kB > 0f)) return;
        float mu = 1f / (kA + kB);
        float impulse = mu * (v - NO_DAMAGE_SPEED);
        float energy = 0.5f * mu * (v * v - NO_DAMAGE_SPEED * NO_DAMAGE_SPEED);
        // The normal runs from A to B, and A was closing on B along it
        CombatPhysics.applyImpulseAt(a, -nx * impulse, -ny * impulse, worldA.x, worldA.y);
        CombatPhysics.applyImpulseAt(b, nx * impulse, ny * impulse, worldB.x, worldB.y);

        Entity ea = crash.entityA, eb = crash.entityB;
        ShipGridComponent ga = ea == null ? null : gridMapper.get(ea);
        ShipGridComponent gb = eb == null ? null : gridMapper.get(eb);
        float hardA = hardness(a, ga, crash.localPointA, -nx, -ny);
        float hardB = hardness(b, gb, crash.localPointB, nx, ny);
        float damage = DAMAGE_SHARE * energy;
        last.entityA = ea;
        last.entityB = eb;
        last.approachSpeed = v;
        last.impulse = impulse;
        last.energy = energy;
        last.damageEnergy = damage;
        last.damagedA = last.damagedB = last.crushedA = last.crushedB = 0;
        last.worstA = last.worstB = 0f;
        if (ga != null) {
            crushInto(ea, a, ga, crash.localPointA, -nx, -ny, damage * hardB / (hardA + hardB), worldA);
            last.damagedA = crushResult.tilesDamaged;
            last.crushedA = crushResult.tilesCrushed;
            last.worstA = crushResult.worstLoss;
            jolt(ea, ga, impulse * kickOf(a));
        }
        if (gb != null) {
            crushInto(eb, b, gb, crash.localPointB, nx, ny, damage * hardA / (hardA + hardB), worldB);
            last.damagedB = crushResult.tilesDamaged;
            last.crushedB = crushResult.tilesCrushed;
            last.worstB = crushResult.worstLoss;
            jolt(eb, gb, impulse * kickOf(b));
        }
    }

    /** 1 / effective mass of {@code body} along (nx, ny) at world point {@code at}; 0 for a body nothing moves. */
    private float inverseMassAlong(Body body, Vector2 at, float nx, float ny) {
        if (body.getType() != BodyDef.BodyType.DynamicBody) return 0f;
        float m = PhysicsManager.getMass(body);
        if (!(m > 0f)) return 0f;
        float inertia = PhysicsManager.getInertia(body);
        center.set(body.getWorldCenter()).scl(PhysicsManager.METERS_PER_UNIT);
        float rx = at.x - center.x, ry = at.y - center.y;
        float rn = rx * ny - ry * nx;
        return 1f / m + (inertia > 0f ? rn * rn / inertia : 0f);
    }

    /** The body's change of speed per N s of impulse: 1 / mass, or 0 for one nothing moves. */
    private float kickOf(Body body) {
        if (body.getType() != BodyDef.BodyType.DynamicBody) return 0f;
        float m = PhysicsManager.getMass(body);
        return m > 0f ? 1f / m : 0f;
    }

    /**
     * Hit points of the first solid tile in from the contact: how hard the hull is where it was
     * struck. A body with no grid counts as medium hull steel.
     */
    private float hardness(Body body, ShipGridComponent grid, Vector2 localPoint, float worldDirX, float worldDirY) {
        MaterialTable materials = impacts.getResolver().getMaterials();
        float fallback = materials.get(MaterialTable.DEFAULT_MATERIAL, WeightClass.MEDIUM).hitPoints;
        if (grid == null) return fallback;
        CombatPhysics.vectorToBodyLocal(body, worldDirX, worldDirY, localDir);
        float s = grid.tileSize;
        float px = (localPoint.x - grid.gridOriginLocal.x) / s - localDir.x, py = (localPoint.y - grid.gridOriginLocal.y) / s - localDir.y;
        for (float t = 0f; t <= 4f; t += 0.25f) {
            Tile tile = grid.getTile(MathUtils.floor(px + localDir.x * t), MathUtils.floor(py + localDir.y * t));
            if (tile != null && !tile.isVoid() && tile.getIntegrity() > 0f) {
                return materials.get(tile.material, tile.weightClass).hitPoints;
            }
        }
        return fallback;
    }

    private void crushInto(Entity ship, Body body, ShipGridComponent grid, Vector2 localPoint, float worldDirX,
                           float worldDirY, float energy, Vector2 worldPoint) {
        CombatPhysics.vectorToBodyLocal(body, worldDirX, worldDirY, localDir);
        local.set(localPoint);
        report.clear();
        report.ship = ship;
        impacts.getResolver().crush(grid, ship, local, localDir, energy, SPREAD_TILES, impacts.rngFor(ship), report, crushResult);
        if (report.structureChanged) grid.gridDirty = true;
        if (crushResult.entryX >= 0) {
            hullHit.ship = ship;
            hullHit.tileX = crushResult.entryX;
            hullHit.tileY = crushResult.entryY;
            hullHit.worldPoint.set(worldPoint);
            hullHit.outcome = crushResult.tilesCrushed > 0 ? HullHitEvent.Outcome.PENETRATED : HullHitEvent.Outcome.STOPPED;
            hullHit.energyAbsorbed = crushResult.absorbed;
            hullHit.severity = MathUtils.clamp(crushResult.absorbed / ImpactResolutionSystem.SEVERITY_SCALE, 0f, 1f);
            hullHit.ram = true;
            hullHit.beam = false;
            hullHit.ion = false;
            hullHit.shielded = 0f;
            hullHit.tick = physics.getTick();
            events.hullHits.dispatch(hullHit);
            hullHit.ram = false;
        }
        if (!report.isEmpty()) events.damage.dispatch(report);
    }

    /**
     * Everyone aboard {@code ship} is thrown about by a change of speed of {@code kick} m/s. The
     * crew are found and hurt as combat finds them ({@link ImpactResolutionSystem#setCrew}).
     */
    private void jolt(Entity ship, ShipGridComponent grid, float kick) {
        CrewDamageBridge crew = impacts.getResolver().getCrew();
        float injury = JOLT_WOUND_PER_MPS * (kick - JOLT_SAFE_SPEED);
        if (crew == null || !(injury > 0f)) return;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                occupants.clear();
                crew.occupantsAt(ship, x, y, occupants);
                for (int i = 0; i < occupants.size; i++) crew.applyDamage(occupants.get(i), injury, HitCause.IMPACT);
            }
        }
    }
}
