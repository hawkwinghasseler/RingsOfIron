package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.ringsofiron.coreengine.components.TransientComponent;

public class SerializationService {
    private final Engine engine;
    private final Json json;
    private final ComponentMapper<TransientComponent> transientMapper = ComponentMapper.getFor(TransientComponent.class);

    public SerializationService(Engine engine) {
        this.engine = engine;
        this.json = new Json();
        this.json.setOutputType(OutputType.json);
    }

    /**
     * Serializes all non-transient entities in the engine to a JSON string.
     */
    public String saveGame() {
        Array<EntityData> allData = new Array<>();
        for (Entity entity : engine.getEntities()) {
            if (transientMapper.has(entity)) {
                continue; // Skip transient entities
            }
            
            EntityData data = new EntityData();
            for (Component component : entity.getComponents()) {
                data.components.add(component);
            }
            allData.add(data);
        }
        
        return json.prettyPrint(allData);
    }

    /**
     * Loads entities from a JSON string into the ECS engine.
     */
    @SuppressWarnings("unchecked")
    public void loadGame(String jsonString) {
        engine.removeAllEntities(); // Clear current state
        
        Array<EntityData> allData = json.fromJson(Array.class, EntityData.class, jsonString);
        if (allData != null) {
            for (EntityData data : allData) {
                Entity entity = engine.createEntity();
                for (Component component : data.components) {
                    entity.add(component);
                }
                engine.addEntity(entity);
            }
        }
    }
    
    /**
     * Internal POJO to facilitate JSON serialization of entities.
     */
    public static class EntityData {
        public Array<Component> components = new Array<>();
    }
}
