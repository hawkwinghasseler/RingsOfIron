package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

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
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        return m != null && m.label != null && !m.label.isEmpty() ? m.label : "the other ship";
    }
}
