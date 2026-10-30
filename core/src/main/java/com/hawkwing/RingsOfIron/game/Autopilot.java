package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.ClosestApproach;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.DropOutSource;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.util.Locale;
import java.util.function.Function;

/**
 * Flies a planned transfer under time warp, so a trip to the Moon needs no hand on the warp keys:
 * it accepts the plan to the navigation target (planning one if there is none), warps from burn to
 * burn (each fires on time, as every planned burn does, and warp drops there), accepts the capture
 * and the rest of the leg once the ship crosses into the next sphere of influence, warps on to the
 * closest approach, and once the target is within {@link HelmOrders#APPROACH_RANGE} hands the ship to
 * the helm to dock there ({@link HelmOrderComponent.Mode#DOCK}), or to approach it if it has no port.
 *
 * <p>While engaged it sets the warp: at 1x and physics warp it picks the level that suits the time
 * to the next stop, and lets rails warp run. It warps only after a drop it can account for (a burn, a crossing, the target coming close). A
 * drop it can't (the player slowing time, a guard refusing warp, another craft coming close, pirates
 * about) holds it at 1x with a notice until the player raises warp or presses the autopilot key again.
 * Its state is saved on the ship ({@link AutopilotComponent}).
 */
public class Autopilot implements DropOutSource {
    /** Warp stops this long before the closest approach when the target hasn't come close by then, s. */
    static final double ARRIVAL_LEAD = 120;

    private final GameWorld world;
    private final Navigation nav;
    private final Function<Action, String> keys;
    private final StateVector q = new StateVector();
    /** Set while the autopilot changes warp itself, so it doesn't hear its own change as the player's. */
    private boolean warping;

    Autopilot(GameWorld world, Navigation nav, Function<Action, String> keys) {
        this.world = world;
        this.nav = nav;
        this.keys = keys;
        world.timeWarp.changes.add((signal, change) -> heard(change));
        nav.transfer.autopilotFliesTo = uuid -> {
            AutopilotComponent a = state();
            return a != null && uuid.equals(a.targetUuid);
        };
    }

    /** The autopilot's state on the player's ship, or null when it is off. */
    public AutopilotComponent state() {
        Entity ship = world.getPlayer();
        AutopilotComponent a = ship == null ? null : ship.getComponent(AutopilotComponent.class);
        return a == null || a.targetUuid == null ? null : a;
    }

    public boolean engaged() {
        return state() != null;
    }

    /**
     * The autopilot key: engages it for the navigation target, goes on when it holds, or stops it.
     * Returns what to tell the player.
     */
    public String toggle() {
        AutopilotComponent a = state();
        if (a != null && a.held) {
            a.held = false;
            a.holdReason = null;
            return "Autopilot: going on to " + nav.target.name(a.targetUuid);
        }
        if (a != null) {
            String to = nav.target.name(a.targetUuid);
            disengage();
            return "Autopilot off: the transfer to " + to + " is yours to fly";
        }
        return engage();
    }

    /** Engages the autopilot for the navigation target. Returns what to tell the player. */
    public String engage() {
        Entity ship = world.getPlayer();
        if (ship == null) return "No ship";
        String uuid = nav.target.uuid();
        if (uuid == null) return "The autopilot needs a navigation target (" + keys.apply(Action.NAV_TARGET) + " picks one)";
        if (world.docking.dockedTo(ship) != null) return "Undock first (" + keys.apply(Action.UNDOCK) + "): the autopilot flies from open space";
        if (!nav.helm.hasCrewForHelm(ship)) return "Nobody aboard to take the helm: the autopilot is a helmsman's job";
        if (nav.transfer.proposal() == null && !planned()) {
            String said = nav.transfer.plan();
            if (nav.transfer.proposal() == null) return "Autopilot: " + said;
        }
        if (nav.transfer.proposal() != null && !uuid.equals(nav.transfer.proposal().targetUuid)) nav.transfer.discard();
        if (nav.transfer.proposal() != null) nav.transfer.accept(world.burns);
        AutopilotComponent a = ship.getComponent(AutopilotComponent.class);
        if (a == null) ship.add(a = new AutopilotComponent());
        a.targetUuid = uuid;
        a.held = false;
        a.holdReason = null;
        return "Autopilot: flying the transfer to " + nav.target.name(uuid) + ", warping between burns, and "
                + (dockable(ship, world.engine.getEntity(uuid)) ? "docking" : "closing on it") + " on arrival ("
                + keys.apply(Action.AUTOPILOT) + " again stops it)";
    }

