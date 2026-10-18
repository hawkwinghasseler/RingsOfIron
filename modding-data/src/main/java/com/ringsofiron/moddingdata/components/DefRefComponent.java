package com.ringsofiron.moddingdata.components;

import com.badlogic.ashley.core.Component;

/**
 * Records which definition an entity was spawned from, e.g. {@code ship/corvette_patrol}.
 * Saved with the entity, so a loaded game can look the def back up.
 */
public class DefRefComponent implements Component {
    public String defType;
    public String defId;

    public DefRefComponent() {
    }

    public DefRefComponent(String defType, String defId) {
        this.defType = defType;
        this.defId = defId;
    }
}
