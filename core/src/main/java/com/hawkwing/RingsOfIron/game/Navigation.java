package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.RoutePlanner;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.ClosestApproach;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.DropOutSource;

import com.ringsofiron.uihud.HintRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Navigation and flight aids, together: the navigation target ({@link NavTarget}), the closest
 * approach to it along the planned path, "plan to target" ({@link PlayerTransfer}), the helm's
 * autopilot ({@link HelmOrders}), the pirates the player knows of on the rails
 * ({@link PirateSightings}), warping to the next burn, and how much delta-v the plan needs against
 * what the tanks hold.
 *
 * <p>Warp to burn: rails warp at a level that suits how far off the next burn is, dropping out
 * {@link #WARP_LEAD} before it. While the helm has an order to fly the burns, warp always drops out
 * in time for the helmsman to turn the ship and start the burn.
 */
public class Navigation extends EntitySystem implements DropOutSource {
    /** Warp to burn stops this long before the burn, sim seconds. */
    public static final double WARP_LEAD = 60;
    /** How many burns after the next the readout counts down to. */
    static final int LATER_BURNS = 3;
    /** The closest approach is worked out again at most this often, real seconds. */
    static final float APPROACH_REFRESH = 0.25f;
    /** How far ahead an open orbit is searched for the closest approach, s. */
    static final double OPEN_HORIZON = 2 * 86400.0;
    /** Nor further ahead than this on a long orbit, s. */
    static final double MAX_HORIZON = 30 * 86400.0;

    public final PirateSightings sightings;
    public final NavTarget target;
    public final PlayerTransfer transfer;
    public final HelmOrders helm;
    /** Flies an accepted transfer under time warp and hands over to the helm to dock on arrival. */
    public final Autopilot autopilot;
    /** What the player's keys are called, for messages naming them; the screen points it at its bindings. */
    public Function<Action, String> keyNames = Action::defaultKeyName;

    private final GameWorld world;
    private final PlayerOrbitInfo orbits;
    private boolean warpToBurn;
    private final ClosestApproach.Result approach = new ClosestApproach.Result();
    private float sinceApproach = APPROACH_REFRESH;
    private String approachFor;

    private Navigation(GameWorld world) {
        this.world = world;
        orbits = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer);
        sightings = new PirateSightings(world);
        target = new NavTarget(world, sightings);
        transfer = new PlayerTransfer(world, target, orbits, this::key);
        helm = new HelmOrders(world, target, this::key);
        autopilot = new Autopilot(world, this, this::key);
    }

    PlayerOrbitInfo orbits() {
        return orbits;
    }

    /** Puts navigation into {@code world}. */
    public static Navigation install(GameWorld world) {
        Navigation nav = new Navigation(world);
        world.engine.addSystem(nav.sightings);
        world.engine.addSystem(nav);
        world.timeWarp.addDropOutSource(nav);
        world.timeWarp.addDropOutSource(nav.autopilot);
        world.engine.addRailsWarpGuard(nav.helm::railsWarpBlockedReason);
        world.timeWarp.changes.add((signal, change) -> {
            if (!change.rails) nav.warpToBurn = false;
        });
        registerHints(world.hints, () -> nav.transfer.proposal() != null);
        // The low-propellant warning keeps quiet while there is enough to get home
        world.warnings.returnPropellantKg = nav::returnPropellantKg;
        return nav;
    }

    /** The navigation keys, by where the player sits; accepting a plan comes first while one waits. */
    public static void registerHints(HintRegistry hints, java.util.function.BooleanSupplier proposed) {
        for (HintRegistry.Context c : new HintRegistry.Context[]{HintRegistry.Context.HELM, HintRegistry.Context.WEAPONS}) {
            hints.register(c, "accept the transfer plan", 1, Action.ACCEPT_PLAN).onlyWhen(proposed);
            hints.register(c, "discard it", 2, Action.DISCARD_PLAN).onlyWhen(proposed);
            hints.register(c, "navigation target", 60, Action.NAV_TARGET);
            hints.register(c, "plan to the target", 61, Action.PLAN_TO_TARGET);
            hints.register(c, "warp to the next burn", 62, Action.WARP_TO_BURN);
            hints.register(c, "helm: fly burns / match / hold / approach / cancel", c == HintRegistry.Context.WEAPONS ? 30 : 63,
                    Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH, Action.HELM_CANCEL);
            hints.register(c, "helm: dock at the target", c == HintRegistry.Context.WEAPONS ? 31 : 64, Action.HELM_DOCK);
            hints.register(c, "autopilot: fly the transfer", c == HintRegistry.Context.WEAPONS ? 32 : 65, Action.AUTOPILOT);
        }
    }

    @Override
    public void update(float deltaTime) {
        transfer.update(world.burns);
        helm.update();
        for (String news : transfer.notices) world.notices.post(news);
        transfer.notices.clear();
        // A dropped order or a burn cut short wants the player's attention
        for (String news : helm.notices) world.notices.post(news, news.startsWith("Helm:") ? NoticeLog.Kind.INFO : NoticeLog.Kind.WARNING);
        helm.notices.clear();
        sinceApproach += deltaTime;
    }

    // ------------------------------------------------------------------ warp to burn

    /** Rails warp towards the next planned burn. Returns what to tell the player. */
    public String warpToNextBurn() {
        ManeuverNodeComponent.Node n = nextBurn();
        if (n == null) return "No burn planned to warp to";
        double wait = n.time - leadFor(n) - world.universe.now();
        if (wait <= 5) return "The burn is too close to warp to";
        int level = wait > 2 * 86400 ? 100_000 : wait > 3 * 3600 ? 10_000 : wait > 600 ? 1_000 : 100;
        if (!world.timeWarp.setWarp(level)) {
            String reason = world.engine.getRailsWarpBlockedReason();
            return "Time warp refused" + (reason == null ? "" : ": " + reason);
        }
        warpToBurn = true;
        return String.format(Locale.ROOT, "Warping to the next burn; stopping %s before it", formatTime(leadFor(n)));
    }

    @Override
    public double nextDropOutTime() {
        ManeuverNodeComponent.Node n = nextBurn();
        if (n == null || !(warpToBurn || helm.mode() == HelmOrderComponent.Mode.FLY_BURNS)) return Double.NaN;
        double t = n.time - leadFor(n);
        if (t < world.universe.now()) {
            warpToBurn = false;
            return Double.NaN;
        }
        return t;
    }

    /** How long before a burn warp stops: longer when the helmsman is to turn the ship and fly it. */
    double leadFor(ManeuverNodeComponent.Node n) {
        if (helm.mode() != HelmOrderComponent.Mode.FLY_BURNS) return WARP_LEAD;
        double accel = mainEngineAccel();
        double half = accel > 0 ? Math.hypot(n.prograde, n.radial) / accel / 2 : 0;
        return WARP_LEAD + HelmOrders.TURN_LEAD + half;
    }

    public ManeuverNodeComponent.Node nextBurn() {
        ManeuverNodeComponent m = world.burns.nodes();
        return m == null ? null : m.next();
    }

    /**
     * Every planned burn still to come as the outside view marks it, in time order, into {@code out}
     * as four floats each: direction x and y (a unit vector in the parent body's frame), delta-v (m/s)
     * and seconds until it. Each burn after the first is aimed on the orbit the ones before it leave,
     * as the orbit map draws it. Empty with no burn or no orbit.
     */
    public void plannedBurnMarkers(com.badlogic.gdx.utils.FloatArray out) {
        out.clear();
        ManeuverNodeComponent m = world.burns.nodes();
        KeplerOrbit k = m == null || m.nodes.size == 0 ? null : orbits.currentConic();
        if (k == null) return;
        double now = world.universe.now();
        double[] v = new double[2];
        for (ManeuverNodeComponent.Node n : m.nodes) {
            if (n.time < now) continue;
            burnVector(k, n, v);
            double dv = Math.hypot(v[0], v[1]);
            if (dv > 0) out.addAll((float) (v[0] / dv), (float) (v[1] / dv), (float) dv, (float) (n.time - now));
            k = k.withBurn(n.time, n.prograde, n.radial);
        }
    }

    /**
     * A planned burn as a delta-v vector in the conic's frame, from the velocity there at its time
     * (along it, and turned away from the parent, as {@link KeplerOrbit#withBurn} reads them), into {x, y}.
     */
    static void burnVector(KeplerOrbit k, ManeuverNodeComponent.Node n, double[] out) {
        StateVector s = k.stateAt(n.time);
        double speed = Math.hypot(s.vx, s.vy);
        double ux = s.vx / speed, uy = s.vy / speed;
        out[0] = n.prograde * ux + n.radial * uy * k.direction;
        out[1] = n.prograde * uy - n.radial * ux * k.direction;
    }

    // ------------------------------------------------------------------ closest approach

    /** The closest approach to the target along the planned path (or the proposal), or null. */
    public ClosestApproach.Result approach() {
        String uuid = target.uuid();
        if (uuid == null) return null;
        if (sinceApproach >= APPROACH_REFRESH || !uuid.equals(approachFor)) {
            sinceApproach = 0;
            approachFor = uuid;
            findApproach();
        }
        return approach.found() ? approach : null;
    }

    private void findApproach() {
        approach.time = Double.NaN;
        KeplerOrbit k = orbits.currentConic();
        Entity frame = orbits.parent();
        if (k == null || frame == null) return;
        double now = world.universe.now();
        ClosestApproach.Path path = path(k, now);
        KeplerOrbit last = path.last();
        double from = Math.max(now, path.lastStart());
        double horizon = last.isElliptic() ? Math.min(last.period(), MAX_HORIZON) : OPEN_HORIZON;
        ClosestApproach.find(path, (t, out) -> target.stateAt(frame, t, out), now, from + horizon, approach);
    }

    /** The current conic, then after each burn of the proposal if there is one, else of the plan. */
    ClosestApproach.Path path(KeplerOrbit k, double now) {
        ClosestApproach.Path path = new ClosestApproach.Path().add(now, k);
        KeplerOrbit after = k;
        PlayerTransfer.Proposal p = transfer.proposal();
        if (p != null) {
            for (double[] b : p.burns) {
                if (b[0] < now) continue;
                after = after.withBurn(b[0], b[1], b[2]);
                path.add(b[0], after);
            }
            return path;
        }
        ManeuverNodeComponent m = world.burns.nodes();
        if (m != null) {
            for (ManeuverNodeComponent.Node n : m.nodes) {
                if (n.time < now) continue;
                after = after.withBurn(n.time, n.prograde, n.radial);
                path.add(n.time, after);
            }
        }
        return path;
    }

    // ------------------------------------------------------------------ fuel

    /** Delta-v the proposal, or else the planned burns, need, m/s. */
    public double deltaVNeeded() {
        PlayerTransfer.Proposal p = transfer.proposal();
        if (p != null) return p.totalDeltaV;
        ManeuverNodeComponent m = world.burns.nodes();
        double dv = 0;
        if (m != null) for (ManeuverNodeComponent.Node n : m.nodes) dv += Math.hypot(n.prograde, n.radial);
        return dv;
    }

    /** Delta-v the propellant aboard can still make, m/s; infinite for a ship whose thrust is free. */
    public double deltaVLeft() {
        Entity ship = world.getPlayer();
        ShipGridComponent.MassProperties m = ship == null ? null : world.shipMass(ship);
        return m == null ? Double.POSITIVE_INFINITY : PropellantFeed.deltaVLeft(ship, m.mass);
    }

    /**
     * Propellant, kg, the {@link #returnDeltaV} home takes; infinite when no station can be reached,
     * when the low-propellant warning falls back on a fifth of the tanks.
     */
    public double returnPropellantKg() {
        Entity ship = world.getPlayer();
        ShipGridComponent.MassProperties m = ship == null ? null : world.shipMass(ship);
        return m == null ? Double.POSITIVE_INFINITY : PropellantFeed.propellantFor(ship, m.mass, returnDeltaV());
    }

    /**
     * An estimate of the delta-v to reach the nearest station that would give the player clearance,
     * from the orbit the ship is on, m/s: 0 docked at one, NaN when none can be reached. For the
     * low-propellant warning.
     */
    public double returnDeltaV() {
        Entity ship = world.getPlayer();
        if (ship == null) return Double.NaN;
        Entity dock = world.docking.dockedTo(ship);
        if (dock != null && world.macroSim.station(world.universe.uuidOf(dock)) != null
                && world.standing.refusal(dock, ship) == null) return 0;
        KeplerOrbit k = orbits.currentConic();
        Entity parent = orbits.parent();
        if (k == null || parent == null) return Double.NaN;
        double now = world.universe.now();
        StateVector s = orbits.state();
        com.ringsofiron.macrosim.rails.StateVector from = new com.ringsofiron.macrosim.rails.StateVector(s.x, s.y, s.vx, s.vy);
        String parentUuid = world.universe.uuidOf(parent);
        double best = Double.NaN;
        for (EconomyNode station : world.macroSim.stations()) {
            Entity e = world.engine.getEntity(station.stationUuid);
            if (e == null || world.standing.refusal(e, ship) != null) continue;
            RoutePlanner.Route r;
            try {
                r = RoutePlanner.estimate(world.macroSim.rails(), parentUuid, from, station.stationUuid, now, now);
            } catch (RuntimeException ex) {
                r = null;
            }
            if (r != null && !(r.totalDeltaV >= best)) best = r.totalDeltaV;
        }
        return best;
    }

    /** The main engine's acceleration now, m/s², or 0. */
    double mainEngineAccel() {
        Entity ship = world.getPlayer();
        com.ringsofiron.physicsexterior.components.ThrusterComponent t = ship == null ? null
                : ship.getComponent(com.ringsofiron.physicsexterior.components.ThrusterComponent.class);
        ShipGridComponent.MassProperties m = ship == null ? null : world.shipMass(ship);
        return t == null || m == null || m.mass <= 0 ? 0 : t.forwardThrust / m.mass;
    }

    // ------------------------------------------------------------------ the screen

    /** Lines for the screen: the target and the closest approach, the next burn, the helm, the fuel. */
    public List<String> readout() {
        List<String> out = new ArrayList<>();
        double now = world.universe.now();
        if (target.uuid() != null) {
            String line = "Target: " + target.name();
            Entity frame = orbits.currentConic() == null ? null : orbits.parent();
            StateVector q = new StateVector();
            if (frame != null && target.stateAt(frame, now, q)) {
                StateVector s = orbits.state();
                double d = Math.hypot(s.x - q.x, s.y - q.y);
                double closing = -((s.x - q.x) * (s.vx - q.vx) + (s.y - q.y) * (s.vy - q.vy)) / Math.max(d, 1e-9);
                line += String.format(Locale.ROOT, "  %s  %s %.1f m/s", formatDistance(d), closing >= 0 ? "closing" : "opening", Math.abs(closing));
            }
            out.add(line);
            ClosestApproach.Result a = approach();
            if (a != null) out.add(String.format(Locale.ROOT, "Closest %s in %s, at %.0f m/s",
                    formatDistance(a.distance), formatTime(a.time - now), a.speed));
        }
        ManeuverNodeComponent.Node n = nextBurn();
        if (n != null) {
            out.add(String.format(Locale.ROOT, "Next burn in %s: %.0f m/s   %s warps there", formatTime(n.time - now),
                    Math.hypot(n.prograde, n.radial), key(Action.WARP_TO_BURN)));
            // A countdown for every burn after it too
            ManeuverNodeComponent m = world.burns.nodes();
            StringBuilder later = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < m.nodes.size; i++) {
                ManeuverNodeComponent.Node b = m.nodes.get(i);
                if (b == n || b.time < now) continue;
                if (shown++ == LATER_BURNS) {
                    later.append(", ...");
                    break;
                }
                later.append(later.length() == 0 ? "Then burns in " : ", ").append(formatTime(b.time - now))
                        .append(String.format(Locale.ROOT, " (%.0f m/s)", Math.hypot(b.prograde, b.radial)));
            }
            if (later.length() > 0) out.add(later.toString());
        }
        String h = helm.describe();
        if (h != null) out.add(h);
        String auto = autopilot.describe();
        if (auto != null) out.add(auto);
        double need = deltaVNeeded();
        if (need > 0) {
            double left = deltaVLeft();
            out.add(String.format(Locale.ROOT, "Delta-v needed %,.0f / left %s m/s%s", need,
                    Double.isInfinite(left) ? "unlimited" : String.format(Locale.ROOT, "%,.0f", left),
                    left < need ? "   NOT ENOUGH PROPELLANT" : ""));
        }
        return out;
    }

    /** The name of the key the player has on {@code action}. */
    public String key(Action action) {
        return keyNames.apply(action);
    }

    public static String formatDistance(double m) {
        if (m < 10_000) return String.format(Locale.ROOT, "%,.0f m", m);
        return String.format(Locale.ROOT, "%,.0f km", m / 1000);
    }

    /** "1:02:03", or "2.4 d" past two days. */
    public static String formatTime(double seconds) {
        long s = Math.max(0, (long) seconds);
        if (s >= 2 * 86400) return String.format(Locale.ROOT, "%.1f d", s / 86400.0);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }
}
