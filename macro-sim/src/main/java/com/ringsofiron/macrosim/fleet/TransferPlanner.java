package com.ringsofiron.macrosim.fleet;

import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plans travel between two near-circular orbits around the same parent as impulsive maneuver nodes:
 * a phased Hohmann transfer (wait, transfer burn, circularisation burn), or a phasing orbit when both
 * are on the same radius. Plans are expressed as rails {@link Burn}s so the same plan can be handed to
 * an in-bubble pilot as goals. Cross-SOI routes are out of scope for the prototype.
 */
public final class TransferPlanner {
    private static final double TWO_PI = 2 * Math.PI;
    /** Relative radius difference below which two orbits count as the same radius. */
    private static final double SAME_RADIUS = 1e-3;

    public static final class Plan {
        public final List<Burn> burns;
        /** Sim second at which the traveller meets the target. */
        public final double arrivalTime;
        public final double totalDeltaV;

        Plan(List<Burn> burns, double arrivalTime) {
            this.burns = Collections.unmodifiableList(burns);
            this.arrivalTime = arrivalTime;
            double dv = 0;
            for (Burn b : burns) dv += Math.hypot(b.prograde, b.radial);
            this.totalDeltaV = dv;
        }
    }

    private TransferPlanner() {
    }

    /**
     * @param mu       parent gravitational parameter
     * @param ship     traveller's parent-relative state at {@code t}
     * @param target   target's parent-relative state at {@code t}
     * @param t        current sim second
     * @param minStart earliest sim second the first burn may fire (at or after {@code t})
     */
    public static Plan plan(double mu, StateVector ship, StateVector target, double t, double minStart) {
        int dir = (ship.x * ship.vy - ship.y * ship.vx) < 0 ? -1 : 1;
        double r1 = ship.distance();
        double r2 = target.distance();
        double n1 = Math.sqrt(mu / (r1 * r1 * r1));
        double n2 = Math.sqrt(mu / (r2 * r2 * r2));
        double lead0 = wrap(dir * (Math.atan2(target.y, target.x) - Math.atan2(ship.y, ship.x)));
        double wait0 = minStart - t;
        // Target's lead over the ship at the earliest start.
        double lead = wrap(lead0 + (n2 - n1) * wait0);

        List<Burn> burns = new ArrayList<>();
        if (Math.abs(r2 - r1) / r1 < SAME_RADIUS) {
            if (lead < 1e-6 || lead > TWO_PI - 1e-6) return new Plan(burns, minStart);
            // Phasing orbit: one lap with period chosen so the target arrives at the burn point with us.
            double period = TWO_PI / n1;
            double k = lead < Math.PI / 2 ? 1 : 2;
            double phasePeriod = period * (k - lead / TWO_PI);
            double aPh = Math.cbrt(mu * Math.pow(phasePeriod / TWO_PI, 2));
            double vCirc = Math.sqrt(mu / r1);
            double vPh = Math.sqrt(mu * (2 / r1 - 1 / aPh));
            burns.add(new Burn(minStart, vPh - vCirc, 0));
            burns.add(new Burn(minStart + phasePeriod, vCirc - vPh, 0));
            return new Plan(burns, minStart + phasePeriod);
        }

        double aT = (r1 + r2) / 2;
        double transferTime = Math.PI * Math.sqrt(aT * aT * aT / mu);
        double dv1 = Math.sqrt(mu / r1) * (Math.sqrt(2 * r2 / (r1 + r2)) - 1);
        double dv2 = Math.sqrt(mu / r2) * (1 - Math.sqrt(2 * r1 / (r1 + r2)));
        double neededLead = wrap(Math.PI - n2 * transferTime);
        double rate = n2 - n1;
        double wait = rate > 0 ? wrap(neededLead - lead) / rate : wrap(lead - neededLead) / -rate;
        double start = minStart + wait;
        burns.add(new Burn(start, dv1, 0));
        burns.add(new Burn(start + transferTime, dv2, 0));
        return new Plan(burns, start + transferTime);
    }

    /** Prograde/radial burn that puts a body on a circular orbit through its current position, now. */
    public static Burn circularize(double mu, StateVector s, double t) {
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double r = s.distance();
        double vc = Math.sqrt(mu / r);
        // Desired velocity: tangential, in the direction of motion.
        double dvx = -dir * s.y / r * vc - s.vx;
        double dvy = dir * s.x / r * vc - s.vy;
        double speed = s.speed();
        double prograde = (dvx * s.vx + dvy * s.vy) / speed;
        double radial = (dvx * s.x + dvy * s.y) / r;
        // Prograde and radial are not orthogonal on an eccentric orbit; solve the 2x2 system exactly.
        double px = s.vx / speed, py = s.vy / speed, qx = s.x / r, qy = s.y / r;
        double det = px * qy - py * qx;
        if (Math.abs(det) > 1e-12) {
            prograde = (dvx * qy - dvy * qx) / det;
            radial = (px * dvy - py * dvx) / det;
        }
        return new Burn(t, prograde, radial);
    }

    static double wrap(double angle) {
        angle %= TWO_PI;
        return angle < 0 ? angle + TWO_PI : angle;
    }
}
