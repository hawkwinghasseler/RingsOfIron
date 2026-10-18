package com.ringsofiron.uihud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadSolverTest {

    @Test
    void stationaryTargetTakesRangeOverSpeed() {
        assertEquals(2f, LeadSolver.interceptTime(1000f, 0f, 0f, 0f, 500f), 1e-4f);
    }

    @Test
    void crossingTargetIsLed() {
        // Target 1000 m ahead crossing at 300 m/s, shot at 500 m/s: 500^2 t^2 = 1000^2 + 300^2 t^2 -> t = 2.5 s
        float t = LeadSolver.interceptTime(1000f, 0f, 0f, 300f, 500f);
        assertEquals(2.5f, t, 1e-4f);
    }

    @Test
    void recedingFasterThanShotHasNoSolution() {
        assertTrue(Float.isNaN(LeadSolver.interceptTime(1000f, 0f, 600f, 0f, 500f)));
    }

    @Test
    void approachingTargetUsesEarliestPositiveTime() {
        // Closing head-on at 100 m/s with a 500 m/s shot: 1000 / 600
        assertEquals(1000f / 600f, LeadSolver.interceptTime(1000f, 0f, -100f, 0f, 500f), 1e-4f);
    }

    @Test
    void sameSpeedChaseSolvesLinearCase() {
        // Target moving straight at us at the shot's speed: meets in d / (2s)
        assertEquals(1f, LeadSolver.interceptTime(1000f, 0f, -500f, 0f, 500f), 1e-4f);
        assertTrue(Float.isNaN(LeadSolver.interceptTime(1000f, 0f, 500f, 0f, 500f)));
    }
}
