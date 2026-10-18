package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The docking ports of a ship or station, in the order its grid lists them. Saved with the
 * entity: which ports are armed, collar damage, and what each is latched to.
 */
public class DockingPortsComponent implements Component {
    public DockingPort[] ports = new DockingPort[0];
    /** The port the pilot's controls act on. */
    public int selected;

    public DockingPortsComponent() {
    }

    public DockingPortsComponent(DockingPort... ports) {
        this.ports = ports;
    }

    /**
     * One port per docking port mount on {@code grid}, with geometry from its footprint. A port is
     * active if any of its tiles is in {@code activeTiles} ({x, y} pairs), else passive.
     */
    public static DockingPortsComponent fromGrid(ShipGridComponent grid, Iterable<int[]> activeTiles) {
        List<DockingPort> out = new ArrayList<>();
        float[] edge = new float[4];
        for (MountFootprint m : grid.mounts) {
            if (m.kind != MountFootprint.MountKind.PORT) continue;
            DockingPortLayout.outerEdgeLocal(grid, m, edge);
            DockingPort p = new DockingPort();
            p.size = m.size;
            p.edgeX = edge[0];
            p.edgeY = edge[1];
            p.normalX = edge[2];
            p.normalY = edge[3];
            p.width = DockingPortLayout.width(m) * grid.tileSize;
            p.openingWidth = DockingPortLayout.openingWidth(m) * grid.tileSize;
            p.collarDepth = grid.tileSize * 0.5f;
            p.gridX = m.originX;
            p.gridY = m.originY;
            if (activeTiles != null) {
                for (int[] t : activeTiles) {
                    if (m.covers(t[0], t[1])) p.active = true;
                }
            }
            out.add(p);
        }
        return new DockingPortsComponent(out.toArray(new DockingPort[0]));
    }

    /**
     * A port on a hull with no grid (a freighter macro-sim brought into the bubble): {@code size}
     * at body-local {@code (edgeX, edgeY)} facing {@code (normalX, normalY)}, one meter tiles.
     */
    public static DockingPort hullPort(MountFootprint.MountSize size, boolean active,
                                       float edgeX, float edgeY, float normalX, float normalY) {
        DockingPort p = new DockingPort();
        p.size = size;
        p.active = active;
        p.edgeX = edgeX;
        p.edgeY = edgeY;
        float len = (float) Math.hypot(normalX, normalY);
        p.normalX = normalX / len;
        p.normalY = normalY / len;
        p.width = size.baseWidth(MountFootprint.MountKind.PORT);
        p.openingWidth = p.width - 2f;
        p.collarDepth = 0.5f;
        return p;
    }

    public DockingPort get(int index) {
        return index >= 0 && index < ports.length ? ports[index] : null;
    }

    public DockingPort selectedPort() {
        return get(selected);
    }

    /** Index of {@code port}, or -1. */
    public int indexOf(DockingPort port) {
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] == port) return i;
        }
        return -1;
    }

    /** True if any port is latched. */
    public boolean isDocked() {
        for (DockingPort p : ports) {
            if (p.state == DockingPort.State.DOCKED) return true;
        }
        return false;
    }
}
