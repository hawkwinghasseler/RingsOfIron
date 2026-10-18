package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

/**
 * An IteratingSystem on fixed sim ticks, so crew keep pace with the hazard and physics
 * systems under physics warp instead of running once per rendered frame.
 */
public abstract class FixedIteratingSystem extends FixedLogicSystem {

    private final Family family;
    private ImmutableArray<Entity> entities;

    protected FixedIteratingSystem(Family family, int priority, float timeStep) {
        super(priority, timeStep);
        this.family = family;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(family);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    protected void updateFixed(float dt) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) processEntity(entities.get(i), dt);
    }

    protected abstract void processEntity(Entity entity, float dt);

    /** The entities in this system's family; empty before it is added to an engine. */
    public ImmutableArray<Entity> getEntities() {
        return entities;
    }
}
