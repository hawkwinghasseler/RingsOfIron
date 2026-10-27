package com.ringsofiron.combatsys.shields;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Keeps every shielded ship's cloud each physics tick ({@link ShieldModel#tick}): looks ahead
 * {@link ShieldModel#LOOKAHEAD} seconds along every round and missile in flight for where it
 * will cross the ship's perimeter, so the emitters can gather mass there before it arrives. A
 * ship's own rounds pass out through its cloud and are never threats to it.
 *
 * <p>Runs from {@link PhysicsManager.StepListener#beforeStep}. Add it to the engine before
 * {@code ImpactResolutionSystem}, so the regions stand when this tick's hits resolve.
 */
public class ShieldSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family SHIPS = Family.all(ShipSubsystemRegistryComponent.class, ShipGridComponent.class,
            PhysicsBodyComponent.class).get();
    private static final Family ROUNDS = Family.all(ProjectileComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodies = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ProjectileComponent> projectiles = ComponentMapper.getFor(ProjectileComponent.class);
    private final PhysicsManager physics;
    private ImmutableArray<Entity> ships, rounds;
    private final Array<ShieldModel.Threat> threats = new Array<>();
    private final Array<ShieldModel.Threat> pool = new Array<>();
    private final Vector2 local = new Vector2(), rel = new Vector2(), dir = new Vector2();
    private final float[] crossing = new float[2];

    public ShieldSystem(PhysicsManager physics) {
        this.physics = physics;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
        rounds = engine.getEntitiesFor(ROUNDS);
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float dt) {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ShieldCloudComponent cloud = ShieldModel.ensure(ship);
            if (cloud == null) continue;
            Body body = bodies.get(ship).body;
            if (body == null) continue;
            Vector2 com = centerOfMass(body, new Vector2());
            look(ship, body, cloud.perimeter);
            ShieldModel.tick(cloud, threats, com.x, com.y, dt);
        }
    }

    /** The rounds and missiles that will cross {@code ship}'s perimeter within the lookahead, into {@link #threats}. */
    private void look(Entity ship, Body body, ShieldPerimeter perimeter) {
        pool.addAll(threats);
        threats.clear();
        for (int j = 0; j < rounds.size(); j++) {
            ProjectileComponent p = projectiles.get(rounds.get(j));
            if (p.spent || p.owner == ship) continue;
            CombatPhysics.velocityAt(body, p.pos.x, p.pos.y, rel);
            rel.set(p.vel.x - rel.x, p.vel.y - rel.y);
            float speed = rel.len();
            if (speed < 1f) continue;
            CombatPhysics.toBodyLocal(body, p.pos.x, p.pos.y, local);
            CombatPhysics.vectorToBodyLocal(body, rel.x / speed, rel.y / speed, dir);
            if (perimeter.contains(local.x, local.y)) continue;
            if (!perimeter.cross(local.x, local.y, dir.x, dir.y, speed * ShieldModel.LOOKAHEAD, true, crossing)) continue;
            ShieldModel.Threat t = pool.size > 0 ? pool.pop() : new ShieldModel.Threat();
            t.key = p;
            t.s = crossing[0];
            t.eta = crossing[1] / speed;
            t.weight = 0.5f * p.mass * speed * speed;
            threats.add(t);
        }
    }

    /** {@code body}'s center of mass, body-local meters. */
    public static Vector2 centerOfMass(Body body, Vector2 out) {
        return out.set(body.getLocalCenter()).scl(PhysicsManager.METERS_PER_UNIT);
    }
}
