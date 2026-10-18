package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SaveParticipant;

/**
 * Makes ships save and load cleanly. Creating it registers {@link ShipGridSerializer} for
 * grids. After a load it restores the links that saves leave out: each ship's
 * {@link ShipSubsystemRegistryComponent} entities and each mount's installed system. It
 * saves nothing itself; it is read after every entity is loaded, which is when the links
 * can be resolved.
 *
 * <pre>
 * engine.getSerializationService().addParticipant(new InteriorGridSaveParticipant(engine));
 * </pre>
 */
public class InteriorGridSaveParticipant implements SaveParticipant {

    private final GameEngine engine;

    public InteriorGridSaveParticipant(GameEngine engine) {
        this.engine = engine;
        engine.getSerializationService().registerSerializer(ShipGridComponent.class, new ShipGridSerializer());
    }

    @Override
    public String saveKey() {
        return "interior-grid";
    }

    @Override
    public void write(Json json) {
        // Nothing to save; the entry exists so read() runs after a load
    }

    @Override
    public void read(Json json, JsonValue data) {
        for (Entity e : engine.getEcsEngine().getEntitiesFor(Family.all(ShipSubsystemRegistryComponent.class).get())) {
            ShipSubsystemRegistryComponent registry = e.getComponent(ShipSubsystemRegistryComponent.class);
            registry.relink(engine.getUuidRegistry());
            ShipGridComponent grid = e.getComponent(ShipGridComponent.class);
            if (grid != null) grid.relinkMounts(registry.subsystems);
        }
    }
}
