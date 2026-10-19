package com.ringsofiron.orbital.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kepler propagation against real orbits with well known periods and speeds, and the conserved
 * quantities over the very long spans rails warp covers.
 */
class KnownOrbitsTest {
    static final double MU_EARTH = 3.986004418e14;
    static final double MU_SUN = 1.32712440018e20;
    static final double R_EARTH = 6.371e6;
    static final double DAY = 86400;
    static final double SIDEREAL_DAY = 86164.0905;

    @Test
    void geostationaryOrbitTakesOneSiderealDay() {
        KeplerOrbit geo = KeplerOrbit.circular(MU_EARTH, 42_164_000, 0, 0, 1);
        assertEquals(SIDEREAL_DAY, geo.period(), 5);
        assertEquals(3074.7, geo.stateAt(1234).speed(), 0.5);
    }

    @Test
    void theMoonGoesRoundInAboutTwentySevenDays() {
        // Earth's mu only (the Moon's own mass shortens the real 27.32 day period a little).
        KeplerOrbit moon = KeplerOrbit.circular(MU_EARTH, 3.844e8, 0, 0, 1);
        assertEquals(27.45, moon.period() / DAY, 0.05);
        assertEquals(1018, moon.stateAt(0).speed(), 2);
    }

    @Test
    void theEarthGoesRoundTheSunInAYear() {
        KeplerOrbit earth = KeplerOrbit.fromApsides(MU_SUN, 1.47095e11, 1.52100e11, 1.8, 0, 1);
        assertEquals(365.26, earth.period() / DAY, 0.1);
        assertEquals(0.0167, earth.e, 1e-4);
        // Fastest at perihelion (early January), slowest at aphelion: 30.29 and 29.29 km/s.
        assertEquals(30_290, earth.stateAt(0).speed(), 10);
        assertEquals(29_290, earth.stateAt(earth.period() / 2).speed(), 10);
    }

    /** Molniya: a 12-hour orbit, a = 26,600 km and e = 0.74, that lingers far out over one hemisphere. */
    @Test
    void molniyaOrbitApsidesAndDwell() {
        KeplerOrbit molniya = new KeplerOrbit(MU_EARTH, 26_600e3, 0.74, 0, 0, 0, 1);
        assertEquals(SIDEREAL_DAY / 2, molniya.period(), 200);
        assertEquals(6_916e3, molniya.periapsis(), 1e3);
        assertEquals(46_284e3, molniya.apoapsis(), 1e3);
        assertEquals(molniya.periapsis(), molniya.stateAt(0).radius(), 1e-3);
        assertEquals(molniya.apoapsis(), molniya.stateAt(molniya.period() / 2).radius(), 1e-3);
        // Kepler's second law: the half of the orbit around apoapsis takes far longer than the other half.
        double tOut = molniya.nextTimeAtRadius(molniya.a, 0, KeplerOrbit.Crossing.OUTBOUND);
        double tIn = molniya.nextTimeAtRadius(molniya.a, 0, KeplerOrbit.Crossing.INBOUND);
        double farHalf = tIn - tOut;
        assertTrue(farHalf > 0.7 * molniya.period(), "dwells beyond r = a for " + farHalf / molniya.period());
    }

    /** A hyperbolic flyby slows towards v-infinity, with v_inf^2 = -mu / a. */
    @Test
    void hyperbolicExcessSpeed() {
        double vInf = 3000;
        double a = -MU_EARTH / (vInf * vInf);
        KeplerOrbit flyby = new KeplerOrbit(MU_EARTH, a, 1.8, 0, 0, 0, 1);
        double far = flyby.nextTimeAtRadius(1e10, 0, KeplerOrbit.Crossing.OUTBOUND);
        StateVector s = flyby.stateAt(far);
        assertEquals(1e10, s.radius(), 1);
        assertEquals(Math.sqrt(vInf * vInf + 2 * MU_EARTH / 1e10), s.speed(), 1e-6);
        // Heading out along the asymptote: true anomaly approaches acos(-1/e).
        double trueAnomaly = Math.atan2(s.y, s.x) - flyby.argPeriapsis;
        assertEquals(Math.acos(-1 / flyby.e), trueAnomaly, 1e-2);
    }

    /** Vis-viva and angular momentum hold at every point of every conic, both ways round. */
    @ParameterizedTest
    @CsvSource({
            "7.0e6, 0.0, 1", "7.0e6, 0.0, -1",
            "2.66e7, 0.74, 1", "2.66e7, 0.74, -1",
            "4.0e8, 0.97, 1",
            "-2.0e7, 1.3, 1", "-2.0e7, 1.3, -1"})
    void visVivaAndAngularMomentumEverywhere(double a, double e, int direction) {
        KeplerOrbit orbit = new KeplerOrbit(MU_EARTH, a, e, 2.1, 0.4, 0, direction);
        double h = direction * Math.sqrt(MU_EARTH * a * (1 - e * e));
        for (int i = 0; i < 200; i++) {
            double t = i * 731.0;
            StateVector s = orbit.stateAt(t);
            double r = s.radius();
            assertEquals(MU_EARTH * (2 / r - 1 / a), s.speed() * s.speed(), 1e-9 * MU_EARTH * (2 / r), "vis-viva at " + t);
            assertEquals(h, s.x * s.vy - s.y * s.vx, 1e-9 * Math.abs(h), "h at " + t);
        }
    }