    public void disengage() {
        Entity ship = world.getPlayer();
        if (ship != null) ship.remove(AutopilotComponent.class);
    }

    /** What the autopilot is doing, for the screen, or null when off. */
    public String describe() {
        AutopilotComponent a = state();
        if (a == null) return null;
        String to = nav.target.name(a.targetUuid);
        if (a.held) return "Autopilot to " + to + " holds: " + a.holdReason + " (" + keys.apply(Action.AUTOPILOT) + " goes on)";
        ManeuverNodeComponent.Node n = nav.nextBurn();
        if (n != null) return "Autopilot to " + to + ": next burn in " + Navigation.formatTime(n.time - world.universe.now());
        return "Autopilot to " + to + ": coasting to arrival";
    }

    /**
     * Once a frame, after the world has updated (warp may only change between updates): takes up the
     * rest of a leg, re-raises warp after a drop it expects, and hands over to the helm on arrival.
     */
    public void afterUpdate() {
        AutopilotComponent a = state();
        if (a == null || world.getCrash() != null) return;
        Entity ship = world.getPlayer();
        Entity target = world.engine.getEntity(a.targetUuid);
        if (target == null) {
            disengage();
            notice("Autopilot off: " + nav.target.name(a.targetUuid) + " is gone", NoticeLog.Kind.WARNING);
            return;
        }
        if (world.docking.dockedTo(ship) != null) {
            disengage();
            return;
        }
        // The rest of a leg, proposed on crossing into the next sphere of influence
        PlayerTransfer.Proposal p = nav.transfer.proposal();
        if (p != null && a.targetUuid.equals(p.targetUuid)) {
            notice("Autopilot: " + nav.transfer.accept(world.burns), NoticeLog.Kind.INFO);
        }
        if (a.held || world.timeWarp.isRailsWarp()) return;
        double now = world.universe.now();
        if (!planned() && !legGoesOn(ship) && nav.transfer.proposal() == null) {
            if (world.universe.orbit(ship) == null && distance(ship, target) < HelmOrders.APPROACH_RANGE) {
                arrive(ship, target);
                return;
            }
            ClosestApproach.Result c = nav.approach();
            if (c == null || c.time < now + 5) {
                disengage();
                notice(String.format(Locale.ROOT, "Autopilot off: the transfer is flown, but %s is %s off. Plan to it again (%s)",
                        nav.target.name(a.targetUuid), Navigation.formatDistance(distance(ship, target)),
                        keys.apply(Action.PLAN_TO_TARGET)), NoticeLog.Kind.WARNING);
                return;
            }
        }
        double next = nextStop(now);
        if (Double.isNaN(next)) return;
        double wait = next - now;
        // The helmsman flying the burns turns the ship before each: leave warp to Navigation then
        ManeuverNodeComponent.Node n = nav.nextBurn();
        if (nav.helm.mode() == HelmOrderComponent.Mode.FLY_BURNS && n != null && n.time - now < nav.leadFor(n) + 5) return;
        int level = wait > 20 * 86400 ? 1_000_000 : wait > 2 * 86400 ? 100_000 : wait > 3 * 3600 ? 10_000
                : wait > 600 ? 1_000 : wait > 60 ? 100 : wait > 2 ? TimeWarpController.MAX_PHYSICS_WARP : 1;
        // Rails warp refused for now (a burn being flown, a fire): as fast as the bubble goes meanwhile
        if (level > TimeWarpController.MAX_PHYSICS_WARP && world.engine.getRailsWarpBlockedReason() != null) {
            level = TimeWarpController.MAX_PHYSICS_WARP;
        }
        if (level == world.timeWarp.getWarp()) return;
        warping = true;
        try {
            world.timeWarp.setWarp(level);
        } finally {
            warping = false;
        }
    }

