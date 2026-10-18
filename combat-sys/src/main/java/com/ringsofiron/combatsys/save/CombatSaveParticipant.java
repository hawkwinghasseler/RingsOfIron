package com.ringsofiron.combatsys.save;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Saves the entity links that combat components keep as transient fields: each sensor's locked
 * target, and each weapon mount's target and subsystem. They are written by UUID and relinked
 * after every entity is loaded. Sensor tracks and rounds in flight are not saved.
 *
 * <pre>
 * engine.getSerializationService().addParticipant(new CombatSaveParticipant(engine));
 * </pre>
 */
public class CombatSaveParticipant implements SaveParticipant {

    private final GameEngine engine;

    public CombatSaveParticipant(GameEngine engine) {
        this.engine = engine;
    }

    @Override
    public String saveKey() {
        return "combat-sys";
    }

    @Override
    public void write(Json json) {
        json.writeArrayStart("ships");
        for (Entity e : engine.getEcsEngine().getEntitiesFor(Family.all(UuidComponent.class)
                .one(SensorComponent.class, WeaponsComponent.class).get())) {
            json.writeObjectStart();
            json.writeValue("id", e.getComponent(UuidComponent.class).id);
            SensorComponent sensor = e.getComponent(SensorComponent.class);
            if (sensor != null) json.writeValue("locked", uuidOf(sensor.lockedTarget));
            WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
            if (weapons != null) {
                json.writeArrayStart("mounts");
                for (WeaponMountComponent m : weapons.mounts) {
                    json.writeObjectStart();
                    json.writeValue("target", uuidOf(m.target));
                    json.writeValue("subsystem", uuidOf(m.subsystem));
                    json.writeObjectEnd();
                }
                json.writeArrayEnd();
            }
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
    }

    @Override
    public void read(Json json, JsonValue data) {
        UuidRegistry uuids = engine.getUuidRegistry();
        for (JsonValue ship = data.getChild("ships"); ship != null; ship = ship.next) {
            Entity e = uuids.getEntity(ship.getString("id"));
            if (e == null) continue;
            SensorComponent sensor = e.getComponent(SensorComponent.class);
            if (sensor != null) sensor.lockedTarget = resolve(uuids, ship.getString("locked", null));
            WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
            JsonValue mounts = ship.get("mounts");
            if (weapons == null || mounts == null) continue;
            int i = 0;
            for (JsonValue m = mounts.child; m != null && i < weapons.mounts.size; m = m.next, i++) {
                WeaponMountComponent mount = weapons.mounts.get(i);
                mount.target = resolve(uuids, m.getString("target", null));
                // The gunner was already engaged with this target; don't restart the reaction delay
                mount.lastTarget = mount.target;
                mount.subsystem = resolve(uuids, m.getString("subsystem", null));
            }
        }
    }

    private static String uuidOf(Entity e) {
        UuidComponent id = e == null ? null : e.getComponent(UuidComponent.class);
        return id == null ? null : id.id;
    }

    private static Entity resolve(UuidRegistry uuids, String uuid) {
        return uuid == null ? null : uuids.getEntity(uuid);
    }
}
