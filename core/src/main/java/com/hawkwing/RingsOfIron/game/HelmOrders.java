package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.coreengine.EntityRef;

import java.util.ArrayList;
import java.util.List;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.combatsys.events.CombatEvents;

/**
 * The helm's autopilot: orders the player gives the helmsman, flown whenever the player is not at
 * the helm and a crew member mans it ({@link Bridge}). With nobody at the helm nothing is flown, and
 * an order nobody comes to fly for {@link #UNMANNED_LIMIT} is dropped. Orders never fire weapons, and
 * a threat (the ship shot at or hit, a missile launched at it) drops the ones that fly the ship
 * somewhere: burns, approach and dock. Round 8: holding position and matching velocity
 * ({@link #survivesThreat}) carry on under fire, since keeping station is what a ship in a fight
 * wants (the raid's hold on the pirate base was dropped by the first dart, and every one after).
 *
 * <ul>
 *   <li><b>Fly the planned burns</b>: the helmsman turns the ship along the next burn, and fires the main
 *       engine through it, centered on the burn's time, until the delta-v is made. Every planned burn is
 *       flown this way in the bubble, order or none ({@link #flyPlannedBurn}): by the helmsman, ahead of
 *       any other order, or with the player at the helm by the flight computer while they keep their
 *       hands off the stick. Nothing fires by itself: a burn nobody has started by its time is missed,
 *       and one nobody flies on for {@link #UNFLOWN_LIMIT} is cut short.</li>
 *   <li><b>Match velocity</b> with the navigation target.</li>
 *   <li><b>Hold position</b> where the ship is, relative to the target.</li>
 *   <li><b>Approach</b> the target and hold at a set distance off its hull ({@link #APPROACH_DISTANCES},
 *       counted from half its deck plan's diagonal), closing no faster than the ship can brake from.</li>
 *   <li><b>Dock</b> at the target: from up to {@link #APPROACH_RANGE} out, the helmsman flies the ship's
 *       selected port in to a free port of its size there the way freighters berth
 *       ({@link BerthingSystem#pilot}): a station is asked for clearance once in range, another ship's
 *       nearest free port is made for without it. The order ends when the ports latch. The player
 *       takes over by taking the helm (the order then waits) or cancelling it.</li>
 * </ul>
 * Only in the bubble: on the rails there is nothing to fly, and a burn being flown holds rails warp back.
 */
public class HelmOrders {
    /** Approach distances the order cycles through, m; the first is the default. */
    public static final double[] APPROACH_DISTANCES = {500, 1_000, 2_000, 250};
    /** Farther than this the approach order is refused: plan a transfer instead, m. */
    public static final double APPROACH_RANGE = 50_000;
    /** Fastest closing speed an approach flies, m/s. */
    public static final double MAX_APPROACH_SPEED = 50;
    /** The share of its weaker main or retro engine an approach brakes on, leaving room to turn and to spare. */
    static final double BRAKING_SHARE = 0.5;
    /** Near the stopping point the closing speed is at most this per meter to go, 1/s. */
    static final double FINAL_GAIN = 0.2;
    /** An order nobody comes to fly for this long is dropped, sim seconds. */
    public static final double UNMANNED_LIMIT = 60;
    /** The helmsman turns along a burn this long before it starts, s. */
    public static final double TURN_LEAD = 120;
    /** How quickly the velocity error is closed, s. */
    static final double TAU = 1;
    /** Hold position: closing speed per meter off the held point, 1/s, and at most this, m/s. */
    static final double HOLD_GAIN = 0.1, HOLD_SPEED = 5;
    /** Velocity errors below this are left alone, m/s. */
    static final double DEADBAND = 0.05;
    /** A burn is done within this of its delta-v, m/s. */
    static final double BURN_DONE = 0.05;
    /** The main engine fires on a burn only with the nose this close to its direction, radians. */
    static final double BURN_ALIGN = Math.toRadians(5);
    /** A burn nobody flies on for this long is cut short, sim seconds. */
    public static final double UNFLOWN_LIMIT = 5;
    /** Dock: a target with no body in the bubble yet is closed on to this far off its hull first, m. */
    static final double DOCK_STANDOFF = 1_000;

