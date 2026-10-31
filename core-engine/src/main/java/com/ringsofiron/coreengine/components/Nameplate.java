package com.ringsofiron.coreengine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;

/**
 * A component that shows an entity's name on screen. ui-hud's marker is one, so the gameplay
 * modules below ui-hud can read the name a ship is shown by, and grey it out while the ship is
 * disabled, without depending on the HUD.
 */
public interface Nameplate {
    String getLabel();

    void setLabel(String label);

    /** Greys the plate for a disabled ship, or gives it back its usual colour. */
    void setDisabled(boolean disabled);

    /** The entity's nameplate, or null when it has none. */
    static Nameplate of(Entity e) {
        ImmutableArray<Component> components = e.getComponents();
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i) instanceof Nameplate) return (Nameplate) components.get(i);
        }
        return null;
    }
}
