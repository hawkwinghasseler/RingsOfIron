package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which hull art a craft wears: its chassis id, which names {@code hulls/<chassis>.svg}.
 * Remembered per craft, since looking a ship's def up converts it afresh.
 */
public final class HullArt {
    private static final String NONE = "";
    private final DefRegistry defs;
    private final Map<Entity, String> known = new WeakHashMap<>();

    public HullArt(DefRegistry defs) {
        this.defs = defs;
    }

    /** The chassis of a station's blueprint or a ship's def, or null when neither says. */
    public String chassisOf(Entity craft) {
        String c = known.get(craft);
        if (c == null) known.put(craft, c = lookUp(craft));
        return c == NONE ? null : c;
    }

    private String lookUp(Entity craft) {
        StationComponent station = craft.getComponent(StationComponent.class);
        if (station != null) return station.blueprint == null ? NONE : station.blueprint;
        DefRefComponent ref = craft.getComponent(DefRefComponent.class);
        if (ref == null || defs == null || !"ship".equals(ref.defType)) return NONE;
        ShipDef ship = defs.get("ship", ref.defId, ShipDef.class);
        return ship == null || ship.chassis == null ? NONE : ship.chassis;
    }
}
