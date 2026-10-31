package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRemovalListener;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * Round 10 (part 6): a fallen base's gun platforms read "(silenced)" in grey on the HUD, as a struck
 * ship reads "(surrendered)". Round 10's window found Den batteries 23 and 24 still in hostile red
 * beside the grey struck raiders after the fall. Their colours come back if the base rises.
 */
public final class SilencedMarkers {
    public static final String SUFFIX = " (silenced)";
    private final GameEngine engine;
    private final MacroSimulation macroSim;
    private final PirateBase pirateBase;
    /** The batteries greyed, with the label each had before. */
    private final Map<Entity, String> greyed = new HashMap<>();

    public SilencedMarkers(GameEngine engine, MacroSimulation macroSim, PirateBase pirateBase) {
        this.engine = engine;
        this.macroSim = macroSim;
        this.pirateBase = pirateBase;
        // A battery gone from the engine comes back from its pool as a new entity, which would be skipped as greyed
        engine.getEcsEngine().addEntityListener((EntityRemovalListener) greyed::remove);
    }

    /** Once a frame. */
    public void update() {
        for (Iterator<Map.Entry<Entity, String>> it = greyed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, String> g = it.next();
            HostileComponent h = g.getKey().getComponent(HostileComponent.class);
            if (h != null && pirateBase.silenced(h.displayName)) continue;
            HudMarkerComponent marker = g.getKey().getComponent(HudMarkerComponent.class);
            if (marker != null && marker.label.endsWith(SUFFIX)) {
                marker.label = g.getValue();
                marker.color.set(marker.kind.defaultColor);
            }
            it.remove();
        }
        if (pirateBase.standing()) return;
        for (String name : pirateBase.guns()) {
            if (!pirateBase.silenced(name)) continue;
            for (var e : macroSim.hostiles().all().entrySet()) {
                if (!name.equals(e.getValue().displayName)) continue;
                Entity ship = engine.getEntity(e.getKey());
                HudMarkerComponent marker = ship == null ? null : ship.getComponent(HudMarkerComponent.class);
                if (marker == null || greyed.containsKey(ship)) continue;
                greyed.put(ship, marker.label);
                if (!marker.label.endsWith(SUFFIX)) marker.label += SUFFIX;
                marker.color.set(HudMarkerComponent.DISABLED_COLOR);
            }
        }
    }
}
