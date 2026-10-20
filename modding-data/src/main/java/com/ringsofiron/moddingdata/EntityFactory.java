package com.ringsofiron.moddingdata;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Builds ECS entities from definitions: {@code factory.spawn("ship", "corvette_patrol", x, y, angle)}.
 *
 * Every spawned entity gets a {@link UuidComponent}, a {@link TransformComponent} and a
 * {@link DefRefComponent}. Everything else comes from the {@link ComponentAssembler}s
 * registered for the def type, run in registration order. Spawn listeners (mods, UI)
 * are told once the entity is in the engine.
 */
public class EntityFactory {

    private final GameEngine engine;
    private final DefRegistry defs;
    private final Map<String, List<ComponentAssembler>> assemblers = new HashMap<>();
    private final List<BiConsumer<Entity, DefEntry>> spawnListeners = new ArrayList<>();

    public EntityFactory(GameEngine engine, DefRegistry defs) {
        this.engine = engine;
        this.defs = defs;
    }

    public GameEngine engine() {
        return engine;
    }

    public DefRegistry defs() {
        return defs;
    }

    public void registerAssembler(String defType, ComponentAssembler assembler) {
        assemblers.computeIfAbsent(defType, t -> new ArrayList<>()).add(assembler);
    }

    public List<ComponentAssembler> assemblers(String defType) {
        List<ComponentAssembler> list = assemblers.get(defType);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public void addSpawnListener(BiConsumer<Entity, DefEntry> listener) {
        spawnListeners.add(listener);
    }

    public Entity spawn(String defType, String defId) {
        return spawn(defType, defId, 0f, 0f, 0f);
    }

    public Entity spawnShip(String shipId, float x, float y, float rotation) {
        return spawn("ship", shipId, x, y, rotation);
    }

    /**
     * Builds the entity, adds it to the engine and returns it.
     *
     * @throws IllegalArgumentException if the definition does not exist
     */
    public Entity spawn(String defType, String defId, float x, float y, float rotation) {
        Entity entity = build(defType, defId, x, y, rotation);
        engine.addEntity(entity);
        DefEntry def = defs.entry(defType, defId);
        for (BiConsumer<Entity, DefEntry> l : new ArrayList<>(spawnListeners)) l.accept(entity, def);
        return entity;
    }

    /** Builds the entity without adding it to the engine, e.g. for previews or tests. */
    public Entity build(String defType, String defId, float x, float y, float rotation) {
        DefEntry def = defs.entry(defType, defId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown " + defType + " '" + defId + "'. Known: " + defs.ids(defType));
        }
        Entity entity = engine.createEntity();
        entity.add(new UuidComponent(engine.newUuid()));
        entity.add(new TransformComponent(x, y, rotation));
        entity.add(new DefRefComponent(defType, defId));
        for (ComponentAssembler a : assemblers(defType)) a.assemble(entity, def, this);
        return entity;
    }
}
