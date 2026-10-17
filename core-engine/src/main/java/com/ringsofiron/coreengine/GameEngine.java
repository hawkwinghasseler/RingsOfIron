package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Main wrapper for the Ashley ECS and core services.
 */
public class GameEngine {
    private final PooledEngine ecsEngine;
    private final UuidRegistry uuidRegistry;
    private final SerializationService serializationService;

    public GameEngine() {
        this.ecsEngine = new PooledEngine();
        this.uuidRegistry = new UuidRegistry();
        
        // Listen for UUID components to maintain O(1) lookup
        this.ecsEngine.addEntityListener(Family.all(UuidComponent.class).get(), new UuidEntityListener(uuidRegistry));
        
        this.serializationService = new SerializationService(this.ecsEngine);
    }

    /**
     * Ticks the ECS engine. Should be called in the main LibGDX render loop.
     * @param deltaTime The time since the last frame.
     */
    public void update(float deltaTime) {
        ecsEngine.update(deltaTime);
    }

    public void addSystem(EntitySystem system) {
        ecsEngine.addSystem(system);
    }

    public void removeSystem(EntitySystem system) {
        ecsEngine.removeSystem(system);
    }

    public Entity createEntity() {
        return ecsEngine.createEntity();
    }

    public void addEntity(Entity entity) {
        ecsEngine.addEntity(entity);
    }

    public void removeEntity(Entity entity) {
        ecsEngine.removeEntity(entity);
    }
    
    public Engine getEcsEngine() {
        return ecsEngine;
    }

    public UuidRegistry getUuidRegistry() {
        return uuidRegistry;
    }

    public SerializationService getSerializationService() {
        return serializationService;
    }
    
    public void clear() {
        ecsEngine.removeAllEntities();
        uuidRegistry.clear();
    }
}
