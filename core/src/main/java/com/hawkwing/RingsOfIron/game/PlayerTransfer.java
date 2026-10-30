package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.macrosim.fleet.RoutePlanner;
import com.ringsofiron.macrosim.fleet.TransferPlanner;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

import com.ringsofiron.shipsystems.PropellantFeed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Function;

/**
 * "Plan to target": burns to take the player's ship to its {@link NavTarget}, from the same
 * {@link TransferPlanner} and {@link RoutePlanner} the freighters fly by (a Hohmann transfer or a
 * phasing orbit around the same body, or out to the Moon and down from it). A ship on an orbit that
 * is not near-circular first circularizes. The plan is only a proposal, drawn on the orbit map, until
 * the player accepts it, when it replaces their planned burns; they edit it from there with the burn
 * planner, or discard it.
 *
 * <p>A leg to another body's station plans only the burn out of the current sphere of influence.
 * Once the ship has crossed into the next one with no burns left, the rest (capture, then the
 * transfer to the station) is proposed by itself.
 */
public class PlayerTransfer {
    /** The first burn of a plan is no sooner than this, seconds: time to get ready, or to undock. */
    public static final double LEAD = 300;
    /** Between a circularization and the transfer after it, seconds. */
    public static final double SETTLE = 60;
    /** Orbits rounder than this are flown from as they are. */
    public static final double ROUND_ENOUGH = 0.01;

    /** A plan not yet accepted. */
    public static final class Proposal {
        public final String targetUuid;
        public final String targetName;
        /** Burns as {time, prograde, radial}, in time order. */
        public final List<double[]> burns;
        public final double arrivalTime;
        public final double totalDeltaV;
        /** The body the ship orbits after these burns' crossing, or null when they reach the target. */
        public final String nextParent;

