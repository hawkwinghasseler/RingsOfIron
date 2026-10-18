package com.ringsofiron.macrosim.fleet;

import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.orbital.math.KeplerOrbit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plans a freighter leg to a station anywhere one sphere-of-influence step away: around the same
 * body ({@link TransferPlanner}), out from a body to one of its moons, or from a moon down to its
 * parent (patched conics).
 *
 * <p>For a crossing, only the burn that leaves the current SOI is planned ahead, together with the
 * predicted crossing time. Once the ship has crossed, {@link #afterCrossing} plans the rest (capture
 * at periapsis, then a transfer to the station) from the real orbit, so prediction error never piles
 * up. {@link #estimate} gives cheap arrival and delta-v figures for choosing orders.
 */
public final class RoutePlanner {
    private static final double TWO_PI = 2 * Math.PI;
    /** Lowest altitude any planned orbit may reach, above the body's surface, meters. */
    private static final double SAFE_ALTITUDE = 200e3;
    /** Seconds between capture at periapsis and the first burn of the transfer to the station. */
    private static final double SETTLE_TIME = 60;

    public static final class Route {
        /** Burns to fly now. */
        public final List<Burn> burns;
        /** Body the ship orbits after the SOI crossing these burns lead to, or null if they reach the target. */
        public final String nextParent;
        /** Predicted sim second of that crossing, or NaN. */
        public final double crossingTime;
        /** Sim second the ship meets the target; an estimate for routes with a crossing. */
        public final double arrivalTime;
        /** Delta-v of the whole leg, m/s; an estimate for routes with a crossing. */
        public final double totalDeltaV;

        Route(List<Burn> burns, String nextParent, double crossingTime, double arrivalTime, double totalDeltaV) {
            this.burns = Collections.unmodifiableList(burns);
            this.nextParent = nextParent;
            this.crossingTime = crossingTime;
            this.arrivalTime = arrivalTime;
            this.totalDeltaV = totalDeltaV;
        }

        static Route direct(TransferPlanner.Plan plan) {
            return new Route(plan.burns, null, Double.NaN, plan.arrivalTime, plan.totalDeltaV);
        }
    }

    private RoutePlanner() {
    }

    /**
     * Plans a leg for a ship on a near-circular orbit around {@code shipParent} to the station
     * {@code target}. Null if the target is not reachable this way.
     */
    public static Route plan(RailsModel rails, String shipParent, StateVector ship, String target,
                             double t, double minStart) {
        String targetParent = rails.parentOf(target);
        StateVector station = rails.stateAt(target, t, new StateVector());
        if (shipParent.equals(targetParent)) {
            return Route.direct(TransferPlanner.plan(rails.muOf(shipParent), ship, station, t, minStart,
                    floor(rails, shipParent)));
        }
        if (shipParent.equals(rails.parentOf(targetParent))) return up(rails, shipParent, ship, targetParent, station, t, minStart);
        if (targetParent.equals(rails.parentOf(shipParent))) return down(rails, shipParent, ship, targetParent, station, t, minStart);
        return null;
    }

    /** Cheap arrival time and delta-v for {@link #plan}'s leg, for scoring orders. Null if unreachable. */
    public static Route estimate(RailsModel rails, String shipParent, StateVector ship, String target,
                                 double t, double minStart) {
        String targetParent = rails.parentOf(target);
        StateVector station = rails.stateAt(target, t, new StateVector());
        if (shipParent.equals(targetParent)) {
            return Route.direct(TransferPlanner.plan(rails.muOf(shipParent), ship, station, t, minStart,
                    floor(rails, shipParent)));
        }
        List<Burn> none = Collections.emptyList();
        if (shipParent.equals(rails.parentOf(targetParent))) {
            String moon = targetParent;
            double muP = rails.muOf(shipParent);
            double muC = rails.muOf(moon);
            StateVector moonNow = rails.stateAt(moon, t, new StateVector());
            TransferPlanner.Plan toMoon = TransferPlanner.plan(muP, ship, moonNow, t, minStart);
            if (toMoon.burns.isEmpty()) return null;
            double r1 = ship.distance();
            double aC = moonNow.distance();
            double vInf = Math.abs(moonNow.speed() - Math.sqrt(muP * (2 / aC - 2 / (r1 + aC))));
            double rT = station.distance();
            double capture = Math.sqrt(vInf * vInf + 2 * muC / rT) - Math.sqrt(muC / rT);
            return new Route(none, moon, Double.NaN, toMoon.arrivalTime + period(muC, rT),
                    toMoon.burns.get(0).prograde + capture);
        }
        if (targetParent.equals(rails.parentOf(shipParent))) {
            String moon = shipParent;
            double muP = rails.muOf(targetParent);
            double muC = rails.muOf(moon);
            StateVector moonNow = rails.stateAt(moon, t, new StateVector());
            double aC = moonNow.distance();
            double r = ship.distance();
            double r2 = station.distance();
            double vInf = Math.abs(moonNow.speed() - Math.sqrt(muP * (2 / aC - 2 / (aC + r2))));
            double escape = Math.sqrt(vInf * vInf + 2 * muC / r) - Math.sqrt(muC / r);
            double circularize = Math.sqrt(muP * (2 / r2 - 2 / (aC + r2))) - Math.sqrt(muP / r2);
            double fall = Math.PI * Math.sqrt(Math.pow((aC + r2) / 2, 3) / muP);
            double arrival = minStart + period(muC, r) / 2 + rails.soiRadiusOf(moon) / Math.max(vInf, 1) + fall
                    + period(muP, r2);
            return new Route(none, targetParent, Double.NaN, arrival, escape + Math.abs(circularize));
        }
        return null;
    }

    /**
     * Rest of a leg after the SOI crossing: circularize at the next periapsis of the ship's real orbit
     * around its new parent (or right away if that is behind it), then transfer to the station.
     */
    public static Route afterCrossing(RailsModel rails, String parent, StateVector ship, String target,
                                      double t, double minStart) {
        double mu = rails.muOf(parent);
        KeplerOrbit k = KeplerOrbit.fromState(ship.x, ship.y, ship.vx, ship.vy, mu, t);
        double tp = Math.max(nextPeriapsis(k, t), minStart);
        StateVector s = toMacro(k.stateAt(tp));
        Burn circ = TransferPlanner.circularize(mu, s, tp);
        StateVector circular = circularState(mu, s);
        TransferPlanner.Plan plan = TransferPlanner.plan(mu, circular, rails.stateAt(target, tp, new StateVector()),
                tp, tp + SETTLE_TIME, floor(rails, parent));
        List<Burn> burns = new ArrayList<>();
        burns.add(circ);
        burns.addAll(plan.burns);
        return new Route(burns, null, Double.NaN, plan.arrivalTime,
                Math.hypot(circ.prograde, circ.radial) + plan.totalDeltaV);
    }

    /** Same position, circular speed, same sense of motion. */
    static StateVector circularState(double mu, StateVector s) {
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double r = s.distance();
        double vc = Math.sqrt(mu / r);
        return new StateVector(s.x, s.y, -dir * s.y / r * vc, dir * s.x / r * vc);
    }

    /**
     * Out to a moon: a Hohmann transfer up to the moon's orbit, its start shifted so the ship passes
     * the moon at the station's orbit radius, on the side that leaves it circling the same way as the
     * station. The shift is found by bisection on the signed periapsis.
     */
    private static Route up(RailsModel rails, String parent, StateVector ship, String moon, StateVector station,
                            double t, double minStart) {
        double muP = rails.muOf(parent);
        double muC = rails.muOf(moon);
        double soi = rails.soiRadiusOf(moon);
        StateVector moonNow = rails.stateAt(moon, t, new StateVector());
        TransferPlanner.Plan toMoon = TransferPlanner.plan(muP, ship, moonNow, t, minStart);
        if (toMoon.burns.isEmpty()) return null;
        double dv = toMoon.burns.get(0).prograde;
        double nominal = toMoon.burns.get(0).time;
        double flight = toMoon.arrivalTime - nominal;
        KeplerOrbit shipOrbit = KeplerOrbit.fromState(ship.x, ship.y, ship.vx, ship.vy, muP, t);
        double want = signedRadius(station);
        double aC = moonNow.distance();
        double relativeRate = Math.abs(shipOrbit.meanMotion() - Math.sqrt(muP / (aC * aC * aC)));
        double synodic = TWO_PI / relativeRate;
        // Starting dt later turns the whole transfer by the relative rate, moving the far end by
        // aC * rate * dt along the moon's orbit: search the starts that miss by up to two SOI radii.
        double window = 2 * soi / (aC * relativeRate);
        int samples = 40;

        for (int k = 0; k < 3; k++) {
            double center = nominal + k * synodic;
            double prevStart = Double.NaN;
            double prevMiss = Double.NaN;
            for (int i = 0; i <= samples; i++) {
                double start = center - window + 2 * window * i / samples;
                if (start < minStart) continue;
                Pass pass = passMoon(rails, moon, muC, soi, shipOrbit.withBurn(start, dv, 0), start, flight);
                if (pass == null) {
                    prevStart = Double.NaN;
                    continue;
                }
                double miss = pass.signedPeriapsis - want;
                if (!Double.isNaN(prevStart) && Math.signum(miss) != Math.signum(prevMiss)) {
                    return bisectUp(rails, moon, muC, soi, shipOrbit, dv, flight, want, prevStart, prevMiss, start,
                            station.distance());
                }
                prevStart = start;
                prevMiss = miss;
            }
        }
        return null;
    }

    private static Route bisectUp(RailsModel rails, String moon, double muC, double soi, KeplerOrbit shipOrbit,
                                  double dv, double flight, double want, double lo, double loMiss, double hi,
                                  double stationRadius) {
        Pass best = null;
        double bestStart = hi;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            Pass pass = passMoon(rails, moon, muC, soi, shipOrbit.withBurn(mid, dv, 0), mid, flight);
            if (pass == null) break;
            best = pass;
            bestStart = mid;
            double miss = pass.signedPeriapsis - want;
            if (Math.signum(miss) == Math.signum(loMiss)) {
                lo = mid;
                loMiss = miss;
            } else {
                hi = mid;
            }
        }
        if (best == null) return null;
        List<Burn> burns = new ArrayList<>();
        burns.add(new Burn(bestStart, dv, 0));
        double vPeri = Math.sqrt(muC * (2 / best.orbit.periapsis() - 1 / best.orbit.a));
        double capture = vPeri - Math.sqrt(muC / best.orbit.periapsis());
        double arrival = nextPeriapsis(best.orbit, best.entryTime) + period(muC, stationRadius);
        return new Route(burns, moon, best.entryTime, arrival, dv + Math.abs(capture));
    }

    private static final class Pass {
        final double entryTime;
        final KeplerOrbit orbit;
        final double signedPeriapsis;

        Pass(double entryTime, KeplerOrbit orbit) {
            this.entryTime = entryTime;
            this.orbit = orbit;
            this.signedPeriapsis = orbit.direction * orbit.periapsis();
        }
    }

    /** Where the transfer orbit enters the moon's SOI and the moon-relative orbit it starts, or null. */
    private static Pass passMoon(RailsModel rails, String moon, double muC, double soi, KeplerOrbit transfer,
                                 double start, double flight) {
        int steps = 300;
        double end = start + 1.5 * flight;
        double prev = start;
        StateVector m = new StateVector();
        for (int i = 1; i <= steps; i++) {
            double ti = start + (end - start) * i / steps;
            if (distanceToMoon(rails, moon, transfer, ti, m) < soi) {
                double lo = prev;
                double hi = ti;
                for (int j = 0; j < 40; j++) {
                    double mid = (lo + hi) / 2;
                    if (distanceToMoon(rails, moon, transfer, mid, m) < soi) hi = mid;
                    else lo = mid;
                }
                com.ringsofiron.orbital.math.StateVector s = transfer.stateAt(hi);
                rails.stateAt(moon, hi, m);
                KeplerOrbit rel = KeplerOrbit.fromState(s.x - m.x, s.y - m.y, s.vx - m.vx, s.vy - m.vy, muC, hi);
                // Must be on its way in, so the periapsis is still ahead.
                if (rel.isHyperbolic() && rel.meanAnomalyAt(hi) >= 0) return null;
                return new Pass(hi, rel);
            }
            prev = ti;
        }
        return null;
    }

    private static double distanceToMoon(RailsModel rails, String moon, KeplerOrbit k, double t, StateVector m) {
        com.ringsofiron.orbital.math.StateVector s = k.stateAt(t);
        rails.stateAt(moon, t, m);
        return Math.hypot(s.x - m.x, s.y - m.y);
    }

    /**
     * Down to the parent: an escape burn from the moon orbit, timed so that once outside the SOI the
     * ship falls towards the station's orbit radius, circling the parent the same way as the station.
     */
    private static Route down(RailsModel rails, String moon, StateVector ship, String parent, StateVector station,
                              double t, double minStart) {
        double muC = rails.muOf(moon);
        double muP = rails.muOf(parent);
        double soi = rails.soiRadiusOf(moon);
        double floor = floor(rails, parent);
        StateVector moonNow = rails.stateAt(moon, t, new StateVector());
        double aC = moonNow.distance();
        double r2 = station.distance();
        int dirT = signedRadius(station) < 0 ? -1 : 1;
        double vInf = Math.abs(moonNow.speed() - Math.sqrt(muP * (2 / aC - 2 / (aC + r2))));
        double r = ship.distance();
        double dv = Math.sqrt(vInf * vInf + 2 * muC / r) - Math.sqrt(muC / r);
        KeplerOrbit shipOrbit = KeplerOrbit.fromState(ship.x, ship.y, ship.vx, ship.vy, muC, t);
        double lap = shipOrbit.period();

        int samples = 72;
        double step = lap / samples;
        double bestStart = Double.NaN;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int i = 0; i < samples; i++) {
            double start = minStart + i * step;
            double cost = escapeCost(rails, moon, muP, soi, shipOrbit, dv, start, r2, dirT, floor);
            if (cost < bestCost) {
                bestCost = cost;
                bestStart = start;
            }
        }
        if (Double.isNaN(bestStart)) return null;
        // Golden-section refinement around the best sample.
        double lo = Math.max(minStart, bestStart - step);
        double hi = bestStart + step;
        double g = (Math.sqrt(5) - 1) / 2;
        for (int i = 0; i < 40; i++) {
            double a = hi - g * (hi - lo);
            double b = lo + g * (hi - lo);
            if (escapeCost(rails, moon, muP, soi, shipOrbit, dv, a, r2, dirT, floor)
                    < escapeCost(rails, moon, muP, soi, shipOrbit, dv, b, r2, dirT, floor)) hi = b;
            else lo = a;
        }
        double start = (lo + hi) / 2;
        if (!Double.isFinite(escapeCost(rails, moon, muP, soi, shipOrbit, dv, start, r2, dirT, floor))) start = bestStart;

        KeplerOrbit escape = shipOrbit.withBurn(start, dv, 0);
        double exit = escape.nextTimeAtRadius(soi, start, KeplerOrbit.Crossing.OUTBOUND);
        KeplerOrbit fall = parentOrbitAfterExit(rails, moon, muP, escape, exit);
        double vPeri = Math.sqrt(muP * (2 / fall.periapsis() - 1 / fall.a));
        double circularize = vPeri - Math.sqrt(muP / fall.periapsis());
        List<Burn> burns = new ArrayList<>();
        burns.add(new Burn(start, dv, 0));
        double arrival = nextPeriapsis(fall, exit) + period(muP, r2);
        return new Route(burns, parent, exit, arrival, dv + Math.abs(circularize));
    }

    /** How far the post-escape periapsis is from the station radius; +inf if unusable. */
    private static double escapeCost(RailsModel rails, String moon, double muP, double soi, KeplerOrbit shipOrbit,
                                     double dv, double start, double r2, int dirT, double floor) {
        KeplerOrbit escape = shipOrbit.withBurn(start, dv, 0);
        double exit = escape.nextTimeAtRadius(soi, start, KeplerOrbit.Crossing.OUTBOUND);
        if (Double.isNaN(exit)) return Double.POSITIVE_INFINITY;
        KeplerOrbit fall = parentOrbitAfterExit(rails, moon, muP, escape, exit);
        if (fall.direction != dirT || fall.periapsis() < floor || !fall.isElliptic()) return Double.POSITIVE_INFINITY;
        return Math.abs(fall.periapsis() - r2);
    }

    private static KeplerOrbit parentOrbitAfterExit(RailsModel rails, String moon, double muP, KeplerOrbit escape,
                                                    double exit) {
        com.ringsofiron.orbital.math.StateVector s = escape.stateAt(exit);
        StateVector m = rails.stateAt(moon, exit, new StateVector());
        return KeplerOrbit.fromState(s.x + m.x, s.y + m.y, s.vx + m.vx, s.vy + m.vy, muP, exit);
    }

    /** Lowest radius a plan may take a ship to around {@code body}. */
    private static double floor(RailsModel rails, String body) {
        return rails.radiusOf(body) + SAFE_ALTITUDE;
    }

    /** Next periapsis at or after t; t itself if a hyperbolic orbit has already passed it. */
    static double nextPeriapsis(KeplerOrbit k, double t) {
        double m = k.meanAnomalyAt(t);
        double n = k.meanMotion();
        if (k.isElliptic()) return t + (TWO_PI - m) / n;
        return m < 0 ? t - m / n : t;
    }

    /** Distance from the parent, negative for clockwise motion. */
    private static double signedRadius(StateVector s) {
        double h = s.x * s.vy - s.y * s.vx;
        return h < 0 ? -s.distance() : s.distance();
    }

    private static double period(double mu, double r) {
        return TWO_PI * Math.sqrt(r * r * r / mu);
    }

    private static StateVector toMacro(com.ringsofiron.orbital.math.StateVector s) {
        return new StateVector(s.x, s.y, s.vx, s.vy);
    }
}
