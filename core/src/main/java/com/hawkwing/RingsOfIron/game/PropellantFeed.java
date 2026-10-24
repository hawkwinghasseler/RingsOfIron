package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Feeds a ship's thrusters from the propellant tanks in its grid. A ship whose layout has an
 * {@link SubsystemComponent.SubsystemType#ENGINE} burns propellant: its thrusters may use what the
 * propellant tanks piped to its working engines hold, at force / exhaust velocity (torque at
 * torque / ({@link #RCS_ARM} x exhaust velocity)), and nothing once those run dry, the engines lose
 * power or are disabled, or the pipes are cut. What the thrusters burned comes out of those tanks.
 *
 * <p>A ship with no engine in its layout (one from before tanks) keeps free thrust.
 *
 * <p>Runs every frame: it puts what the tanks hold on {@link ThrusterComponent#fuel}, and on the
 * next frame takes what flight control burned in between out of the tanks.
 */
public class PropellantFeed extends EntitySystem {
    /** Lever arm the RCS thrusters turn the ship with, meters, for pricing torque in propellant. */
    public static final float RCS_ARM = 3f;

    private static final Family SHIPS = Family.all(ThrusterComponent.class, ShipSubsystemRegistryComponent.class).get();
    private static final ComponentMapper<ThrusterComponent> THRUSTERS = ComponentMapper.getFor(ThrusterComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    /** What each ship's thrusters were last given, and from which tanks. */
    private static final class Feed {
        float given;
        final Array<SubsystemComponent> tanks = new Array<>();
    }

    /** Runs after the interior systems, so it sees this frame's power and pipes. */
    public static final int DEFAULT_PRIORITY = LifeSupportSystem.DEFAULT_PRIORITY + 1;

    private final Map<Entity, Feed> feeds = new IdentityHashMap<>();

    public PropellantFeed() {
        super(DEFAULT_PRIORITY);
    }
    private ImmutableArray<Entity> ships;

    private final EntityListener forget = new EntityListener() {
        @Override
        public void entityAdded(Entity entity) {
        }

        @Override
        public void entityRemoved(Entity entity) {
            feeds.remove(entity);
        }
    };

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
        engine.addEntityListener(SHIPS, forget);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(forget);
        ships = null;
        feeds.clear();
    }

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) feed(ships.get(i));
    }

    private void feed(Entity ship) {
        ThrusterComponent thruster = THRUSTERS.get(ship);
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        if (!hasEngine(registry)) {
            feeds.remove(ship);
            return;
        }
        Feed feed = feeds.get(ship);
        if (feed == null) {
            feed = new Feed();
            feeds.put(ship, feed);
        } else {
            // What flight control burned since the last frame comes out of the tanks it was given
            double burned = feed.given - thruster.fuel;
            if (burned > 0) TankSupply.draw(feed.tanks, Fluid.PROPELLANT, burned);
        }
        float ve = exhaustVelocity(thruster);
        thruster.fuelPerNewtonSecond = 1f / ve;
        thruster.fuelPerTorqueSecond = 1f / (ve * RCS_ARM);
        fedTanks(registry, feed.tanks);
        feed.given = (float) TankSupply.available(feed.tanks, Fluid.PROPELLANT);
        thruster.fuel = feed.given;
        thruster.fuelCapacity = (float) capacity(registry);
    }

    /** The drive's exhaust velocity: the ship's, else the standard, times its engines' efficiency. */
    private static float exhaustVelocity(ThrusterComponent thruster) {
        float ve = thruster.exhaustVelocity > 0f ? thruster.exhaustVelocity : ShipPhysicsAssembler.STANDARD_EXHAUST_VELOCITY;
        return ve * (thruster.efficiency > 0f ? thruster.efficiency : 1f);
    }

    /** True if the ship's layout has an engine, working or not: its thrust then burns propellant. */
    public static boolean hasEngine(ShipSubsystemRegistryComponent registry) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s != null && s.type == SubsystemComponent.SubsystemType.ENGINE) return true;
        }
        return false;
    }

    /** The propellant tanks piped to the ship's working engines, into {@code out}. */
    public static void fedTanks(ShipSubsystemRegistryComponent registry, Array<SubsystemComponent> out) {
        out.clear();
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s == null || s.type != SubsystemComponent.SubsystemType.ENGINE || !s.isOperational) continue;
            for (SubsystemComponent t : s.pipedTanks) {
                if (t.fluid == Fluid.PROPELLANT && !out.contains(t, true)) out.add(t);
            }
        }
    }

    /** Kilograms of propellant the ship's working engines can draw now. */
    public static double available(ShipSubsystemRegistryComponent registry) {
        Array<SubsystemComponent> tanks = new Array<>();
        fedTanks(registry, tanks);
        return TankSupply.available(tanks, Fluid.PROPELLANT);
    }

    /** Kilograms of propellant all the ship's tanks hold when full. */
    public static double capacity(ShipSubsystemRegistryComponent registry) {
        double kg = 0;
        for (SubsystemComponent t : TankSupply.tanks(registry, Fluid.PROPELLANT)) kg += t.capacity;
        return kg;
    }

    /**
     * The delta-v a ship of {@code mass} kg can still make on the propellant its working engines
     * draw on, m/s, by the rocket equation. Infinite for a ship with no engine (its thrust is free).
     */
    public static double deltaVLeft(Entity ship, double mass) {
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        ThrusterComponent thruster = THRUSTERS.get(ship);
        if (registry == null || !hasEngine(registry)) return Double.POSITIVE_INFINITY;
        if (mass <= 0) return 0;
        double ve = thruster != null ? exhaustVelocity(thruster) : ShipPhysicsAssembler.STANDARD_EXHAUST_VELOCITY;
        double have = Math.min(available(registry), mass * 0.999);
        return ve * Math.log(mass / (mass - have));
    }

    /**
     * Propellant, kg, an impulsive burn of {@code deltaV} m/s by a ship of {@code mass} kg takes, by the
     * rocket equation; 0 for a ship with no engine, infinite for a delta-v that isn't a number.
     */
    public static double propellantFor(Entity ship, double mass, double deltaV) {
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        if (registry == null || !hasEngine(registry)) return 0;
        if (Double.isNaN(deltaV)) return Double.POSITIVE_INFINITY;
        ThrusterComponent thruster = THRUSTERS.get(ship);
        double ve = thruster != null ? exhaustVelocity(thruster) : ShipPhysicsAssembler.STANDARD_EXHAUST_VELOCITY;
        return mass * (1 - Math.exp(-deltaV / ve));
    }

    /**
     * Pays for an impulsive burn of {@code deltaV} m/s by a ship of {@code mass} kg out of the tanks
     * its engines draw on, by the rocket equation, and returns the delta-v it could pay for: all of
     * it, less when the tanks run short, 0 with none. A ship with no engine pays nothing.
     */
    public static double payForBurn(Entity ship, double mass, double deltaV) {
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        ThrusterComponent thruster = THRUSTERS.get(ship);
        if (registry == null || !hasEngine(registry) || deltaV <= 0 || mass <= 0) return deltaV;
        double ve = thruster != null ? exhaustVelocity(thruster) : ShipPhysicsAssembler.STANDARD_EXHAUST_VELOCITY;
        Array<SubsystemComponent> tanks = new Array<>();
        fedTanks(registry, tanks);
        double have = TankSupply.available(tanks, Fluid.PROPELLANT);
        double need = mass * (1 - Math.exp(-deltaV / ve));
        if (need <= have) {
            TankSupply.draw(tanks, Fluid.PROPELLANT, need);
            return deltaV;
        }
        TankSupply.draw(tanks, Fluid.PROPELLANT, have);
        return have >= mass ? deltaV : ve * Math.log(mass / (mass - have));
    }
}
