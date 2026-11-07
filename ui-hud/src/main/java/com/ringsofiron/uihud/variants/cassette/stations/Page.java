package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Alert;

import java.util.Locale;

/** The MFD pages (spec 9.2), each one station's, named on its soft key in full. */
enum Page {
    HELM(Station.HELM),
    DOCK(Station.HELM),
    NAV(Station.NAV),
    PLAN(Station.NAV),
    SENSORS(Station.TAC),
    WEAPONS(Station.TAC),
    DEFENSE(Station.TAC),
    DAMAGE(Station.ENG),
    POWER(Station.ENG),
    ATMOS(Station.ENG),
    COMMS(Station.COMMS),
    CREW(Station.CMD),
    CONTRACTS(Station.CMD);

    final Station station;

    Page(Station station) {
        this.station = station;
    }

    /** The soft key's legend: the page's full name. */
    String label() {
        return name();
    }

    /**
     * Where an alert is acted on and read (13.2's home page), what auto-paging turns an MFD to.
     * {@code emitter}: the hull has a shield emitter, so DEFENSE has the shields to show.
     */
    static Page home(Alert a, boolean emitter, String disabledReason) {
        String id = a.id;
        if (id.equals("missile")) return emitter ? DEFENSE : SENSORS;
        if (id.equals("hostile")) return SENSORS;
        if (id.startsWith("shield:")) return DEFENSE;
        if (id.startsWith("empty:")) return WEAPONS;
        if (id.startsWith("fire:") || id.startsWith("breach:") || id.startsWith("air:") || id.startsWith("smoke:") || id.equals("hull")) return DAMAGE;
        if (id.startsWith("sys:")) return POWER;
        if (id.equals("oxygen")) return ATMOS;
        if (id.equals("propellant")) return PLAN;
        if (id.equals("helm")) return HELM;
        if (id.equals("disabled")) {
            String r = disabledReason.toLowerCase(Locale.ROOT);
            if (r.contains("hull")) return DAMAGE;
            if (r.contains("crew")) return CREW;
            return POWER;
        }
        return CREW;
    }
}
