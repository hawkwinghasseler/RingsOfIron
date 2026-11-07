package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The slate (functional-stations 10.4; mockup st-slate): the captain's own panel, anywhere aboard, its
 * title row the captain's rank, the ship's name and the credits. Ship, Crew, Medical, EVA, Boarding,
 * Prisoners, Contracts, Career and the Log: Boarding only while a ship is latched or boarded, the
 * Prisoners only while some are held or alongside, the rest always.
 *
 * <p>I opens it at its Crew and closes it from any page, so every page stands for the crew roster too;
 * K turns it to Boarding, J away from a station's board to the Contracts, and L to the Log, and each
 * closes it from there.
 */
final class CaptainSlate extends SlatePanel {
    final ShipPage ship = new ShipPage();
    final CrewPage crew = new CrewPage();
    final MedicalPage medical = new MedicalPage();
    final EvaPage eva = new EvaPage();
    final BoardingPage boarding = new BoardingPage();
    final PrisonersPage prisoners = new PrisonersPage();
    final ContractsPage contracts = new ContractsPage();
    final CareerPage career = new CareerPage();
    final LogPage log = new LogPage();

    @Override
    Page[] pages() {
        return new Page[] {ship, crew, medical, eva, boarding, prisoners, contracts, career, log};
    }

    /** I opens it at the Crew. */
    @Override
    Page pageFor(ClassicWindow w, Boards b, HudData d) {
        if (w == ClassicWindow.CREW) return crew;
        return super.pageFor(w, b, d);
    }

    /** Every page stands for the roster, so I closes the slate from any of them. */
    @Override
    boolean showing(ClassicWindow w) {
        return page != null && (w == ClassicWindow.CREW || page.standsFor(w));
    }

    @Override
    void title(Boards b, HudData d) {
        String rank = b.career.present ? b.career.rank.toUpperCase(Locale.ROOT) : "";
        String ship = StationLink.shipName(b);
        frame.title = rank.isEmpty() ? ship : ship.isEmpty() ? rank : rank + " · " + ship;
        frame.right = Glass.thousands(StationLink.creditsHeld(b)) + " CR";
    }
}