        Proposal(String targetUuid, String targetName, List<double[]> burns, double arrivalTime,
                 double totalDeltaV, String nextParent) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.burns = Collections.unmodifiableList(burns);
            this.arrivalTime = arrivalTime;
            this.totalDeltaV = totalDeltaV;
            this.nextParent = nextParent;
        }
    }

    private final GameContext ctx;
    private final RailsUniverse universe;
    private final MacroSimulation macroSim;
    private final Function<Entity, com.ringsofiron.interiorgrid.ShipGridComponent.MassProperties> shipMass;
    private final NavTarget target;
    private final PlayerOrbitInfo orbits;
    private Proposal proposal;
    /** What the player should hear of: the rest of a leg proposed. */
    public final List<String> notices = new ArrayList<>();

    private final java.util.function.Function<Action, String> keys;
    /** True while the autopilot flies to {@code uuid} and so accepts the rest of a leg itself. */
    public java.util.function.Predicate<String> autopilotFliesTo = uuid -> false;

    public PlayerTransfer(GameContext ctx, RailsUniverse universe, MacroSimulation macroSim, Function<Entity, com.ringsofiron.interiorgrid.ShipGridComponent.MassProperties> shipMass, NavTarget target, PlayerOrbitInfo orbits,
                          java.util.function.Function<Action, String> keys) {
        this.ctx = ctx;
        this.universe = universe;
        this.macroSim = macroSim;
        this.shipMass = shipMass;
        this.target = target;
        this.orbits = orbits;
        this.keys = keys;
    }

    /** The plan waiting to be accepted, or null. */
    public Proposal proposal() {
        return proposal;
    }

    /** Plans to the navigation target. Returns what to tell the player. */
    public String plan() {
        String uuid = target.uuid();
        if (uuid == null) return "No navigation target (" + keys.apply(Action.NAV_TARGET) + " picks one)";
        Entity e = target.entity();
        String name = target.name();
        if (universe.orbit(e) == null) return name + " is in the bubble with you: fly there, or order the helm to approach ("
                + keys.apply(Action.HELM_APPROACH) + ")";
        KeplerOrbit k = orbits.currentConic();
        Entity parent = orbits.parent();
        if (k == null || parent == null) return "No orbit to plan from";
        String parentUuid = universe.uuidOf(parent);
        double now = universe.now();
        Proposal p = plan(macroSim.rails(), parentUuid, k, uuid, name, now, now + LEAD);
        setLeg(null, null);
        if (p == null) return "No transfer to " + name + " from this orbit";
        if (p.burns.isEmpty()) return "You are already with " + name;
        proposal = p;
        return describe(p) + ". " + shortfall(p) + keys.apply(Action.ACCEPT_PLAN) + " accepts, " + keys.apply(Action.DISCARD_PLAN) + " discards";
    }

    /** "Not enough propellant: 1,030 m/s left. ", or nothing when the tanks cover the plan's burns. */
    String shortfall(Proposal p) {
        Entity ship = ctx.player();
        com.ringsofiron.interiorgrid.ShipGridComponent.MassProperties m = ship == null ? null : shipMass.apply(ship);
        double left = m == null ? Double.POSITIVE_INFINITY : PropellantFeed.deltaVLeft(ship, m.mass);
        if (!(left < p.totalDeltaV)) return "";
        return String.format(java.util.Locale.ROOT, "Not enough propellant: %,.0f m/s left. ", left);
    }

    /**
     * Plans for a ship on conic {@code k} around {@code parentUuid} to the craft {@code uuid}, the
     * first burn at {@code start} or later. Null when there is no route.
     */
    static Proposal plan(RailsModel rails, String parentUuid, KeplerOrbit k, String uuid, String name,
                         double now, double start) {
        StateVector s = k.stateAt(start);
        com.ringsofiron.macrosim.rails.StateVector from = new com.ringsofiron.macrosim.rails.StateVector(s.x, s.y, s.vx, s.vy);
        List<double[]> burns = new ArrayList<>();
        double dv = 0;
        double begin = start;
        if (k.e > ROUND_ENOUGH) {
            double mu = rails.muOf(parentUuid);
            com.ringsofiron.macrosim.rails.RailsModel.Burn c = TransferPlanner.circularize(mu, from, start);
            burns.add(new double[]{c.time, c.prograde, c.radial});
            dv += Math.hypot(c.prograde, c.radial);
            from = circular(mu, from);
            begin = start + SETTLE;
        }
        RoutePlanner.Route route;
        try {
            route = RoutePlanner.plan(rails, parentUuid, from, uuid, start, begin);
        } catch (RuntimeException ex) {
            // A target on an orbit the planners can't handle (a body's parent unknown, say)
            return null;
        }
        if (route == null) return null;
        for (com.ringsofiron.macrosim.rails.RailsModel.Burn b : route.burns) burns.add(new double[]{b.time, b.prograde, b.radial});
        if (route.burns.isEmpty() && burns.size() == 1 && Math.hypot(burns.get(0)[1], burns.get(0)[2]) < 0.5) burns.clear();
        return new Proposal(uuid, name, burns, route.arrivalTime, dv + route.totalDeltaV, route.nextParent);
    }

    /** Accepts the proposal: it replaces the planned burns. Returns what to tell the player. */
    public String accept(BurnPlanner planner) {
        if (proposal == null) return "No transfer plan to accept (" + keys.apply(Action.PLAN_TO_TARGET) + " plans one)";
        planner.replace(proposal.burns);
        setLeg(proposal.nextParent, proposal.nextParent == null ? null : proposal.targetUuid);
        String said = "Transfer to " + proposal.targetName + " planned: " + proposal.burns.size()
                + (proposal.burns.size() == 1 ? " burn" : " burns");
        proposal = null;
        return said;
    }

    /** Throws the proposal away. Returns what to tell the player. */
    public String discard() {
        if (proposal == null) return "No transfer plan to discard";
        proposal = null;
        return "Transfer plan discarded";
    }

    /**
     * Once a frame: drops a proposal whose target is gone, and proposes the rest of an accepted leg
     * once the ship is in the next sphere of influence with no burns left.
     */
    public void update(BurnPlanner planner) {
        if (proposal != null && ctx.engine.getEntity(proposal.targetUuid) == null) proposal = null;
        Entity ship = ctx.player();
        TransferLegComponent leg = ship == null ? null : ship.getComponent(TransferLegComponent.class);
        if (leg == null || leg.continueIn == null) return;
        if (leg.continueTo == null || ctx.engine.getEntity(leg.continueTo) == null) {
            setLeg(null, null);
            return;
        }
        KeplerOrbit k = orbits.currentConic();
        Entity parent = orbits.parent();
        if (k == null || parent == null || !leg.continueIn.equals(universe.uuidOf(parent))) return;
        if (planner.nodes() != null && planner.nodes().nodes.size > 0) return;
        String to = leg.continueTo;
        setLeg(null, null);
        double now = universe.now();
        RailsModel rails = macroSim.rails();
        StateVector s = k.stateAt(now);
        RoutePlanner.Route route;
        try {
            route = RoutePlanner.afterCrossing(rails, universe.uuidOf(parent),
                    new com.ringsofiron.macrosim.rails.StateVector(s.x, s.y, s.vx, s.vy), to, now, now + SETTLE);
        } catch (RuntimeException ex) {
            route = null;
        }
        String name = target.name(to);
        if (route == null || route.burns.isEmpty()) {
            notices.add("No capture found for " + name + " here: plan to it again (" + keys.apply(Action.PLAN_TO_TARGET) + ")");
            return;
        }
        List<double[]> burns = new ArrayList<>();
        for (com.ringsofiron.macrosim.rails.RailsModel.Burn b : route.burns) burns.add(new double[]{b.time, b.prograde, b.radial});
        proposal = new Proposal(to, name, burns, route.arrivalTime, route.totalDeltaV, route.nextParent);
        // The autopilot accepts it on its next frame, so the key would only mislead
        notices.add("Arrived at " + orbits.body().name + ": capture and transfer to " + name
                + (autopilotFliesTo.test(to) ? " planned; the autopilot takes it"
                : " proposed on the orbit map. " + keys.apply(Action.ACCEPT_PLAN) + " accepts"));
    }

    /**
     * An accepted plan's next sphere of influence and target, for proposing the rest of the leg
     * there; kept on the player's ship ({@link TransferLegComponent}) so it is saved.
     */
    private void setLeg(String continueIn, String continueTo) {
        Entity ship = ctx.player();
        if (ship == null) return;
        TransferLegComponent leg = ship.getComponent(TransferLegComponent.class);
        if (leg == null) {
            if (continueIn == null) return;
            ship.add(leg = new TransferLegComponent());
        }
        leg.continueIn = continueIn;
        leg.continueTo = continueTo;
    }

    /** "To Shackleton Ice: 2 burns, 3,140 m/s, arrives in 4.2 d" */
    public String describe(Proposal p) {
        double in = p.arrivalTime - universe.now();
        return String.format(java.util.Locale.ROOT, "To %s: %d %s, %,.0f m/s, %s %s", p.targetName, p.burns.size(),
                p.burns.size() == 1 ? "burn" : "burns", p.totalDeltaV,
                p.nextParent == null ? "arrives in" : "then on from there; about", RailsTrafficMap.formatEta(in));
    }

    /** Same position, circular speed, same sense of motion. */
    private static com.ringsofiron.macrosim.rails.StateVector circular(double mu, com.ringsofiron.macrosim.rails.StateVector s) {
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double r = s.distance();
        double vc = Math.sqrt(mu / r);
        return new com.ringsofiron.macrosim.rails.StateVector(s.x, s.y, -dir * s.y / r * vc, dir * s.x / r * vc);
    }
}
