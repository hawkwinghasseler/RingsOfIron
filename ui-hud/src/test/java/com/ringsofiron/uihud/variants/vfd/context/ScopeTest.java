package com.ringsofiron.uihud.variants.vfd.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** H5's range: the smallest step that holds what matters with room to spare, and its labels. */
class ScopeTest {
    @Test
    void aFightTakesTheSmallestStepThatHoldsTheLockAndTheMissiles() {
        assertEquals(0, Scope.holding(0f));
        assertEquals(0, Scope.holding(400f));
        assertEquals(1, Scope.holding(401f), "past 80% of 0.5 km");
        assertEquals(2, Scope.holding(3_000f));
        assertEquals(4, Scope.holding(30_000f));
        assertEquals(Scope.STEPS.length - 1, Scope.holding(5e9f), "beyond the last step it stays on it");
    }

    @Test
    void theRangeReadsInKilometres() {
        assertEquals("0.5 KM", Scope.rangeText(0));
        assertEquals("2 KM", Scope.rangeText(Scope.APPROACH_STEP));
        assertEquals("50 KM", Scope.rangeText(Scope.CRUISE_STEP));
    }

    @Test
    void theKeysStopAtEitherEnd() {
        Scope s = new Scope();
        s.step = 0;
        s.zoom(-1);
        assertEquals(0, s.step);
        s.step = Scope.STEPS.length - 1;
        s.zoom(1);
        assertEquals(Scope.STEPS.length - 1, s.step);
    }
}
