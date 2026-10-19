package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The helm's autopilot: orders the player gives the helmsman, flown whenever the player is not at
 * the helm and a crew member mans it ({@link Bridge}). With nobody at the helm nothing is flown, and
 * an order nobody comes to fly for {@link #UNMANNED_LIMIT} is dropped. Orders never fire weapons, and
 * any threat (the ship shot at or hit, a missile launched at it) drops them.
 *
 * <ul>
 *   <li><b>Fly the planned burns</b>: the helmsman turns the ship along the next burn, and fires the main
 *       engine through it, centered on the burn's time, until the delta-v is made. A burn the helmsman
 *       has not started by its time fires by itself, as every planned burn does.</li>
 *   <li><b>Match velocity</b> with the navigation target.</li>
 *   <li><b>Hold position</b> where the ship is, relative to the target.</li>
 *   <li><b>Approach</b> the target and hold at a set distance off its hull ({@link #APPROACH_DISTANCES},
 *       counted from half its deck plan's diagonal), closing no faster than the ship can brake from.</li>
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

    private final GameWorld world;
    private final NavTarget target;
    /** What the player should hear of: orders dropped, burns flown. */
    public final List<String> notices = new ArrayList<>();
    private final double[] trueState = new double[4];
    private final StateVector q = new StateVector();
    /** The ship's true velocity at the last tick of a burn, for measuring what the engine gave. */
    private double lastVx = Double.NaN, lastVy;

    private final java.util.function.Function<Action, String> keys;

    public HelmOrders(GameWorld world, NavTarget target, java.util.function.Function<Action, String> keys) {
        this.world = world;
        this.target = target;
        this.keys = keys;
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.target != null && shot.target == world.getPlayer()) threat("shots fired at us");
        });
        world.combatEvents.impacts.add((signal, hit) -> {
            if (hit.target != null && hit.target == world.getPlayer()) threat("we're hit");
        });
    }

    /** The standing order, never null. */
    public HelmOrderComponent order() {
        Entity ship = world.getPlayer();
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
        Entity ship = world.getPlayer();
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
            return "Helm: fly the planned burns" + handOver();
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
            default: return null;
        }
        if (world.bridge.playerAtHelm()) return "Helm order (" + what + ") waits: you have the helm (" + keys.apply(Action.VIEW) + " hands it over)";
        if (world.bridge.helmsman(world.getPlayer()) == null) return "Helm order (" + what + ") waits for a helmsman";
        return "Helm: " + what;
    }

    /** Why rails warp must wait, or null: a burn half flown can't be. */
    public String railsWarpBlockedReason() {
        Entity ship = world.getPlayer();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        return o != null && o.burning ? "the helm is flying a burn" : null;
    }

    /** Once a frame: drops an order nobody has come to fly, or whose target is gone. */
    public void update() {
        Entity ship = world.getPlayer();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (o == null || o.mode == HelmOrderComponent.Mode.NONE) return;
        if (o.targetUuid != null && world.engine.getEntity(o.targetUuid) == null) {
            drop(o, "the target is gone");
            return;
        }
        if (world.universe.orbit(ship) != null || world.bridge.playerAtHelm()) return;
        double now = world.universe.now();
        if (world.bridge.helmsman(ship) != null || o.mannedAt < 0) o.mannedAt = now;
        else if (now - o.mannedAt > UNMANNED_LIMIT) drop(o, "nobody took the helm");
    }

    /**
     * Flies the standing order, from {@link Bridge} while a helmsman has the helm. Sets
     * {@code controls} (and may turn the held heading) and returns true, or returns false with no
     * order to fly, leaving the helmsman to hold the heading.
     */
    boolean steer(Entity ship, HelmComponent helm, FlightControlComponent controls, Body body, float dt) {
        HelmOrderComponent o = ship.getComponent(HelmOrderComponent.class);
        if (o == null || o.mode == HelmOrderComponent.Mode.NONE || body == null) return false;
        if (!trueState(ship)) return false;
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        float mass = PhysicsManager.getMass(body);
        if (thruster == null || mass <= 0f) return false;
        double fwd = thruster.forwardThrust / mass, rev = thruster.reverseThrust / mass, lat = thruster.lateralThrust / mass;
        controls.rotationAssist = true;
        if (o.mode == HelmOrderComponent.Mode.FLY_BURNS) return flyBurns(ship, o, helm, controls, body, thruster, fwd, lat, dt);

        Entity other = world.engine.getEntity(o.targetUuid);
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
        } else if (o.mode == HelmOrderComponent.Mode.APPROACH) {
            double d = Math.hypot(rx, ry);
            double e = d - o.distance - radius(other);
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

    private boolean flyBurns(Entity ship, HelmOrderComponent o, HelmComponent helm, FlightControlComponent controls,
                             Body body, ThrusterComponent thruster, double fwd, double lat, float dt) {
        ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
        double now = world.universe.now();
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
        ManeuverNodeComponent.Node n = m == null ? null : m.next();
        if (n == null) {
            drop(o, null);
            notices.add("Helm: the planned burns are flown");
            return false;
        }
        CelestialBodyComponent c = parentBody();
        if (c == null) return false;
        // The burn as a vector, from the conic the ship is on now
        KeplerOrbit k = KeplerOrbit.fromState(trueState[0], trueState[1], trueState[2], trueState[3], c.mu, now);
        double[] b = new double[2];
        Navigation.burnVector(k, n, b);
        double bx = b[0], by = b[1];
        double dv = Math.hypot(bx, by);
        double start = n.time - dv / Math.max(fwd, 1e-6) / 2;
        if (now < start - TURN_LEAD || dv < BURN_DONE) {
            controls.turn = Bridge.holdTurn(body, thruster, helm.heldAngle);
            return true;
        }
        helm.heldAngle = (float) Math.atan2(by, bx);
        controls.turn = Bridge.holdTurn(body, thruster, helm.heldAngle);
        if (now >= start) {
            // The helmsman flies it: it no longer fires by itself
            m.nodes.removeValue(n, true);
            o.burning = true;
            o.burnX = bx;
            o.burnY = by;
            lastVx = Double.NaN;
        }
        return true;
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
        Entity ship = world.getPlayer();
        HelmOrderComponent o = ship == null ? null : ship.getComponent(HelmOrderComponent.class);
        if (o != null && o.mode != HelmOrderComponent.Mode.NONE) drop(o, why);
    }

    private void drop(HelmOrderComponent o, String why) {
        boolean wasBurning = o.burning;
        clear(o);
        if (why != null) notices.add("Helm order dropped: " + why + (wasBurning ? " (burn cut short)" : ""));
    }

    private void clear(HelmOrderComponent o) {
        o.mode = HelmOrderComponent.Mode.NONE;
        o.targetUuid = null;
        o.burning = false;
        o.burnX = o.burnY = 0;
        o.mannedAt = -1;
        lastVx = Double.NaN;
    }

    private String handOver() {
        return world.bridge.playerAtHelm() ? ". Hand over the helm (" + keys.apply(Action.VIEW) + ") for the helmsman to fly it" : "";
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
    private boolean hasCrewForHelm(Entity ship) {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
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
        Body a = world.docking.bodyOf(ship), b = other == null ? null : world.docking.bodyOf(other);
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
        if (frame == null || !target.stateOf(other, frame, world.universe.now(), q)) return false;
        out[0] = trueState[0] - q.x;
        out[1] = trueState[1] - q.y;
        out[2] = trueState[2] - q.vx;
        out[3] = trueState[3] - q.vy;
        return true;
    }

    /** The body the bubble falls around, or null. */
    private Entity frame() {
        String anchor = world.physics.getAnchorParentUuid();
        return anchor == null ? null : world.universe.entity(anchor);
    }

    private CelestialBodyComponent parentBody() {
        Entity f = frame();
        return f == null ? null : world.universe.celestial(f);
    }

    /** The ship's true state into {@link #trueState}; false off the bubble. */
    private boolean trueState(Entity ship) {
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        if (pb == null || pb.body == null || world.universe.orbit(ship) != null) return false;
        world.physics.getTrueState(pb.body, trueState);
        return true;
    }
}
