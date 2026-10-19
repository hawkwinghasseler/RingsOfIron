package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * A connected run of pipes and the ship systems it reaches: every tank whose footprint it touches,
 * and every other system it touches that draws from them. Rebuilt by {@link PipeRouting}; not saved.
 */
public class PipeNetwork {
    /** The tanks on this network. */
    public final Array<SubsystemComponent> tanks = new Array<>();
    /** The other systems on this network: life support, engines and anything else it touches. */
    public final Array<SubsystemComponent> systems = new Array<>();

    /** Kilograms of {@code fluid} its tanks can deliver now. */
    public double available(Fluid fluid) {
        return TankSupply.available(tanks, fluid);
    }
}
