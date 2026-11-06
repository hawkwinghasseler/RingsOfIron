package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.input.Action;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.ClosestApproach;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * ui-hud's orbit map from orbital-mechanics: the player's conic now and after its planned burns,
 * sampled evenly in eccentric (or hyperbolic) anomaly rather than in time, so the curve stays smooth
 * through a fast periapsis pass. Burns already due are left out; they fire this frame.
 *
 * <p>The path the ship will fly (after the plan's last burn, or the orbit now with none) goes on past a
 * change of sphere of influence: around a moon it meets and back out, through {@link MapTrajectory},
 * with its apsides, the changes and any impact marked. A pass through a moon's sphere is drawn where the
 * ship really goes, the moon moving on under it, and the moon is drawn where it is at the closest
 * approach; a ship caught by the moon has its orbit there drawn about that point.
 *
 * <p>With {@link Navigation}: the stations around the same body, the navigation target with its
 * orbit (when it shares the parent), the closest approach to it, and a transfer proposed to it.
 */
public class PlayerOrbitPath implements OrbitPathProvider {
    public static final int SEGMENTS = 128;
    /** How far an open orbit is drawn, seconds. */
    public static final double OPEN_SPAN = 2 * 86400.0;
    /** And how far out at most, in body radii. */
    public static final double OPEN_RADII = 20;

    private final PlayerOrbitInfo orbits;
    private final BurnPlanner planner;
    private final DoubleSupplier clock;
    private final RailsUniverse universe;
    /** With navigation: what the map marks besides the player's path (stations, the target). */
    private Navigation nav;
    private GameEngine engine;
    private MacroSimulation macroSim;
    private PirateBase pirateBase;
    private final Array<Entity> children = new Array<>();
    private final StateVector s = new StateVector();
    private final MapTrajectory trajectory;
    /** The burns not yet due, soonest first: those the map draws. */
    private final List<ManeuverNodeComponent.Node> burns = new ArrayList<>();
    private final StringBuilder summary = new StringBuilder();
    private final double[] off = new double[2];
    /** Refines the curves where they cross the map's view, and measures them from its middle. */
    private final MapSampler sampler = new MapSampler();

    public PlayerOrbitPath(GameContext ctx, PhysicsManager physics, RailsUniverse universe, MacroSimulation macroSim,
                           PirateBase pirateBase, Navigation nav, BurnPlanner planner) {
        this(universe, new PlayerOrbitInfo(universe, physics, ctx::player), planner);
        this.engine = ctx.engine;
        this.macroSim = macroSim;
        this.pirateBase = pirateBase;
        this.nav = nav;
    }

    public PlayerOrbitPath(RailsUniverse universe, PlayerOrbitInfo orbits, BurnPlanner planner) {
        this.universe = universe;
        this.orbits = orbits;
        this.planner = planner;
        this.clock = universe::now;
        this.trajectory = new MapTrajectory(universe);
    }

    /** The body the last {@link #getPlayerPath} drew around, or null. */
    public Entity frame() {
        return orbits.parent();
    }

    @Override
    public boolean getPlayerPath(OrbitPath out) {
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (k == null) return false;
        Entity frame = orbits.parent();
        double now = clock.getAsDouble();
        sampler.setView(out);
        out.bodyName = body.name == null ? "" : body.name;
        out.bodyRadius = body.radius;
        out.shipX = orbits.state().x;
        out.shipY = orbits.state().y;
        sample(sampler, k, now, Double.NaN, body, 0, 0, out.current, out.currentFade);
        addBodies(now, out);

        ManeuverNodeComponent m = planner.nodes();
        ManeuverNodeComponent.Node selected = planner.selected();
        burns.clear();
        if (m != null) for (ManeuverNodeComponent.Node n : m.nodes) if (n.time >= now) burns.add(n);
        // The whole plan as patched conics: each burn flown around whatever the ship is around by then
        List<MapTrajectory.Leg> legs = trajectory.plan(k, frame, now, burns);
        for (int i = 0; i < burns.size(); i++) {
            ManeuverNodeComponent.Node n = burns.get(i);
            MapTrajectory.Leg at = trajectory.burnLeg(i);
            at.conic.stateAt(n.time, s);
            double x = s.x, y = s.y, vx = s.vx, vy = s.vy;
            offset(at, frame, n.time, off);
            sampler.add(x + off[0], y + off[1], out.nodes);
            // The burn's axes: along the velocity there (around the body it is flown about), and radially
            // out as withBurn reads it
            double speed = Math.hypot(vx, vy);
            float ux = (float) (vx / speed), uy = (float) (vy / speed);
            out.nodeAxes.add(ux, uy, uy * at.conic.direction, -ux * at.conic.direction);
            if (n == selected) out.selectedNode = i;
            out.nodeLabels.add((i + 1) + ": " + Navigation.formatTime(n.time - now));
        }
        trajectory(legs, frame, body, burns.size(), now, out);
        if (selected != null) {
            int i = burns.indexOf(selected);
            MapTrajectory.Leg after = i < 0 ? null : trajectory.afterBurn(i);
            CelestialBodyComponent around = after == null ? null : around(after, frame, body);
            if (around != null) caption(out.caption, selected, planner.selectedIndex(), m.nodes.size, now, after.conic, around);
        } else {
            out.caption.append("N plans a burn");
        }
        if (summary.length() > 0) out.caption.append("\n").append(summary);
        if (nav != null) navigation(k, now, body, out);
        return true;
    }

    /**
     * What the path does past its own conic, in a line: the moon it meets and how close, an impact, or
     * leaving the body; empty when it does none of them. As of the last {@link #getPlayerPath}.
     */
    public String trajectorySummary() {
        return summary.toString();
    }

    /** The body a leg is flown around: the map's own for its frame. */
    private CelestialBodyComponent around(MapTrajectory.Leg leg, Entity frame, CelestialBodyComponent body) {
        return leg.frame == frame ? body : universe.celestial(leg.frame);
    }

    /**
     * The planned path from {@link MapTrajectory#plan}: the orbit now (into {@code current}) until the
     * first burn, each burn's orbit on to the next ({@code stretches}, dotted on the map), and past the
     * last burn ({@code planned} and {@code legs}), each cut where it meets a moon, the surface or the
     * edge of the body's sphere of influence and on from there; with the changes marked, and the apsides
     * of the path past the last burn (or of the orbit now, with none).
     */
    private void trajectory(List<MapTrajectory.Leg> legs, Entity frame, CelestialBodyComponent body, int burnCount,
                            double now, OrbitPath out) {
        summary.setLength(0);
        boolean reachable = true;
        int firstOfStretch = 0;
        for (int i = 0; i < legs.size(); i++) {
            MapTrajectory.Leg leg = legs.get(i);
            boolean last = leg.stretch == burnCount;
            boolean planned = leg.stretch > 0;
            if (i > 0 && leg.stretch != legs.get(i - 1).stretch) {
                firstOfStretch = i;
                // A burn the path never gets to (it hit the ground or left the body first) is not drawn on from
                reachable &= legs.get(i - 1).toBurn;
            }
            if (!reachable) break;
            CelestialBodyComponent around = around(leg, frame, body);
            if (around == null) break;
            String name = around.name == null ? "" : around.name;
            FloatArray points, fade;
            if (last && i == firstOfStretch) {
                points = planned ? out.planned : out.current;
                fade = planned ? out.plannedFade : out.currentFade;
            } else if (i == 0) {
                // The orbit now up to the first burn: drawn whole as the current orbit already
                points = fade = null;
            } else {
                OrbitPathProvider.PathLeg l = last ? out.addLeg(planned) : out.addStretch(planned);
                points = l.points;
                fade = l.fade;
            }
            if (points != null) {
                points.clear();
                fade.clear();
                if (leg.frame == frame) {
                    sample(sampler, leg.conic, leg.from, leg.to, around, 0, 0, points, fade);
                } else if (Double.isNaN(leg.to)) {
                    // Caught by the moon: its orbit there, drawn about where the moon is at the periapsis
                    offset(leg, frame, ghostTime(leg), off);
                    sample(sampler, leg.conic, leg.from, leg.to, around, off[0], off[1], points, fade);
                } else {
                    // Through a moon's sphere: where the ship really is, the moon moving on under it, so the
                    // path runs on unbroken from the leg before into the one after
                    sampleMoving(leg, frame, points, fade);
                }
            }
            if (leg.frame != frame) {
                offset(leg, frame, ghostTime(leg), off);
                OrbitPathProvider.PathMark ghost = out.addMark(OrbitPathProvider.MarkKind.GHOST, off[0], off[1], name);
                ghost.radius = around.radius;
                ghost.soiRadius = around.soiRadius;
                ghost.planned = planned;
            }
            if (last) apsides(leg, frame, around, i == firstOfStretch ? "" : name + " ", planned, now, out);
            if (leg.end == null) continue;
            offset(leg, frame, leg.to, off);
            leg.conic.stateAt(leg.to, s);
            double ex = s.x + off[0], ey = s.y + off[1];
            String in = Navigation.formatTime(leg.to - now);
            switch (leg.end) {
                case SOI_ENTRY: {
                    CelestialBodyComponent moon = universe.celestial(leg.entered);
                    String moonName = moon.name == null ? "" : moon.name;
                    out.addMark(OrbitPathProvider.MarkKind.SOI_ENTRY, ex, ey, "Enters " + moonName + " in " + in).planned = planned;
                    if (summary.length() == 0) summary.append("Meets ").append(moonName).append(" in ").append(in);
                    break;
                }
                case SOI_EXIT:
                    out.addMark(OrbitPathProvider.MarkKind.SOI_EXIT, ex, ey, "Leaves " + name + " in " + in).planned = planned;
                    if (summary.length() == 0) summary.append("Leaves ").append(name).append("'s sphere of influence in ").append(in);
                    break;
                default:
                    out.addMark(OrbitPathProvider.MarkKind.IMPACT, ex, ey, "Impact: " + name + " in " + in).planned = planned;
                    if (summary.length() == 0 || i > 0) {
                        summary.append(summary.length() == 0 ? "" : "; then ").append("IMPACT on ").append(name).append(" in ").append(in);
                    }
            }
        }
    }

    /**
     * Where the leg's body is at {@code t} in the map's frame, into {@code out}: nothing for the map's
     * own body.
     */
    private void offset(MapTrajectory.Leg leg, Entity frame, double t, double[] out) {
        out[0] = out[1] = 0;
        if (leg.frame == frame) return;
        universe.stateInFrame(leg.frame, frame, t, s);
        out[0] = s.x;
        out[1] = s.y;
    }

    /** When a leg around a moon comes closest to it, or when it starts if that is never: where the moon is drawn. */
    static double ghostTime(MapTrajectory.Leg leg) {
        double pe = MapTrajectory.nextPeriapsis(leg.conic, leg.from);
        return !Double.isNaN(pe) && (Double.isNaN(leg.to) || pe <= leg.to) ? pe : leg.from;
    }

    /**
     * A leg through a moon's sphere as the map's frame sees it: its conic around the moon plus where the
     * moon is at each moment, sampled evenly in anomaly so the swing past periapsis stays smooth.
     */
    private void sampleMoving(MapTrajectory.Leg leg, Entity frame, FloatArray out, FloatArray fade) {
        KeplerOrbit c = leg.conic;
        double a0 = c.anomalyAt(leg.from), a1 = c.anomalyAt(leg.to);
        if (c.isElliptic() && a1 <= a0) a1 += 2 * Math.PI;
        double m0 = meanAnomaly(c, a0);
        sampler.sample((anomaly, xy) -> {
            double t = leg.from + (meanAnomaly(c, anomaly) - m0) / c.meanMotion();
            c.positionAtAnomaly(anomaly, s);
            double x = s.x, y = s.y;
            universe.stateInFrame(leg.frame, frame, t, s);
            xy[0] = x + s.x;
            xy[1] = y + s.y;
        }, a0, a1, SEGMENTS, out, fade);
        fade(c, a0, a1, fade);
    }

    /** Mean anomaly at an eccentric (or hyperbolic) anomaly, unwrapped. */
    private static double meanAnomaly(KeplerOrbit c, double anomaly) {
        return c.isElliptic() ? anomaly - c.e * Math.sin(anomaly) : c.e * Math.sinh(anomaly) - anomaly;
    }

    /** The leg's periapsis and apoapsis where it reaches them before it ends, labelled with the height. */
    private void apsides(MapTrajectory.Leg leg, Entity frame, CelestialBodyComponent around, String prefix,
                         boolean planned, double now, OrbitPath out) {
        KeplerOrbit c = leg.conic;
        double pe = MapTrajectory.nextPeriapsis(c, leg.from);
        boolean ends = !Double.isNaN(leg.to);
        boolean moving = leg.frame != frame && ends;
        if (!Double.isNaN(pe) && (!ends || pe <= leg.to) && c.periapsis() > around.radius) {
            offset(leg, frame, moving ? pe : ghostTime(leg), off);
            c.positionAtAnomaly(0, s);
            String text = prefix + "Pe " + km(c.periapsis() - around.radius);
            if (!prefix.isEmpty()) {
                text += " in " + Navigation.formatTime(pe - now);
                if (leg.end != OrbitalEvent.Type.IMPACT) summary.append(", ").append(prefix).append("Pe ").append(km(c.periapsis() - around.radius));
            }
            out.addMark(OrbitPathProvider.MarkKind.PERIAPSIS, s.x + off[0], s.y + off[1], text).planned = planned;
        }
        double ap = MapTrajectory.nextApoapsis(c, leg.from);
        if (!Double.isNaN(ap) && (!ends || ap <= leg.to)) {
            offset(leg, frame, moving ? ap : ghostTime(leg), off);
            c.positionAtAnomaly(Math.PI, s);
            out.addMark(OrbitPathProvider.MarkKind.APOAPSIS, s.x + off[0], s.y + off[1], prefix + "Ap " + km(c.apoapsis() - around.radius)).planned = planned;
        }
    }

    /** Stations, the target and its orbit, the closest approach and a proposed transfer. */
    private void navigation(KeplerOrbit k, double now, CelestialBodyComponent body, OrbitPath out) {
        Entity parent = orbits.parent();
        String targetUuid = nav.target.uuid();
        for (com.ringsofiron.macrosim.economy.EconomyNode station : macroSim.stations()) {
            Entity e = engine.getEntity(station.stationUuid);
            if (e == null || universe.parentOf(e) != parent || station.stationUuid.equals(targetUuid)
                    || pirateBase.hidden(station.stationUuid)) continue;
            universe.stateInFrame(e, parent, now, s);
            MapMarker m = out.addMarker();
            m.name = station.displayName == null ? "" : station.displayName;
            m.x = s.x;
            m.y = s.y;
        }
        PlayerTransfer.Proposal p = nav.transfer.proposal();
        if (p != null) {
            KeplerOrbit after = k;
            double from = now;
            for (double[] b : p.burns) {
                if (b[0] < now) continue;
                after.stateAt(b[0], s);
                sampler.add(s.x, s.y, out.proposedNodes);
                after = after.withBurn(b[0], b[1], b[2]);
                from = b[0];
            }
            sample(sampler, after, from, Double.NaN, body, 0, 0, out.proposed, out.proposedFade);
            out.caption.append("\nProposed: ").append(nav.transfer.describe(p)).append("\n")
                    .append(nav.key(Action.ACCEPT_PLAN)).append(" accepts  ").append(nav.key(Action.DISCARD_PLAN)).append(" discards");
        }
        if (targetUuid == null) {
            out.caption.append("\n").append(nav.key(Action.NAV_TARGET)).append(" picks a navigation target");
            return;
        }
        if (nav.target.stateAt(parent, now, s)) {
            MapMarker m = out.addMarker();
            m.name = nav.target.name();
            m.target = true;
            m.x = s.x;
            m.y = s.y;
        }
        Entity e = nav.target.entity();
        OrbitComponent o = e == null ? null : universe.orbit(e);
        Entity around = o == null ? null : universe.parentOf(e);
        if (around != null && o.getOrbit().isElliptic()) {
            // Around another body (a moon's station from the planet, or the planet's from a moon): its
            // orbit drawn about where that body is now
            double ox = 0, oy = 0;
            if (around != parent) {
                universe.stateInFrame(around, parent, now, s);
                ox = s.x;
                oy = s.y;
            }
            ellipse(o.getOrbit(), ox, oy, out.targetOrbit);
        }
        ClosestApproach.Result a = nav.approach();
        out.caption.append("\nTarget ").append(nav.target.name());
        if (a != null) {
            sampler.add(a.ship.x, a.ship.y, out.approach);
            sampler.add(a.target.x, a.target.y, out.approach);
            out.caption.append(": closest ").append(Navigation.formatDistance(a.distance))
                    .append(" in ").append(Navigation.formatTime(a.time - now));
        }
        out.caption.append("\n").append(nav.key(Action.NAV_TARGET)).append(" next target  ").append(nav.key(Action.PLAN_TO_TARGET))
                .append(" plan to it  ").append(nav.key(Action.WARP_TO_BURN)).append(" warp to burn");
    }

    /** The parent's moons with their orbits, and the body the parent itself orbits. */
    private void addBodies(double now, OrbitPath out) {
        Entity parent = orbits.parent();
        if (parent == null) return;
        for (Entity child : universe.childBodies(parent, children)) {
            MapBody b = addBody(child, parent, now, out);
            OrbitComponent o = universe.orbit(child);
            KeplerOrbit k = o.getOrbit();
            if (k.isElliptic()) ellipse(k, 0, 0, b.orbit);
        }
        Entity grandparent = universe.parentOf(parent);
        if (grandparent != null) addBody(grandparent, parent, now, out);
    }

    /** The whole of elliptic {@code k}, moved by ({@code ox}, {@code oy}). */
    private void ellipse(KeplerOrbit k, double ox, double oy, FloatArray out) {
        StateVector p = new StateVector();
        sampler.sample((anomaly, xy) -> {
            k.positionAtAnomaly(anomaly, p);
            xy[0] = p.x + ox;
            xy[1] = p.y + oy;
        }, 0, 2 * Math.PI, SEGMENTS, out);
    }

    private MapBody addBody(Entity body, Entity frame, double now, OrbitPath out) {
        CelestialBodyComponent c = universe.celestial(body);
        MapBody b = out.addBody();
        b.name = c.name == null ? "" : c.name;
        b.radius = c.radius;
        universe.stateInFrame(body, frame, now, s);
        b.x = s.x;
        b.y = s.y;
        return b;
    }

    /**
     * Samples {@code k} as the map draws it, x,y pairs in meters from {@code body}'s centre: the
     * whole ellipse, or an open orbit from {@code t} until it leaves the body (at most
     * {@link #OPEN_SPAN} on, or {@link #OPEN_RADII} radii out).
     */
    public static void sample(KeplerOrbit k, double t, CelestialBodyComponent body, FloatArray out) {
        sample(MapSampler.NONE, k, t, Double.NaN, body, 0, 0, out);
    }

    /**
     * As {@link #sample(KeplerOrbit, double, CelestialBodyComponent, FloatArray)}, but only as far as
     * {@code to} (NaN for the whole orbit), and moved by ({@code ox}, {@code oy}): a path that meets a
     * moon stops there.
     */
    public static void sample(KeplerOrbit k, double t, double to, CelestialBodyComponent body, double ox, double oy,
                              FloatArray out) {
        sample(MapSampler.NONE, k, t, to, body, ox, oy, out);
    }

    /** As above, refined where it crosses {@code sampler}'s view and measured from its origin. */
    static void sample(MapSampler sampler, KeplerOrbit k, double t, double to, CelestialBodyComponent body, double ox,
                       double oy, FloatArray out) {
        sample(sampler, k, t, to, body, ox, oy, out, null);
    }

    /**
     * As above, with how far along each point is into {@code fade} (when not null), as
     * {@link OrbitPath#currentFade} reads it. A whole ellipse starts where the ship is at {@code t}, so
     * the line runs the way it flies.
     */
    static void sample(MapSampler sampler, KeplerOrbit k, double t, double to, CelestialBodyComponent body, double ox,
                       double oy, FloatArray out, FloatArray fade) {
        StateVector s = new StateVector();
        double from, end;
        if (k.isElliptic() && (Double.isNaN(to) || to - t >= k.period())) {
            from = k.anomalyAt(t);
            end = from + 2 * Math.PI;
        } else if (k.isElliptic()) {
            from = k.anomalyAt(t);
            end = k.anomalyAt(to);
            if (end <= from) end += 2 * Math.PI;
        } else if (Double.isNaN(to)) {
            double edge = Math.min(body.soiRadius, OPEN_RADII * body.radius);
            double leave = k.nextTimeAtRadius(edge, t, KeplerOrbit.Crossing.OUTBOUND);
            double span = Double.isNaN(leave) ? OPEN_SPAN : Math.min(OPEN_SPAN, Math.max(1, leave - t));
            from = k.anomalyAt(t);
            end = k.anomalyAt(t + span);
        } else {
            from = k.anomalyAt(t);
            end = k.anomalyAt(to);
        }
        sampler.sample((anomaly, xy) -> {
            k.positionAtAnomaly(anomaly, s);
            xy[0] = s.x + ox;
            xy[1] = s.y + oy;
        }, from, end, SEGMENTS, out, fade);
        if (fade != null) fade(k, from, end, fade);
    }

    /**
     * Turns {@code fade}'s anomalies on {@code k} into how far along each is from {@code from}: the share
     * of a period the ship takes to get there, or for an open orbit of the line from {@code from} to
     * {@code end}; 0 to 1.
     */
    static void fade(KeplerOrbit k, double from, double end, FloatArray fade) {
        double m0 = meanAnomaly(k, from);
        double span = k.isElliptic() ? 2 * Math.PI : meanAnomaly(k, end) - m0;
        for (int i = 0; i < fade.size; i++) {
            double share = span > 0 ? (meanAnomaly(k, fade.get(i)) - m0) / span : 0;
            fade.set(i, (float) Math.max(0, Math.min(1, share)));
        }
    }

    private static void caption(StringBuilder out, ManeuverNodeComponent.Node n, int index, int count, double now,
                                KeplerOrbit after, CelestialBodyComponent body) {
        long in = Math.max(0, (long) (n.time - now));
        out.append(String.format("Burn %d/%d in %d:%02d:%02d%n", index + 1, count, in / 3600, (in / 60) % 60, in % 60));
        out.append(String.format("Pro %+.1f  Rad %+.1f m/s%n", n.prograde, n.radial));
        out.append(String.format("After: Pe %s", km(after.periapsis() - body.radius)));
        out.append(after.isElliptic() ? "  Ap " + km(after.apoapsis() - body.radius) : "  escaping");
        if (after.periapsis() < body.radius) out.append("  IMPACT");
        out.append("\nUp/Down pro  Left/Right rad\n[ ] time  B next  Del remove");
    }

    private static String km(double meters) {
        return String.format("%.0f km", meters / 1000.0);
    }
}
