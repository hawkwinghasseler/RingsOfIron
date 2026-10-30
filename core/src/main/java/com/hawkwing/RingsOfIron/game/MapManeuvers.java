package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.orbital.rails.RailsUniverse;

/**
 * ui-hud's orbit map planning over the game's {@link BurnPlanner} and {@link Navigation}: a click on the
 * path plans a burn where the ship will be nearest that point, and the map's buttons give the same
 * orders the keys do, each told to the player as a notice.
 *
 * <p>The path is the one the map draws: the orbit now until the first burn, then the orbit each burn
 * leaves until the next, and the last one as {@link MapTrajectory} patches it: cut where it meets a moon,
 * on through the moon's sphere (the moon moving on under it, as {@link PlayerOrbitPath} draws it) and
 * back out, each leg with no end found drawn for an orbit (or {@link PlayerOrbitPath#OPEN_SPAN} on an
 * open one). A point is matched to the time the ship passes nearest it on that path.
 */
public class MapManeuvers implements ManeuverPlannerProvider {
    /** Samples per span of path when finding the time nearest a point; refined after. */
    static final int SAMPLES = 360;

    private final GameContext ctx;
    private final PhysicsManager physics;
    private final RailsUniverse universe;
    private final BurnPlanner burnPlanner;
    private final Navigation nav;
    private final PlayerOrbitPath path;
    private final PlayerOrbitInfo orbits;
    private final MapTrajectory trajectory;
    private final StateVector s = new StateVector();
    private final StateVector moon = new StateVector();

    public MapManeuvers(GameContext ctx, PhysicsManager physics, RailsUniverse universe, BurnPlanner burnPlanner, Navigation nav, PlayerOrbitPath path) {
        this.ctx = ctx;
        this.physics = physics;
        this.universe = universe;
        this.burnPlanner = burnPlanner;
        this.nav = nav;
        this.path = path;
        orbits = new PlayerOrbitInfo(universe, physics, ctx::player);
        trajectory = new MapTrajectory(universe);
    }

    // ------------------------------------------------------------------ planning

    /**
     * One stretch of the planned path: a conic flown from {@code from} until {@code to}, in the map's
     * frame moved by ({@code ox}, {@code oy}), or around {@code moving} as it moves on in the map's frame.
     */
    static final class Span {
        final KeplerOrbit conic;
        final double from, to;
        final Entity moving;
        final double ox, oy;

        Span(KeplerOrbit conic, double from, double to) {
            this(conic, from, to, null, 0, 0);
        }

        Span(KeplerOrbit conic, double from, double to, Entity moving, double ox, double oy) {
            this.conic = conic;
            this.from = from;
            this.to = to;
            this.moving = moving;
            this.ox = ox;
            this.oy = oy;
        }
    }

    /** The planned path as spans, the orbit now first; empty with no orbit. */
    List<Span> spans() {
        List<Span> out = new ArrayList<>();
        KeplerOrbit k = orbits.currentConic();
        if (k == null) return out;
        double t = universe.now();
        List<ManeuverNodeComponent.Node> nodes = shown();
        for (ManeuverNodeComponent.Node n : nodes) {
            out.add(new Span(k, t, n.time));
            k = k.withBurn(n.time, n.prograde, n.radial);
            t = n.time;
        }
        path(k, t, nodes, nodes.size(), out);
        return out;
    }

    /**
     * The path on from {@code from} on conic {@code k}, as the map draws it past the plan's last burn
     * (or past {@code burns} of {@code nodes}), into {@code out}: its legs through any moon it meets.
     */
    private void path(KeplerOrbit k, double from, List<ManeuverNodeComponent.Node> nodes, int burns, List<Span> out) {
        Entity frame = orbits.parent();
        if (frame == null) {
            out.add(new Span(k, from, from + reach(k)));
            return;
        }
        long plan = 17;
        for (int i = 0; i < burns; i++) {
            ManeuverNodeComponent.Node n = nodes.get(i);
            plan = plan * 1_000_003 + Double.hashCode(n.time) * 31L + Double.hashCode(n.prograde) * 7L + Double.hashCode(n.radial);
        }
        for (MapTrajectory.Leg leg : trajectory.legs(k, frame, from, plan)) {
            boolean ends = !Double.isNaN(leg.to);
            double to = ends ? leg.to : leg.from + reach(leg.conic);
            if (leg.frame == frame) {
                out.add(new Span(leg.conic, leg.from, to));
            } else if (ends) {
                // Through a moon's sphere: drawn where the ship really is, the moon moving on under it
                out.add(new Span(leg.conic, leg.from, to, leg.frame, 0, 0));
            } else {
                // Caught by the moon: its orbit there, drawn about where the moon is at the periapsis
                universe.stateInFrame(leg.frame, frame, PlayerOrbitPath.ghostTime(leg), moon);
                out.add(new Span(leg.conic, leg.from, to, null, moon.x, moon.y));
            }
        }
    }

