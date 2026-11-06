package com.ringsofiron.uihud.variants.cassette.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
