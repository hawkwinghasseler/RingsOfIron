package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.KeplerSolver;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.rails.SoiPredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a path goes past its own conic, for the orbit map: patched conics, the way the rails fly it.
 * From the end of the plan (or now, with none) orbital-mechanics' {@link SoiPredictor} finds the first
 * change of sphere of influence or impact; a moon the path meets gives a leg around the moon, and a leg
 * that leaves the moon again goes on around the map's body. At most {@link #MAX_LEGS} legs; one that
 * leaves the map's own body ends there.
 *
 * <p>The search is reused while the conic stays the same (on the rails it does until a burn), or for
 * {@link #REUSE} real seconds while it drifts (flying in the bubble), and never for more than
 * {@link #MAX_AGE}; a changed plan redoes it at once.
 */
public class MapTrajectory {
    public static final int MAX_LEGS = 3;
    /** A drifting conic's prediction is reused this long, real seconds. */
    static final double REUSE = 0.25;
    /** Even an unchanged conic is searched again this often, real seconds: the moons move on. */
    static final double MAX_AGE = 2;
    /** How far past its start a leg is searched: an elliptic one's period, at most this long, s. */
    static final double MAX_HORIZON = 30 * 86400.0;
    /** Relative change of a conic's elements that still counts as the same conic. */
    static final double SAME = 1e-7;

    /** One conic of the path: around {@code frame}, from {@code from} until {@code to} (NaN: no end found). */
    public static final class Leg {
        public Entity frame;
        public KeplerOrbit conic;
        public double from, to = Double.NaN;
        /** What ends the leg: SOI_ENTRY, SOI_EXIT or IMPACT; null when nothing does within its horizon. */
        public OrbitalEvent.Type end;
        /** The body it enters, for SOI_ENTRY. */
        public Entity entered;
        /** How many of the plan's burns are flown before it: 0 for the orbit now, 1 after the first burn. */
        public int stretch;
        /** It ends at the plan's next burn rather than at an event: {@link #to} is the burn's time. */
        public boolean toBurn;
    }

    private final RailsUniverse universe;
    private final List<Leg> legs = new ArrayList<>();
    /** For each burn of the last plan, the leg the ship is on when it fires. */
    private final List<Leg> burnFrom = new ArrayList<>();
    private final StateVector s = new StateVector();
    private final StateVector b = new StateVector();
    private KeplerOrbit lastConic;
    private Entity lastFrame;
    private long lastPlan;
    private int lastBurns = -1;
    private long computedAt;

    public MapTrajectory(RailsUniverse universe) {
        this.universe = universe;
    }

    /**
     * The legs of the path that flies conic {@code k} around {@code frame} from {@code from}; the first
     * is {@code k} itself. {@code plan} stands for the burns planned before it: a new value redoes the
     * search at once. The list is reused: read it before the next call.
     */
    public List<Leg> legs(KeplerOrbit k, Entity frame, double from, long plan) {
        return compute(k, frame, from, java.util.Collections.emptyList(), plan, -1);
    }

    /**
     * The whole planned path as patched conics: conic {@code k} around {@code frame} from {@code from},
     * through each of {@code burns} (soonest first) in turn, and on past the last as {@link #legs} goes.
     * Each burn is flown in the sphere of influence the ship is in by its time, the way the rails fly it:
     * a burn after the path meets a moon is a burn around the moon. Each leg says how many burns come
     * before it ({@link Leg#stretch}); {@link #burnLeg} gives the leg each burn fires from. A burn the
     * path never reaches (after an impact, or out of the map body's sphere) is flown on the last leg's
     * conic as if nothing had happened. The list is reused: read it before the next call.
     */
    public List<Leg> plan(KeplerOrbit k, Entity frame, double from, List<ManeuverNodeComponent.Node> burns) {
        long plan = 17;
        for (ManeuverNodeComponent.Node n : burns) {
            plan = plan * 1_000_003 + Double.hashCode(n.time) * 31L + Double.hashCode(n.prograde) * 7L + Double.hashCode(n.radial);
        }
        return compute(k, frame, from, burns, plan, burns.size());
    }

    /** The leg burn {@code i} of the last {@link #plan} fires from: its conic and frame just before the burn. */
    public Leg burnLeg(int i) {
        return burnFrom.get(i);
    }

    /** The first leg after burn {@code i} of the last {@link #plan}: the conic the burn leaves the ship on. */
    public Leg afterBurn(int i) {
        for (Leg l : legs) if (l.stretch == i + 1) return l;
        return legs.get(legs.size() - 1);
    }

    private List<Leg> compute(KeplerOrbit k, Entity frame, double from, List<ManeuverNodeComponent.Node> burns,
                              long plan, int burnCount) {
        long now = System.nanoTime();
        double age = (now - computedAt) / 1e9;
        boolean same = frame == lastFrame && plan == lastPlan && burnCount == lastBurns && !legs.isEmpty() && age < MAX_AGE
                && (sameConic(k, lastConic) || age < REUSE);
        if (!same) {
            legs.clear();
            burnFrom.clear();
            Entity f = frame;
            KeplerOrbit c = k;
            double t0 = from;
            for (int i = 0; ; i++) {
                double until = i < burns.size() ? burns.get(i).time : Double.NaN;
                walk(c, f, frame, t0, until, i);
                if (i >= burns.size()) break;
                Leg last = legs.get(legs.size() - 1);
                ManeuverNodeComponent.Node n = burns.get(i);
                burnFrom.add(last);
                c = last.conic.withBurn(n.time, n.prograde, n.radial);
                f = last.frame;
                t0 = n.time;
            }
            lastConic = k;
            lastFrame = frame;
            lastPlan = plan;
            lastBurns = burnCount;
            computedAt = now;
        }
        // The path starts where the ship is now, or at the last burn
        Leg first = legs.get(0);
        first.conic = k;
        first.from = from;
        return legs;
    }

    /**
     * Legs on from {@code t0} on conic {@code c} around {@code frame}, into {@link #legs}, through the
     * moons of {@code mapFrame} it meets, until {@code until} (the next burn; NaN to go on as far as an
     * orbit, or {@link PlayerOrbitPath#OPEN_SPAN} open) or until it hits something or leaves
     * {@code mapFrame}'s sphere. At most {@link #MAX_LEGS} legs.
     */
    private void walk(KeplerOrbit c, Entity frame, Entity mapFrame, double t0, double until, int stretch) {
        boolean bounded = !Double.isNaN(until);
        for (int i = 0; i < MAX_LEGS; i++) {
            Leg leg = new Leg();
            leg.frame = frame;
            leg.conic = c;
            leg.from = t0;
            leg.stretch = stretch;
            legs.add(leg);
            CelestialBodyComponent body = universe.celestial(frame);
            String frameUuid = universe.uuidOf(frame);
            if (body == null || frameUuid == null) {
                if (bounded) endAtBurn(leg, until);
                return;
            }
            double horizon = bounded ? Math.min(Math.max(until - t0, 0), MAX_HORIZON)
                    : c.isElliptic() ? Math.min(c.period(), MAX_HORIZON) : PlayerOrbitPath.OPEN_SPAN;
            OrbitalEvent ev = new SoiPredictor(universe, horizon).predict("orbit-map-path", frameUuid, c, 0, t0);
            boolean event = ev.type == OrbitalEvent.Type.SOI_ENTRY || ev.type == OrbitalEvent.Type.SOI_EXIT
                    || ev.type == OrbitalEvent.Type.IMPACT;
            if (!event || bounded && ev.time >= until) {
                if (bounded) endAtBurn(leg, until);
                return;
            }
            leg.to = ev.time;
            leg.end = ev.type;
            double te = ev.time;
            if (ev.type == OrbitalEvent.Type.SOI_ENTRY) {
                Entity child = universe.entity(ev.targetUuid);
                CelestialBodyComponent moon = child == null ? null : universe.celestial(child);
                if (moon == null) return;
                leg.entered = child;
                c.stateAt(te, s);
                universe.stateInFrame(child, frame, te, b);
                c = KeplerOrbit.fromState(s.x - b.x, s.y - b.y, s.vx - b.vx, s.vy - b.vy, moon.mu, te);
                frame = child;
            } else if (ev.type == OrbitalEvent.Type.SOI_EXIT && frame != mapFrame && universe.parentOf(frame) == mapFrame) {
                // Out of a moon and back around the map's body
                c.stateAt(te, s);
                universe.stateInFrame(frame, mapFrame, te, b);
                c = KeplerOrbit.fromState(s.x + b.x, s.y + b.y, s.vx + b.vx, s.vy + b.vy, universe.celestial(mapFrame).mu, te);
                frame = mapFrame;
            } else {
                return;
            }
            t0 = te;
        }
    }

    private static void endAtBurn(Leg leg, double until) {
        leg.to = until;
        leg.end = null;
        leg.toBurn = true;
    }

    static boolean sameConic(KeplerOrbit a, KeplerOrbit b) {
        if (a == b) return true;
        if (a == null || b == null || a.direction != b.direction) return false;
        double t = a.epoch;
        return Math.abs(a.a - b.a) <= SAME * Math.abs(a.a) && Math.abs(a.e - b.e) <= SAME
                && Math.abs(KeplerSolver.wrapPi(a.argPeriapsis - b.argPeriapsis)) <= SAME
                && Math.abs(KeplerSolver.wrapPi(a.meanAnomalyAt(t) - b.meanAnomalyAt(t))) <= SAME;
    }

    /** The next time at or after {@code t} the conic passes periapsis, or NaN when it is past it for good. */
    public static double nextPeriapsis(KeplerOrbit k, double t) {
        double m = k.meanAnomalyAt(t);
        if (k.isElliptic()) return t + KeplerSolver.wrapTwoPi(-m) / k.meanMotion();
        return m < 0 ? t - m / k.meanMotion() : Double.NaN;
    }

    /** The next time at or after {@code t} an elliptic conic passes apoapsis, or NaN for an open one. */
    public static double nextApoapsis(KeplerOrbit k, double t) {
        if (!k.isElliptic()) return Double.NaN;
        return t + KeplerSolver.wrapTwoPi(Math.PI - k.meanAnomalyAt(t)) / k.meanMotion();
    }
}
