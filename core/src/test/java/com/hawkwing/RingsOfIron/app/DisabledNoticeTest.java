package com.hawkwing.RingsOfIron.app;

import com.ringsofiron.shipops.DisabledComponent.Reason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the player's ship's states the DISABLED alert carries, so its notice names the alert: the
 * ship disabled, not wrecked or abandoned, which are news (the Task spec's 9.2), nor back in action.
 */
class DisabledNoticeTest {
    @Test
    void theShipDisabledIsTheAlertsAndAWreckOrAbandoningIsNews() {
        assertTrue(GameBootstrap.disabledOnly(Reason.NO_POWER));
        assertTrue(GameBootstrap.disabledOnly(Reason.NO_CREW));
        assertTrue(GameBootstrap.disabledOnly(Reason.HULL));
        assertFalse(GameBootstrap.disabledOnly(Reason.WRECKED));
        assertFalse(GameBootstrap.disabledOnly(Reason.ABANDONED));
        assertFalse(GameBootstrap.disabledOnly(Reason.SURRENDERED));
        assertFalse(GameBootstrap.disabledOnly(null), "back in action");
    }
}
