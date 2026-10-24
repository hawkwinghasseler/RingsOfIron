package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;

import java.util.function.Supplier;

/**
 * A ship's engines set its thrust. Each {@link SubsystemComponent.SubsystemType#ENGINE} in its
 * layout adds its rated thrust: its component's {@code thrust} for its mount size in
 * {@code components.toml}, else the {@link #standardThrust standard thrust} for the size. The main
 * engine gives the sum over the engines that work (switched on, powered, not disabled by damage),
 * each scaled down by its damage, so a damaged engine gives less and a destroyed or unpowered one
 * none. Retro and lateral thrust are the usual fractions of the engines' rated sum, since they come
 * from the RCS rather than the main engines; turning ({@code max_torque}) is left alone.
 *
 * <p>Each component's {@code efficiency} (1 if left out) multiplies the exhaust velocity: the
 * ship's is the engines' together, weighted by their rated thrust as their propellant flows add
 * up, so {@link PropellantFeed} prices a newton-second for the engines fitted.
 *
 * <p>A ship with no engine in its layout keeps the {@code max_thrust} its {@code ships.toml} entry
 * gives it. Every engine counts whichever way its nozzle points.
 *
 * <p>Runs every frame, before flight control reads the thrusters. Engines also feed the thrusters
 * propellant ({@link PropellantFeed}); this only sets how hard they can push.
 */
public class EngineThrust extends EntitySystem {
    /** Newtons a standard Small engine gives at full health. The corvette's two make its 2.4 MN. */
    public static final double THRUST_S = 1.2e6;
    public static final double THRUST_M = 3.6e6;
    public static final double THRUST_L = 1.2e7;
    public static final double THRUST_XL = 3.6e7;

    private static final Family SHIPS = Family.all(ThrusterComponent.class, ShipSubsystemRegistryComponent.class).get();
    private static final ComponentMapper<ThrusterComponent> THRUSTERS = ComponentMapper.getFor(ThrusterComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    /** The loaded data, for each component's thrust; null (or nothing loaded) uses the standard thrust. */
    private final Supplier<ModContext> content;
    private ImmutableArray<Entity> ships;

    public EngineThrust(Supplier<ModContext> content) {
        super(PropellantFeed.DEFAULT_PRIORITY);
        this.content = content;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        ModContext ctx = content == null ? null : content.get();
        DefRegistry defs = ctx == null ? null : ctx.defs;
        for (int i = 0; i < ships.size(); i++) apply(ships.get(i), defs);
    }

    /** Sets one ship's thrusters from its engines. Returns false, changing nothing, for a ship with none. */
    public static boolean apply(Entity ship, DefRegistry defs) {
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        ThrusterComponent thruster = THRUSTERS.get(ship);
        if (registry == null || thruster == null) return false;
        double rated = 0, working = 0, flow = 0;
        boolean any = false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s == null || s.type != SubsystemComponent.SubsystemType.ENGINE) continue;
            any = true;
            double full = ratedThrust(s, defs);
            rated += full;
            flow += full / efficiency(s, defs);
            if (s.isOperational && s.switchedOn && !s.isDisabled()) working += full * Math.max(0f, Math.min(1f, s.health));
        }
        if (!any) return false;
        thruster.forwardThrust = (float) working;
        thruster.efficiency = rated > 0 && flow > 0 ? (float) (rated / flow) : 1f;
        thruster.reverseThrust = (float) (rated * ShipPhysicsAssembler.REVERSE_THRUST_FRACTION);
        thruster.lateralThrust = (float) (rated * ShipPhysicsAssembler.LATERAL_THRUST_FRACTION);
        return true;
    }

    /** Newtons an undamaged engine gives: its component's thrust for its size, else the standard thrust. */
    public static double ratedThrust(SubsystemComponent engine, DefRegistry defs) {
        MountFootprint.MountSize size = engine.footprint.size;
        ShipComponentDef def = def(engine, defs);
        Double n = def == null || def.thrust == null ? null : def.thrust.get(size.name());
        if (n != null && n > 0) return n;
        return standardThrust(size);
    }

    /** How much an engine multiplies the exhaust velocity: its component's {@code efficiency}, else 1. */
    public static double efficiency(SubsystemComponent engine, DefRegistry defs) {
        ShipComponentDef def = def(engine, defs);
        return def != null && def.efficiency != null && def.efficiency > 0f ? def.efficiency : 1.0;
    }

    private static ShipComponentDef def(SubsystemComponent engine, DefRegistry defs) {
        if (defs == null || engine.component == null || !defs.has("component", engine.component)) return null;
        return defs.get("component", engine.component, ShipComponentDef.class);
    }

    /** Newtons an undamaged standard engine in a mount of this size gives. */
    public static double standardThrust(MountFootprint.MountSize size) {
        switch (size) {
            case S: return THRUST_S;
            case M: return THRUST_M;
            case L: return THRUST_L;
            default: return THRUST_XL;
        }
    }

    /** Newtons all of a ship's engines give at full health, or 0 for a ship with none. */
    public static double ratedThrust(Entity ship, DefRegistry defs) {
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        if (registry == null) return 0;
        double rated = 0;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s != null && s.type == SubsystemComponent.SubsystemType.ENGINE) rated += ratedThrust(s, defs);
        }
        return rated;
    }
}
