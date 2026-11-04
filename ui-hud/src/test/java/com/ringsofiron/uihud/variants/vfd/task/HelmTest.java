package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HLM's words for who has the helm and what it is doing, from the game's own. */
class HelmTest {
    @Test
    void theGamesOrderLinesInHlmsWords() {
        assertEquals("MATCHING VELOCITY WITH RAIDER 'VEX'", Helm.order("Helm: matching velocity with Raider 'Vex'"));
        assertEquals("HOLDING POSITION ON HIGHPORT, WAITING: NO HELMSMAN",
                Helm.order("Helm order (holding position on Highport) waits for a helmsman"));
        assertEquals("APPROACHING HIGHPORT TO 500 M, WAITING: YOU ARE AT THE HELM",
                Helm.order("Helm order (approaching Highport to 500 m) waits: you have the helm (V hands it over)"));
    }

    @Test
    void anOrderLongerThanItsWindowMarqueesSoItsReasonIsReadWhole() {
        String line = Helm.order("Helm order (approaching Raider to 500 m) waits: you have the helm (V hands it over)");
        int cells = Geo.fit(6f, Geo.FLT_W - 6f);
        int over = line.length() - cells;
        assertTrue(over > 0, line);
        // Held at its start, stepped a cell at a time, held at its end, then round again
        assertEquals(line.substring(0, cells), Words.marquee(line, cells, 0f));
        assertEquals(line.substring(0, cells), Words.marquee(line, cells, Words.HOLD - 0.05f));
        assertEquals(line.substring(1, cells + 1), Words.marquee(line, cells, Words.HOLD + Words.STEP + 0.01f));
        float end = Words.HOLD + over * Words.STEP;
        assertTrue(Words.marquee(line, cells, end + 0.5f).endsWith("YOU ARE AT THE HELM"));
        assertEquals(line.substring(0, cells), Words.marquee(line, cells, end + Words.HOLD + 0.01f));
        // A line that fits stands still
        assertEquals("HOLDING HEADING", Words.marquee("HOLDING HEADING", cells, 7f));
    }

    @Test
    void aNewOrderStartsItsMarqueeAtItsStart() {
        HudData d = new HudData();
        d.ctx.ship = new Entity();
        d.ctx.presence = Presence.HELM;
        d.ctx.exteriorView = true;
        d.ctx.helmsman = "you";
        d.orderState.helm = HelmOrder.APPROACH;
        d.orderState.helmLine = "Helm order (approaching Raider to 500 m) waits: you have the helm (V hands it over)";
        Posts p = new Posts();
        p.update(d, false);
        Helm h = new Helm();
        h.update(d, p, true, 5f);
        assertTrue(h.on);
        assertEquals(5f, h.since);
        h.update(d, p, true, 9f);
        assertEquals(5f, h.since, "the same line runs on");
        d.orderState.helmLine = "Helm order (approaching Raider to 1,000 m) waits: you have the helm (V hands it over)";
        h.update(d, p, true, 12f);
        assertEquals(12f, h.since);
    }

    @Test
    void whoHasTheHelm() {
        assertEquals("YOU", Helm.who("you"));
        assertEquals("NOBODY", Helm.who(""));
        assertEquals("BEA", Helm.who("Bea"));
    }

    @Test
    void theAutopilotFirstThenTheOrderThenABurnFlownThenTheHeadingHeld() {
        HudData d = new HudData();
        d.ctx.helmsman = "Bea";
        assertEquals("HOLDING HEADING", Helm.line(d));
        d.ctx.burnFlown = true;
        d.navReadout.burnToGo = 412f;
        d.navReadout.burnNumber = 1;
        assertEquals("FLYING BURN 1, 412 M/S TO GO", Helm.line(d));
        d.orderState.helm = HelmOrder.HOLD_POSITION;
        d.orderState.helmLine = "Helm: holding position on Highport";
        assertEquals("HOLDING POSITION ON HIGHPORT", Helm.line(d));
        d.orderState.autopilot = Autopilot.HELD;
        d.orderState.autopilotTo = "Shackleton Ice";
        d.orderState.autopilotHeld = "raider near";
        assertEquals("AUTOPILOT TO SHACKLETON ICE, HOLDING: RAIDER NEAR", Helm.line(d));
        d.ctx.helmsman = "";
        d.orderState.autopilot = Autopilot.OFF;
        d.orderState.helm = HelmOrder.NONE;
        d.ctx.burnFlown = false;
        assertEquals("THE SHIP DRIFTS", Helm.line(d));
    }

    @Test
    void refusalsShort() {
        assertEquals("NO TARGET", Helm.refusal("The helm needs a navigation target for that (Q picks one)"));
        assertEquals("TOO FAR: PLAN A TRANSFER", Helm.refusal("Highport is too far for the helm to dock at: plan a transfer (;)"));
        assertEquals("NO BURNS PLANNED", Helm.refusal("No burns planned for the helm to fly (N plans one, ; plans to the target)"));
    }
}
