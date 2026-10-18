package com.ringsofiron.orbital.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class KeplerOrbitTest {
    /** Earth. */
    static final double MU = 3.986004418e14;
    static final double R_EARTH = 6.371e6;

    @ParameterizedTest
    @CsvSource({"0.0", "0.5", "0.99"})
    void keplerSolverConverges(double e) {
        for (int i = 0; i <= 400; i++) {
            double m = -Math.PI + i * (2 * Math.PI / 400);
            double E = KeplerSolver.eccentricAnomaly(m, e);
            assertEquals(KeplerSolver.wrapPi(m), E - e * Math.sin(E), 1e-11, "M=" + m);
        }
    }

    @ParameterizedTest
    @CsvSource({"1.5", "1.0001", "5.0"})
    void hyperbolicSolverConverges(double e) {
        for (int i = -200; i <= 200; i++) {
            double m = i * 0.5;
            double H = KeplerSolver.hyperbolicAnomaly(m, e);
            assertEquals(m, e * Math.sinh(H) - H, 1e-9 * Math.max(1, Math.abs(m)), "M=" + m);
        }
    }

    /** stateAt -> fromState -> stateAt lands on the same point, for every conic type and direction. */
    @ParameterizedTest
    @CsvSource({
            "7.0e6, 0.0, 1", "7.0e6, 0.0, -1",
            "2.0e7, 0.5, 1", "2.0e7, 0.5, -1",
            "4.0e8, 0.99, 1", "4.0e8, 0.99, -1",
            "-1.0e7, 1.5, 1", "-1.0e7, 1.5, -1"})
    void roundTrip(double a, double e, int direction) {
        KeplerOrbit orbit = new KeplerOrbit(MU, a, e, 0.7, 0.3, 100, direction);
        for (int i = 0; i < 50; i++) {
            double t = 100 + i * 997.0;
            StateVector s = orbit.stateAt(t);
            KeplerOrbit back = KeplerOrbit.fromState(s, MU, t);
            assertEquals(e, back.e, 1e-9);
            assertEquals(direction, back.direction);
            assertEquals(a, back.a, Math.abs(a) * 1e-9);
            // Evaluate both well away from the fit time.
            double later = t + 12345;
            StateVector p = orbit.stateAt(later);
            StateVector q = back.stateAt(later);
            double tol = 1e-6 * Math.max(p.radius(), 1);
            assertEquals(p.x, q.x, tol, "x at " + later);
            assertEquals(p.y, q.y, tol, "y at " + later);
            assertEquals(p.vx, q.vx, 1e-6 * p.speed());
            assertEquals(p.vy, q.vy, 1e-6 * p.speed());
        }
    }

    /** The analytic conic agrees with brute-force RK4 integration of Newtonian gravity. */
    @ParameterizedTest
    @CsvSource({"0.0", "0.3", "0.8", "1.5"})
    void matchesNumericalIntegration(double e) {
        double periapsis = R_EARTH + 400e3;
        double a = e < 1 ? periapsis / (1 - e) : periapsis / (1 - e);
        KeplerOrbit orbit = new KeplerOrbit(MU, a, e, 1.1, 0, 0, 1);
        StateVector s = orbit.stateAt(0);
        double[] y = {s.x, s.y, s.vx, s.vy};
        double dt = 0.5;
        double duration = e < 1 ? orbit.period() : 4000;
        int steps = (int) (duration / dt);
        for (int i = 0; i < steps; i++) rk4(y, dt);
        StateVector exact = orbit.stateAt(steps * dt);
        assertEquals(exact.x, y[0], 1.0, "x");   // within a metre after a full orbit
        assertEquals(exact.y, y[1], 1.0, "y");
        assertEquals(exact.vx, y[2], 1e-3);
        assertEquals(exact.vy, y[3], 1e-3);
    }

    @Test
    void circularOrbitPeriod() {
        KeplerOrbit leo = KeplerOrbit.circular(MU, R_EARTH + 400e3, 0, 0, 1);
        assertEquals(92.4 * 60, leo.period(), 30);   // ISS: ~92 minutes
        StateVector s = leo.stateAt(0);
        assertEquals(7670, s.speed(), 10);
        StateVector later = leo.stateAt(leo.period());
        assertEquals(s.x, later.x, 1e-3);
        assertEquals(s.y, later.y, 1e-3);
        // A quarter period counter-clockwise from +X lands on +Y.
        StateVector quarter = leo.stateAt(leo.period() / 4);
        assertEquals(0, quarter.x, 1e-3);
        assertTrue(quarter.y > 0);
        assertTrue(KeplerOrbit.circular(MU, R_EARTH + 400e3, 0, 0, -1).stateAt(leo.period() / 4).y < 0);
    }

    @Test
    void circularStartAngle() {
        KeplerOrbit orbit = KeplerOrbit.circular(MU, 1e7, 2.0, 50, -1);
        StateVector s = orbit.stateAt(50);
        assertEquals(2.0, Math.atan2(s.y, s.x), 1e-12);
    }

    @Test
    void nextTimeAtRadius() {
        KeplerOrbit orbit = KeplerOrbit.fromApsides(MU, 7e6, 3e7, 0.4, 0, 1);
        double r = 1.5e7;
        double tOut = orbit.nextTimeAtRadius(r, 0, KeplerOrbit.Crossing.OUTBOUND);
        double tIn = orbit.nextTimeAtRadius(r, 0, KeplerOrbit.Crossing.INBOUND);
        assertTrue(tOut > 0 && tOut < tIn && tIn < orbit.period());
        assertEquals(r, orbit.stateAt(tOut).radius(), 1e-3);
        assertEquals(r, orbit.stateAt(tIn).radius(), 1e-3);
        assertTrue(radialSpeed(orbit.stateAt(tOut)) > 0);
        assertTrue(radialSpeed(orbit.stateAt(tIn)) < 0);
        assertTrue(Double.isNaN(orbit.nextTimeAtRadius(4e7, 0, KeplerOrbit.Crossing.EITHER)));
        // Asking from just after the outbound crossing finds the inbound one next.
        assertEquals(tIn, orbit.nextTimeAtRadius(r, tOut + 1, KeplerOrbit.Crossing.EITHER), 1e-6);

        KeplerOrbit escape = new KeplerOrbit(MU, -2e7, 1.4, 0, -0.5, 0, 1);
        double tEsc = escape.nextTimeAtRadius(9e8, 0, KeplerOrbit.Crossing.OUTBOUND);
        assertEquals(9e8, escape.stateAt(tEsc).radius(), 1e-1);
        assertTrue(Double.isNaN(escape.nextTimeAtRadius(9e8, tEsc + 10, KeplerOrbit.Crossing.OUTBOUND)));
    }

    @Test
    void progradeBurnRaisesOrbit() {
        KeplerOrbit leo = KeplerOrbit.circular(MU, 7e6, 0, 0, -1);
        KeplerOrbit raised = leo.withBurn(100, 100, 0);
        assertTrue(raised.a > leo.a);
        assertEquals(7e6, raised.periapsis(), 1);  // burned at what is now periapsis
        KeplerOrbit radial = leo.withBurn(100, 0, 50);
        assertEquals(leo.a, radial.a, leo.a * 1e-3);  // a radial burn barely changes energy
        assertTrue(radial.e > 0);
    }

    @Test
    void fromStateEscapeIsHyperbolic() {
        double r = 7e6;
        double v = OrbitMath.escapeSpeed(MU, r) * 1.2;
        KeplerOrbit k = KeplerOrbit.fromState(r, 0, 0, v, MU, 0);
        assertTrue(k.isHyperbolic());
        assertEquals(r, k.periapsis(), 1e-3);
    }

    @Test
    void tidalAccelerationMatchesLinearApproximation() {
        double r = R_EARTH + 400e3;
        double n2 = MU / (r * r * r);
        double[] out = new double[2];
        // Radially outward offset: +2 n^2 d (stretches away). Along-track: -n^2 d (squeezes in).
        OrbitMath.tidalAcceleration(MU, r, 0, 1000, 0, out);
        assertEquals(2 * n2 * 1000, out[0], 2 * n2 * 1000 * 1e-3);
        OrbitMath.tidalAcceleration(MU, r, 0, 0, 1000, out);
        assertEquals(-n2 * 1000, out[1], n2 * 1000 * 1e-3);
    }

    @Test
    void soiOfEarthAndMoon() {
        double muSun = 1.32712440018e20;
        assertEquals(9.25e8, OrbitMath.soiRadius(1.496e11, MU, muSun), 0.01e8);
        assertEquals(6.6e7, OrbitMath.soiRadius(3.844e8, 4.9048695e12, MU), 0.1e7);
    }

    private static double radialSpeed(StateVector s) {
        return (s.x * s.vx + s.y * s.vy) / s.radius();
    }

    private static void rk4(double[] y, double h) {
        double[] k1 = deriv(y);
        double[] k2 = deriv(add(y, k1, h / 2));
        double[] k3 = deriv(add(y, k2, h / 2));
        double[] k4 = deriv(add(y, k3, h));
        for (int i = 0; i < 4; i++) y[i] += h / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
    }

    private static double[] add(double[] y, double[] k, double h) {
        return new double[]{y[0] + k[0] * h, y[1] + k[1] * h, y[2] + k[2] * h, y[3] + k[3] * h};
    }

    private static double[] deriv(double[] y) {
        double r2 = y[0] * y[0] + y[1] * y[1];
        double r3 = r2 * Math.sqrt(r2);
        return new double[]{y[2], y[3], -MU * y[0] / r3, -MU * y[1] / r3};
    }
}
