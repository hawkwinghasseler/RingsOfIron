package com.ringsofiron.uihud.variants.vfd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FmtTest {

    @Test
    void numbersAreCutToTheirCellsWithTheDecimalsThatFit() {
        assertEquals("7.67", Fmt.fit(7.67, 4, 2));
        // '.' joins the cell before, so three cells still hold two decimals
        assertEquals("7.67", Fmt.fit(7.67, 3, 2));
        assertEquals("7.7", Fmt.fit(7.67, 2, 2));
        assertEquals("1840", Fmt.fit(1840, 4, 0));
        assertEquals("-3.9", Fmt.fit(-3.9, 3, 1));
        // A negative zero reads as zero
        assertEquals("0.0", Fmt.fit(-0.04, 3, 1));
    }

    @Test
    void aValueTooBigForItsFieldShowsNinesAndNothingShowsBlank() {
        assertEquals("9999", Fmt.fit(12_345, 4, 0));
        assertEquals("-99", Fmt.fit(-12_345, 3, 0));
        assertEquals("", Fmt.fit(Double.NaN, 4, 1));
    }

    @Test
    void rangesAreMetresUnderAKilometreAndKilometresAbove() {
        assertEquals("140", Fmt.range(140, 4));
        assertFalse(Fmt.km(140));
        assertEquals("2.8", Fmt.range(2_800, 3));
        assertTrue(Fmt.km(2_800));
        assertEquals("140 M", Fmt.rangeText(140));
        assertEquals("2.8 KM", Fmt.rangeText(2_800));
        assertEquals("1,204 KM", Fmt.rangeText(1_204_000));
        assertEquals("", Fmt.rangeText(Double.NaN));
    }

    @Test
    void timesCountUpToTheWholeSecond() {
        assertEquals("0:11", Fmt.mmss(10.2));
        assertEquals("12:04", Fmt.mmss(724));
        assertEquals("1:02:05", Fmt.mmss(3_725));
        assertEquals("1D01", Fmt.mmss(90_000));
        assertEquals("", Fmt.mmss(-1));
        assertEquals("", Fmt.mmss(Double.NaN));
        assertEquals("22", Fmt.secs(21.5));
    }

    @Test
    void theChronometerReadsTheCareersDayAndTime() {
        double t = 3 * 86_400 + 14 * 3_600 + 22 * 60 + 5;
        assertEquals(3, Fmt.day(t));
        assertEquals("14:22:05", Fmt.clock(t));
        assertEquals(0, Fmt.day(0));
    }

    @Test
    void namesAreCapitalsCutToTheirCells() {
        assertEquals("HIGH", Fmt.cut("Highport", 4));
        assertEquals("RAIDER", Fmt.cut("Raider", 8));
        assertEquals("", Fmt.cut(null, 4));
    }

    @Test
    void aShipsNameKeepsTheWordsThatTellItApart() {
        assertEquals("FREIGHTER", Fmt.name("Freighter", 9));
        assertEquals("RAIDER 6", Fmt.name("Pirate raider 6", 8));
        assertEquals("PIRATE RAIDER 6", Fmt.name("Pirate raider 6", 15));
        assertEquals("RAIDE 6", Fmt.name("Pirate raider 6", 7));
        assertEquals("GUNSH 12", Fmt.name("Pirate gunship 12", 8));
        assertEquals("FREIGHTE", Fmt.name("Freighter", 8));
        assertEquals("HIGH", Fmt.name("Highport", 4));
        assertEquals("KESTREL", Fmt.name("The Kestrel", 7));
        assertEquals("", Fmt.name(null, 8));
        assertEquals("HAUL", Fmt.name("Hauler 123456", 4));
        // The game's tags on a marker's label are not the name
        assertEquals("RAIDER 6", Fmt.name("Pirate raider 6 (surrendered)", 10));
        assertEquals("KESTREL", Fmt.name("Kestrel [contract]", 14));
        assertEquals("BATTERY 23", Fmt.name("Battery 23 (silenced) [contract]", 14));
        assertEquals("(", Fmt.bare("("));
    }

    @Test
    void stopsAndColonsJoinTheCellBefore() {
        assertEquals(3, Fmt.segCells("7.67"));
        assertEquals(4, Fmt.segCells("12:04"));
        assertEquals(1, Fmt.segCells("."));
    }
}
