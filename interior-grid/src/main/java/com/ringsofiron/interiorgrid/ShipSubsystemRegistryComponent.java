package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/**
 * Attached to a parent ship entity. Holds a reference to all child ECS entities
 * that are subsystems of this ship's interior grid.
 */
public class ShipSubsystemRegistryComponent implements Component {
    public final com.badlogic.gdx.utils.Array<Entity> subsystems = new com.badlogic.gdx.utils.Array<>();
    public Entity ownerEntity;

    public ShipSubsystemRegistryComponent(Entity owner) {
        this.ownerEntity = owner;
    }

    public void registerSubsystem(Entity subsystem) {
        subsystems.add(subsystem);
    }
}
