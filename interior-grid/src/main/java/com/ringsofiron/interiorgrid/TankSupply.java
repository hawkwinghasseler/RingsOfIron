package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Draws fluids from tanks. A system reaches the tanks on every pipe network its footprint touches
 * ({@link SubsystemComponent#pipedTanks}); a draw comes out of the tanks that can supply it in
 * proportion to what each holds, so they run down together.
 *
 * <p>A ship with no tank of a fluid at all is a layout from before tanks: its systems are not
 * limited by that fluid (see {@link #hasTanks}). Once a layout has one, only piped tanks count.
 *
 * <p>A tank damaged below {@link #LEAK_THRESHOLD} leaks ({@link #leak}) until the crew isolate it, and what the tanks hold
 * weighs on the ship ({@link #addMassTo}).
 */
public final class TankSupply {
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    /** A tank's health below which it leaks. */
    public static final float LEAK_THRESHOLD = 0.6f;
    /** Share of its contents a destroyed tank loses each second; a less damaged one, in proportion. */
    public static final double LEAK_RATE = 0.02;

    private TankSupply() {
    }

    /** Share of its contents a tank at {@code health} loses each second: 0 at or above {@link #LEAK_THRESHOLD}. */
    public static double leakRate(float health) {
        if (health >= LEAK_THRESHOLD) return 0;
        return LEAK_RATE * (LEAK_THRESHOLD - Math.max(0f, health)) / LEAK_THRESHOLD;
    }

    /**
     * Lets every damaged tank of the ship leak for {@code dt} seconds. Oxygen and nitrogen escape into
     * the air of the room the tank stands in (lost to space where its tiles hold no air); propellant
     * is lost overboard. Returns the kilograms lost.
     */
    public static double leak(ShipGridComponent grid, ShipSubsystemRegistryComponent registry, double dt) {
        double lost = 0;
        for (Entity e : registry.subsystems) {
            SubsystemComponent t = SUBSYSTEMS.get(e);
            if (t == null || t.type != SubsystemComponent.SubsystemType.TANK || t.contents <= 0) continue;
            double rate = leakRate(t.health);
            // Isolated by the crew: its shut valve holds what is left, unless the tank itself is wrecked
            if (rate <= 0 || (t.isolated && t.health > 0f)) continue;
            double kg = t.contents * (1 - Math.exp(-rate * dt));
            t.contents -= kg;
            lost += kg;
            if (t.fluid == Fluid.PROPELLANT) continue;
            Compartment room = roomOf(grid, t.footprint);
            if (room == null) continue;
            double moles = kg / t.fluid.molarMass;
            if (t.fluid == Fluid.OXYGEN) room.oxygen += moles;
            else room.nitrogen += moles;
        }
        return lost;
    }

    /** The first room with air among the footprint's tiles, or null. */
    private static Compartment roomOf(ShipGridComponent grid, MountFootprint f) {
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (!grid.inBounds(x, y)) continue;
                Compartment c = grid.compartmentAt(x, y);
                if (c != null) return c;
            }
        }
        return null;
    }

    /** Kilograms all the ship's tanks hold. */
    public static double contents(ShipSubsystemRegistryComponent registry) {
        double kg = 0;
        for (SubsystemComponent t : tanks(registry, null)) kg += Math.max(0, t.contents);
        return kg;
    }

    /**
     * Adds what the tanks hold to structural mass properties, as a point mass at the middle of each
     * tank's footprint, keeping the combined center of mass and moment of inertia about it.
     */
    public static void addMassTo(ShipGridComponent grid, ShipSubsystemRegistryComponent registry,
                                 ShipGridComponent.MassProperties props) {
        double m = props.mass;
        double mx = m * props.center.x, my = m * props.center.y;
        double i0 = props.inertia + m * props.center.len2();
        for (SubsystemComponent t : tanks(registry, null)) {
            double tm = Math.max(0, t.contents);
            if (tm <= 0) continue;
            MountFootprint f = t.footprint;
            Vector2 a = grid.tileToLocal(f.originX, f.originY);
            Vector2 b = grid.tileToLocal(f.originX + f.width() - 1, f.originY + f.height() - 1);
            double px = (a.x + b.x) / 2, py = (a.y + b.y) / 2;
            double w = f.width() * grid.tileSize, h = f.height() * grid.tileSize;
            m += tm;
            mx += tm * px;
            my += tm * py;
            i0 += tm * (px * px + py * py + (w * w + h * h) / 12.0);
        }
        props.mass = (float) m;
        if (m > 0) {
            props.center.set((float) (mx / m), (float) (my / m));
            props.inertia = (float) (i0 - m * props.center.len2());
        }
    }

    /** Kilograms of {@code fluid} the tanks that can supply it hold. */
    public static double available(Array<SubsystemComponent> tanks, Fluid fluid) {
        double sum = 0;
        for (SubsystemComponent t : tanks) if (t.fluid == fluid && t.canSupply()) sum += Math.max(0, t.contents);
        return sum;
    }

    /**
     * Kilograms of {@code fluid} the tanks piped to {@code system} can deliver, and for oxygen at life
     * support, its own emergency oxygen ({@link #reserve}).
     */
    public static double available(SubsystemComponent system, Fluid fluid) {
        return available(system.pipedTanks, fluid) + (fluid == Fluid.OXYGEN ? reserve(system) : 0);
    }

    /** Kilograms of its emergency oxygen ({@link LifeSupportSystem#EMERGENCY_OXYGEN_KG}) a life support unit has left; 0 for other systems. */
    public static double reserve(SubsystemComponent system) {
        if (system.type != SubsystemComponent.SubsystemType.LIFE_SUPPORT) return 0;
        return Math.max(0, LifeSupportSystem.EMERGENCY_OXYGEN_KG * system.throughput - system.reserveUsed);
    }

    /**
     * Takes up to {@code kg} of {@code fluid} from the tanks, shared by what each holds.
     * Returns the kilograms taken.
     */
    public static double draw(Array<SubsystemComponent> tanks, Fluid fluid, double kg) {
        if (kg <= 0) return 0;
        double total = available(tanks, fluid);
        if (total <= 0) return 0;
        double take = Math.min(kg, total);
        double share = take / total;
        for (SubsystemComponent t : tanks) {
            if (t.fluid != fluid || !t.canSupply() || t.contents <= 0) continue;
            t.contents = Math.max(0, t.contents - t.contents * share);
        }
        return take;
    }

    /**
     * Takes up to {@code kg} of {@code fluid} from the tanks piped to {@code system}, and for oxygen at
     * life support, what they can't give from its emergency oxygen.
     */
    public static double draw(SubsystemComponent system, Fluid fluid, double kg) {
        double take = draw(system.pipedTanks, fluid, kg);
        if (fluid != Fluid.OXYGEN || take >= kg) return take;
        double own = Math.min(kg - take, reserve(system));
        system.reserveUsed += own;
        return take + own;
    }

    /** True if any of the ship's systems is a tank of {@code fluid}, working or not. */
    public static boolean hasTanks(ShipSubsystemRegistryComponent registry, Fluid fluid) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s != null && s.type == SubsystemComponent.SubsystemType.TANK && s.fluid == fluid) return true;
        }
        return false;
    }

    /** Every tank of the ship holding {@code fluid}, or of any fluid when it is null. */
    public static Array<SubsystemComponent> tanks(ShipSubsystemRegistryComponent registry, Fluid fluid) {
        Array<SubsystemComponent> out = new Array<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s != null && s.type == SubsystemComponent.SubsystemType.TANK && (fluid == null || s.fluid == fluid)) out.add(s);
        }
        return out;
    }
}
