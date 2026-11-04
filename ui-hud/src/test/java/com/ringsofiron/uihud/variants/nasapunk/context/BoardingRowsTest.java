package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** X2's rows from the game's boarding board (section 8), as BoardingWork fills it. */
class BoardingRowsTest {
    private final BoardingRows rows = new BoardingRows();

    /** A beaten raider in reach, the party ready: what BoardingWork says before anyone goes. */
    private static BoardingBoard raider(boolean byEva) {
        BoardingBoard b = new BoardingBoard();
        b.craftName = "Raider 6";
        b.byEva = byEva;
        b.disabled = true;
        b.state = "Disabled: reactor down";
        b.defenders = 3;
        b.salvageRefusal = "Take it to strip it: send the boarding party across, or strip a ship no one defends";
        return b;
    }

    private static Part part(BoardingBoard b, boolean marked, boolean off) {
        Part p = b.addPart();
        p.name = "Coilgun";
        p.marked = marked;
        p.off = off;
        return p;
    }

    private static Cargo cargo(BoardingBoard b, String what, double onOrder) {
        Cargo c = b.addCargo();
        c.commodity = what;
        c.units = 10;
        c.onOrder = onOrder;
        return c;
    }

    @Test
    void aShipInReachIsABoardingOnlyOnceItIsBeatenOrOurs() {
        BoardingBoard b = raider(true);
        assertTrue(BoardingRows.boarding(b));
        b.disabled = false;
        b.state = "Fit to fight";
        assertFalse(BoardingRows.boarding(b), "a fit ship in reach on EVA is still a fight");
        b.boarding = true;
        assertTrue(BoardingRows.boarding(b), "storming it is a boarding");
        b.boarding = false;
        b.byEva = false;
        assertTrue(BoardingRows.boarding(b), "latched to it is a boarding");
        assertFalse(BoardingRows.boarding(new BoardingBoard()), "nothing latched or in reach");
        assertFalse(BoardingRows.boarding(null));
    }

    @Test
    void aBeatenShipSaysWhyAndThatThePartyIsReady() {
        rows.read(raider(false), true);
        assertEquals("RAIDER 6", rows.ship);
        assertEquals("LATCHED", rows.held);
        assertEquals("DISABLED:", rows.stateHead);
        assertEquals("REACTOR DOWN", rows.state);
        assertSame(NpColors.AMBER, rows.stateColor);
        assertEquals("READY", rows.party);
        assertFalse(rows.partyWhy);
        assertEquals("", rows.salvage, "its crew still defend it, so nothing can be stripped yet");
        assertEquals("", rows.cargo);
        assertFalse(rows.prisoners);
    }

    @Test
    void aReasonThatSaysItAllStandsAlone() {
        BoardingBoard b = raider(true);
        b.state = "Disabled: surrendered";
        rows.read(b, false);
        assertEquals("BY EVA", rows.held);
        assertEquals("SURRENDERED", rows.stateHead);
        assertEquals("", rows.state);
        assertSame(NpColors.WHITE, rows.stateColor);
        b.state = "Disabled: wreck";
        rows.read(b, false);
        assertEquals("WRECK", rows.stateHead);
    }

    @Test
    void whyNoPartyCanGoTakesThePartyRow() {
        BoardingBoard b = raider(true);
        b.boardRefusal = "No EVA suits aboard to go across in";
        b.stormRefusal = "No EVA suits aboard to go across in";
        rows.read(b, false);
        assertEquals("NO EVA SUITS ABOARD TO GO ACROSS IN", rows.party);
        assertTrue(rows.partyWhy);
    }

    @Test
    void aFitShipLatchedCanBeStormed() {
        BoardingBoard b = raider(false);
        b.disabled = false;
        b.state = "Fit to fight";
        b.boardRefusal = "Raider 6 can still fight: disable it first, or storm it";
        rows.read(b, true);
        assertEquals("FIT TO FIGHT", rows.stateHead);
        assertSame(NpColors.AMBER, rows.stateColor);
        assertEquals("READY TO STORM", rows.party);
        assertFalse(rows.partyWhy);
    }

    @Test
    void thePartyCrossesThenFightsTheDefendersStillStanding() {
        BoardingBoard b = raider(false);
        b.boarding = true;
        b.state = "Boarding: 0 of ours aboard, 3 of theirs standing";
        rows.read(b, true);
        assertEquals("BEING BOARDED:", rows.stateHead);
        assertEquals("3 DEFENDERS", rows.state);
        assertEquals("CROSSING", rows.party);
        b.aboard = 2;
        b.defenders = 1;
        rows.read(b, true);
        assertEquals("1 DEFENDER", rows.state);
        assertEquals("2 ABOARD · FIGHTING", rows.party);
    }

    @Test
    void aBoardingWithNobodyLeftToSendSaysSo() {
        BoardingBoard b = raider(false);
        b.boarding = true;
        b.stormRefusal = "No one aboard to send";
        rows.read(b, true);
        assertEquals("NO ONE ABOARD TO SEND", rows.party);
        assertTrue(rows.partyWhy);
    }

    @Test
    void aShipTakenHoldsItsPrisonersAndItsPartsAndCargoAreOurs() {
        BoardingBoard b = raider(false);
        b.taken = b.salvage = true;
        b.state = "Taken, 2 held";
        b.salvageRefusal = "";
        b.aboard = 2;
        b.defenders = 0;
        b.prisoners = 2;
        part(b, true, true);
        part(b, true, false);
        part(b, true, false);
        part(b, false, false);
        cargo(b, "Food", 10);
        cargo(b, "Ore", 4);
        cargo(b, "Water", 0);
        rows.read(b, true);
        assertEquals("TAKEN:", rows.stateHead);
        assertEquals("2 HELD", rows.state);
        assertSame(NpColors.GREEN, rows.stateColor);
        assertEquals("2 ABOARD · HOLDING", rows.party);
        assertEquals("3 PARTS MARKED · 1 OFF", rows.salvage);
        assertEquals("2 LOTS COMING ACROSS", rows.cargo);
        assertTrue(rows.prisoners);
    }

    @Test
    void takenAndEmptiedTheRowsSayWhatIsLeft() {
        BoardingBoard b = raider(true);
        b.taken = b.salvage = true;
        b.state = "Taken";
        b.salvageRefusal = "Stand off alongside it to strip its hull on EVA";
        part(b, false, false);
        part(b, false, false);
        rows.read(b, false);
        assertEquals("TAKEN", rows.stateHead);
        assertEquals("", rows.state);
        assertEquals("", rows.party, "the party has come home");
        assertEquals("2 PARTS · NONE MARKED", rows.salvage);
        assertFalse(rows.prisoners, "no one is held");
        b.prisoners = 1;
        rows.read(b, false);
        assertFalse(rows.prisoners, "prisoners come aboard only across a dock");
        cargo(b, "Food", 0);
        rows.read(b, false);
        assertEquals("1 LOT TO TAKE", rows.cargo);
    }

    @Test
    void theNameIsCutToSixteen() {
        BoardingBoard b = raider(false);
        b.craftName = "Independent freighter 12";
        rows.read(b, true);
        assertEquals(16, rows.ship.length());
        assertTrue(rows.ship.endsWith("…"));
        rows.only("Raider 6", true);
        assertEquals("RAIDER 6", rows.ship);
        assertEquals("", rows.stateHead + rows.state + rows.party + rows.salvage + rows.cargo);
    }
}
