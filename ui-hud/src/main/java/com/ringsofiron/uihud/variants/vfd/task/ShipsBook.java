package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The ship's book (functional-task 7, BOOK; mockup tk-book): the captain's own records on an LCD slate
 * across the screen under the band, its tabs on the keys down its left edge: Ship, Crew (I), Contracts
 * (J away from a station's board), Career and Log (L). The silk names the ship and its sponsor.
 *
 * <p>I opens it on the Crew and closes it from any tab, so every tab stands for the crew roster too; J
 * and L turn it to the Contracts and the Log and close it from there.
 */
final class ShipsBook extends BookScreen {
    final ShipTab ship = new ShipTab();
    final CrewTab crew = new CrewTab();
    final HeldTab held = new HeldTab();
    final CareerTab career = new CareerTab();
    final LogTab log = new LogTab();

    @Override
    Tab[] tabs() {
        return new Tab[] {ship, crew, held, career, log};
    }

    /** I opens it on the Crew. */
    @Override
    Tab tabFor(ClassicWindow w, Boards b, HudData d) {
        if (w == ClassicWindow.CREW) return crew;
        return super.tabFor(w, b, d);
    }

    /** Every tab stands for the roster, so I closes the book from any of them. */
    @Override
    boolean showing(ClassicWindow w) {
        return tab != null && (w == ClassicWindow.CREW || tab.standsFor(w));
    }

    @Override
    void title(Boards b, HudData d) {
        frame.brand = "SHIP'S BOOK";
        String ship = StationTerminal.shipName(b);
        String sponsor = b.career.present ? b.career.sponsor.toUpperCase(Locale.ROOT) : "";
        frame.owner = ship.isEmpty() ? sponsor : sponsor.isEmpty() ? ship : ship + " · " + sponsor;
    }
}
