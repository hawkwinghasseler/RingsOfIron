package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The station terminal (functional-task 7, TRM; mockup tk-terminal): one terminal for everything the
 * station docked at offers, across the screen under the band, its tabs on the keys along its top edge:
 * Contracts (J), Market (T), Yard (Y), Shipyard and Crew (I), each only where the station offers it, a
 * blank key in its fixed home where it doesn't. The header row is the berth: the station, its owner
 * and how they stand to the player, then the standing, the credits, the hold and the contracts held.
 *
 * <p>Away from a station, Y keeps the game's two uses as a terminal with only the tab that applies: the
 * crew's field refit on the Yard tab, or handing over a prize in tow on the Shipyard tab.
 */
final class StationTerminal extends TerminalScreen {
    /** The contracts a captain may hold at once ({@code ContractBoard.MAX_ACTIVE}). */
    static final int MAX_HELD = 3;

    final OffersTab offers = new OffersTab();
    final MarketTab market = new MarketTab();
    final YardTab yard = new YardTab();
    final ShipyardTab shipyard = new ShipyardTab();
    final HireTab hire = new HireTab();

    @Override
    Tab[] tabs() {
        return new Tab[] {offers, market, yard, shipyard, hire};
    }

    /** Docked; or away from a station, on the crew's field refit or a prize in tow's terms, the tab offered there. */
    @Override
    boolean available(Boards b, HudData d) {
        return b.docked() || tab == yard && b.yard.field || tab == shipyard && !b.shipyard.prize.isEmpty();
    }

    /** Away from a station, Y opens the field refit and a prize's terms. */
    @Override
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return b.docked() || w == ClassicWindow.YARD && b.yard.field || w == ClassicWindow.SHIPYARD && !b.shipyard.prize.isEmpty();
    }

    /** Whether it has its Crew tab here, for I: the station hires. */
    boolean hires(Boards b, HudData d) {
        return hire.offered(b, d);
    }

    @Override
    void title(Boards b, HudData d) {
        boolean prize = !b.docked() && tab == shipyard;
        frame.title = b.docked() ? b.station : prize ? prizeStation(b.shipyard.prize) : "FIELD REFIT";
        frame.owner = b.docked() ? owner(b, d) : prize ? "PRIZE IN TOW" : shipName(b);
        frame.noFigures();
        CargoHoldComponent hold = d.ctx.ship == null ? null : d.ctx.ship.getComponent(CargoHoldComponent.class);
        // From the right: the contracts held, the hold, the credits, the standing
        if (b.docked()) frame.figure("CONTRACTS", Integer.toString(b.missions.held.size), 1, null, Integer.toString(MAX_HELD), 1);
        if (hold != null && hold.bayTileCount() > 0) {
            frame.figure("HOLD", whole(hold.usedVolume()), 3, "M3", whole(hold.capacity()), 3);
        }
        // Seven segments have no comma: the credits in plain digits
        frame.figure("CREDITS", whole(credits(b)), 7, "CR", null, 0);
        if (b.docked() && !b.standing.isEmpty()) frame.figure("STANDING", b.standing, 3, null, null, 0);
    }

    /** "TERRAN PATROL AUTHORITY · YOUR SPONSOR": the owner and how they stand to the player. */
    static String owner(Boards b, HudData d) {
        String o = b.owner;
        var st = d.navReadout.docked;
        String rel = st.present && st.sponsor ? "YOUR SPONSOR" : st.present && !st.standingName.isEmpty() ? st.standingName.toUpperCase(Locale.ROOT) : "";
        if (o.isEmpty()) return rel;
        return rel.isEmpty() ? o : o + " · " + rel;
    }

    /** A volume in whole cubic metres for the seven segments. */
    static String whole(double v) {
        return String.format(Locale.ROOT, "%d", Math.round(v));
    }

    /** The credits in hand, from whichever board has them. */
    static double credits(Boards b) {
        return b.trade.isDocked() ? b.trade.credits : b.yard.credits;
    }

    /** "Raider (taken), off Highport" to "HIGHPORT". */
    static String prizeStation(String prize) {
        int at = prize.lastIndexOf(", off ");
        return at < 0 ? "SHIPYARD" : prize.substring(at + 6).trim().toUpperCase(Locale.ROOT);
    }

    static String shipName(Boards b) {
        return b.crew.shipName == null ? "" : b.crew.shipName.toUpperCase(Locale.ROOT);
    }
}
