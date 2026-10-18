package com.ringsofiron.uihud.widgets;

import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeWindowTest {

    private static TradeBoard board(double credits, double used, double capacity) {
        TradeBoard b = new TradeBoard();
        b.stationName = "Highport";
        b.hasHold = true;
        b.credits = credits;
        b.cargoUsed = used;
        b.cargoCapacity = capacity;
        return b;
    }

    private static TradeLine line(TradeBoard b, double available, double held, boolean stationBuys) {
        TradeLine l = b.addLine();
        l.commodityId = "water";
        l.available = available;
        l.held = held;
        l.stationBuys = stationBuys;
        l.buyPrice = 12;
        return l;
    }

    @Test
    void buyingNeedsStockRoomCreditsAndAHold() {
        TradeBoard b = board(100, 0, 50);
        assertTrue(TradeWindow.canBuy(b, line(b, 10, 0, true)));
        assertFalse(TradeWindow.canBuy(b, line(b, 0, 0, true)));
        assertFalse(TradeWindow.canBuy(board(0, 0, 50), line(b, 10, 0, true)));
        assertFalse(TradeWindow.canBuy(board(100, 50, 50), line(b, 10, 0, true)));
        TradeBoard noHold = board(100, 0, 50);
        noHold.hasHold = false;
        assertFalse(TradeWindow.canBuy(noHold, line(noHold, 10, 0, true)));
    }

    @Test
    void sellingNeedsCargoAndAStationThatBuys() {
        TradeBoard b = board(0, 5, 50);
        assertTrue(TradeWindow.canSell(line(b, 0, 5, true)));
        assertFalse(TradeWindow.canSell(line(b, 0, 5, false)));
        assertFalse(TradeWindow.canSell(line(b, 0, 0, true)));
    }

    @Test
    void boardClearsBackToUndocked() {
        TradeBoard b = board(100, 10, 50);
        line(b, 1, 1, true);
        assertTrue(b.isDocked());
        assertEquals(40, b.freeCargo(), 1e-9);
        b.clear();
        assertFalse(b.isDocked());
        assertEquals(0, b.lines.size);
    }

    @Test
    void formatsMoneyAndUnits() {
        assertEquals("4,456 cr", TradeWindow.formatCredits(4456.2));
        assertEquals("12.6", TradeWindow.formatPrice(12.6));
        assertEquals("630", TradeWindow.formatPrice(630.4));
        assertEquals("10", TradeWindow.formatUnits(10.01));
        assertEquals("2.5", TradeWindow.formatUnits(2.5));
    }
}
