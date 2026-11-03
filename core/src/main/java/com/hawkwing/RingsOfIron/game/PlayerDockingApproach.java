package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.shipops.ShipNames;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.providers.DockingApproachProvider;

/**
 * ui-hud's {@link DockingApproachProvider}: the player's selected port, how it lines up with the
 * nearest free port of its size within {@link PlayerDocking#READOUT_RANGE} (as the docking readout
 * works it), the latch while docked, and the clearance the nearest station has given or would
 * give. Clearance is read from the station's ports (one armed for the player's ship is clearance
 * given) and, short of that, from what the station would answer now; nothing is asked.
 */
public class PlayerDockingApproach implements DockingApproachProvider {
    private static final Family STATIONS = Family.all(StationComponent.class, DockingPortsComponent.class).get();

    private final GameContext ctx;
    private final DockingSystem docking;
    private final PhysicsManager physics;
    private final ImmutableArray<Entity> stations;
    private final DockingSystem.Approach approach = new DockingSystem.Approach();
    private final Vector2 at = new Vector2(), other = new Vector2();

    public PlayerDockingApproach(GameContext ctx, DockingSystem docking, PhysicsManager physics) {
        this.ctx = ctx;
        this.docking = docking;
        this.physics = physics;
        this.stations = ctx.engine.getEcsEngine().getEntitiesFor(STATIONS);
    }

    @Override
    public void read(DockingApproach out) {
        Entity ship = ctx.player();
        DockingPortsComponent ports = ship == null ? null : ship.getComponent(DockingPortsComponent.class);
        if (ports == null || ports.ports.length == 0) return;
        out.present = true;
        DockingPort port = ports.selectedPort();
        out.port = ports.selected + 1;
        out.ports = ports.ports.length;
        out.portSize = port.size.name();
        out.armed = port.armed;
        out.active = port.active;
        out.collarTorn = !port.intact();
        out.collarJammed = port.jammed;
        if (port.state == DockingPort.State.DOCKED) {
            out.capture = Capture.DOCKED;
            partner(out, ship, port);
            out.strain = docking.strain(ship);
            out.rigid = docking.isRigid(ship);
            return;
        }
        if (port.state == DockingPort.State.CAPTURED) {
            out.capture = Capture.CAPTURED;
            partner(out, ship, port);
            return;
        }
        docking.approach(ship, ports.selected, PlayerDocking.READOUT_RANGE, approach);
        if (approach.target != null) {
            out.capture = Capture.APPROACH;
            out.other = ShipNames.name(approach.target);
            out.otherPort = approach.port + 1;
            DockingPortsComponent theirs = approach.target.getComponent(DockingPortsComponent.class);
            if (theirs != null && approach.port < theirs.ports.length) out.otherPortSize = theirs.ports[approach.port].size.name();
            out.range = approach.distance;
            out.lateral = approach.lateral;
            out.angleDeg = approach.angle * MathUtils.radiansToDegrees;
            out.closing = approach.closing;
            out.safeSpeed = PlayerDocking.safeSpeed(approach.distance);
        }
        if (port.armed) clearance(out, ship, port);
    }

    /** The craft and port the selected port is latched or captured to. */
    private void partner(DockingApproach out, Entity ship, DockingPort port) {
        Entity partner = port.partnerUuid == null ? null : ctx.engine.getEntity(port.partnerUuid);
        if (partner == null) partner = docking.dockedTo(ship);
        if (partner == null) return;
        out.other = ShipNames.name(partner);
        out.otherPort = port.partnerPort + 1;
        DockingPortsComponent theirs = partner.getComponent(DockingPortsComponent.class);
        if (theirs != null && port.partnerPort >= 0 && port.partnerPort < theirs.ports.length) {
            out.otherPortSize = theirs.ports[port.partnerPort].size.name();
        }
    }

    /**
     * What the station said: a port armed for this ship is clearance given (and when it lapses); a
     * struck ship lined up yields without being asked; otherwise what the nearest station within
     * {@link DockingSystem#CLEARANCE_RANGE} would answer, refusing or with no free port of the size.
     */
    private void clearance(DockingApproach out, Entity ship, DockingPort port) {
        if (approach.target != null && docking.yields.test(approach.target)) {
            out.clearance = Clearance.YIELDS;
            return;
        }
        UuidComponent id = ship.getComponent(UuidComponent.class);
        Body body = docking.bodyOf(ship);
        if (id == null || body == null) return;
        PhysicsManager.getPosition(body, at);
        Entity nearest = null;
        float best = DockingSystem.CLEARANCE_RANGE;
        double now = physics.getSimSeconds();
        for (int i = 0; i < stations.size(); i++) {
            Entity s = stations.get(i);
            DockingPort[] list = s.getComponent(DockingPortsComponent.class).ports;
            for (int p = 0; p < list.length; p++) {
                DockingPort q = list[p];
                if (id.id.equals(q.armedFor) && q.isFree()) {
                    out.clearance = Clearance.GRANTED;
                    if (out.other.isEmpty()) {
                        out.other = ShipNames.name(s);
                        out.otherPort = p + 1;
                        out.otherPortSize = q.size.name();
                    }
                    if (!Double.isNaN(q.clearanceUntil)) out.clearanceLeft = (float) Math.max(0, q.clearanceUntil - now);
                    return;
                }
            }
            Body sb = docking.bodyOf(s);
            if (sb == null) continue;
            float d = PhysicsManager.getPosition(sb, other).dst(at);
            if (d < best) {
                best = d;
                nearest = s;
            }
        }
        if (nearest == null) return;
        String refused = docking.clearance.refuse(nearest, ship);
        if (refused != null) {
            out.clearance = Clearance.DENIED;
            out.refusal = refused;
            return;
        }
        for (DockingPort q : nearest.getComponent(DockingPortsComponent.class).ports) {
            if (q.size == port.size && q.isFree() && q.usable()) return;
        }
        out.clearance = Clearance.NO_PORT;
    }
}
