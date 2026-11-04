package com.ringsofiron.uihud.variants.nasapunk.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The situations' ways in and out (section 4.2), with the hysteresis that keeps panels from flickering. */
class SituationsTest {
    private final Situations s = new Situations();
    private final Situations.Input in = new Situations.Input();

    @Test
    void anApproachStartsInsideFiveKilometresWithATriggerAndEndsPastFiveAndAHalf() {
        in.approachTrigger = true;
        in.portRange = 6_000f;
        s.update(in, 0.1f);
        assertFalse(s.approach, "a port 6 km off is not an approach yet");
        in.portRange = 4_900f;
        s.update(in, 0.1f);
        assertTrue(s.approach);
        in.portRange = 5_400f;
        s.update(in, 0.1f);
        assertTrue(s.approach, "between 5 and 5.5 km it holds");
        in.portRange = 5_600f;
        s.update(in, 0.1f);
        assertFalse(s.approach);
    }

    @Test
    void aPortInRangeWithoutATriggerIsNoApproach() {
        in.portRange = 1_000f;
        s.update(in, 0.1f);
        assertFalse(s.approach);
        assertTrue(s.cruise());
    }

    @Test
    void theApproachHoldsThreeSecondsAfterItsTriggerGoes() {
        in.approachTrigger = true;
        in.portRange = 2_000f;
        s.update(in, 0.1f);
        in.approachTrigger = false;
        s.update(in, 2.9f);
        assertTrue(s.approach);
        s.update(in, 0.2f);
        assertFalse(s.approach);
    }

    @Test
    void dockingEndsTheApproach() {
        in.approachTrigger = true;
        in.portRange = 20f;
        s.update(in, 0.1f);
        assertTrue(s.approach);
        in.docked = true;
        s.update(in, 0.1f);
        assertFalse(s.approach);
        assertTrue(s.docked);
        assertFalse(s.cruise());
    }

    @Test
    void combatHoldsTenSecondsAfterItsLastTrigger() {
        assertFalse(s.combat);
        in.combatTrigger = true;
        s.update(in, 0.1f);
        assertTrue(s.combat);
        in.combatTrigger = false;
        s.update(in, 9.5f);
        assertTrue(s.combat);
        s.update(in, 1f);
        assertFalse(s.combat);
        assertTrue(s.cruise());
    }

    @Test
    void warpBoardingAndEmergencyFollowTheirInputs() {
        in.warp = true;
        in.boarding = true;
        in.emergency = true;
        s.update(in, 0.1f);
        assertTrue(s.warp && s.boarding && s.emergency);
        in.warp = in.boarding = in.emergency = false;
        s.update(in, 0.1f);
        assertFalse(s.warp || s.boarding || s.emergency);
    }
}
