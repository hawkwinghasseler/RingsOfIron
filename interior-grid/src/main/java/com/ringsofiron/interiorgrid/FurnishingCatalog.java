package com.ringsofiron.interiorgrid;

import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.FurnishingDef;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The furnishings a yard can sell ({@code [furnishing.*]}, furnishings.toml), by id, in the data's order. */
public final class FurnishingCatalog {
    private final Map<String, FurnishingType> types = new LinkedHashMap<>();

    public FurnishingCatalog() {
    }

    public static FurnishingCatalog from(DefRegistry defs) {
        FurnishingCatalog c = new FurnishingCatalog();
        if (defs == null || defs.registeredClass("furnishing") == null) return c;
        for (FurnishingDef d : defs.all("furnishing", FurnishingDef.class)) c.add(FurnishingType.from(d));
        return c;
    }

    public FurnishingCatalog add(FurnishingType type) {
        types.put(type.id, type);
        return this;
    }

    /** The type of {@code id}, or null. */
    public FurnishingType get(String id) {
        return id == null ? null : types.get(id);
    }

    public Collection<FurnishingType> all() {
        return Collections.unmodifiableCollection(types.values());
    }

    public boolean isEmpty() {
        return types.isEmpty();
    }
}
