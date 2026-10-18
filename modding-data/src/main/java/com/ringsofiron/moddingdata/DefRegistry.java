package com.ringsofiron.moddingdata;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import groovy.lang.GString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every data definition in the game, keyed by type and id.
 *
 * Data files are tables of the form {@code [type.id]}, e.g. {@code [weapon.railgun_mk1]}
 * or {@code [commodity.water]}. Definitions are kept as raw field trees and only turned
 * into Java objects when asked for, so a module can register its POJO class for a type at
 * any time, before or after the data was loaded, and any class works without depending on
 * this module.
 *
 * Merging: when a later file (usually a mod) defines a type/id that already exists, its
 * fields are deep-merged over the existing ones, so a mod can change one stat without
 * repeating the rest. Tables merge key by key; arrays and plain values are replaced.
 * Two reserved keys change that: {@code _replace = true} discards the earlier fields
 * first, and {@code _remove = true} deletes the definition.
 *
 * Field mapping: snake_case keys fill camelCase fields ({@code hit_points} -> {@code hitPoints}).
 * If the target class has a String field {@code id}, it receives the definition id.
 */
public class DefRegistry {

    public static final String KEY_REPLACE = "_replace";
    public static final String KEY_REMOVE = "_remove";

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final Map<String, Map<String, DefEntry>> defs = new LinkedHashMap<>();
    private final Map<String, Class<?>> typeClasses = new HashMap<>();
    /** Converted objects, cleared for an entry whenever it changes. */
    private final Map<DefEntry, Map<Class<?>, Object>> converted = new HashMap<>();
    private int revision;

    /**
     * Binds a def type to the class it converts to. Safe to call after data is loaded;
     * the next {@link #get} converts with the new class.
     */
    public synchronized void registerType(String type, Class<?> defClass) {
        Class<?> previous = typeClasses.put(type, defClass);
        if (previous != null && previous != defClass) {
            Map<String, DefEntry> ofType = defs.get(type);
            if (ofType != null) for (DefEntry e : ofType.values()) converted.remove(e);
        }
    }

    public synchronized Class<?> registeredClass(String type) {
        return typeClasses.get(type);
    }

    /**
     * Merges every {@code [type.id]} table of a parsed data file into the registry.
     * Top-level values that are not tables of tables are ignored with a warning.
     */
    public synchronized void mergeFile(Map<String, Object> tree, String source, LoadReport report) {
        for (Map.Entry<String, Object> typeEntry : tree.entrySet()) {
            String type = typeEntry.getKey();
            if (!(typeEntry.getValue() instanceof Map)) {
                report.warn(source, "Top-level key '" + type + "' is not a table of definitions; ignored");
                continue;
            }
            Map<?, ?> ofType = (Map<?, ?>) typeEntry.getValue();
            for (Map.Entry<?, ?> defEntry : ofType.entrySet()) {
                String id = String.valueOf(defEntry.getKey());
                if (!(defEntry.getValue() instanceof Map)) {
                    report.warn(source, type + "." + id + " is not a table; ignored");
                    continue;
                }
                define(type, id, castMap(defEntry.getValue()), source);
            }
        }
    }

    /**
     * Adds or patches one definition. Also used by mod scripts:
     * {@code defs.define("commodity", "spice", [name: "Spice", base_price: 400], "my_mod")}.
     */
    public synchronized DefEntry define(String type, String id, Map<String, ?> fields, String source) {
        Map<String, Object> incoming = castMap(normalize(fields));
        Map<String, DefEntry> ofType = defs.computeIfAbsent(type, t -> new LinkedHashMap<>());
        DefEntry existing = ofType.get(id);

        if (Boolean.TRUE.equals(incoming.remove(KEY_REMOVE))) {
            if (existing != null) {
                ofType.remove(id);
                converted.remove(existing);
                revision++;
            }
            return null;
        }
        boolean replace = Boolean.TRUE.equals(incoming.remove(KEY_REPLACE));

        DefEntry entry;
        if (existing == null || replace) {
            entry = new DefEntry(type, id, new LinkedHashMap<>());
            if (existing != null) {
                entry.sources.addAll(existing.sources);
                converted.remove(existing);
            }
            ofType.put(id, entry);
        } else {
            entry = existing;
            converted.remove(entry);
        }
        deepMerge(entry.raw, incoming);
        entry.sources.add(source);
        revision++;
        return entry;
    }

