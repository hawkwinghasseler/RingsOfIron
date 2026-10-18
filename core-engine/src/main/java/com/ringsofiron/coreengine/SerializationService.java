package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.ringsofiron.coreengine.components.TransientComponent;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saves and loads the game state as flat, human-readable JSON.
 *
 * <pre>
 * {
 *   "version": 1,
 *   "tick": 123456,
 *   "entities": [
 *     { "com.ringsofiron.coreengine.components.UuidComponent": { "id": "..." },
 *       "com.ringsofiron.coreengine.components.TransformComponent": { "x": 1, "y": 2, "rotation": 0 } }
 *   ],
 *   "modules": { "orbital.events": { ... } }
 * }
 * </pre>
 *
 * Rules for components:
 * <ul>
 *   <li>Entities tagged {@link TransientComponent} are skipped.</li>
 *   <li>Each component is written field by field, keyed by its class name, and needs a no-arg constructor.</li>
 *   <li>References to other entities must be {@link EntityRef} fields (written as UUID strings);
 *       a raw {@link Entity} field is an error. After all entities load, refs are checked in a second
 *       pass and dangling ones are reported in {@link #getLastLoadWarnings()}.</li>
 *   <li>Components holding live runtime objects (a Box2D Body, a sound handle) are either marked
 *       with {@link #skipComponent(Class)} and rebuilt after load, or given a custom serializer with
 *       {@link #registerSerializer(Class, Json.Serializer)}.</li>
 * </ul>
 * Module-global state is saved through {@link SaveParticipant}s.
 */
public class SerializationService {
    public static final int FORMAT_VERSION = 1;

    private final GameEngine gameEngine;
    private final Engine engine;
    private final Json json;
    private final ComponentMapper<TransientComponent> transientMapper = ComponentMapper.getFor(TransientComponent.class);
    private final ObjectSet<Class<? extends Component>> skipped = new ObjectSet<>();
    private final ObjectSet<Class<?>> checkedTypes = new ObjectSet<>();
    private final Array<SaveParticipant> participants = new Array<>();
    private final Array<EntityRef> refsRead = new Array<>();
    private final List<String> lastLoadWarnings = new ArrayList<>();

    public SerializationService(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.engine = gameEngine.getEcsEngine();
        this.json = new Json(OutputType.json);
        this.json.setUsePrototypes(false);
        this.json.setIgnoreUnknownFields(true);
        this.json.setSerializer(EntityRef.class, new EntityRefSerializer());
        skipped.add(TransientComponent.class);
    }

    /** Never save components of this class; the owning module rebuilds them after load. */
    public void skipComponent(Class<? extends Component> type) {
        skipped.add(type);
    }

    /** Custom JSON for a type used in components (a component class itself, or a field type). */
    public <T> void registerSerializer(Class<T> type, Json.Serializer<T> serializer) {
        json.setSerializer(type, serializer);
    }

    public void addParticipant(SaveParticipant participant) {
        participants.add(participant);
    }

    public void removeParticipant(SaveParticipant participant) {
        participants.removeValue(participant, true);
    }

    /** The configured Json instance, for participants and serializers that need it. */
    public Json getJson() {
        return json;
    }

    /**
     * Serializes sim time, all non-transient entities and all participants to a JSON string.
     */
    public String saveGame() {
        StringWriter out = new StringWriter();
        json.setWriter(out);
        try {
            json.writeObjectStart();
            json.writeValue("version", FORMAT_VERSION);
            json.writeValue("tick", gameEngine.getTick());

            json.writeArrayStart("entities");
            for (Entity entity : engine.getEntities()) {
                if (transientMapper.has(entity)) continue;
                json.writeObjectStart();
                for (Component component : entity.getComponents()) {
                    if (skipped.contains(component.getClass())) continue;
                    checkNoRawEntityFields(component.getClass());
                    json.writeValue(component.getClass().getName(), component, component.getClass());
                }
                json.writeObjectEnd();
            }
            json.writeArrayEnd();

            json.writeObjectStart("modules");
            for (SaveParticipant participant : participants) {
                json.writeObjectStart(participant.saveKey());
                participant.write(json);
                json.writeObjectEnd();
            }
            json.writeObjectEnd();

            json.writeObjectEnd();
        } finally {
            json.setWriter(null);
        }
        return json.prettyPrint(out.toString());
    }

    /**
     * Replaces the current state with the one in {@code jsonString}: clears all entities, sets the
     * tick, recreates entities, checks entity references, then loads participants.
     *
     * @throws SerializationException if the JSON is malformed or from a newer format version.
     */
    public void loadGame(String jsonString) {
        JsonValue root = new JsonReader().parse(jsonString);
        if (root == null || !root.isObject()) throw new SerializationException("Save is not a JSON object");
        int version = root.getInt("version", 0);
        if (version > FORMAT_VERSION) {
            throw new SerializationException("Save format " + version + " is newer than supported " + FORMAT_VERSION);
        }

        lastLoadWarnings.clear();
        gameEngine.clear();
        gameEngine.setTick(root.getLong("tick", 0L));

        refsRead.clear();
        JsonValue entities = root.get("entities");
        if (entities != null) {
            for (JsonValue entityData = entities.child; entityData != null; entityData = entityData.next) {
                Entity entity = engine.createEntity();
                for (JsonValue componentData = entityData.child; componentData != null; componentData = componentData.next) {
                    Component component = readComponent(componentData);
                    if (component != null) entity.add(component);
                }
                engine.addEntity(entity);
            }
        }

        // Second pass: every reference must point at an entity that now exists.
        UuidRegistry registry = gameEngine.getUuidRegistry();
        for (EntityRef ref : refsRead) {
            if (ref.getUuid() != null && ref.get(registry) == null) {
                lastLoadWarnings.add("Dangling entity reference " + ref.getUuid());
            }
        }
        refsRead.clear();

        JsonValue modules = root.get("modules");
        for (SaveParticipant participant : participants) {
            JsonValue data = modules == null ? null : modules.get(participant.saveKey());
            if (data != null) participant.read(json, data);
        }
    }

    public void saveToFile(FileHandle file) {
        file.writeString(saveGame(), false, "UTF-8");
    }

    public void loadFromFile(FileHandle file) {
        loadGame(file.readString("UTF-8"));
    }

    /** Problems found by the last {@link #loadGame(String)} that did not stop it (unknown classes, dangling refs). */
    public List<String> getLastLoadWarnings() {
        return Collections.unmodifiableList(lastLoadWarnings);
    }

    @SuppressWarnings("unchecked")
    private Component readComponent(JsonValue data) {
        Class<?> type;
        try {
            type = ClassReflection.forName(data.name);
        } catch (ReflectionException e) {
            lastLoadWarnings.add("Unknown component class " + data.name + " (removed mod?), skipped");
            return null;
        }
        if (!Component.class.isAssignableFrom(type)) {
            lastLoadWarnings.add(data.name + " is not a Component, skipped");
            return null;
        }
        return json.readValue((Class<Component>) type, data);
    }

    private class EntityRefSerializer implements Json.Serializer<EntityRef> {
        @Override
        public void write(Json json, EntityRef ref, Class knownType) {
            json.writeValue(ref == null ? null : ref.getUuid());
        }

        @Override
        public EntityRef read(Json json, JsonValue data, Class type) {
            if (data == null || data.isNull()) return null;
            EntityRef ref = new EntityRef(data.asString());
            refsRead.add(ref);
            return ref;
        }
    }

    /** A raw Entity field would recurse through the whole engine; fail with a clear message instead. */
    private void checkNoRawEntityFields(Class<?> type) {
        if (checkedTypes.contains(type)) return;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())) continue;
                if (Entity.class.isAssignableFrom(field.getType())) {
                    throw new SerializationException(type.getName() + "." + field.getName()
                        + " is a raw Entity; reference entities with EntityRef, or mark the field transient");
                }
            }
        }
        checkedTypes.add(type);
    }
}
