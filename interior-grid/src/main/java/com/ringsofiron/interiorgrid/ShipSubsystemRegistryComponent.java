package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Attached to a parent ship entity. Holds a reference to all child ECS entities
 * that are subsystems of this ship's interior grid.
 *
 * Saves keep only the {@link EntityRef}s ({@link #ownerRef}, {@link #subsystemRefs}).
 * The live entities ({@link #ownerEntity}, {@link #subsystems}) are a cache that is
 * not saved; after a load, {@link #relink(UuidRegistry)} rebuilds it.
 */
public class ShipSubsystemRegistryComponent implements Component {
    /** The ship's system entities. Not saved; see {@link #relink(UuidRegistry)}. */
    public transient Array<Entity> subsystems = new Array<>();
    /** The ship entity. Not saved; see {@link #relink(UuidRegistry)}. */
    public transient Entity ownerEntity;

    /** Saved references to the system entities, for those that have a UUID. */
    public Array<EntityRef> subsystemRefs = new Array<>();
    /** Saved reference to the ship entity, or null if it has no UUID. */
    public EntityRef ownerRef;

    /** For deserialization. */
    public ShipSubsystemRegistryComponent() {}

    public ShipSubsystemRegistryComponent(Entity owner) {
        this.ownerEntity = owner;
        this.ownerRef = refOf(owner);
    }

    public void registerSubsystem(Entity subsystem) {
        subsystems.add(subsystem);
        EntityRef ref = refOf(subsystem);
        if (ref != null) subsystemRefs.add(ref);
    }

    /**
     * Rebuilds the live entities from the saved references after a load. References to
     * entities no longer in the engine are dropped.
     */
    public void relink(UuidRegistry registry) {
        ownerEntity = ownerRef == null ? null : ownerRef.get(registry);
        subsystems.clear();
        for (int i = subsystemRefs.size - 1; i >= 0; i--) {
            Entity e = subsystemRefs.get(i).get(registry);
            if (e == null) subsystemRefs.removeIndex(i);
        }
        for (EntityRef ref : subsystemRefs) subsystems.add(ref.get(registry));
    }

    private static EntityRef refOf(Entity e) {
        return e != null && e.getComponent(UuidComponent.class) != null ? EntityRef.of(e) : null;
    }
}
