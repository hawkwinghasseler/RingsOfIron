package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Which hull art a craft wears: its chassis id, which names {@code hulls/<chassis>.svg}.
 * Remembered per ship def, since looking a ship's def up converts it afresh. Not per craft: the
 * engine hands a removed craft's entity out again as a new one, which would wear the old one's hull.
 */
public final class HullArt {
    private static final String NONE = "";
    private final DefRegistry defs;
    private final Map<String, String> known = new HashMap<>();

    public HullArt(DefRegistry defs) {
        this.defs = defs;
    }

    /** The chassis of a station's blueprint or a ship's def, or null when neither says. */
    public String chassisOf(Entity craft) {
        StationComponent station = craft.getComponent(StationComponent.class);
        if (station != null) return station.blueprint;
        DefRefComponent ref = craft.getComponent(DefRefComponent.class);
        if (ref == null || defs == null || !"ship".equals(ref.defType) || ref.defId == null) return null;
        String c = known.get(ref.defId);
        if (c == null) known.put(ref.defId, c = lookUp(ref.defId));
        return c == NONE ? null : c;
    }

    private String lookUp(String shipDef) {
        ShipDef ship = defs.get("ship", shipDef, ShipDef.class);
        return ship == null || ship.chassis == null ? NONE : ship.chassis;
    }
}
