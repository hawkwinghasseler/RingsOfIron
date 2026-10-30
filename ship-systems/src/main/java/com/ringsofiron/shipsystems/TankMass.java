package com.ringsofiron.shipsystems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Keeps the weight of what a ship's tanks hold on its hull. Whenever the tanks' contents have
 * changed by more than {@link #STEP} kg since the ship was last weighed (propellant burned, air
 * used, a leak, a refill), its Box2D mass, center of mass and inertia are recomputed from its
 * structure, cargo and tanks. On a ship's first frame it keeps the mass the ship was assembled or
 * loaded with, which already counts the tanks. Runs every frame, after the propellant feed.
 */
public class TankMass extends EntitySystem {
    /** Kilograms the tanks must gain or lose before the ship's mass is recomputed. */
    public static final double STEP = 5.0;

    private static final Family SHIPS = Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class,
            PhysicsBodyComponent.class).get();
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODY = ComponentMapper.getFor(PhysicsBodyComponent.class);

    /** The ship's mass properties, structure, cargo and tanks, or null when they cannot be worked out. */
    private final Function<Entity, ShipGridComponent.MassProperties> massOf;
    /** What each ship's tanks held when its mass was last set. */
    private final Map<Entity, Double> weighed = new IdentityHashMap<>();
    private ImmutableArray<Entity> ships;

    private final EntityListener forget = new EntityListener() {
        @Override
        public void entityAdded(Entity entity) {
        }

        @Override
        public void entityRemoved(Entity entity) {
            weighed.remove(entity);
        }
    };

    public TankMass(Function<Entity, ShipGridComponent.MassProperties> massOf) {
        super(PropellantFeed.DEFAULT_PRIORITY + 1);
        this.massOf = massOf;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
        engine.addEntityListener(SHIPS, forget);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(forget);
        ships = null;
        weighed.clear();
    }

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) {
            Entity e = ships.get(i);
            PhysicsBodyComponent body = BODY.get(e);
            if (body.body == null) continue;
            double kg = TankSupply.contents(REGISTRY.get(e));
            Double last = weighed.get(e);
            // The physics assembler (or a loaded save) weighed the ship with the tanks as they are now
            if (last == null) {
                weighed.put(e, kg);
                continue;
            }
            if (Math.abs(kg - last) <= STEP) continue;
            ShipGridComponent.MassProperties m = massOf.apply(e);
            if (m == null || m.mass <= 0f) continue;
            PhysicsManager.setMassData(body.body, m.mass, m.center.x, m.center.y, m.inertia);
            weighed.put(e, kg);
        }
    }
}
