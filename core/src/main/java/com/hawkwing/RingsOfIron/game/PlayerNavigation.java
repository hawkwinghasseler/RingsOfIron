package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.career.Contract;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.ClosestApproach;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.providers.NavigationProvider;

/**
 * ui-hud's {@link NavigationProvider}: the nav target from {@link NavTarget} with its distance and
 * closing as the navigation readout works them (the ship's and the target's states in the frame of
 * the body the ship orbits), the closest approach along the plan, the plan's burns and the one
 * being flown, and the station the ship is docked at.
 */
public class PlayerNavigation implements NavigationProvider {
    private final GameContext ctx;
    private final Navigation nav;
    private final DockingSystem docking;
    private final ContractBoard contracts;
    private final RailsUniverse universe;
    private final PirateBase pirateBase;
    private final PlayerOrbitInfo orbits;
    private final StationServices services;
    private final BurnCount burns = new BurnCount();
    private final StateVector target = new StateVector();
    private final Vector2 pos = new Vector2(), vel = new Vector2(), shipPos = new Vector2();
    private final double[] burn = new double[2];

    PlayerNavigation(GameContext ctx, Navigation nav, DockingSystem docking, ContractBoard contracts, RailsUniverse universe,
                     PirateBase pirateBase, PlayerOrbitInfo orbits, StationServices services) {
        this.ctx = ctx;
        this.nav = nav;
        this.docking = docking;
        this.contracts = contracts;
        this.universe = universe;
        this.pirateBase = pirateBase;
        this.orbits = orbits;
        this.services = services;
    }

    @Override
    public void read(NavReadout out) {
        Entity ship = ctx.player();
        if (ship == null) return;
        double now = universe.now();
        Entity dock = docking.dockedTo(ship);
        if (dock != null && dock.getComponent(StationComponent.class) != null) services.fill(out.docked, dock, ship);
        target(out, ship, now);
        burns(out, ship, now);
        out.planDeltaV = nav.deltaVNeeded();
        out.proposal = nav.transfer.proposal() != null;
        onward(out, now);
    }

    /** The leg still to come past the plan's last burn, and when the trip (or the proposal) arrives. */
    private void onward(NavReadout out, double now) {
        TransferLegComponent leg = nav.transfer.leg();
        if (leg != null) {
            Entity body = universe.entity(leg.continueIn);
            com.ringsofiron.orbital.components.CelestialBodyComponent c = body == null ? null : universe.celestial(body);
            out.onwardBody = c == null || c.name == null ? "" : c.name;
            if (leg.restDeltaV > 0) out.onwardDeltaV = (float) leg.restDeltaV;
            if (leg.arrival > now) out.arrivalIn = (float) (leg.arrival - now);
        }
        double proposed = nav.transfer.proposalArrival();
        if (out.proposal && !Double.isNaN(proposed) && proposed > now) out.arrivalIn = (float) (proposed - now);
    }

    private void target(NavReadout out, Entity ship, double now) {
        String uuid = nav.target.uuid();
        Entity e = uuid == null ? null : nav.target.entity();
        if (e == null) return;
        out.present = true;
        out.name = nav.target.name();
        out.kind = kind(e);
        out.relation = HudContacts.relation(ship, pirateBase, e);
        if (out.kind == Kind.STATION) services.fill(out.station, e, ship);
        // Distance and closing in the frame of the body the ship orbits, as the readout has them
        KeplerOrbit k = orbits.currentConic();
        Entity frame = k == null ? null : orbits.parent();
        // Where it is, when that isn't round the body the ship orbits: "at the Moon"
        Entity around = universe.parentOf(e);
        com.ringsofiron.orbital.components.CelestialBodyComponent body0 = around == null || around == frame ? null : universe.celestial(around);
        if (body0 != null && body0.name != null) out.around = body0.name;
        if (frame != null && nav.target.stateAt(frame, now, target)) {
            StateVector s = orbits.state();
            double dx = s.x - target.x, dy = s.y - target.y;
            double d = Math.hypot(dx, dy);
            out.distance = d;
            out.closing = (float) (-(dx * (s.vx - target.vx) + dy * (s.vy - target.vy)) / Math.max(d, 1e-9));
        }
        Body body = docking.bodyOf(ship);
        if (body != null && docking.bodyOf(e) != null && HudContacts.read(e, pos, vel)) {
            out.inBubble = true;
            PhysicsManager.getPosition(body, shipPos);
            out.bearingDeg = HudContacts.wrapDeg(MathUtils.atan2(pos.y - shipPos.y, pos.x - shipPos.x) * MathUtils.radiansToDegrees
                    - body.getAngle() * MathUtils.radiansToDegrees);
            if (Double.isNaN(out.distance)) out.distance = pos.dst(shipPos);
        }
        ClosestApproach.Result a = nav.approach();
        if (a != null) {
            out.cpaDistance = a.distance;
            out.cpaIn = (float) (a.time - now);
            out.cpaSpeed = (float) a.speed;
        }
        for (Contract c : contracts.active()) {
            if (uuid.equals(c.waypointUuid())) {
                out.contract = c.title();
                break;
            }
        }
    }

