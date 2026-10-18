package com.ringsofiron.moddingdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One merged definition, such as {@code [weapon.railgun_mk1]}: its raw field tree after
 * every file and mod that touched it has been applied, and the list of those sources.
 */
public class DefEntry {
    public final String type;
    public final String id;
    final Map<String, Object> raw;
    final List<String> sources = new ArrayList<>();

    DefEntry(String type, String id, Map<String, Object> raw) {
        this.type = type;
        this.id = id;
        this.raw = raw;
    }

    /** The merged fields, read-only. Keys are the snake_case names from the data file. */
    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(raw);
    }

    /** Files and scripts that contributed to this definition, in the order applied. */
    public List<String> sources() {
        return Collections.unmodifiableList(sources);
    }

    /** The source that last changed this definition, e.g. a mod overriding the base game. */
    public String lastSource() {
        return sources.isEmpty() ? "" : sources.get(sources.size() - 1);
    }

    @Override
    public String toString() {
        return type + "." + id;
    }
}
