package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4: newest on top with its source, and in a fight the gunnery summarised and routine news held. */
class FeedTest {
    private final MessageFeed feed = new MessageFeed();
    private final Feed p4 = new Feed();

    /** A notice still on show, given newest first as the game lists them. */
    private Message post(String text, double time) {
        Message m = feed.add(text, Level.ADVISORY, time);
        m.secondsLeft = 6f;
        return m;
    }

    @Test
    void eachLineSaysItsSource() {
        assertEquals("HELM", Feed.source("Helm: burn 2 of 3 flown"));
        assertEquals("HELM", Feed.source("Navigation target: Raider, 10.7 km"));
        assertEquals("GUNNERY", Feed.source("Railgun hit the pirate gunship"));
        assertEquals("ENGINEERING", Feed.source("Fire out in the galley"));
        assertEquals("STATION", Feed.source("Docked at Highport"));
        assertEquals("BURN 2 OF 3 FLOWN", Feed.body("Helm: burn 2 of 3 flown"));
        // Words, not parts of words: "aboard" is no boarding, a mission board is the station's
        assertEquals("LOG", Feed.source("Welcome aboard, Ensign. Terran Patrol Authority."));
        assertEquals("STATION", Feed.source("Docked at your home station. J opens its mission board"));
        assertEquals("BOARDING", Feed.source("3 prisoners brought aboard"));
        // Salvage names the craft and its guns, but it is a boarding's
        assertEquals("BOARDING", Feed.source("No one can get to the engine on Raider from inside: stand off alongside to strip it on EVA"));
        assertEquals("BOARDING", Feed.source("The harpoon launcher is off Raider; it's being carried home"));
        assertEquals("BOARDING", Feed.source("The harpoon launcher is in the hold (sells for 1,500 cr)"));
        post("Helm: burn 2 of 3 flown", 2);
        post("Docked at Highport", 1);
        p4.update(feed, false, 2, 0.1f);
        assertEquals(2, p4.count);
        assertEquals("HELM", p4.source[0]);
        assertEquals("STATION", p4.source[1]);
    }

    @Test
    void inAFightGunneryIsOneLineAndRoutineNewsWaitsInTheLog() {
        post("Railgun hit the pirate gunship", 12);
        post("Railgun missed the pirate gunship", 11);
        post("Hired a deckhand at Highport", 10.5);
        post("Railgun hit the pirate gunship", 10);
        p4.update(feed, true, 12, 0.1f);
        assertEquals(1, p4.count, "the gunnery line alone");
        assertEquals("GUNNERY", p4.source[0]);
        assertTrue(p4.text[0].endsWith("(×3)"), p4.text[0]);
        // The fight ends: the held news is counted in one line for 5 s
        for (Message m : feed.all()) m.secondsLeft = 0f;
        p4.update(feed, false, 25, 0.1f);
        assertEquals("LOG", p4.source[0]);
        assertEquals("1 MESSAGE IN THE LOG", p4.text[0]);
        for (int i = 0; i < 60; i++) p4.update(feed, false, 25 + i * 0.1, 0.1f);
        assertEquals(0, p4.count);
    }

    @Test
    void aLineKeepsToTheColumn() {
        post("Engineering: the reactor is coming back up and the conduits in the hold are patched", 1);
        p4.update(feed, false, 1, 0.1f);
        assertTrue(p4.source[0].length() + 2 + p4.text[0].length() <= Feed.CELLS, p4.text[0]);
        assertTrue(p4.text[0].endsWith("…"));
    }
}