    /** The berthing pilot the dock order flies by; its state lives on {@link HelmOrderComponent}. */
    private final BerthingComponent pilot = new BerthingComponent();

    private final GameContext ctx;
    private final PhysicsManager physics;
    private final RailsUniverse universe;
    private final DockingSystem docking;
    private final BerthingSystem berthing;
    private final Bridge bridge;
    private final CombatEvents combatEvents;
    private final NavTarget target;
    /** What the player should hear of: orders dropped, burns flown. */
    public final List<String> notices = new ArrayList<>();
    private final double[] trueState = new double[4];
    private final StateVector q = new StateVector();
    /** The ship's true velocity at the last tick of a burn, for measuring what the engine gave. */
    private double lastVx = Double.NaN, lastVy;
    /** Sim second a planned burn was last looked after ({@link #flyPlannedBurn}); NaN before. */
    private double lastFlown = Double.NaN;

    private final java.util.function.Function<Action, String> keys;

    public HelmOrders(GameContext ctx, PhysicsManager physics, RailsUniverse universe, DockingSystem docking, BerthingSystem berthing, Bridge bridge, CombatEvents combatEvents, NavTarget target, java.util.function.Function<Action, String> keys) {
        this.ctx = ctx;
        this.physics = physics;
        this.universe = universe;
        this.docking = docking;
        this.berthing = berthing;
        this.bridge = bridge;
        this.combatEvents = combatEvents;
        this.target = target;
        this.keys = keys;
        combatEvents.shots.add((signal, shot) -> {
            if (shot.target != null && shot.target == ctx.player()) threat("shots fired at us");
        });
        combatEvents.impacts.add((signal, hit) -> {
            if (hit.target != null && hit.target == ctx.player()) threat("we're hit");
        });
    }

    /** The standing order, never null. */
    public HelmOrderComponent order() {
        Entity ship = ctx.player();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (o == null) {
            o = new HelmOrderComponent();
            if (ship != null) ship.add(o);
        }
        return o;
    }

    public HelmOrderComponent.Mode mode() {
        return order().mode;
    }

    /**
     * Gives the helm an order; giving the approach order again steps through
     * {@link #APPROACH_DISTANCES}. Returns what to tell the player.
     */
    public String give(HelmOrderComponent.Mode mode) {
        Entity ship = ctx.player();
        if (ship == null) return "No ship";
        HelmOrderComponent o = order();
        if (mode == HelmOrderComponent.Mode.NONE) {
            boolean had = o.mode != HelmOrderComponent.Mode.NONE;
            clear(o);
            return had ? "Helm order cancelled" : "No helm order to cancel";
        }
        if (!hasCrewForHelm(ship)) return "Nobody aboard to take the helm: the autopilot is a helmsman's job";
        if (mode == HelmOrderComponent.Mode.FLY_BURNS) {
            ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
            if (m == null || m.nodes.size == 0) return "No burns planned for the helm to fly (" + keys.apply(Action.PLAN_BURN) + " plans one, "
                    + keys.apply(Action.PLAN_TO_TARGET) + " plans to the target)";
            clear(o);
            o.mode = mode;
            // With the player at the helm the flight computer flies them, hands off the stick
            return "Helm: fly the planned burns";
        }
        String uuid = target.uuid();
        if (uuid == null) return "The helm needs a navigation target for that (" + keys.apply(Action.NAV_TARGET) + " picks one)";
        if (!trueState(ship)) return "The helm can only fly that in the bubble";
        if (!relative(ship, target.entity(), rel)) return "Can't place " + target.name() + " from here";
        double rx = rel[0], ry = rel[1];
        if (mode == HelmOrderComponent.Mode.APPROACH && o.mode == mode && uuid.equals(o.targetUuid)) {
            o.distance = nextDistance(o.distance);
            return String.format(java.util.Locale.ROOT, "Helm: approach %s to %,.0f m off its hull", target.name(), o.distance);
        }
        if (mode == HelmOrderComponent.Mode.DOCK) {
            Entity other = target.entity();
            String refused = dockRefusal(ship, other);
            if (refused != null) return refused;
            if (Math.hypot(rx, ry) > APPROACH_RANGE) {
                return target.name() + " is too far for the helm to dock at: plan a transfer (" + keys.apply(Action.PLAN_TO_TARGET) + ")";
            }
            clear(o);
            o.mode = mode;
            o.targetUuid = uuid;
            o.dockOwnPort = ship.getComponent(DockingPortsComponent.class).selected;
            o.dockStatus = "";
            return "Helm: dock at " + target.name() + handOver();
        }
        if (mode == HelmOrderComponent.Mode.APPROACH && Math.hypot(rx, ry) > APPROACH_RANGE) {
            return target.name() + " is too far for the helm to approach: plan a transfer (" + keys.apply(Action.PLAN_TO_TARGET) + ")";
        }
        clear(o);
        o.mode = mode;
        o.targetUuid = uuid;
        o.holdX = rx;
        o.holdY = ry;
        switch (mode) {
            case MATCH_VELOCITY: return "Helm: match velocity with " + target.name() + handOver();
            case HOLD_POSITION: return "Helm: hold position on " + target.name() + handOver();
            default:
                return String.format(java.util.Locale.ROOT, "Helm: approach %s to %,.0f m off its hull%s", target.name(), o.distance, handOver());
        }
    }