    /** Convenience for scripts: {@code define(type, id, fields)} credited to "script". */
    public DefEntry define(String type, String id, Map<String, ?> fields) {
        return define(type, id, fields, "script");
    }

    public synchronized DefEntry entry(String type, String id) {
        Map<String, DefEntry> ofType = defs.get(type);
        return ofType == null ? null : ofType.get(id);
    }

    public synchronized boolean has(String type, String id) {
        return entry(type, id) != null;
    }

    public synchronized Collection<String> types() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(defs.keySet()));
    }

    public synchronized List<DefEntry> entries(String type) {
        Map<String, DefEntry> ofType = defs.get(type);
        return ofType == null ? Collections.emptyList() : new ArrayList<>(ofType.values());
    }

    public synchronized List<String> ids(String type) {
        Map<String, DefEntry> ofType = defs.get(type);
        return ofType == null ? Collections.emptyList() : new ArrayList<>(ofType.keySet());
    }

    /** Converts a definition with the class registered for its type. */
    public synchronized Object get(String type, String id) {
        Class<?> cls = typeClasses.get(type);
        if (cls == null) throw new IllegalStateException("No class registered for def type '" + type + "'");
        return get(type, id, cls);
    }

    /**
     * Converts a definition to any class whose fields mirror its keys.
     *
     * @return the object, or null if no such definition exists
     * @throws IllegalArgumentException if a field has the wrong shape, naming the def and its source
     */
    public synchronized <T> T get(String type, String id, Class<T> cls) {
        DefEntry entry = entry(type, id);
        return entry == null ? null : convert(entry, cls);
    }

    /** Like {@link #get(String, String, Class)} but fails with a clear message when missing. */
    public synchronized <T> T require(String type, String id, Class<T> cls) {
        T def = get(type, id, cls);
        if (def == null) throw new IllegalArgumentException("Unknown " + type + " '" + id + "'. Known: " + ids(type));
        return def;
    }

    /** Every definition of a type, converted, in load order. */
    public synchronized <T> List<T> all(String type, Class<T> cls) {
        List<T> out = new ArrayList<>();
        for (DefEntry e : entries(type)) out.add(convert(e, cls));
        return out;
    }

    /** Every definition of whichever type is registered to {@code cls}. */
    public synchronized <T> List<T> all(Class<T> cls) {
        for (Map.Entry<String, Class<?>> e : typeClasses.entrySet()) {
            if (e.getValue() == cls) return all(e.getKey(), cls);
        }
        throw new IllegalStateException(cls.getSimpleName() + " is not registered for any def type");
    }

    public synchronized <T> T convert(DefEntry entry, Class<T> cls) {
        Map<Class<?>, Object> cache = converted.computeIfAbsent(entry, e -> new HashMap<>());
        Object cached = cache.get(cls);
        if (cached != null) return cls.cast(cached);
        try {
            JsonElement tree = gson.toJsonTree(entry.raw);
            JsonObject obj = tree.getAsJsonObject();
            if (!obj.has("id")) obj.addProperty("id", entry.id);
            T value = gson.fromJson(obj, cls);
            cache.put(cls, value);
            return value;
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            throw new IllegalArgumentException("Cannot read " + entry + " (from " + entry.lastSource() + ") as "
                    + cls.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Increases on every change; lets callers cache derived tables cheaply. */
    public synchronized int revision() {
        return revision;
    }

    public synchronized int size() {
        int n = 0;
        for (Map<String, DefEntry> m : defs.values()) n += m.size();
        return n;
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> target, Map<String, Object> patch) {
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            Object current = target.get(e.getKey());
            Object incoming = e.getValue();
            if (current instanceof Map && incoming instanceof Map) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) current);
                deepMerge(copy, (Map<String, Object>) incoming);
                target.put(e.getKey(), copy);
            } else {
                target.put(e.getKey(), incoming);
            }
        }
    }

    /** Deep-copies a tree, turning Groovy strings and other CharSequences into Strings. */
    private static Object normalize(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(e.getKey()), normalize(e.getValue()));
            }
            return out;
        }
        if (value instanceof Collection) {
            List<Object> out = new ArrayList<>();
            for (Object o : (Collection<?>) value) out.add(normalize(o));
            return out;
        }
        if (value != null && value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            List<Object> out = new ArrayList<>();
            for (Object o : (Object[]) value) out.add(normalize(o));
            return out;
        }
        if (value instanceof GString || value instanceof CharSequence && !(value instanceof String)) {
            return value.toString();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
