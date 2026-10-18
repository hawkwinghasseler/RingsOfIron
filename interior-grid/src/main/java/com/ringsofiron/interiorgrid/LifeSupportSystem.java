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
 *   <li>It releases up to {@link #OXYGEN_SUPPLY_RATE} mol of oxygen per second into served
 *       compartments whose oxygen partial pressure is below standard, shared by deficit.
 *       This is the only source of new air aboard; it does not refill nitrogen, so a
 *       vented room needs fresh air from elsewhere to come back to full pressure.</li>
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
        for (int i = 0; i < entities.size(); i++) runAll(entities.get(i), (float) seconds);
    }

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
        double deficitTotal = 0;
        for (Compartment c : served) {
            c.carbonDioxide -= c.carbonDioxide * drawn;
            c.smoke -= c.smoke * drawn;
            deficitTotal += oxygenDeficit(c);
        }

        // Replenish oxygen, shared by how far each room is below standard
        if (deficitTotal <= 0) return;
        double supply = Math.min(OXYGEN_SUPPLY_RATE * dt, deficitTotal);
        for (Compartment c : served) {
            double d = oxygenDeficit(c);
            if (d > 0) c.oxygen += supply * d / deficitTotal;
        }
    }

    /** Moles of oxygen needed to bring the compartment to standard oxygen partial pressure. */
    static double oxygenDeficit(Compartment c) {
        double perMole = c.kPaPerMole();
        if (perMole <= 0) return 0;
        double pO2 = c.oxygen * perMole;
        return Math.max(0, (Compartment.STANDARD_O2_PRESSURE - pO2) / perMole);
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
