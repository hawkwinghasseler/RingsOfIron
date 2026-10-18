package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * A reference from a component to another entity, held as that entity's UUID.
 *
 * Components must use this instead of a direct {@link Entity} field so that saves stay flat: an
 * EntityRef is written as a plain UUID string. Resolve it to the live entity with
 * {@link #get(UuidRegistry)}, an O(1) lookup that stays correct across save/load and entity pooling.
 */
public final class EntityRef {
    private static final ComponentMapper<UuidComponent> UUIDS = ComponentMapper.getFor(UuidComponent.class);

    private String uuid;

    public EntityRef() {
    }

    public EntityRef(String uuid) {
        this.uuid = uuid;
    }

    /** @throws IllegalArgumentException if the entity has no {@link UuidComponent}. */
    public static EntityRef of(Entity entity) {
        UuidComponent id = UUIDS.get(entity);
        if (id == null) throw new IllegalArgumentException("Entity has no UuidComponent, so it cannot be referenced");
        return new EntityRef(id.id);
    }

    public String getUuid() {
        return uuid;
    }

    /** @return the live entity, or null if it is not (or no longer) in the engine. */
    public Entity get(UuidRegistry registry) {
        return uuid == null ? null : registry.getEntity(uuid);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityRef && java.util.Objects.equals(uuid, ((EntityRef) o).uuid);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        return "EntityRef(" + uuid + ")";
    }
}
