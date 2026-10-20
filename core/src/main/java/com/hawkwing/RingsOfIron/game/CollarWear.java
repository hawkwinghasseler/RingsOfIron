package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

/**
 * A hit on or beside a docking port wears its collar, by the share of a hull plate the hit took
 * ({@link HullHitEvent#severity}), so heavy damage to a collar can come from a fight and the yard's
 * refit matters. A worn collar holds that much less of a latch's load. Hits wear a collar down to
 * {@link #FLOOR} and no further: shot at, it weakens, but only a latch's strain tears it off.
 */
public final class CollarWear {
    /** Tiles round a port's footprint a hit still wears its collar from. */
    static final int REACH = 1;
    /** The least a collar is worn down to by hits. */
    public static final float FLOOR = 0.1f;

    private CollarWear() {
    }

    /** Wears collars from every hull hit {@code events} publishes, but an ion bolt's, which breaks nothing. */
    public static void install(CombatEvents events) {
        events.hullHits.add((signal, hit) -> {
            if (!hit.ion && hit.severity > 0f) wear(hit.ship, hit.tileX, hit.tileY, hit.severity);
        });
    }

    /** Wears each collar of {@code ship} whose port is within {@link #REACH} of tile (x, y) by {@code share}. */
    static void wear(Entity ship, int x, int y, float share) {
        DockingPortsComponent ports = ship == null ? null : ship.getComponent(DockingPortsComponent.class);
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        if (ports == null || grid == null) return;
        for (DockingPort p : ports.ports) {
            if (p.gridX < 0 || !p.intact() || p.collarIntegrity <= FLOOR) continue;
            Tile origin = grid.getTile(p.gridX, p.gridY);
            MountFootprint m = origin == null ? null : origin.mount;
            int x0 = m == null ? p.gridX : m.originX, y0 = m == null ? p.gridY : m.originY;
            int x1 = m == null ? x0 : x0 + m.width() - 1, y1 = m == null ? y0 : y0 + m.height() - 1;
            if (x < x0 - REACH || x > x1 + REACH || y < y0 - REACH || y > y1 + REACH) continue;
            p.collarIntegrity = Math.max(FLOOR, p.collarIntegrity - share);
        }
    }
}