    /** What the helm is doing, for the screen, or null with no order. */
    public String describe() {
        HelmOrderComponent o = order();
        String who = o.targetUuid == null ? "" : target.name(o.targetUuid);
        String what;
        switch (o.mode) {
            case FLY_BURNS: what = o.burning ? String.format(java.util.Locale.ROOT, "flying a burn, %.1f m/s to go",
                    Math.hypot(o.burnX, o.burnY)) : "flying the planned burns"; break;
            case MATCH_VELOCITY: what = "matching velocity with " + who; break;
            case HOLD_POSITION: what = "holding position on " + who; break;
            case APPROACH: what = String.format(java.util.Locale.ROOT, "approaching %s to %,.0f m", who, o.distance); break;
            case DOCK: what = "docking at " + who + (o.dockStatus.isEmpty() ? "" : ": " + Character.toLowerCase(o.dockStatus.charAt(0)) + o.dockStatus.substring(1)); break;
            default: return null;
        }
        if (bridge.playerAtHelm() && o.mode == HelmOrderComponent.Mode.FLY_BURNS) return "Flight computer: " + what;
        if (bridge.playerAtHelm()) return "Helm order (" + what + ") waits: you have the helm (" + keys.apply(Action.VIEW) + " hands it over)";
        if (bridge.helmsman(ctx.player()) == null) return "Helm order (" + what + ") waits for a helmsman";
        return "Helm: " + what;
    }

    /**
     * Why rails warp must wait, or null: a burn half flown can't be, nor one close enough that the
     * ship must turn onto it ({@link #burnLead}): on the rails it would fire as one impulse.
     */
    public String railsWarpBlockedReason() {
        Entity ship = ctx.player();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (o != null && o.burning) return "the helm is flying a burn";
        ManeuverNodeComponent m = ship == null ? null : ship.getComponent(ManeuverNodeComponent.class);
        ManeuverNodeComponent.Node n = m == null ? null : m.next();
        if (n != null && universe.now() >= n.time - burnLead(n)) return "a planned burn is coming up";
        return null;
    }

    /**
     * How long before a planned burn's time the helm starts turning the ship onto it: {@link #TURN_LEAD}
     * and half the burn, as long as the main engine takes to give its delta-v, s.
     */
    public double burnLead(ManeuverNodeComponent.Node n) {
        Entity ship = ctx.player();
        ThrusterComponent thruster = ship == null ? null : ship.getComponent(ThrusterComponent.class);
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        float mass = pb == null || pb.body == null ? 0f : PhysicsManager.getMass(pb.body);
        double accel = thruster == null || mass <= 0f ? 0 : thruster.forwardThrust / mass;
        return TURN_LEAD + (accel > 0 ? Math.hypot(n.prograde, n.radial) / accel / 2 : 0);
    }