    /** How far on the last stretch of a path is drawn: an orbit, or the open span. */
    static double reach(KeplerOrbit k) {
        return k.isElliptic() ? k.period() : PlayerOrbitPath.OPEN_SPAN;
    }

    /** The burns the map draws, soonest first: those not yet due. */
    List<ManeuverNodeComponent.Node> shown() {
        List<ManeuverNodeComponent.Node> out = new ArrayList<>();
        ManeuverNodeComponent m = burnPlanner.nodes();
        if (m == null) return out;
        double now = universe.now();
        for (ManeuverNodeComponent.Node n : m.nodes) if (n.time >= now) out.add(n);
        return out;
    }

    /**
     * The time in [from, to] when the ship on {@code k} passes nearest (x, y), and how near, into {time,
     * distance}: sampled evenly in time, then narrowed between the samples either side of the best.
     */
    double[] nearest(Span k, double from, double to, double x, double y) {
        double best = from, bestD = Double.POSITIVE_INFINITY;
        double step = (to - from) / SAMPLES;
        if (!(step > 0)) return new double[]{from, distance(k, from, x, y)};
        for (int i = 0; i <= SAMPLES; i++) {
            double t = from + step * i;
            double d = distance(k, t, x, y);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        // Golden-section search between the neighbouring samples
        double lo = Math.max(from, best - step), hi = Math.min(to, best + step);
        double g = (Math.sqrt(5) - 1) / 2;
        for (int i = 0; i < 40; i++) {
            double a = hi - g * (hi - lo), b = lo + g * (hi - lo);
            if (distance(k, a, x, y) < distance(k, b, x, y)) hi = b;
            else lo = a;
        }
        double t = (lo + hi) / 2;
        double d = distance(k, t, x, y);
        return d < bestD ? new double[]{t, d} : new double[]{best, bestD};
    }

    /** How far the ship on the span is from (x, y) at {@code t}, in the map's frame. */
    private double distance(Span k, double t, double x, double y) {
        k.conic.stateAt(t, s);
        double px = s.x + k.ox, py = s.y + k.oy;
        if (k.moving != null) {
            universe.stateInFrame(k.moving, orbits.parent(), t, moon);
            px += moon.x;
            py += moon.y;
        }
        return Math.hypot(px - x, py - y);
    }

    /** The time on the spans, no sooner than a burn can be planned, nearest (x, y), and how near; NaN with none. */
    private double[] nearest(List<Span> spans, double x, double y) {
        double[] best = {Double.NaN, Double.POSITIVE_INFINITY};
        double earliest = universe.now() + BurnPlanner.MIN_LEAD;
        for (Span span : spans) {
            if (span.to < earliest) continue;
            double[] r = nearest(span, Math.max(span.from, earliest), span.to, x, y);
            if (r[1] < best[1]) best = r;
        }
        return best;
    }

    @Override
    public boolean addBurnNear(double x, double y, double tolerance) {
        double[] best = nearest(spans(), x, y);
        if (Double.isNaN(best[0]) || best[1] > tolerance) return false;
        burnPlanner.addNodeAt(best[0]);
        return true;
    }

    @Override
    public void selectBurn(int index) {
        List<ManeuverNodeComponent.Node> nodes = shown();
        if (index >= 0 && index < nodes.size()) burnPlanner.select(nodes.get(index));
    }

    @Override
    public boolean selectedBurn(double[] out) {
        ManeuverNodeComponent.Node n = burnPlanner.selected();
        if (n == null) return false;
        out[0] = n.prograde;
        out[1] = n.radial;
        return true;
    }

    @Override
    public void setSelectedBurn(double prograde, double radial) {
        burnPlanner.set(prograde, radial);
    }

    @Override
    public void moveSelectedNear(double x, double y) {
        ManeuverNodeComponent.Node n = burnPlanner.selected();
        if (n == null) return;
        // Along the path the burns before it leave, from the last of them: through any moon it meets
        KeplerOrbit k = orbits.currentConic();
        if (k == null) return;
        double from = universe.now();
        List<ManeuverNodeComponent.Node> nodes = shown();
        int before = 0;
        for (ManeuverNodeComponent.Node b : nodes) {
            if (b == n) break;
            k = k.withBurn(b.time, b.prograde, b.radial);
            from = b.time;
            before++;
        }
        List<Span> spans = new ArrayList<>();
        path(k, from, nodes, before, spans);
        double t = nearest(spans, x, y)[0];
        if (!Double.isNaN(t)) burnPlanner.setTime(t);
    }

    @Override
    public void deleteSelected() {
        burnPlanner.deleteSelected();
    }

    @Override
    public void clearBurns() {
        burnPlanner.clear();
        ctx.notices.post("Every planned burn cleared");
    }

    // ------------------------------------------------------------------ orders

    @Override
    public void warpToBurn() {
        ctx.notices.post(nav.warpToNextBurn());
    }

    @Override
    public void helmFlyBurns() {
        ctx.notices.post(nav.helm.give(HelmOrderComponent.Mode.FLY_BURNS));
    }

    @Override
    public void autopilot() {
        ctx.notices.post(nav.autopilot.toggle());
    }

    @Override
    public void nextTarget() {
        ctx.notices.replace("Navigation target", nav.target.cycle(1));
    }

    @Override
    public void planToTarget() {
        ctx.notices.post(nav.transfer.plan());
    }

    @Override
    public void acceptPlan() {
        if (nav.transfer.proposal() != null) ctx.notices.post(nav.transfer.accept(burnPlanner));
    }

    @Override
    public void discardPlan() {
        if (nav.transfer.proposal() != null) ctx.notices.post(nav.transfer.discard());
    }

    @Override
    public boolean hasTarget() {
        return nav.target.uuid() != null;
    }

    @Override
    public boolean hasProposal() {
        return nav.transfer.proposal() != null;
    }

    // ------------------------------------------------------------------ the panel

    @Override
    public String orbitText() {
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (k == null || body == null) return "No orbit";
        double now = universe.now();
        StateVector at = orbits.state();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Around %s: altitude %s, %,.0f m/s%n", body.name,
                km(at.radius() - body.radius), at.speed()));
        out.append("Pe ").append(km(k.periapsis() - body.radius));
        double pe = MapTrajectory.nextPeriapsis(k, now);
        if (!Double.isNaN(pe)) out.append(" in ").append(Navigation.formatTime(pe - now));
        if (k.isElliptic()) {
            out.append("\nAp ").append(km(k.apoapsis() - body.radius)).append(" in ")
                    .append(Navigation.formatTime(MapTrajectory.nextApoapsis(k, now) - now));
            out.append(String.format(Locale.ROOT, "%nPeriod %s, e %.3f", Navigation.formatTime(k.period()), k.e));
        } else {
            out.append(String.format(Locale.ROOT, "%nEscaping, e %.3f", k.e));
        }
        if (k.periapsis() < body.radius) out.append("\nON COURSE TO HIT ").append(body.name.toUpperCase(Locale.ROOT));
        return out.toString();
    }

    @Override
    public String burnText() {
        ManeuverNodeComponent.Node n = burnPlanner.selected();
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (n == null || k == null || body == null) return "";
        double now = universe.now();
        List<ManeuverNodeComponent.Node> nodes = shown();
        KeplerOrbit after = k;
        for (ManeuverNodeComponent.Node b : nodes) {
            after = after.withBurn(b.time, b.prograde, b.radial);
            if (b == n) break;
        }
        double dv = Math.hypot(n.prograde, n.radial);
        double accel = nav.mainEngineAccel();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Burn %d of %d in %s%n", nodes.indexOf(n) + 1, nodes.size(),
                Navigation.formatTime(n.time - now)));
        out.append(String.format(Locale.ROOT, "Prograde %+.1f, radial %+.1f m/s%n", n.prograde, n.radial));
        out.append(String.format(Locale.ROOT, "%,.1f m/s", dv));
        if (accel > 0 && dv > 0) out.append(", about ").append(Navigation.formatTime(Math.max(1, dv / accel))).append(" on the main engine");
        out.append("\nThen Pe ").append(km(after.periapsis() - body.radius));
        out.append(after.isElliptic() ? ", Ap " + km(after.apoapsis() - body.radius) : ", escaping");
        if (after.periapsis() < body.radius) out.append(", IMPACT");
        return out.toString();
    }

    @Override
    public String planText() {
        StringBuilder out = new StringBuilder();
        String trajectory = path.trajectorySummary();
        if (!trajectory.isEmpty()) out.append(trajectory);
        for (String line : nav.readout()) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    private static String km(double meters) {
        return String.format(Locale.ROOT, "%,.0f km", meters / 1000.0);
    }
}
