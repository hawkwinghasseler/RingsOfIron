package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;

/**
 * An {@link EntityListener} that hears only removals, so it can be written as a lambda.
 *
 * <p>The game's engine is a {@link com.badlogic.ashley.core.PooledEngine}: a removed entity is reset
 * and handed out again, the same object, by a later {@code createEntity}. A map or set keyed by
 * {@link Entity} that still holds a removed one gives its entry to whatever new entity comes out of
 * the pool next. Added with no family ({@code engine.addEntityListener(listener)}), this hears each
 * entity as it leaves the engine and before the pool takes it back: the moment to drop it from such
 * a cache.
 */
@FunctionalInterface
public interface EntityRemovalListener extends EntityListener {
    @Override
    default void entityAdded(Entity entity) {
    }
}
