package com.hawkwing.RingsOfIron.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatThreatsTest {

    @Test
    void closingHeadOnMeetsAtRangeOverSpeed() {
        assertEquals(10f, CombatThreats.closestApproach(1000f, 0f, -100f, 0f), 1e-4);
    }

    @Test
    void crossingPassesAtTheFootOfThePerpendicular() {
        assertEquals(5f, CombatThreats.closestApproach(-500f, 300f, 100f, 0f), 1e-4);
    }

    @Test
    void openingOrStillHasNoApproach() {
        assertTrue(Float.isNaN(CombatThreats.closestApproach(1000f, 0f, 100f, 0f)));
        assertTrue(Float.isNaN(CombatThreats.closestApproach(1000f, 0f, 0f, 0f)));
    }
}
