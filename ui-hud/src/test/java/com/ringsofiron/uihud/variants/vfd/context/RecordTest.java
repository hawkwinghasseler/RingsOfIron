package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.CareerProvider.Region;
import com.ringsofiron.uihud.variants.ClassicWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M5, the captain's record: its keys, and the Log's rows, merged, filtered and opened. */
class RecordTest {
    private final HudData d = new HudData();
    private final VfdContextHud hud = new VfdContextHud();
    private final Modes modes = hud.modes;
    private final RecordMode record = modes.record;

    /** Away from a station, as with no game behind it: the game has no mission board to open. */
    private void openAt(ClassicWindow w) {
        assertTrue(record.open(w, modes.boards, d));
        modes.current = record;
    }

    @Test
    void itStandsForTheLogOnEveryTabAndLTurnsToTheLogThenGoesOnToTheGame() {
        openAt(ClassicWindow.LOG);
        assertSame(record.log, record.tab);
        assertEquals("L", record.closeKey());
        assertFalse(record.key(Keys.L), "on the Log, L is the game's, which closes the log");
        assertTrue(record.key(Keys.NUM_1));
        assertSame(record.career, record.tab);
        assertTrue(record.showing(ClassicWindow.LOG), "the log window, whichever tab shows");
        assertEquals("", record.closeKey(), "only Esc closes it from the Career");
        assertTrue(record.key(Keys.NUM_2));
        assertSame(record.standing, record.tab);
        assertTrue(record.key(Keys.L));
        assertSame(record.log, record.tab);
    }

    @Test
    void awayFromAStationJTurnsToTheContractsAndClosesTheRecordFromThere() {
        openAt(ClassicWindow.MISSIONS);
        assertSame(record.contracts, record.tab);
        assertEquals("J", record.closeKey());
        assertFalse(record.showing(ClassicWindow.MISSIONS), "no board to stand for, so the game won't close it as one");
        assertTrue(record.key(Keys.NUM_4));
        assertSame(record.log, record.tab);
        assertTrue(record.key(Keys.J));
        assertSame(record.contracts, record.tab);
        assertTrue(record.key(Keys.J));
        assertNull(modes.current, "closed");
    }

    @Test
    void theLogMergesGunneryAFewSecondsApartAtTheFirstOnesTime() {
        LogTab log = log();
        assertEquals(4, log.entries.size);
        LogTab.Entry gunnery = log.entries.get(0);
        assertEquals("GUNNERY", gunnery.source);
        assertEquals(2, gunnery.count);
        assertEquals(86_400 + 27, gunnery.time, 1e-9);
        assertEquals("RAILGUN MISSED THE PIRATE GUNSHIP (+1 MORE)", gunnery.what);
        assertEquals(1, gunnery.day);
        LogTab.Entry fire = log.entries.get(1);
        assertEquals("ENGINEERING", fire.source);
        assertEquals(Level.WARNING, fire.level);
        assertEquals(1, log.entries.get(2).count, "ten seconds before the fire: its own row");
        assertEquals("DOCKED AT HIGHPORT (×2)", log.entries.get(3).what);
        assertEquals(0, log.entries.get(3).day);
    }

    @Test
    void theLogsFiltersAreReachedFromItsNewestRowAndStepped() {
        LogTab log = log();
        assertTrue(log.key(Keys.UP));
        assertEquals(LogTab.BY_SEVERITY, log.filter);
        assertTrue(log.key(Keys.UP));
        assertEquals(LogTab.BY_SOURCE, log.filter);
        // Helm, then Gunnery
        assertTrue(log.key(Keys.RIGHT));
        assertTrue(log.key(Keys.RIGHT));
        assertEquals(2, log.entries.size, "the gunnery rows");
        assertTrue(log.key(Keys.DOWN));
        assertTrue(log.key(Keys.RIGHT));
        assertTrue(log.key(Keys.RIGHT));
        assertEquals(0, log.entries.size, "no gunnery warnings");
        assertEquals("NO WARNINGS FROM GUNNERY", log.empty());
        assertTrue(log.key(Keys.LEFT));
        assertTrue(log.key(Keys.LEFT));
        assertTrue(log.key(Keys.DOWN));
        assertEquals(LogTab.NO_FILTER, log.filter);
        assertEquals(2, log.entries.size);
    }

    @Test
    void rightOpensAMergedRowToEachOfItsMessagesAndLeftFoldsIt() {
        LogTab log = log();
        LogTab.Entry gunnery = log.entries.get(0);
        assertFalse(log.isOpen(gunnery));
        assertTrue(log.key(Keys.RIGHT));
        assertTrue(log.isOpen(gunnery));
        assertEquals(2, gunnery.extra);
        assertTrue(log.key(Keys.LEFT));
        assertFalse(log.isOpen(gunnery));
        // A row of one short message has nothing more to open to
        assertTrue(log.key(Keys.DOWN));
        assertTrue(log.key(Keys.RIGHT));
        assertFalse(log.isOpen(log.entries.get(1)));
    }

    @Test
    void theCareerAndStandingSayItInWords() {
        assertEquals("UP TO A CORVETTE", CareerTab.mayFly("corvette"));
        assertEquals("UP TO AN ESCORT", CareerTab.mayFly("escort"));
        assertEquals("ANY SHIP", CareerTab.mayFly(""));
        Region r = new Region();
        r.high = true;
        r.yardRise = 0.12f;
        r.combatJobs = 2;
        assertEquals("HIGH, YARDS +12%, 2 MORE COMBAT JOBS", record.standing.regionNote(r));
        r.high = false;
        r.yardRise = 0f;
        r.combatJobs = 0;
        assertEquals("", record.standing.regionNote(r));
    }

    /** The Log on a full-screen slate, read from five messages over two days, newest first as the game lists them. */
    private LogTab log() {
        MessageFeed feed = new MessageFeed();
        feed.add("Railgun hit the pirate gunship", Level.ADVISORY, 86_400 + 30);
        feed.add("Railgun missed the pirate gunship", Level.ADVISORY, 86_400 + 27);
        feed.add("Fire in the bridge", Level.WARNING, 86_400 + 20);
        feed.add("Railgun hit the pirate gunship", Level.ADVISORY, 86_400 + 10);
        feed.add("Docked at Highport", Level.ADVISORY, 100).count = 2;
        openAt(ClassicWindow.LOG);
        LogTab log = record.log;
        log.place(40f, 132f, 1200f, 564f);
        log.read(feed);
        log.build();
        log.shown();
        return log;
    }
}
