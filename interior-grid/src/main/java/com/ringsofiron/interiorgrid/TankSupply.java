package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Draws fluids from tanks. A system reaches the tanks on every pipe network its footprint touches
 * ({@link SubsystemComponent#pipedTanks}); a draw comes out of the tanks that can supply it in
 * proportion to what each holds, so they run down together.
 *
 * <p>A ship with no tank of a fluid at all is a layout from before tanks: its systems are not
 * limited by that fluid (see {@link #hasTanks}). Once a layout has one, only piped tanks count.
 */
public final class TankSupply {
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    private TankSupply() {
    }

    /** Kilograms of {@code fluid} the tanks that can supply it hold. */
    public static double available(Array<SubsystemComponent> tanks, Fluid fluid) {
        double sum = 0;
        for (SubsystemComponent t : tanks) if (t.fluid == fluid && t.canSupply()) sum += Math.max(0, t.contents);
        return sum;
    }

    /** Kilograms of {@code fluid} the tanks piped to {@code system} can deliver. */
    public static double available(SubsystemComponent system, Fluid fluid) {
        return available(system.pipedTanks, fluid);
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

    /** Takes up to {@code kg} of {@code fluid} from the tanks piped to {@code system}. */
    public static double draw(SubsystemComponent system, Fluid fluid, double kg) {
        return draw(system.pipedTanks, fluid, kg);
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
