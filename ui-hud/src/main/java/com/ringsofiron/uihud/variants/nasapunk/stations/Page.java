package com.ringsofiron.uihud.variants.nasapunk.stations;

/**
 * An MFD page (functional-stations.md section 9.2), named on its soft key in full. CARGO is left
 * out: the shared data has nothing of the hold to show.
 */
enum Page {
    HELM(Station.HELM, "HELM"),
    DOCK(Station.HELM, "DOCK"),
    NAV(Station.NAV, "NAV"),
    PLAN(Station.NAV, "PLAN"),
    SENSORS(Station.TAC, "SENSORS"),
    WEAPONS(Station.TAC, "WEAPONS"),
    DEFENSE(Station.TAC, "DEFENSE"),
    DAMAGE(Station.ENG, "DAMAGE"),
    POWER(Station.ENG, "POWER"),
    ATMOS(Station.ENG, "ATMOS"),
    COMMS(Station.COMMS, "COMMS"),
    CREW(Station.CMD, "CREW"),
    CONTRACTS(Station.CMD, "CONTRACTS");

    final Station station;
    /** Its name, on its soft key and over its glass. */
    final String title;

    Page(Station station, String title) {
        this.station = station;
        this.title = title;
    }

    /** The station's page after this one, round to the first. */
    Page next() {
        Page[] all = station.pages();
        for (int i = 0; i < all.length; i++) if (all[i] == this) return all[(i + 1) % all.length];
        return all[0];
    }
}
