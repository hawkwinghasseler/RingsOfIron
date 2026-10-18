package com.ringsofiron.macrosim;

import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

/**
 * Drives {@link MacroSimulation} from core-engine's sim clock: every fixed tick at 1x and physics
 * warp, and once per rails-warp jump. {@code advanceTo} fires every event inside the jump in time
 * order, so a jump needs no coarser treatment than a tick.
 */
public class MacroSimSystem extends FixedLogicSystem {
    private final MacroSimulation sim;
    private final SimClock clock;

    public MacroSimSystem(MacroSimulation sim, SimClock clock) {
        super((float) SimClock.TICK_SECONDS);
        this.sim = sim;
        this.clock = clock;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        sim.advanceTo(clock.getSimSeconds());
    }

    @Override
    protected void catchUp(long ticks, double seconds) {
        sim.advanceTo(clock.getSimSeconds());
    }
}
