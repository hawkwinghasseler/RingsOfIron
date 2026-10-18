package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.EntitySystem;

import java.util.function.DoubleSupplier;

/**
 * Ashley adapter: each engine update advances the macro universe to the current sim time.
 * The supplier will be {@code SimClock::getSimSeconds} once core-engine's clock lands.
 */
public class MacroSimSystem extends EntitySystem {
    private final MacroSimulation sim;
    private final DoubleSupplier simSeconds;

    public MacroSimSystem(MacroSimulation sim, DoubleSupplier simSeconds) {
        this.sim = sim;
        this.simSeconds = simSeconds;
    }

    @Override
    public void update(float deltaTime) {
        sim.advanceTo(simSeconds.getAsDouble());
    }
}
