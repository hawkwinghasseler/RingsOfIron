package com.ringsofiron.stationsdocking;

import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/** What tearing a collar off does to the hull around its port. */
final class PortDamage {
    /** Integrity the port's frame tiles lose. */
    static final float FRAME_DAMAGE = 0.5f;
    /** Integrity the port door loses: enough that it leaks, not enough to blow it out. */
    static final float DOOR_DAMAGE = 0.6f;

    private PortDamage() {
    }

    /** Damages the frame and door of {@code port} on {@code grid}. Does nothing for a port with no grid. */
    static void tear(ShipGridComponent grid, DockingPort port) {
        if (port.gridX < 0) return;
        Tile origin = grid.getTile(port.gridX, port.gridY);
        MountFootprint m = origin == null ? null : origin.mount;
        if (m == null || m.kind != MountFootprint.MountKind.PORT) return;
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) continue;
                if (t.isWall()) t.setIntegrity(t.getIntegrity() - FRAME_DAMAGE);
                EdgeWall door = DockingPortLayout.door(grid, m, x, y);
                if (door != null) door.integrity = Math.max(0f, door.integrity - DOOR_DAMAGE);
            }
        }
        grid.gridDirty = true;
    }
}
