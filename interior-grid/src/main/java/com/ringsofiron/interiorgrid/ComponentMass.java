package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * What a ship's installed systems weigh on their own, on top of the tiles they stand on
 * ({@link ShipGridComponent#computeMassProperties}). Each system's mass is its component's
 * {@code mass} for its size in {@code components.toml}, else the {@link #standard standard mass}
 * for its behavior and size, and is added as a point mass at the middle of its footprint. A
 * damaged or destroyed system keeps its mass; what a tank holds is {@link TankSupply}'s to add.
 *
 * <p>The standard masses follow real hardware where there is some:
 * <ul>
 *   <li>Engine: a thrust-to-weight of about 20 at the standard 1.2 to 36 MN, between nuclear
 *       thermal rockets (3 to 10) and chemical engines (70 to 180).</li>
 *   <li>Reactor: MW-class space fission studies at 6 to 15 kg per kWe with shielding and radiators;
 *       small ones are heavier per watt.</li>
 *   <li>Turret mount, with its gun: a Phalanx CIWS is about 6 t, an OTO Melara 76 mm about 7.5 t,
 *       a Mk 45 5-inch 22 to 29 t.</li>
 *   <li>Life support: the ISS's racks weigh 3 to 4 t for six or seven crew.</li>
 *   <li>Airlock: the ISS's Quest is about 6 t with its equipment.</li>
 *   <li>Propellant tank, empty: about 5% of what it holds (the Shuttle's external tank was 4%).
 *       Gas tanks are composite pressure vessels at 200 to 300 bar, about 1.5 times their gas.</li>
 *   <li>Escape pod: a Soyuz descent module is about 3 t for three.</li>
 *   <li>Docking port: docking systems and their adapters are 0.3 to 0.5 t.</li>
 * </ul>
 * Shield emitters have no real counterpart and are sized like power equipment of a similar draw.
 */
public final class ComponentMass {

    private ComponentMass() {}

    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    /** Kilograms a standard system of {@code type} in a mount of {@code size} weighs; a tank's by what it holds. */
    public static double standard(SubsystemComponent.SubsystemType type, MountFootprint.MountSize size, Fluid fluid) {
        int i = size.ordinal();
        switch (type) {
            case ENGINE:         return pick(i, 6000, 18000, 60000, 180000);
            case REACTOR:        return pick(i, 10000, 25000, 70000, 180000);
            case SHIELD_EMITTER: return pick(i, 3000, 10000, 30000, 80000);
            case TURRET_MOUNT:   return pick(i, 5000, 25000, 80000, 250000);
            case LIFE_SUPPORT:   return pick(i, 1500, 4000, 12000, 30000);
            case HELM:
            case WEAPONS:        return pick(i, 500, 1500, 4000, 10000);
            case CARGO:          return pick(i, 200, 600, 2000, 6000);
            case AIRLOCK:        return pick(i, 1500, 4000, 12000, 30000);
            case ESCAPE_POD:     return pick(i, 3000, 5000, 10000, 20000);
            case DOCKING_PORT:   return pick(i, 500, 1500, 4000, 4000);
            case TANK:
                return fluid == null || fluid == Fluid.PROPELLANT
                        ? pick(i, 150, 400, 1200, 3000)
                        : pick(i, 400, 1200, 4000, 10000);
            default:             return 0;
        }
    }

    private static double pick(int i, double s, double m, double l, double xl) {
        switch (i) {
            case 0:  return s;
            case 1:  return m;
            case 2:  return l;
            default: return xl;
        }
    }

    /** Kilograms {@code system} weighs: its component's figure when it has one, else the standard. */
    public static double of(SubsystemComponent system) {
        if (system.mass >= 0) return system.mass;
        return standard(system.type, system.footprint.size, system.fluid);
    }

    /**
     * Adds every installed system's own mass to structural mass properties, as a point mass at the
     * middle of its footprint, keeping the combined center of mass and moment of inertia about it.
     */
    public static void addMassTo(ShipGridComponent grid, ShipSubsystemRegistryComponent registry,
                                 ShipGridComponent.MassProperties props) {
        double m = props.mass;
        double mx = m * props.center.x, my = m * props.center.y;
        double i0 = props.inertia + m * props.center.len2();
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s == null) continue;
            double sm = of(s);
            if (sm <= 0) continue;
            MountFootprint f = s.footprint;
            Vector2 a = grid.tileToLocal(f.originX, f.originY);
            Vector2 b = grid.tileToLocal(f.originX + f.width() - 1, f.originY + f.height() - 1);
            double px = (a.x + b.x) / 2, py = (a.y + b.y) / 2;
            double w = f.width() * grid.tileSize, h = f.height() * grid.tileSize;
            m += sm;
            mx += sm * px;
            my += sm * py;
            i0 += sm * (px * px + py * py + (w * w + h * h) / 12.0);
        }
        props.mass = (float) m;
        if (m > 0) {
            props.center.set((float) (mx / m), (float) (my / m));
            props.inertia = (float) (i0 - m * props.center.len2());
        }
    }
}
