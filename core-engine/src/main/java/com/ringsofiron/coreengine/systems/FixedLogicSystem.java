package com.ringsofiron.coreengine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;

/**
 * A base system class that restricts its updates to fixed physics ticks rather than variable frame rates.
 */
public abstract class FixedLogicSystem extends EntitySystem {
    private float accumulator = 0f;
    private final float timeStep;

    /**
     * @param timeStep The fixed delta time (e.g., 1/60f for 60 updates per second)
     */
    public FixedLogicSystem(float timeStep) {
        this.timeStep = timeStep;
    }

    public FixedLogicSystem(int priority, float timeStep) {
        super(priority);
        this.timeStep = timeStep;
    }

    @Override
    public final void update(float deltaTime) {
        accumulator += deltaTime;
        while (accumulator >= timeStep) {
            updateFixed(timeStep);
            accumulator -= timeStep;
        }
    }

    /**
     * Called at a fixed rate, regardless of the rendering frame rate.
     * @param deltaTime The fixed time step
     */
    protected abstract void updateFixed(float deltaTime);
    
    /**
     * Optional hook to process interpolation or rendering logic if needed,
     * which runs every frame with the leftover accumulator as alpha.
     */
    protected void updateInterpolated(float alpha) {
        // Default empty implementation
    }
}
