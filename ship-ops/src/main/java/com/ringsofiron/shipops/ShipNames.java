package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.Nameplate;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.stationsdocking.StationComponent;

/** The names the player knows stations and ships by, in notices, prompts and the HUD. */
public final class ShipNames {
    private ShipNames() {
    }

    /** What the player calls a station or ship. */
    public static String name(Entity e) {
        StationComponent s = e.getComponent(StationComponent.class);
        if (s != null && s.name != null) return s.name;
        FreighterComponent f = e.getComponent(FreighterComponent.class);
        if (f != null && f.displayName != null) return f.displayName;
        Nameplate m = Nameplate.of(e);
        String label = m == null ? null : m.getLabel();
        return label != null && !label.isEmpty() ? label : "the other ship";
    }
}
