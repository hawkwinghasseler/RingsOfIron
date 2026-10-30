package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/** Docking clearance for {@link DockingSystem}: a station arms a free port for the craft that asks. */
final class DockingClearance {
    private final DockingSystem sys;
    private final Vector2 v0 = new Vector2(), v1 = new Vector2();

    DockingClearance(DockingSystem sys) {
        this.sys = sys;
    }

    /**
     * Asks the nearest station within {@link DockingSystem#CLEARANCE_RANGE} for clearance to dock with
     * {@code vessel}'s port {@code index}. A station that grants it arms its nearest free port of
     * the same size for this craft; its collar then reaches out for it (unless the craft's own
     * port is active and brings its collar). Without clearance the station's ports stay unarmed.
     */
    public DockingSystem.Clearance requestClearance(Entity vessel, int index) {
        DockingSystem.Clearance out = new DockingSystem.Clearance();
        DockingPortsComponent ports = sys.portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        Body body = sys.bodyOf(vessel);
        String uuid = sys.uuidOf(vessel);
        if (port == null || body == null || uuid == null || sys.stations == null) return out;
        Vector2 face = DockingSystem.worldPoint(body, port.faceX(), port.faceY(), v0);
        float best = DockingSystem.CLEARANCE_RANGE;
        for (Entity s : sys.stations) {
            Body sb = sys.bodyOf(s);
            if (s == vessel || sb == null) continue;
            float d = DockingSystem.worldPoint(sb, 0f, 0f, v1).dst(face);
            if (d < best) {
                best = d;
                out.station = s;
            }
        }
        if (out.station == null) return out;
        String refused = sys.clearance.refuse(out.station, vessel);
        if (refused != null) {
            out.outcome = DockingSystem.Clearance.Outcome.DENIED;
            out.reason = refused;
            return out;
        }
        DockingPortsComponent stationPorts = sys.portsMapper.get(out.station);
        Body sb = sys.bodyOf(out.station);
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < stationPorts.ports.length; i++) {
            DockingPort q = stationPorts.ports[i];
            if (q.size != port.size || !q.isFree() || !q.usable()) continue;
            if (q.armed && q.armedFor != null && !q.armedFor.equals(uuid)) continue;
            float d = DockingSystem.worldPoint(sb, q.faceX(), q.faceY(), v1).dst(face);
            if (d < nearest) {
                nearest = d;
                out.port = i;
            }
        }
        if (out.port < 0) {
            out.outcome = DockingSystem.Clearance.Outcome.NO_FREE_PORT;
            return out;
        }
        DockingPort q = stationPorts.ports[out.port];
        q.armed = true;
        q.armedFor = uuid;
        q.clearanceUntil = sys.physics.getSimSeconds() + DockingSystem.CLEARANCE_SECONDS;
        // Two collars reaching for each other would only push each other off: the asker's wins.
        q.holdCollar = port.active;
        out.outcome = DockingSystem.Clearance.Outcome.GRANTED;
        return out;
    }
}