    /** The target is close: the helm takes the ship in. */
    private void arrive(Entity ship, Entity target) {
        AutopilotComponent a = state();
        String to = nav.target.name(a.targetUuid);
        nav.target.select(a.targetUuid);
        disengage();
        HelmOrderComponent.Mode mode = dockable(ship, target) ? HelmOrderComponent.Mode.DOCK : HelmOrderComponent.Mode.APPROACH;
        String said = nav.helm.give(mode);
        notice("Autopilot: arrived off " + to + ". " + said, said.startsWith("Helm:") ? NoticeLog.Kind.INFO : NoticeLog.Kind.WARNING);
    }

    /** When warp should next stop on its own: the next burn or crossing, else the closest approach. NaN for none. */
    private double nextStop(double now) {
        double t = Double.NaN;
        ManeuverNodeComponent.Node n = nav.nextBurn();
        if (n != null) t = n.time;
        String id = world.universe.uuidOf(world.getPlayer());
        OrbitalEvent e = id == null ? null : world.orbitalEvents.getQueue().nextFor(id);
        if (e != null && e.time > now && !(e.time >= t)) t = e.time;
        if (Double.isNaN(t)) {
            ClosestApproach.Result c = nav.approach();
            if (c != null) t = c.time - ARRIVAL_LEAD;
        }
        return t;
    }

    /** Warp stops before the closest approach once every burn is flown, if the target hasn't come close first. */
    @Override
    public double nextDropOutTime() {
        AutopilotComponent a = state();
        if (a == null || planned() || legGoesOn(world.getPlayer())) return Double.NaN;
        ClosestApproach.Result c = nav.approach();
        if (c == null) return Double.NaN;
        double t = c.time - ARRIVAL_LEAD;
        return t < world.universe.now() ? Double.NaN : t;
    }

    private void heard(TimeWarpController.WarpChange change) {
        AutopilotComponent a = state();
        if (warping || a == null) return;
        if (change.rails) {
            // The player raised warp themselves: going on
            if (a.held) {
                a.held = false;
                a.holdReason = null;
            }
            return;
        }
        if (change.reason == null) return;
        switch (change.reason) {
            case REQUESTED:
                hold(a, "you slowed time");
                break;
            case VETOED:
                hold(a, change.blockedReason == null ? "warp was refused" : change.blockedReason);
                break;
            case ENCOUNTER:
                if (change.encounter != null && change.encounter != world.engine.getEntity(a.targetUuid)) {
                    hold(a, ShipNames.name(change.encounter) + " is coming close");
                }
                break;
            case EXTERNAL:
                if (world.hostiles.alerted()) hold(a, "pirates about");
                break;
            default:
                break;
        }
    }

    private void hold(AutopilotComponent a, String why) {
        a.held = true;
        a.holdReason = why;
        notice("Autopilot holds: " + why + ". " + keys.apply(Action.AUTOPILOT) + " or raising warp goes on", NoticeLog.Kind.WARNING);
    }

    private void notice(String text, NoticeLog.Kind kind) {
        world.notices.post(text, kind);
    }

    private boolean planned() {
        ManeuverNodeComponent m = world.burns.nodes();
        return m != null && m.nodes.size > 0;
    }

    /** The accepted leg goes on past the next crossing: its capture is still to be proposed. */
    private static boolean legGoesOn(Entity ship) {
        TransferLegComponent leg = ship == null ? null : ship.getComponent(TransferLegComponent.class);
        return leg != null && leg.continueIn != null;
    }

    private static boolean dockable(Entity ship, Entity target) {
        DockingPortsComponent own = ship.getComponent(DockingPortsComponent.class);
        DockingPortsComponent theirs = target == null ? null : target.getComponent(DockingPortsComponent.class);
        return own != null && own.ports.length > 0 && theirs != null && theirs.ports.length > 0;
    }

    /** How far {@code target} is from the ship now, m; infinite when it can't be placed. */
    private double distance(Entity ship, Entity target) {
        PlayerOrbitInfo orbits = nav.orbits();
        Entity frame = orbits.currentConic() == null ? null : orbits.parent();
        if (frame == null || !nav.target.stateOf(target, frame, world.universe.now(), q)) return Double.POSITIVE_INFINITY;
        StateVector s = orbits.state();
        return Math.hypot(s.x - q.x, s.y - q.y);
    }
}
