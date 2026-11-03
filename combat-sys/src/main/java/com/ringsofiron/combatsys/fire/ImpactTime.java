package com.ringsofiron.combatsys.fire;

import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.defs.MissileDef;

/**
 * When a missile reaches its target, with its closing burn in it. The closest approach at the present
 * speed is an upper bound that runs long while the motor burns: a harpoon from 7.7 km reads 37 s five
 * seconds after launch, when it lands in about 17. Guidance spends {@link MissileDef#closingBurn}
 * along the line of sight while it has fuel above its {@link MissileDef#terminalReserve}
 * ({@code MissileGuidanceSystem}), so the missile closes at constant acceleration a until that fuel
 * is gone and coasts after: t = (−v + √(v² + 2ad)) / a while it burns.
 */
public final class ImpactTime {
    private ImpactTime() {
    }

    /**
     * Seconds to cover {@code range} m closing at {@code closing} m/s and speeding up at
     * {@code accel} m/s² for at most {@code burnDeltaV} m/s more, then coasting; NaN when it never
     * gets there (opening with no burn to turn it round).
     */
    public static float solve(float range, float closing, float accel, float burnDeltaV) {
        if (!(range > 0f)) return 0f;
        if (!(accel > 0f) || !(burnDeltaV > 0f)) return closing > 1e-3f ? range / closing : Float.NaN;
        float burnTime = burnDeltaV / accel;
        // Constant acceleration the whole way
        float t = (float) ((-closing + Math.sqrt((double) closing * closing + 2.0 * accel * range)) / accel);
        if (t <= burnTime) return t;
        // The burn ends first: what it covers, then a coast at the speed it ends on
        float covered = closing * burnTime + 0.5f * accel * burnTime * burnTime;
        float speed = closing + accel * burnTime;
        if (speed <= 1e-3f) return Float.NaN;
        return burnTime + (range - covered) / speed;
    }

    /**
     * {@link #solve} for missile {@code m}, {@code range} m from its target and closing at
     * {@code closing} m/s: its closing burn while its fuel above the reserve lasts, none once
     * guidance is cut or it is coasting.
     */
    public static float of(MissileComponent m, float range, float closing) {
        MissileDef def = m.def;
        if (def == null || m.guidanceCut || m.phase == MissileComponent.Phase.COAST) return solve(range, closing, 0f, 0f);
        float reserve = def.terminalReserve * def.propellantMass;
        float above = m.propellant - reserve;
        if (above <= 0f) return solve(range, closing, 0f, 0f);
        // Rocket equation over the fuel above the reserve
        float wet = def.dryMass + m.propellant, dry = def.dryMass + reserve;
        float dv = (float) (def.exhaustVelocity * Math.log(wet / dry));
        return solve(range, closing, def.closingBurn, dv);
    }
}
