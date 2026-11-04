package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TickerTest {

    @Test
    void theNewestLineIsAtTheBottomAndALineHoldsWhileTheGameKeepsIt() {
        MessageFeed feed = new MessageFeed();
        feed.clear();
        feed.add("Missiles inbound from port", Level.WARNING, 12).secondsLeft = 4f;
        feed.add("Fire in the bridge", Level.WARNING, 10).secondsLeft = 3f;
        feed.add("Docked at Highport", Level.ADVISORY, 2).secondsLeft = 0f;
        Ticker t = new Ticker();
        t.fill(feed, 0.016f);
        assertNull(t.lines[0]);
        assertEquals("FIRE IN THE BRIDGE", t.lines[1].text);
        assertEquals("MISSILES INBOUND FROM PORT", t.lines[2].text);
        assertEquals("TAC", t.lines[2].tag);
        assertEquals("ENG", t.lines[1].tag);
        Ticker.Line kept = t.lines[2];
        t.fill(feed, 0.5f);
        assertSame(kept, t.lines[2]);
        assertEquals(0.516f, kept.shown, 1e-4f);
    }

    @Test
    void theStationIsReadFromTheWords() {
        assertEquals("HELM", Ticker.tag("Helm: holding position"));
        assertEquals("COMMS", Ticker.tag("Clearance granted at Highport"));
        assertEquals("NAV", Ticker.tag("Burn 2 in 30 seconds"));
        assertEquals("CMD", Ticker.tag("Cole is wounded"));
        assertEquals("CMD", Ticker.tag("Welcome aboard"));
    }

    @Test
    void aLongLineHoldsAtItsStartStepsAlongAndHoldsAtItsEnd() {
        assertEquals(0, Ticker.scroll(20, 30, 100f));
        assertEquals(0, Ticker.scroll(40, 30, 1f));
        assertEquals(1, Ticker.scroll(40, 30, Ticker.HOLD + Ticker.STEP * 1.5f));
        assertEquals(10, Ticker.scroll(40, 30, Ticker.HOLD + Ticker.STEP * 12f));
        // The cycle starts again from the beginning
        float cycle = Ticker.HOLD * 2f + 10 * Ticker.STEP;
        assertEquals(0, Ticker.scroll(40, 30, cycle + 0.1f));
    }
}
