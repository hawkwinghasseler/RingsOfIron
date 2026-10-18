package com.ringsofiron.macrosim;

import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;

/**
 * Drives {@link MacroSimulation} from core-engine's sim clock: every fixed tick at 1x and physics
 * warp, and once per rails-warp jump. {@code advanceTo} fires every event inside the jump in time
 * order, so a jump needs no coarser treatment than a tick.
 *
 * <p>Runs after physics-exterior has stepped and rebased, so the bubble's true states and anchor are
 * at the same sim second as the clock; before it, every handoff would be a tick of orbital speed off.
 */
public class MacroSimSystem extends FixedLogicSystem {
    public static final int DEFAULT_PRIORITY = FloatingOriginSystem.DEFAULT_PRIORITY + 5;

    private final MacroSimulation sim;
    private final SimClock clock;

    public MacroSimSystem(MacroSimulation sim, SimClock clock) {
        super(DEFAULT_PRIORITY, (float) SimClock.TICK_SECONDS);
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
