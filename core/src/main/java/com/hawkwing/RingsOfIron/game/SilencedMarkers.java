package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRemovalListener;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Round 10 (part 6): a fallen base's gun platforms read "(silenced)" in grey on the HUD, as a struck
 * ship reads "(surrendered)". Round 10's window found Den batteries 23 and 24 still in hostile red
 * beside the grey struck raiders after the fall. Their colours come back if the base rises.
 */
public final class SilencedMarkers {
    public static final String SUFFIX = " (silenced)";
    private final GameWorld world;
    /** The batteries greyed, with the label each had before. */
    private final Map<Entity, String> greyed = new HashMap<>();

    public SilencedMarkers(GameWorld world) {
        this.world = world;
        // A battery gone from the engine comes back from its pool as a new entity, which would be skipped as greyed
        world.engine.getEcsEngine().addEntityListener((EntityRemovalListener) greyed::remove);
    }

    /** Once a frame. */
    public void update() {
        for (Iterator<Map.Entry<Entity, String>> it = greyed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, String> g = it.next();
            HostileComponent h = g.getKey().getComponent(HostileComponent.class);
            if (h != null && world.pirateBase.silenced(h.displayName)) continue;
            HudMarkerComponent marker = g.getKey().getComponent(HudMarkerComponent.class);
            if (marker != null && marker.label.endsWith(SUFFIX)) {
                marker.label = g.getValue();
                marker.color.set(marker.kind.defaultColor);
            }
            it.remove();
        }
        if (world.pirateBase.standing()) return;
        for (String name : world.pirateBase.guns()) {
            if (!world.pirateBase.silenced(name)) continue;
            for (var e : world.macroSim.hostiles().all().entrySet()) {
                if (!name.equals(e.getValue().displayName)) continue;
                Entity ship = world.engine.getEntity(e.getKey());
                HudMarkerComponent marker = ship == null ? null : ship.getComponent(HudMarkerComponent.class);
                if (marker == null || greyed.containsKey(ship)) continue;
                greyed.put(ship, marker.label);
                if (!marker.label.endsWith(SUFFIX)) marker.label += SUFFIX;
                marker.color.set(ShipDisablement.DISABLED_COLOR);
            }
        }
    }
}
