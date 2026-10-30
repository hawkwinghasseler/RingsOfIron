package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;

/** Drops a craft's latches when it leaves the engine or loses its ports. */
final class VesselLifecycle implements EntityListener {
    private final DockingSystem sys;

    VesselLifecycle(DockingSystem sys) {
        this.sys = sys;
    }

    @Override
    public void entityAdded(Entity entity) {
    }

    @Override
    public void entityRemoved(Entity entity) {
        for (int i = sys.linkList.size - 1; i >= 0; i--) {
            LatchLink link = sys.linkList.get(i);
            if (link.a == entity || link.b == entity) sys.latches.dropLink(link, false);
        }
        DockingPortsComponent ports = sys.portsMapper.get(entity);
        if (ports != null) for (DockingPort p : ports.ports) sys.heads.destroyHead(p);
    }
}