    /** The plan's burns: the next still in it, and the one being flown; their place in the plan. */
    private void burns(NavReadout out, Entity ship, double now) {
        ManeuverNodeComponent plan = ship.getComponent(ManeuverNodeComponent.class);
        HelmOrderComponent order = ship.getComponent(HelmOrderComponent.class);
        boolean burning = order != null && order.burning;
        burns.update(plan, burning, now, nav.helm::burnLead);
        int pending = plan == null ? 0 : plan.nodes.size;
        out.burning = burning;
        out.burnsLeft = pending + (burning ? 1 : 0);
        out.burnsPlanned = burns.flown() + pending;
        out.burnNumber = out.burnsLeft == 0 ? 0 : burning ? burns.flown() : burns.flown() + 1;

        Body body = docking.bodyOf(ship);
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        float mass = body == null ? 0f : PhysicsManager.getMass(body);
        double accel = thruster == null || mass <= 0f ? 0 : thruster.forwardThrust / mass;
        ManeuverNodeComponent.Node next = plan == null ? null : plan.next();
        double dv = next == null ? 0 : Math.hypot(next.prograde, next.radial);
        if (next != null) {
            out.burnIn = (float) (next.time - now);
            out.burnDeltaV = (float) dv;
            out.burnStartIn = (float) (next.time - (accel > 0 ? dv / accel / 2 : 0) - now);
            out.turnIn = (float) (next.time - nav.helm.burnLead(next) - now);
        }
        if (burning) {
            double left = Math.hypot(order.burnX, order.burnY);
            out.burnToGo = (float) left;
            if (accel > 0) out.burnTime = (float) (left / accel);
            direction(out, order.burnX, order.burnY, body);
        } else if (next != null) {
            if (accel > 0) out.burnTime = (float) (dv / accel);
            KeplerOrbit k = orbits.currentConic();
            if (k != null) {
                Navigation.burnVector(k, next, burn);
                direction(out, burn[0], burn[1], body);
            }
        }
    }

    /** A burn's direction (x, y) in the bubble's axes, which are the parent body's, and the turn onto it from the nose. */
    private static void direction(NavReadout out, double x, double y, Body body) {
        if (x == 0 && y == 0) return;
        float heading = (float) Math.toDegrees(Math.atan2(y, x));
        out.burnHeadingDeg = HudContacts.heading(heading);
        if (body != null) out.pointingDeg = HudContacts.wrapDeg(heading - body.getAngle() * MathUtils.radiansToDegrees);
    }

    /** What the target is: a station, a celestial body, a wreck or abandoned hull, a ship, else a point. */
    private Kind kind(Entity e) {
        if (e.getComponent(StationComponent.class) != null) return Kind.STATION;
        if (universe.celestial(e) != null) return Kind.BODY;
        DisabledComponent d = e.getComponent(DisabledComponent.class);
        if (d != null && (d.reason == DisabledComponent.Reason.WRECKED || d.reason == DisabledComponent.Reason.ABANDONED)) return Kind.WRECK;
        if (e.getComponent(ShipGridComponent.class) != null || e.getComponent(FreighterComponent.class) != null
                || e.getComponent(HostileComponent.class) != null) return Kind.SHIP;
        return Kind.POINT;
    }
}
