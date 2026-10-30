package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ui-hud's orbit map planning over the game's {@link BurnPlanner} and {@link Navigation}: a click on the
 * path plans a burn where the ship will be nearest that point, and the map's buttons give the same
 * orders the keys do, each told to the player as a notice.
 *
 * <p>The path is the one the map draws: the orbit now until the first burn, then the orbit each burn
 * leaves until the next, and the last one for an orbit (or {@link PlayerOrbitPath#OPEN_SPAN} on an open
 * one). A point is matched to the time the ship passes nearest it on that path.
 */
public class MapManeuvers implements ManeuverPlannerProvider {
    /** Samples per span of path when finding the time nearest a point; refined after. */
    static final int SAMPLES = 360;

    private final GameWorld world;
    private final PlayerOrbitPath path;
    private final PlayerOrbitInfo orbits;
    private final StateVector s = new StateVector();

    public MapManeuvers(GameWorld world, PlayerOrbitPath path) {
        this.world = world;
        this.path = path;
        orbits = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer);
    }

    // ------------------------------------------------------------------ planning

    /** One stretch of the planned path: a conic flown from {@code from} until {@code to}. */
    static final class Span {
        final KeplerOrbit conic;
        final double from, to;

        Span(KeplerOrbit conic, double from, double to) {
            this.conic = conic;
            this.from = from;
            this.to = to;
        }
    }

    /** The planned path as spans, the orbit now first; empty with no orbit. */
    List<Span> spans() {
        List<Span> out = new ArrayList<>();
        KeplerOrbit k = orbits.currentConic();
        if (k == null) return out;
        double t = world.universe.now();
        for (ManeuverNodeComponent.Node n : shown()) {
            out.add(new Span(k, t, n.time));
            k = k.withBurn(n.time, n.prograde, n.radial);
            t = n.time;
        }
        out.add(new Span(k, t, t + reach(k)));
        return out;
    }

    /** How far on the last stretch of a path is drawn: an orbit, or the open span. */
    static double reach(KeplerOrbit k) {
        return k.isElliptic() ? k.period() : PlayerOrbitPath.OPEN_SPAN;
    }

    /** The burns the map draws, soonest first: those not yet due. */
    List<ManeuverNodeComponent.Node> shown() {
        List<ManeuverNodeComponent.Node> out = new ArrayList<>();
        ManeuverNodeComponent m = world.burns.nodes();
        if (m == null) return out;
        double now = world.universe.now();
        for (ManeuverNodeComponent.Node n : m.nodes) if (n.time >= now) out.add(n);
        return out;
    }

    /**
     * The time in [from, to] when {@code k} passes nearest (x, y), and how near, into {time, distance}:
     * sampled evenly in time, then narrowed between the samples either side of the best.
     */
    double[] nearest(KeplerOrbit k, double from, double to, double x, double y) {
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

    private double distance(KeplerOrbit k, double t, double x, double y) {
        k.stateAt(t, s);
        return Math.hypot(s.x - x, s.y - y);
    }

    @Override
    public boolean addBurnNear(double x, double y, double tolerance) {
        double bestT = Double.NaN, bestD = Double.POSITIVE_INFINITY;
        double earliest = world.universe.now() + BurnPlanner.MIN_LEAD;
        for (Span span : spans()) {
            if (span.to < earliest) continue;
            double[] r = nearest(span.conic, Math.max(span.from, earliest), span.to, x, y);
            if (r[1] < bestD) {
                bestD = r[1];
                bestT = r[0];
            }
        }
        if (Double.isNaN(bestT) || bestD > tolerance) return false;
        world.burns.addNodeAt(bestT);
        return true;
    }

    @Override
    public void selectBurn(int index) {
        List<ManeuverNodeComponent.Node> nodes = shown();
        if (index >= 0 && index < nodes.size()) world.burns.select(nodes.get(index));
    }

    @Override
    public boolean selectedBurn(double[] out) {
        ManeuverNodeComponent.Node n = world.burns.selected();
        if (n == null) return false;
        out[0] = n.prograde;
        out[1] = n.radial;
        return true;
    }

    @Override
    public void setSelectedBurn(double prograde, double radial) {
        world.burns.set(prograde, radial);
    }

    @Override
    public void moveSelectedNear(double x, double y) {
        ManeuverNodeComponent.Node n = world.burns.selected();
        if (n == null) return;
        // Along the orbit the burns before it leave, for one turn of it from the last of them
        KeplerOrbit k = orbits.currentConic();
        if (k == null) return;
        double from = world.universe.now();
        for (ManeuverNodeComponent.Node b : shown()) {
            if (b == n) break;
            k = k.withBurn(b.time, b.prograde, b.radial);
            from = b.time;
        }
        from = Math.max(from, world.universe.now() + BurnPlanner.MIN_LEAD);
        world.burns.setTime(nearest(k, from, from + reach(k), x, y)[0]);
    }

    @Override
    public void deleteSelected() {
        world.burns.deleteSelected();
    }

    @Override
    public void clearBurns() {
        world.burns.clear();
        world.notices.post("Every planned burn cleared");
    }

    // ------------------------------------------------------------------ orders

    @Override
    public void warpToBurn() {
        world.notices.post(world.nav.warpToNextBurn());
    }

    @Override
    public void helmFlyBurns() {
        world.notices.post(world.nav.helm.give(HelmOrderComponent.Mode.FLY_BURNS));
    }

    @Override
    public void autopilot() {
        world.notices.post(world.nav.autopilot.toggle());
    }

    @Override
    public void nextTarget() {
        world.notices.replace("Navigation target", world.nav.target.cycle(1));
    }

    @Override
    public void planToTarget() {
        world.notices.post(world.nav.transfer.plan());
    }

    @Override
    public void acceptPlan() {
        if (world.nav.transfer.proposal() != null) world.notices.post(world.nav.transfer.accept(world.burns));
    }

    @Override
    public void discardPlan() {
        if (world.nav.transfer.proposal() != null) world.notices.post(world.nav.transfer.discard());
    }

    @Override
    public boolean hasTarget() {
        return world.nav.target.uuid() != null;
    }

    @Override
    public boolean hasProposal() {
        return world.nav.transfer.proposal() != null;
    }

    // ------------------------------------------------------------------ the panel

    @Override
    public String orbitText() {
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (k == null || body == null) return "No orbit";
        double now = world.universe.now();
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
        ManeuverNodeComponent.Node n = world.burns.selected();
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (n == null || k == null || body == null) return "";
        double now = world.universe.now();
        List<ManeuverNodeComponent.Node> nodes = shown();
        KeplerOrbit after = k;
        for (ManeuverNodeComponent.Node b : nodes) {
            after = after.withBurn(b.time, b.prograde, b.radial);
            if (b == n) break;
        }
        double dv = Math.hypot(n.prograde, n.radial);
        double accel = world.nav.mainEngineAccel();
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
        for (String line : world.nav.readout()) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    private static String km(double meters) {
        return String.format(Locale.ROOT, "%,.0f km", meters / 1000.0);
    }
}