    /** Once a frame: drops an order nobody has come to fly, or whose target is gone. */
    public void update() {
        Entity ship = ctx.player();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (ship != null && universe.orbit(ship) == null) unflown(ship, o);
        if (o == null || o.mode == HelmOrderComponent.Mode.NONE) return;
        if (o.targetUuid != null && ctx.engine.getEntity(o.targetUuid) == null) {
            drop(o, "the target is gone");
            return;
        }
        if (o.mode == HelmOrderComponent.Mode.DOCK && docking.docked(ship, ctx.engine.getEntity(o.targetUuid))) {
            String at = target.name(o.targetUuid);
            clear(o);
            notices.add("Helm: docked at " + at);
            return;
        }
        if (universe.orbit(ship) != null || bridge.playerAtHelm()) return;
        double now = universe.now();
        if (bridge.helmsman(ship) != null || o.mannedAt < 0) o.mannedAt = now;
        else if (now - o.mannedAt > UNMANNED_LIMIT) drop(o, "nobody took the helm");
    }

    /**
     * Flies the standing order, from {@link Bridge} while a helmsman has the helm. Sets
     * {@code controls} (and may turn the held heading) and returns true, or returns false with no
     * order to fly, leaving the helmsman to hold the heading.
     */
    boolean steer(Entity ship, HelmComponent helm, FlightControlComponent controls, Body body, float dt) {
        if (body == null) return false;
        // A planned burn comes first, whatever the order
        if (flyPlannedBurn(ship, helm, controls, body, dt)) {
            controls.rotationAssist = true;
            return true;
        }
        HelmOrderComponent o = ship.getComponent(HelmOrderComponent.class);
        if (o == null || o.mode == HelmOrderComponent.Mode.NONE) return false;
        if (o.mode == HelmOrderComponent.Mode.FLY_BURNS) {
            ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
            if (m == null || m.next() == null) {
                drop(o, null);
                notices.add("Helm: the planned burns are flown");
            }
            return false;
        }
        if (!trueState(ship)) return false;
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        float mass = PhysicsManager.getMass(body);
        if (thruster == null || mass <= 0f) return false;
        double fwd = thruster.forwardThrust / mass, rev = thruster.reverseThrust / mass, lat = thruster.lateralThrust / mass;
        controls.rotationAssist = true;

        Entity other = ctx.engine.getEntity(o.targetUuid);
        if (o.mode == HelmOrderComponent.Mode.DOCK && docking.bodyOf(other) != null) return dock(ship, other, o, controls, dt);
        if (!relative(ship, other, rel)) return false;
        double rx = rel[0], ry = rel[1], wx = rel[2], wy = rel[3];
        // The relative velocity wanted
        double vx = 0, vy = 0;
        if (o.mode == HelmOrderComponent.Mode.HOLD_POSITION) {
            double ex = rx - o.holdX, ey = ry - o.holdY;
            double e = Math.hypot(ex, ey);
            double v = Math.min(HOLD_SPEED, HOLD_GAIN * e);
            if (e > 1e-6) {
                vx = -ex / e * v;
                vy = -ey / e * v;
            }
        } else if (o.mode == HelmOrderComponent.Mode.APPROACH || o.mode == HelmOrderComponent.Mode.DOCK) {
            // Docking at a craft not yet in the bubble: close on it first
            double distance = o.mode == HelmOrderComponent.Mode.DOCK ? DOCK_STANDOFF : o.distance;
            if (o.mode == HelmOrderComponent.Mode.DOCK) o.dockStatus = "closing on " + target.name(o.targetUuid);
            double d = Math.hypot(rx, ry);
            double e = d - distance - radius(other);
            // Closing no faster than the ship can brake from in the distance left
            double brake = BRAKING_SHARE * Math.min(fwd, rev > 0 ? rev : fwd);
            double v = Math.min(MAX_APPROACH_SPEED, Math.sqrt(2 * brake * Math.abs(e)));
            v = Math.min(v, FINAL_GAIN * Math.abs(e));
            if (d > 1e-6) {
                vx = -Math.signum(e) * rx / d * v;
                vy = -Math.signum(e) * ry / d * v;
            }
        }
        double ax = (vx - wx) / TAU, ay = (vy - wy) / TAU;
        if (Math.hypot(vx - wx, vy - wy) < DEADBAND) ax = ay = 0;
        // Too much for the side thrusters alone: turn the main engine onto it
        if (Math.hypot(ax, ay) > 1.5 * lat) helm.heldAngle = (float) Math.atan2(ay, ax);
        controls.turn = Bridge.holdTurn(body, thruster, helm.heldAngle);
        thrust(controls, body.getAngle(), ax, ay, fwd, rev, lat);
        return true;
    }

