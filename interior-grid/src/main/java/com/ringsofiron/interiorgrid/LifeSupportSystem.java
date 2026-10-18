package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

/**
 * Operational {@link SubsystemComponent.SubsystemType#LIFE_SUPPORT} units clean and
 * replenish the air of the compartments they serve.
 *
 * <ul>
 *   <li>A unit serves the compartment its footprint stands in, plus every compartment
 *       reachable from it through fully open openings (open doors, gaps a tile wide).
 *       Air behind a closed door, a seal or a leaking wall is out of reach.</li>
 *   <li>It draws {@link #PROCESS_RATE} m³ of air per second, spread over the served
 *       volume, and strips {@link #SCRUB_EFFICIENCY} of the CO2 and smoke in it.</li>
 *   <li>It releases up to {@link #AIR_SUPPLY_RATE} mol of standard air per second into
 *       served compartments below standard pressure, shared by shortfall, so a vented
 *       room comes back to full pressure once it is sealed.</li>
 *   <li>It releases up to {@link #OXYGEN_SUPPLY_RATE} mol of oxygen per second into served
 *       compartments whose air is short of the standard oxygen share.</li>
 *   <li>Nothing is released into a compartment that is venting to space. Tanks are not
 *       modelled: the supply never runs out.</li>
 * </ul>
 * A unit that is switched off, unpowered or destroyed does nothing. Life support is far
 * too slow to keep up with a large fire, which uses oxygen much faster than this.
 */
public class LifeSupportSystem extends FixedLogicSystem {