    /**
     * Rails warp at 1,000,000x covers years in minutes. Positions are a pure function of time, so a
     * ship in low orbit is still on the same orbit, with the same energy, a century on.
     */
    @Test
    void noEnergyDriftOverACentury() {
        KeplerOrbit leo = KeplerOrbit.fromApsides(MU_EARTH, R_EARTH + 400e3, R_EARTH + 600e3, 0.3, 0, 1);
        double century = 100 * 365.25 * DAY;
        double h0 = angularMomentum(leo.stateAt(0));
        for (int i = 0; i <= 100; i++) {
            double t = century * i / 100;
            StateVector s = leo.stateAt(t);
            assertEquals(leo.specificEnergy(), energy(s), 1e-9 * Math.abs(leo.specificEnergy()), "energy at year " + i);
            assertEquals(h0, angularMomentum(s), 1e-9 * h0);
            assertTrue(s.radius() >= leo.periapsis() - 1e-3 && s.radius() <= leo.apoapsis() + 1e-3);
        }
        // Whole periods later it is back where it started, to within a few centimetres.
        long orbits = (long) (century / leo.period());
        StateVector start = leo.stateAt(0);
        StateVector back = leo.stateAt(orbits * leo.period());
        assertEquals(0, Math.hypot(back.x - start.x, back.y - start.y), 0.1);
    }

    /**
     * Every rails warp and every bubble handoff refits the conic to the current state. Doing that
     * thousands of times must not walk the orbit away.
     */
    @Test
    void repeatedRefitsDoNotDrift() {
        KeplerOrbit k = new KeplerOrbit(MU_EARTH, 2.66e7, 0.74, 1.0, 0, 0, 1);
        KeplerOrbit original = k;
        double t = 0;
        for (int i = 0; i < 5000; i++) {
            t += 3_600 + i;   // a little over an hour each time, never lined up with the period
            k = KeplerOrbit.fromState(k.stateAt(t), MU_EARTH, t);
        }
        assertEquals(original.a, k.a, original.a * 1e-9);
        assertEquals(original.e, k.e, 1e-9);
        assertEquals(original.argPeriapsis, k.argPeriapsis, 1e-8);
        StateVector p = original.stateAt(t + 10_000);
        StateVector q = k.stateAt(t + 10_000);
        assertEquals(0, Math.hypot(p.x - q.x, p.y - q.y), 10, "within 10 m after 5000 refits");
    }

    @Test
    void negativeTimeRunsTheOrbitBackwards() {
        KeplerOrbit orbit = new KeplerOrbit(MU_EARTH, 2e7, 0.5, 0, 1.0, 1000, 1);
        StateVector before = orbit.stateAt(-5000);
        KeplerOrbit refit = KeplerOrbit.fromState(before, MU_EARTH, -5000);
        StateVector now = refit.stateAt(1000);
        StateVector expected = orbit.stateAt(1000);
        assertEquals(expected.x, now.x, 1e-3);
        assertEquals(expected.y, now.y, 1e-3);
    }

    @Test
    void invalidElementsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new KeplerOrbit(0, 7e6, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new KeplerOrbit(MU_EARTH, 7e6, -0.1, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new KeplerOrbit(MU_EARTH, -7e6, 0.5, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new KeplerOrbit(MU_EARTH, 7e6, 1.5, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new KeplerOrbit(MU_EARTH, 7e6, 1.0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> KeplerOrbit.fromState(0, 0, 1, 0, MU_EARTH, 0));
    }

    /** Exactly escape speed would be a parabola; it is nudged to just past one, and still propagates. */
    @Test
    void escapeSpeedExactlyBecomesANearParabola() {
        double r = 7e6;
        KeplerOrbit k = KeplerOrbit.fromState(r, 0, 0, OrbitMath.escapeSpeed(MU_EARTH, r), MU_EARTH, 0);
        assertNotEquals(1.0, k.e);
        assertEquals(1.0, k.e, 1e-6);
        assertEquals(r, k.stateAt(0).radius(), 1);
        assertTrue(k.stateAt(10_000).radius() > r);
    }

    private static double energy(StateVector s) {
        return 0.5 * s.speed() * s.speed() - MU_EARTH / s.radius();
    }

    private static double angularMomentum(StateVector s) {
        return s.x * s.vy - s.y * s.vx;
    }
}