    /** Flies the dock order's berthing pilot one step; its state goes back on the order to be saved. */
    private boolean dock(Entity ship, Entity other, HelmOrderComponent o, FlightControlComponent controls, float dt) {
        pilot.station = EntityRef.of(other);
        pilot.ownPort = o.dockOwnPort;
        pilot.port = o.dockPort;
        pilot.phase = o.dockPhase;
        pilot.status = o.dockStatus;
        pilot.uninvited = other.getComponent(StationComponent.class) == null;
        berthing.pilot(ship, pilot, controls, dt);
        o.dockPort = pilot.port;
        o.dockPhase = pilot.phase;
        o.dockStatus = pilot.status;
        return true;
    }

    /** Why the ship can't dock at {@code other} with its selected port, or null when it can. */
    private String dockRefusal(Entity ship, Entity other) {
        DockingPortsComponent own = ship.getComponent(DockingPortsComponent.class);
        if (docking.dockedTo(ship) != null) return "Already docked: undock first (" + keys.apply(Action.UNDOCK) + ")";
        if (own == null || own.ports.length == 0) return "This ship has no docking port";
        DockingPort port = own.selectedPort();
        if (!port.intact()) return "The selected docking port's collar is torn off (" + keys.apply(Action.NEXT_PORT) + " picks another)";
        if (port.jammed) return "The selected docking port's collar is jammed: the crew can free it on EVA (" + keys.apply(Action.NEXT_PORT) + " picks another)";
        DockingPortsComponent theirs = other == null ? null : other.getComponent(DockingPortsComponent.class);
        if (theirs == null || theirs.ports.length == 0) return target.name() + " has no docking port";
        for (DockingPort q : theirs.ports) if (q.size == port.size && q.isFree() && q.usable()) return null;
        return target.name() + " has no free " + port.size + " port for yours (" + keys.apply(Action.NEXT_PORT) + " picks another of yours)";
    }

