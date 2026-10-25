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
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
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
 *   "version": 2,
 *   "tick": 123456,
 *   "entities": [
 *     { "core.Uuid": { "id": "..." },
 *       "core.Transform": { "x": 1, "y": 2, "rotation": 0 } }
 *   ],
 *   "modules": { "orbital.events": { ... } }
 * }
 * </pre>
 *
 * Rules for components:
 * <ul>
 *   <li>Entities tagged {@link TransientComponent} are skipped.</li>
 *   <li>Each component is written field by field and needs a no-arg constructor. It is keyed by the
 *       stable type id given with {@link #registerType(String, Class)}, so a class can be renamed or
 *       moved without breaking saves; a class with no id is keyed by its class name, as every
 *       component was in format 1. Saves keyed by class names still load. A class that was renamed
 *       before it had an id keeps its old saves with {@link #registerAlias(String, Class)}.</li>
 *   <li>References to other entities must be {@link EntityRef} fields (written as UUID strings);
 *       a raw {@link Entity} field is an error. After all entities load, refs are checked in a second
 *       pass and dangling ones are reported in {@link #getLastLoadWarnings()}.</li>
 *   <li>Components holding live runtime objects (a Box2D Body, a sound handle) are either marked
 *       with {@link #skipComponent(Class)} and rebuilt after load, or given a custom serializer with
 *       {@link #registerSerializer(Class, Json.Serializer)}.</li>
 * </ul>
 * Module-global state is saved through {@link SaveParticipant}s.
 *
 * <p>A load reads and decodes the whole save before it touches the game ({@link #prepareLoad}), so a
 * malformed or unreadable save is refused with the game as it was; only then are the entities
 * replaced ({@link #applyLoad}).
 */
public class SerializationService {
    /** 1: components keyed by class name. 2: keyed by stable type id where the class has one. */
    public static final int FORMAT_VERSION = 2;

    private final GameEngine gameEngine;
    private final Engine engine;
    private final Json json;
    private final ComponentMapper<TransientComponent> transientMapper = ComponentMapper.getFor(TransientComponent.class);
    private final ObjectSet<Class<? extends Component>> skipped = new ObjectSet<>();
    private final ObjectSet<Class<?>> checkedTypes = new ObjectSet<>();
    private final Array<SaveParticipant> participants = new Array<>();
    private final Array<EntityRef> refsRead = new Array<>();
    private final List<String> lastLoadWarnings = new ArrayList<>();
    /** Type id or old key (an alias, a class name) -> component class. */
    private final ObjectMap<String, Class<? extends Component>> typesByKey = new ObjectMap<>();
    private final ObjectMap<Class<?>, String> idsByType = new ObjectMap<>();

    public SerializationService(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.engine = gameEngine.getEcsEngine();
        this.json = new Json(OutputType.json);
        this.json.setUsePrototypes(false);
        this.json.setIgnoreUnknownFields(true);
        this.json.setSerializer(EntityRef.class, new EntityRefSerializer());
        skipped.add(TransientComponent.class);
        registerType("core.Uuid", UuidComponent.class);
        registerType("core.Transform", TransformComponent.class);
        registerType("core.PlayerController", PlayerControllerComponent.class);
    }

    /**
     * Gives {@code type} the stable id {@code id} in saves, in place of its class name. Once a save
     * has been made with it the id must never change or be given to another class. Saves made before
     * the class had an id (keyed by its class name) still load.
     */
    public void registerType(String id, Class<? extends Component> type) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Empty type id for " + type.getName());
        String had = idsByType.get(type);
        if (had != null && !had.equals(id)) {
            throw new IllegalArgumentException(type.getName() + " already has the type id " + had + ", not " + id);
        }
        Class<? extends Component> owner = typesByKey.get(id);
        if (owner != null && owner != type) {
            throw new IllegalArgumentException("Type id " + id + " is already " + owner.getName());
        }
        idsByType.put(type, id);
        typesByKey.put(id, type);
        typesByKey.put(type.getName(), type);
    }

    /**
     * Reads components saved under {@code oldKey} (a class name from before a rename, say) as
     * {@code type}.
     */
    public void registerAlias(String oldKey, Class<? extends Component> type) {
        Class<? extends Component> owner = typesByKey.get(oldKey);
        if (owner != null && owner != type) {
            throw new IllegalArgumentException("Save key " + oldKey + " already reads as " + owner.getName());
        }
        typesByKey.put(oldKey, type);
    }

    /** The key {@code type} is saved under: its type id, or its class name when it has none. */
    public String typeKey(Class<?> type) {
        String id = idsByType.get(type);
        return id != null ? id : type.getName();
    }

    /** Whether {@code type} has a stable type id. */
    public boolean hasTypeId(Class<?> type) {
        return idsByType.containsKey(type);
    }

    /** Whether this service never saves components of {@code type}. */
    public boolean isSkipped(Class<? extends Component> type) {
        return skipped.contains(type);
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
                    checkNoNestedEntities(component, component.getClass().getName(), 0);
                    json.writeValue(typeKey(component.getClass()), component, component.getClass());
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
     * Replaces the current state with the one in {@code jsonString}: {@link #prepareLoad} then
     * {@link #applyLoad}.
     *
     * @throws SerializationException if the JSON is malformed, from a newer format version, or a
     *         component or module in it can't be read.
     */
    public void loadGame(String jsonString) {
        applyLoad(prepareLoad(jsonString));
    }

    /** A save read and decoded by {@link #prepareLoad}, ready for {@link #applyLoad}. */
    public static final class PreparedLoad {
        private final long tick;
        private final List<List<Component>> entities = new ArrayList<>();
        private final List<EntityRef> refs = new ArrayList<>();
        private final JsonValue modules;
        private final List<String> warnings = new ArrayList<>();
        private boolean applied;

        private PreparedLoad(long tick, JsonValue modules) {
            this.tick = tick;
            this.modules = modules;
        }

        /** What will be lost loading it (unknown component classes, modules this game lacks). */
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
    }

    /**
     * Parses {@code jsonString} and builds every component in it, without touching the game. If it
     * throws, nothing has changed.
     *
     * @throws SerializationException if the JSON is malformed, from a newer format version, or a
     *         component in it can't be read.
     */
    public PreparedLoad prepareLoad(String jsonString) {
        JsonValue root;
        try {
            root = new JsonReader().parse(jsonString);
        } catch (RuntimeException e) {
            throw new SerializationException("Save is not valid JSON", e);
        }
        if (root == null || !root.isObject()) throw new SerializationException("Save is not a JSON object");
        int version = root.getInt("version", 0);
        if (version > FORMAT_VERSION) {
            throw new SerializationException("Save format " + version + " is newer than supported " + FORMAT_VERSION);
        }

        PreparedLoad load = new PreparedLoad(root.getLong("tick", 0L), root.get("modules"));
        refsRead.clear();
        try {
            JsonValue entities = root.get("entities");
            if (entities != null) {
                for (JsonValue entityData = entities.child; entityData != null; entityData = entityData.next) {
                    List<Component> components = new ArrayList<>();
                    for (JsonValue componentData = entityData.child; componentData != null; componentData = componentData.next) {
                        Component component = readComponent(componentData, load.warnings);
                        if (component != null) components.add(component);
                    }
                    load.entities.add(components);
                }
            }
            for (EntityRef ref : refsRead) load.refs.add(ref);
        } finally {
            refsRead.clear();
        }

        if (load.modules != null) {
            for (JsonValue module = load.modules.child; module != null; module = module.next) {
                if (participantFor(module.name) == null) {
                    load.warnings.add("Save has " + module.name + " data this game doesn't use (removed mod?), skipped");
                }
            }
        }
        return load;
    }

    /**
     * Replaces the current state with {@code load}: clears all entities, sets the tick, adds the
     * loaded entities, checks entity references, then loads participants. The game is replaced from
     * the first step on, so a participant that throws leaves it part loaded; {@link #saveGame()}
     * beforehand is the way back.
     *
     * @throws SerializationException if a participant can't read its data.
     */
    public void applyLoad(PreparedLoad load) {
        if (load.applied) throw new IllegalStateException("This save has already been loaded");
        load.applied = true;
        lastLoadWarnings.clear();
        lastLoadWarnings.addAll(load.warnings);
        gameEngine.clear();
        gameEngine.setTick(load.tick);

        for (List<Component> components : load.entities) {
            Entity entity = engine.createEntity();
            for (Component component : components) entity.add(component);
            engine.addEntity(entity);
        }

        // Second pass: every reference must point at an entity that now exists.
        UuidRegistry registry = gameEngine.getUuidRegistry();
        for (EntityRef ref : load.refs) {
            if (ref.getUuid() != null && ref.get(registry) == null) {
                lastLoadWarnings.add("Dangling entity reference " + ref.getUuid());
            }
        }

        // A save that does not say how its UUIDs were made goes on with random ones
        gameEngine.getEntityIds().unseed();
        for (SaveParticipant participant : participants) {
            JsonValue data = load.modules == null ? null : load.modules.get(participant.saveKey());
            if (data == null) continue;
            try {
                participant.read(json, data);
            } catch (RuntimeException e) {
                throw new SerializationException("Couldn't load " + participant.saveKey() + ": " + e.getMessage(), e);
            }
        }
    }

    /** Writes the save through a temporary file, so a failed write leaves the old save whole. */
    public void saveToFile(FileHandle file) {
        AtomicFiles.writeString(file, saveGame());
    }

    public void loadFromFile(FileHandle file) {
        loadGame(file.readString("UTF-8"));
    }

    /**
     * Problems found by the last {@link #loadGame(String)} or {@link #applyLoad} that did not stop it
     * (unknown classes, modules this game lacks, dangling refs).
     */
    public List<String> getLastLoadWarnings() {
        return Collections.unmodifiableList(lastLoadWarnings);
    }

    @SuppressWarnings("unchecked")
    private Component readComponent(JsonValue data, List<String> warnings) {
        Class<?> type = typesByKey.get(data.name);
        if (type == null) {
            try {
                type = ClassReflection.forName(data.name);
            } catch (ReflectionException e) {
                warnings.add("Unknown component type " + data.name + " (removed mod?), skipped");
                return null;
            }
        }
        if (!Component.class.isAssignableFrom(type)) {
            warnings.add(data.name + " is not a Component, skipped");
            return null;
        }
        try {
            return json.readValue((Class<Component>) type, data);
        } catch (RuntimeException e) {
            throw new SerializationException("Couldn't read component " + data.name + ": " + e.getMessage(), e);
        }
    }

    private SaveParticipant participantFor(String key) {
        for (SaveParticipant participant : participants) {
            if (participant.saveKey().equals(key)) return participant;
        }
        return null;
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

    /** How deep {@link #checkNoNestedEntities} follows nested objects. */
    private static final int MAX_NESTING = 8;

    /**
     * The same rule for objects held inside a component (an {@code Array<Track>}, a nested
     * struct): Json would follow a raw Entity in any of them. Checks live values, since a
     * collection's element type is erased. Types with a registered serializer write themselves
     * and are not checked.
     */
    private void checkNoNestedEntities(Object value, String path, int depth) {
        if (value == null || depth > MAX_NESTING) return;
        if (value instanceof Entity) {
            throw new SerializationException(path
                + " is a raw Entity; reference entities with EntityRef, or mark the field transient");
        }
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof CharSequence || value instanceof Number
            || value instanceof Boolean || value instanceof Character || value instanceof EntityRef) return;
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) return;
            Object[] items = (Object[]) value;
            for (int i = 0; i < items.length; i++) checkNoNestedEntities(items[i], path + "[" + i + "]", depth + 1);
            return;
        }
        if (value instanceof Iterable) {
            int i = 0;
            for (Object item : (Iterable<?>) value) checkNoNestedEntities(item, path + "[" + i++ + "]", depth + 1);
            return;
        }
        if (value instanceof java.util.Map) {
            for (Object item : ((java.util.Map<?, ?>) value).values()) checkNoNestedEntities(item, path + "[]", depth + 1);
            return;
        }
        if (json.getSerializer(type) != null) return;
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.")) return;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.getType().isPrimitive()) continue;
                Object fieldValue;
                try {
                    field.setAccessible(true);
                    fieldValue = field.get(value);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                checkNoNestedEntities(fieldValue, path + "." + field.getName(), depth + 1);
            }
        }
    }
}
