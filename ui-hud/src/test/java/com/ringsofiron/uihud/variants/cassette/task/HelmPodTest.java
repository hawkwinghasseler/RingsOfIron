package com.ringsofiron.uihud.variants.cassette.task;

import org.junit.jupiter.api.Test;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HLM's standing order (spec HLM): the autopilot is one, and its hold is the line's (9.2). */
class HelmPodTest {

    private final HudData d = new HudData();

    HelmPodTest() {
        d.ctx.helmsman = "Bea";
        d.orderState.autopilotTo = "Shackleton Ice";
    }

    @Test
    void theAutopilotSaysWhereItFliesAndWhyItHolds() {
        assertEquals("HOLDING HEADING", HelmPod.order(d));
        d.orderState.autopilot = Autopilot.ON;
        assertEquals("AUTOPILOT TO SHACKLETON ICE", HelmPod.order(d));
        assertFalse(HelmPod.saysHold(d));
        d.orderState.autopilot = Autopilot.HELD;
        d.orderState.autopilotHeld = "pirates about";
        assertEquals("AUTOPILOT TO SHACKLETON ICE, HOLDING: PIRATES ABOUT", HelmPod.order(d));
        assertTrue(HelmPod.saysHold(d));
        TaskFrame f = new TaskFrame();
        f.data = d;
        assertEquals("Helm: bea, autopilot to shackleton ice, holding: pirates about", new HelmPod(f).hover(0f, 0f), "the whole of it on the hover");
    }

    @Test
    void heldTooLongForTheLineItKeepsWhyOverWhere() {
        d.orderState.autopilot = Autopilot.HELD;
        d.orderState.autopilotHeld = "pirates about";
        // The line on FLT, 460 units across
        assertEquals("AUTOPILOT HOLDING: PIRATES ABOUT", HelmPod.orderFit(d, FlightPod.W - 154f));
        d.orderState.autopilot = Autopilot.ON;
        assertEquals("AUTOPILOT TO SHACKLETON ICE", HelmPod.orderFit(d, FlightPod.W - 154f));
    }

    @Test
    void aBurnFlownOrAnOrderGoesAheadOfIt() {
        d.orderState.autopilot = Autopilot.HELD;
        d.orderState.autopilotHeld = "pirates about";
        d.ctx.helmOrder = "Helm: approaching Highport to 500 m";
        assertEquals("HELM: APPROACHING HIGHPORT TO 500 M", HelmPod.order(d));
        assertFalse(HelmPod.saysHold(d), "the line says the order, so the hold is news");
        d.ctx.helmOrder = "";
        d.ctx.burnFlown = true;
        d.navReadout.burnNumber = 1;
        d.navReadout.burnDeltaV = 412f;
        assertEquals("FLYING BURN 1, 412 M/S TO GO", HelmPod.order(d));
        assertFalse(HelmPod.saysHold(d));
    }
}