    /**
     * Flies the next planned burn one tick with real thrust, whoever has the helm: turns the ship
     * along it from {@link #TURN_LEAD} before it starts, then fires the main engine through it,
     * centered on its time, until the delta-v is made. Sets {@code controls} (and the held heading)
     * and returns true while it does; false, changing nothing, with no burn to see to yet.
     */
    public boolean flyPlannedBurn(Entity ship, HelmComponent helm, FlightControlComponent controls, Body body, float dt) {
        if (body == null || universe.orbit(ship) != null || !trueState(ship)) return false;
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        float mass = PhysicsManager.getMass(body);
        if (thruster == null || mass <= 0f) return false;
        double fwd = thruster.forwardThrust / mass, lat = thruster.lateralThrust / mass;
        HelmOrderComponent o = order();
        double now = universe.now();
        // A tick it wasn't flown (the player on the stick): what happened then isn't the burn's
        if (!(now - lastFlown <= dt * 1.5)) lastVx = Double.NaN;
        lastFlown = now;
        if (o.burning) {
            // What the engine gave since last tick: the change in velocity less the parent's pull
            if (!Double.isNaN(lastVx)) {
                double r = Math.hypot(trueState[0], trueState[1]);
                CelestialBodyComponent c = parentBody();
                double g = c == null || r <= 0 ? 0 : c.mu / (r * r * r);
                o.burnX -= trueState[2] - lastVx + g * trueState[0] * dt;
                o.burnY -= trueState[3] - lastVy + g * trueState[1] * dt;
            }
            lastVx = trueState[2];
            lastVy = trueState[3];
            double left = Math.hypot(o.burnX, o.burnY);
            float nose = body.getAngle();
            double along = o.burnX * Math.cos(nose) + o.burnY * Math.sin(nose);
            if (left < BURN_DONE || (along < 0 && left < 1)) {
                endBurn(o, null);
            } else if (thruster.isOutOfFuel()) {
                endBurn(o, String.format(java.util.Locale.ROOT, "Out of propellant: burn cut short, %.0f m/s to go", left));
            } else {
                helm.heldAngle = (float) Math.atan2(o.burnY, o.burnX);
                controls.turn = Bridge.holdTurn(body, thruster, helm.heldAngle);
                double off = Math.abs(MathUtils.atan2(MathUtils.sin(helm.heldAngle - nose), MathUtils.cos(helm.heldAngle - nose)));
                double ax = off < BURN_ALIGN ? o.burnX / left * Math.min(fwd, left / 0.5) : 0;
                double ay = off < BURN_ALIGN ? o.burnY / left * Math.min(fwd, left / 0.5) : 0;
                thrust(controls, nose, ax, ay, fwd, 0, lat);
                return true;
            }
        }
        ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
        ManeuverNodeComponent.Node n = m == null ? null : m.next();
        if (n == null) return false;
        CelestialBodyComponent c = parentBody();
        if (c == null) return false;
        // The burn as a vector, from the conic the ship is on now
        KeplerOrbit k = KeplerOrbit.fromState(trueState[0], trueState[1], trueState[2], trueState[3], c.mu, now);
        double[] b = new double[2];
        Navigation.burnVector(k, n, b);
        double bx = b[0], by = b[1];
        double dv = Math.hypot(bx, by);
        double start = n.time - dv / Math.max(fwd, 1e-6) / 2;
        if (now < start - TURN_LEAD) return false;
        if (dv < BURN_DONE) {
            m.nodes.removeValue(n, true);
            return false;
        }
        helm.heldAngle = (float) Math.atan2(by, bx);
        controls.turn = Bridge.holdTurn(body, thruster, helm.heldAngle);
        if (now >= start) {
            // Under way: the node is now the burn being flown
            m.nodes.removeValue(n, true);
            o.burning = true;
            o.burnX = bx;
            o.burnY = by;
            lastVx = Double.NaN;
        }
        return true;
    }

    /**
     * Once a frame in the bubble: a planned burn nobody started by its time is missed, and one
     * nobody has flown on for {@link #UNFLOWN_LIMIT} is cut short. Nothing fires by itself.
     */
    private void unflown(Entity ship, HelmOrderComponent o) {
        double now = universe.now();
        if (Double.isNaN(lastFlown)) lastFlown = now;
        if (o != null && o.burning) {
            if (now - lastFlown > UNFLOWN_LIMIT && !bridge.playerAtHelm()) {
                endBurn(o, String.format(java.util.Locale.ROOT, "Burn cut short: nobody at the helm, %.0f m/s to go",
                        Math.hypot(o.burnX, o.burnY)));
            }
            return;
        }
        ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
        while (m != null && m.next() != null && m.next().time < now) {
            ManeuverNodeComponent.Node n = m.nodes.removeIndex(0);
            notices.add(String.format(java.util.Locale.ROOT, "Burn missed: nobody flew the planned %.0f m/s burn in time",
                    Math.hypot(n.prograde, n.radial)));
        }
    }