    /** Air drawn through one unit's scrubbers, m³/s. */
    public static final float PROCESS_RATE = 3f;
    /** Share of the CO2 and smoke removed from the air drawn through. */
    public static final float SCRUB_EFFICIENCY = 0.9f;
    /** Oxygen one unit can release, mol/s. */
    public static final float OXYGEN_SUPPLY_RATE = 1.5f;
    /** Standard air one unit can release to bring rooms back to standard pressure, mol/s. */
    public static final float AIR_SUPPLY_RATE = 20f;
    /** Ticks at the same rate as the hazard simulation. */
    public static final float TICK = GridHazardSystem.HAZARD_TICK;
    /** Runs after the hazard step, so it works on that tick's air and airflow. */
    public static final int DEFAULT_PRIORITY = GridHazardSystem.DEFAULT_PRIORITY + 1;
    private static final Family FAMILY = Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class).get();

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> registryMapper =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper =
            ComponentMapper.getFor(SubsystemComponent.class);

    private final Array<Compartment> served = new Array<>();
    private ImmutableArray<Entity> entities;

    public LifeSupportSystem() {
        super(DEFAULT_PRIORITY, TICK);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) runAll(entities.get(i), deltaTime);
    }

    /** Rails warp: one long step. The scrubbing and oxygen supply below both handle any length of time. */
    @Override
    protected void catchUp(long ticks, double seconds) {
        if (entities == null) return;
        // After the hazard catch-up nothing flows, so any opening to space counts as venting
        catchingUp = true;
        try {
            for (int i = 0; i < entities.size(); i++) runAll(entities.get(i), (float) seconds);
        } finally {
            catchingUp = false;
        }
    }

    /** During a rails-warp catch-up, when airflow has been settled to zero. */
    private boolean catchingUp;

    private void runAll(Entity entity, float dt) {
        ShipGridComponent grid = gridMapper.get(entity);
        ShipSubsystemRegistryComponent registry = registryMapper.get(entity);
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            if (sub == null || sub.type != SubsystemComponent.SubsystemType.LIFE_SUPPORT || !sub.isOperational) continue;
            run(grid, sub.footprint, dt);
        }
    }

    /** Runs one life support unit installed in {@code footprint} for dt seconds. */
    public void run(ShipGridComponent grid, MountFootprint footprint, float dt) {
        Compartment home = homeCompartment(grid, footprint);
        if (home == null) return;
        collectServed(grid, home, served);

        float totalVolume = 0f;
        for (Compartment c : served) totalVolume += c.volume;
        if (totalVolume <= 0f) return;

        // Scrub: every served room has the same share of its air drawn through
        double drawn = 1.0 - Math.exp(-PROCESS_RATE * SCRUB_EFFICIENCY * dt / totalVolume);
        for (Compartment c : served) {
            c.carbonDioxide -= c.carbonDioxide * drawn;
            c.smoke -= c.smoke * drawn;
        }

        // Refill sealed rooms below standard pressure with standard air, shared by shortfall
        double deficitTotal = 0;
        for (Compartment c : served) if (!isVenting(grid, c)) deficitTotal += pressureDeficit(c);
        if (deficitTotal > 0) {
            double supply = Math.min(AIR_SUPPLY_RATE * dt, deficitTotal);
            for (Compartment c : served) {
                double d = isVenting(grid, c) ? 0 : pressureDeficit(c);
                if (d <= 0) continue;
                double moles = supply * d / deficitTotal;
                c.oxygen += moles * Compartment.STANDARD_O2_FRACTION;
                c.nitrogen += moles * (1.0 - Compartment.STANDARD_O2_FRACTION);
            }
        }
        // Then top up oxygen burned off by fires and breathing
        deficitTotal = 0;
        for (Compartment c : served) if (!isVenting(grid, c)) deficitTotal += oxygenDeficit(c);
        if (deficitTotal > 0) {
            double supply = Math.min(OXYGEN_SUPPLY_RATE * dt, deficitTotal);
            for (Compartment c : served) {
                double d = isVenting(grid, c) ? 0 : oxygenDeficit(c);
                if (d > 0) c.oxygen += supply * d / deficitTotal;
            }
        }
    }

    /** True if air leaves the compartment for space faster than a trickle. */
    boolean isVenting(ShipGridComponent grid, Compartment c) {
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.room == c && e.other == null && (catchingUp || e.flow > ShipGridComponent.VENTING_HAZARD_FLOW)) return true;
        }
        return false;
    }

    /** Moles of gas needed to bring the compartment to standard pressure. */
    static double pressureDeficit(Compartment c) {
        double perMole = c.kPaPerMole();
        if (perMole <= 0) return 0;
        return Math.max(0, (Compartment.STANDARD_PRESSURE - c.pressure()) / perMole);
    }

    /**
     * Moles of oxygen needed to bring the compartment's air to the standard oxygen share,
     * and no further than standard oxygen partial pressure. Aiming at the share rather
     * than the partial pressure keeps a room that is still refilling from getting oxygen-rich.
     */
    static double oxygenDeficit(Compartment c) {
        double perMole = c.kPaPerMole();
        if (perMole <= 0) return 0;
        double others = c.totalMoles() - c.oxygen;
        double target = Math.min(others * Compartment.STANDARD_O2_FRACTION / (1.0 - Compartment.STANDARD_O2_FRACTION),
                Compartment.STANDARD_O2_PRESSURE / perMole);
        return Math.max(0, target - c.oxygen);
    }

    /** The compartment the unit's footprint stands in, or one next to it. */
    static Compartment homeCompartment(ShipGridComponent grid, MountFootprint footprint) {
        Compartment adjacent = null;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (!footprint.covers(x, y)) continue;
                Compartment c = grid.compartmentAt(x, y);
                if (c != null) return c;
                if (adjacent == null) {
                    adjacent = firstNonNull(grid.compartmentAt(x + 1, y), grid.compartmentAt(x - 1, y),
                            grid.compartmentAt(x, y + 1), grid.compartmentAt(x, y - 1));
                }
            }
        }
        return adjacent;
    }

    /** {@code home} and every compartment reachable from it through fully open openings. */
    static void collectServed(ShipGridComponent grid, Compartment home, Array<Compartment> out) {
        out.clear();
        out.add(home);
        for (int i = 0; i < out.size; i++) {
            Compartment c = out.get(i);
            for (int j = 0; j < grid.airflow.size; j++) {
                AirflowEdge e = grid.airflow.get(j);
                if (e.room != c || e.other == null || e.width < 1f) continue;
                if (!out.contains(e.other, true)) out.add(e.other);
            }
        }
    }

    private static Compartment firstNonNull(Compartment... cs) {
        for (Compartment c : cs) if (c != null) return c;
        return null;
    }
}
