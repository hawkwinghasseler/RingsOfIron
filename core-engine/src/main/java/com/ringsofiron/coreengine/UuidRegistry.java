package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Entity;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a map of UUIDs to their corresponding Entity instances for O(1) lookups.
 */
public class UuidRegistry {
    private final Map<String, Entity> entityMap = new HashMap<>();

    public void register(String uuid, Entity entity) {
        entityMap.put(uuid, entity);
    }

    public void unregister(String uuid) {
        entityMap.remove(uuid);
    }

    public Entity getEntity(String uuid) {
        return entityMap.get(uuid);
    }
    
    public void clear() {
        entityMap.clear();
    }
}
