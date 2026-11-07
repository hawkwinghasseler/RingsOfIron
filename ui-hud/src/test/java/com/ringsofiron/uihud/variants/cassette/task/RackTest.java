package com.ringsofiron.uihud.variants.cassette.task;

import org.junit.jupiter.api.Test;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.NoticeAlerts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rack's rows (spec round 11): a title or a notice too long for its line takes a second, never a third. */
class RackTest {

    @Test
    void aTitleBreaksBeforeWhereTheJobGoes() {
        assertArrayEquals(new String[]{"Deliver 40 Food", "to Shackleton Ice"}, Rack.wrap("Deliver 40 Food to Shackleton Ice", Rack.SLIP_CHARS, true));
        assertArrayEquals(new String[]{"Defend Highport", "from 3 raiders"}, Rack.wrap("Defend Highport from 3 raiders", Rack.SLIP_CHARS, true));
        // A bounty breaks before the faction its title names (spec round 13)
        assertArrayEquals(new String[]{"Bounty: Raider", "of the Void Raiders"}, Rack.wrap("Bounty: Raider of the Void Raiders", Rack.SLIP_CHARS, true));
    }

    @Test
    void aTitleThatFitsTakesOneLine() {
        assertArrayEquals(new String[]{"Bounty: Black Sal"}, Rack.wrap("Bounty: Black Sal", Rack.SLIP_CHARS, true));
    }

    @Test
    void elseItBreaksAtAWordAndPastTwoLinesIsCut() {
        assertArrayEquals(new String[]{"Bounty: Pirate gunship 2", "(the ace, 3 kills)"},
                Rack.wrap("Bounty: Pirate gunship 2 (the ace, 3 kills)", Rack.SLIP_CHARS, true));
        String[] news = Rack.wrap("Budget from the Terran Patrol Authority: +2,000 cr, paid on the first of each week as it falls due",
                Rack.TELEX_CHARS, false);
        assertEquals(2, news.length);
        assertEquals("Budget from the Terran Patrol", news[0]);
        assertTrue(news[1].endsWith("…") && news[1].length() == Rack.TELEX_CHARS, news[1]);
    }

    /** 9.2's table of the game's warnings: one this HUD shows as an alert is the band's, the rest are news. */
    @Test
    void aWarningIsNewsUnlessTheBandOrThePlacardCarriesIt() {
        MessageFeed feed = new MessageFeed();
        TaskAlerts none = new TaskAlerts();
        MessageFeed.Message death = feed.add("Cole has died of wounds", Level.WARNING, 10);
        assertTrue(Rack.news(death, none, false, false), "nothing in the band could undo it");
        assertTrue(Rack.important(death), "in red");
        MessageFeed.Message leak = feed.add("Hull leaking: the crew are patching it", Level.WARNING, 10);
        leak.alert = NoticeAlerts.VENTING;
        assertTrue(Rack.news(leak, none, false, false), "news until the band has the room's air (TaskAlertsTest has it carried)");
        MessageFeed.Message boarders = feed.add("Pirate raider 3 is coming alongside to board you", Level.WARNING, 10);
        boarders.alert = NoticeAlerts.BOARDERS;
        assertTrue(Rack.news(boarders, none, false, false), "an alert this HUD can't raise yet: news, not lost");
        MessageFeed.Message asked = feed.add("Abandon ship? P again to send the crew to the escape pods", Level.WARNING, 10);
        asked.alert = NoticeAlerts.ASKED;
        assertTrue(Rack.news(asked, none, false, true), "away from the placard the question is the only word of it");
        assertFalse(Rack.news(asked, none, false, false), "its guard gone, the key asks again");
        assertFalse(Rack.news(asked, none, true, true), "the placard's guard says AGAIN");
        MessageFeed.Message couch = feed.add("The couch burned", Level.WARNING, 10);
        couch.minor = true;
        assertTrue(Rack.news(couch, none, false, false));
        assertFalse(Rack.important(couch), "a loss and nothing more: info");
        MessageFeed.Message docked = feed.add("Docked at Highport", Level.ADVISORY, 10);
        assertTrue(Rack.news(docked, none, false, false));
        assertFalse(Rack.important(docked));
        MessageFeed.Message promoted = feed.add("Promoted to Lieutenant", Level.ADVISORY, 10);
        promoted.important = true;
        assertTrue(Rack.important(promoted));
    }
}
