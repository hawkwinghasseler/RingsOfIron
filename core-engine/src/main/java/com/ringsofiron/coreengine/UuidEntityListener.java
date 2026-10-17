package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Automatically registers and unregisters entities with a UuidComponent in the UuidRegistry.
 */
public class UuidEntityListener implements EntityListener {
    private final UuidRegistry registry;
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    public UuidEntityListener(UuidRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void entityAdded(Entity entity) {
        UuidComponent uuidComponent = uuidMapper.get(entity);
        if (uuidComponent != null) {
            registry.register(uuidComponent.id, entity);
        }
    }

    @Override
    public void entityRemoved(Entity entity) {
        UuidComponent uuidComponent = uuidMapper.get(entity);
        if (uuidComponent != null) {
            registry.unregister(uuidComponent.id);
        }
    }
}
