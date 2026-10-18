package com.ringsofiron.coreengine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.coreengine.GameEngine;

/**
 * A system that runs once per rendered frame with the interpolation alpha (0..1) between the last
 * fixed sim tick and the next, so drawing can blend previous and current state.
 *
 * Under a {@link GameEngine} it never runs during fixed ticks. In a plain Ashley engine alpha is 0.
 */
public abstract class RenderSystem extends EntitySystem {
    private GameEngine engine;

    public RenderSystem() {
        super();
    }

    public RenderSystem(int priority) {
        super(priority);
    }

    @Override
    public final void update(float deltaTime) {
        render(deltaTime, engine == null ? 0f : engine.getAlpha());
    }

    protected abstract void render(float deltaTime, float alpha);

    /** Called by {@link GameEngine}; not for general use. */
    public final void bindEngine(GameEngine engine) {
        this.engine = engine;
    }

    public final boolean isBoundTo(GameEngine engine) {
        return this.engine == engine;
    }
}
