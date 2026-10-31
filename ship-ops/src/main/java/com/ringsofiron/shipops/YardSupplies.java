package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;

import static com.ringsofiron.shipops.StationYard.NITROGEN_PRICE;
import static com.ringsofiron.shipops.StationYard.OXYGEN_PRICE;
import static com.ringsofiron.shipops.StationYard.PROPELLANT_PRICE;

/** {@link StationYard}'s tanks and air: what filling them costs at the base prices, and the filling. */
final class YardSupplies {
    private YardSupplies() {
    }

    public static double pricePerKg(Fluid fluid) {
        switch (fluid) {
            case OXYGEN: return OXYGEN_PRICE;
            case NITROGEN: return NITROGEN_PRICE;
            default: return PROPELLANT_PRICE;
        }
    }

    /** Credits to fill every tank aboard {@code ship}. */
    static double refillCost(Entity ship) {
        double total = 0;
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return 0;
        for (SubsystemComponent t : TankSupply.tanks(registry, null)) {
            total += Math.max(0, t.capacity - t.contents) * pricePerKg(t.fluid);
        }
        for (SubsystemComponent u : lifeSupport(registry)) total += u.reserveUsed * OXYGEN_PRICE;
        return total;
    }

    /** The life support units in {@code registry}, whose emergency oxygen a refill makes good. */
    private static Array<SubsystemComponent> lifeSupport(ShipSubsystemRegistryComponent registry) {
        Array<SubsystemComponent> out = new Array<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) out.add(s);
        }
        return out;
    }

    /**
     * Fills every tank in {@code registry} at the yard's prices, as far as {@code budget} credits
     * go: short of them, every tank gets the same share of what it lacks. Returns the credits spent.
     */
    public static double fill(ShipSubsystemRegistryComponent registry, double budget) {
        if (registry == null || budget <= 0) return 0;
        Array<SubsystemComponent> tanks = TankSupply.tanks(registry, null), units = lifeSupport(registry);
        double cost = 0;
        for (SubsystemComponent t : tanks) cost += Math.max(0, t.capacity - t.contents) * pricePerKg(t.fluid);
        for (SubsystemComponent u : units) cost += u.reserveUsed * OXYGEN_PRICE;
        if (cost <= 0) return 0;
        double share = Math.min(1, budget / cost);
        for (SubsystemComponent t : tanks) t.contents += Math.max(0, t.capacity - t.contents) * share;
        for (SubsystemComponent u : units) u.reserveUsed -= u.reserveUsed * share;
        return cost * share;
    }

    /** Credits, at the base prices, to bring every room aboard {@code ship} back to station pressure. */
    public static double airCost(Entity ship) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        return grid == null ? 0 : air(grid, 0);
    }


    /**
     * Brings every room in {@code grid} up to station pressure and the standard oxygen share, with
     * station air at the refill's prices, as far as {@code budget} credits go: short of them, every
     * room gets the same share of what it lacks. Returns the credits spent, at the base prices.
     */
    public static double topUpAir(ShipGridComponent grid, double budget) {
        if (grid == null || budget <= 0) return 0;
        // The repaired walls and hull may join or close off rooms: fill the rooms as they are now
        CompartmentBuilder.rebuildIfChanged(grid);
        double cost = air(grid, 0);
        if (cost <= 0) return 0;
        double share = Math.min(1, budget / cost);
        air(grid, share);
        return cost * share;
    }

    /**
     * What it costs to bring every room in {@code grid} to station pressure and the standard oxygen
     * share; with {@code share} above 0, also puts that share of the missing air in.
     */
    private static double air(ShipGridComponent grid, double share) {
        double cost = 0;
        for (Compartment c : grid.compartments) {
            double perMole = c.kPaPerMole();
            if (perMole <= 0) continue;
            // Standard air up to station pressure, then oxygen for what fires and breathing burned
            double fill = Math.max(0, (Compartment.STANDARD_PRESSURE - c.pressure()) / perMole);
            double o2 = fill * Compartment.STANDARD_O2_FRACTION, n2 = fill - o2;
            double others = c.totalMoles() + fill - c.oxygen - o2;
            double want = Math.min(others * Compartment.STANDARD_O2_FRACTION / (1 - Compartment.STANDARD_O2_FRACTION),
                    Compartment.STANDARD_O2_PRESSURE / perMole);
            o2 += Math.max(0, want - c.oxygen - o2);
            cost += o2 * Fluid.OXYGEN.molarMass * OXYGEN_PRICE + n2 * Fluid.NITROGEN.molarMass * NITROGEN_PRICE;
            if (share > 0) {
                c.oxygen += o2 * share;
                c.nitrogen += n2 * share;
            }
        }
        return cost;
    }
}
