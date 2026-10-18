package com.ringsofiron.coreengine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SimClock;

/**
 * A system that updates on fixed sim ticks rather than once per rendered frame.
 *
 * The time step is rounded to a whole number of 60 Hz sim ticks, so a 0.25 s system runs every 15th
 * tick and a 1 s system every 60th. {@link #updateFixed(float)} always receives that rounded step.
 *
 * Under a {@link GameEngine} the engine's single tick counter drives every fixed system in priority
 * order, and rails warp calls {@link #catchUp(long, double)} once instead of running each skipped
 * tick. Added to a plain Ashley engine instead, the system keeps its own accumulator.
 */
public abstract class FixedLogicSystem extends EntitySystem {
    private final int tickInterval;
    private final float stepSeconds;
    private float accumulator = 0f;
    private GameEngine driver;

    /**
     * @param timeStep The fixed delta time in seconds (e.g. 1/60f for 60 updates per second)
     */
    public FixedLogicSystem(float timeStep) {
        this(0, timeStep);
    }

    public FixedLogicSystem(int priority, float timeStep) {
        super(priority);
        if (!(timeStep > 0f)) throw new IllegalArgumentException("timeStep must be positive: " + timeStep);
        this.tickInterval = (int) SimClock.secondsToTicks(timeStep);
        this.stepSeconds = (float) (tickInterval * SimClock.TICK_SECONDS);
    }

    @Override
    public final void update(float deltaTime) {
        if (driver != null) {
            driver.runFixedSystem(this);
            return;
        }
        accumulator += deltaTime;
        while (accumulator >= stepSeconds) {
            updateFixed(stepSeconds);
            accumulator -= stepSeconds;
        }
        updateInterpolated(accumulator / stepSeconds);
    }

    /**
     * Called at a fixed rate, regardless of the rendering frame rate.
     * @param deltaTime The fixed time step in seconds
     */
    protected abstract void updateFixed(float deltaTime);

    /**
     * Optional hook that runs every frame with the interpolation alpha (0..1) between the last fixed
     * step and the next.
     */
    protected void updateInterpolated(float alpha) {
        // Default empty implementation
    }

    /**
     * Coarse update for a rails-warp jump of {@code ticks} sim ticks, run once instead of once per
     * skipped tick (e.g. oxygen regeneration, repairs progressing). Default: nothing happens.
     */
    protected void catchUp(long ticks, double seconds) {
        // Default empty implementation
    }

    /** Sim ticks between fixed updates. */
    public final int getTickInterval() {
        return tickInterval;
    }

    /** The rounded time step passed to {@link #updateFixed(float)}. */
    public final float getStepSeconds() {
        return stepSeconds;
    }

    /** The clock driving this system, or null when it runs on its own accumulator in a plain Ashley engine. */
    protected final SimClock getClock() {
        return driver;
    }

    // ---- Driven by GameEngine; not for general use. ----

    /** Called by {@link GameEngine} when it takes over driving this system. */
    public final void bindDriver(GameEngine engine) {
        this.driver = engine;
        this.accumulator = 0f;
    }

    public final boolean isBoundTo(GameEngine engine) {
        return driver == engine;
    }

    public final void invokeFixed() {
        updateFixed(stepSeconds);
    }

    public final void invokeInterpolated(float alpha) {
        updateInterpolated(alpha);
    }

    public final void invokeCatchUp(long ticks, double seconds) {
        catchUp(ticks, seconds);
    }
}
