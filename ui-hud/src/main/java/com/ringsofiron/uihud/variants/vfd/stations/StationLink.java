package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The Station link (functional-stations 10.3; mockup st-link): one terminal for everything the station
 * berthed at offers, open anywhere aboard while docked, with the berth on its title row ("HIGHPORT",
 * its owner, the credits and the hold) and a page for each service: Market (T), Yard (Y), Shipyard,
 * the contract Board (J), Hiring and the Infirmary, each only where the station offers it, the rest
 * blank keys over blank glass. The Yard and the Shipyard take the screen's width (10.1).
 *
 * <p>Away from a yard, Y is the crew's field refit: the link opens on its Yard page alone, titled with
 * the ship, since no station is berthed at. With a prize in tow off a station that buys it, the
 * Shipyard alone.
 */
final class StationLink extends TerminalPanel {
    final MarketPage market = new MarketPage();
    final YardPage yard = new YardPage();
    final ShipyardPage shipyard = new ShipyardPage();
    final BoardPage board = new BoardPage();
    final HiringPage hiring = new HiringPage();
    final InfirmaryPage infirmary = new InfirmaryPage();

    @Override
    Page[] pages() {
        return new Page[] {market, yard, shipyard, board, hiring, infirmary};
    }

    /** Docked; or away from a station, on the crew's field refit or a prize in tow's terms, the pages offered there. */
    @Override
    boolean available(Boards b, HudData d) {
        return b.docked() || page == yard && b.yard.field || page == shipyard && !b.shipyard.prize.isEmpty();
    }

    /** Away from a station, Y opens the field refit and the shipyard's key a prize's terms. */
    @Override
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return b.docked() || w == ClassicWindow.YARD && b.yard.field || w == ClassicWindow.SHIPYARD && !b.shipyard.prize.isEmpty();
    }

    @Override
    void title(Boards b, HudData d) {
        boolean prize = !b.docked() && page == shipyard;
        frame.title = b.docked() ? b.station : prize ? prizeStation(b.shipyard.prize) : "FIELD REFIT";
        frame.owner = b.docked() ? b.owner : prize ? "PRIZE IN TOW" : d.ctx.ship == null ? "" : Glass.cut(shipName(b), 30);
        frame.figs = 0;
        fig("CREDITS", credits(b), "CR", 6);
        CargoHoldComponent hold = d.ctx.ship == null ? null : d.ctx.ship.getComponent(CargoHoldComponent.class);
        if (hold != null && hold.bayTileCount() > 0) {
            fig("HOLD", Glass.units(hold.usedVolume()).replace(",", ""), null, 3);
            fig("OF", Glass.units(hold.capacity()).replace(",", ""), "M3", 3);
        }
    }

    private void fig(String label, String value, String unit, int cells) {
        int i = frame.figs++;
        frame.figLabel[i] = label;
        frame.figValue[i] = value;
        frame.figUnit[i] = unit;
        frame.figCells[i] = cells;
    }

    /** The credits in hand, from whichever board has them, as whole credits for the seven segments. */
    static String credits(Boards b) {
        return String.format(Locale.ROOT, "%d", Math.round(creditsHeld(b)));
    }

    /** The credits in hand, from whichever board has them. */
    static double creditsHeld(Boards b) {
        return b.trade.isDocked() ? b.trade.credits : b.yard.credits;
    }

    /** "Raider 'Vex' (taken), off Highport" to "HIGHPORT". */
    static String prizeStation(String prize) {
        int at = prize.lastIndexOf(", off ");
        return at < 0 ? "SHIPYARD" : prize.substring(at + 6).trim().toUpperCase(Locale.ROOT);
    }

    static String shipName(Boards b) {
        return b.crew.shipName == null ? "" : b.crew.shipName.toUpperCase(Locale.ROOT);
    }
}