    /** Sets forward and strafe for a wanted acceleration (ax, ay) in the parent's frame, m/s². */
    private static void thrust(FlightControlComponent controls, float angle, double ax, double ay,
                               double fwd, double rev, double lat) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double along = ax * c + ay * s;
        double port = -ax * s + ay * c;
        controls.forward = (float) MathUtils.clamp(along >= 0 ? (fwd > 0 ? along / fwd : 0) : (rev > 0 ? along / rev : 0), -1, 1);
        controls.strafe = (float) MathUtils.clamp(lat > 0 ? port / lat : 0, -1, 1);
    }

    private void endBurn(HelmOrderComponent o, String why) {
        o.burning = false;
        o.burnX = o.burnY = 0;
        lastVx = Double.NaN;
        if (why != null) notices.add(why);
    }

    private void threat(String why) {
        Entity ship = ctx.player();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (o != null && o.mode != HelmOrderComponent.Mode.NONE && !survivesThreat(o.mode)) drop(o, why);
    }

    /** True for the orders a threat leaves standing: holding position and matching velocity. */
    static boolean survivesThreat(HelmOrderComponent.Mode mode) {
        return mode == HelmOrderComponent.Mode.HOLD_POSITION || mode == HelmOrderComponent.Mode.MATCH_VELOCITY;
    }

    private void drop(HelmOrderComponent o, String why) {
        clear(o);
        if (why != null) notices.add("Helm order dropped: " + why);
    }

    private void clear(HelmOrderComponent o) {
        o.mode = HelmOrderComponent.Mode.NONE;
        o.targetUuid = null;
        o.dockPort = -1;
        o.dockPhase = BerthingComponent.Phase.APPROACH;
        o.dockStatus = "";
        o.mannedAt = -1;
        // A planned burn under way goes on: it is no order's
    }

    private String handOver() {
        return bridge.playerAtHelm() ? ". Hand over the helm (" + keys.apply(Action.VIEW) + ") for the helmsman to fly it" : "";
    }

    /** How far a craft's hull reaches from its center, m: half its deck plan's diagonal; 0 without one. */
    static double radius(Entity craft) {
        com.ringsofiron.interiorgrid.ShipGridComponent g = craft == null ? null
                : craft.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class);
        return g == null ? 0 : 0.5 * Math.hypot(g.width, g.height) * g.tileSize;
    }

    private static double nextDistance(double current) {
        for (int i = 0; i < APPROACH_DISTANCES.length; i++) {
            if (Math.abs(APPROACH_DISTANCES[i] - current) < 1) return APPROACH_DISTANCES[(i + 1) % APPROACH_DISTANCES.length];
        }
        return APPROACH_DISTANCES[0];
    }

    /** A conscious crew member other than the player aboard {@code ship}, who could take the helm. */
    boolean hasCrewForHelm(Entity ship) {
        for (Entity e : ctx.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(PlayerAvatarComponent.class) != null) continue;
            GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (pos != null && pos.ship == ship && c != null && c.isConscious()) return true;
        }
        return false;
    }

    private final double[] rel = new double[4];
    private final com.badlogic.gdx.math.Vector2 v1 = new com.badlogic.gdx.math.Vector2(), v2 = new com.badlogic.gdx.math.Vector2();

    /**
     * Where {@code ship} is and how it moves relative to {@code other}, {x, y, vx, vy} into
     * {@code out}: from their Box2D bodies when both are in the bubble (what they will hit), else
     * from the ship's true state and the other's rails or conic. False if the other can't be placed.
     */
    private boolean relative(Entity ship, Entity other, double[] out) {
        Body a = docking.bodyOf(ship), b = other == null ? null : docking.bodyOf(other);
        if (a != null && b != null) {
            PhysicsManager.getPosition(a, v1).sub(PhysicsManager.getPosition(b, v2));
            out[0] = v1.x;
            out[1] = v1.y;
            PhysicsManager.getVelocity(a, v1).sub(PhysicsManager.getVelocity(b, v2));
            out[2] = v1.x;
            out[3] = v1.y;
            return true;
        }
        Entity frame = frame();
        if (frame == null || !target.stateOf(other, frame, universe.now(), q)) return false;
        out[0] = trueState[0] - q.x;
        out[1] = trueState[1] - q.y;
        out[2] = trueState[2] - q.vx;
        out[3] = trueState[3] - q.vy;
        return true;
    }

    /** The body the bubble falls around, or null. */
    private Entity frame() {
        String anchor = physics.getAnchorParentUuid();
        return anchor == null ? null : universe.entity(anchor);
    }

    private CelestialBodyComponent parentBody() {
        Entity f = frame();
        return f == null ? null : universe.celestial(f);
    }

    /** The ship's true state into {@link #trueState}; false off the bubble. */
    private boolean trueState(Entity ship) {
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        if (pb == null || pb.body == null || universe.orbit(ship) != null) return false;
        physics.getTrueState(pb.body, trueState);
        return true;
    }
}
