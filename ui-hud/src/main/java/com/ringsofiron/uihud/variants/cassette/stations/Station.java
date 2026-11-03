package com.ringsofiron.uihud.variants.cassette.stations;

import com.ringsofiron.uihud.data.AlertRegistry.Alert;

/**
 * The six stations of the stations spec (section 5), numbered as on the station board (8.5): each a job,
 * with the MFD pages it owns (9.2) and the alerts it answers for (13.4).
 */
enum Station {
    HELM(1, "HELM"),
    NAV(2, "NAV"),
    TAC(3, "TAC"),
    ENG(4, "ENG"),
    COMMS(5, "COMMS"),
    CMD(6, "CMD");

    final int number;
    /** The board's and the ticker's tag. */
    final String tag;

    Station(int number, String tag) {
        this.number = number;
        this.tag = tag;
    }

    /** This station's pages, in soft-key order. */
    Page[] pages() {
        switch (this) {
            case HELM: return new Page[]{Page.HELM, Page.DOCK};
            case NAV: return new Page[]{Page.NAV, Page.PLAN};
            case TAC: return new Page[]{Page.SENSORS, Page.WEAPONS, Page.DEFENSE};
            case ENG: return new Page[]{Page.DAMAGE, Page.POWER, Page.ATMOS};
            // CARGO needs the hold's manifest, which the shared data does not carry yet
            case COMMS: return new Page[]{Page.COMMS};
            default: return new Page[]{Page.CREW, Page.CONTRACTS};
        }
    }

    /** The station an alert belongs to (13.4's Station column). */
    static Station of(Alert a) {
        String id = a.id;
        if (id.equals("missile") || id.equals("hostile") || id.startsWith("shield:") || id.startsWith("empty:")) return TAC;
        if (id.equals("helm")) return HELM;
        if (id.equals("propellant")) return NAV;
        if (id.startsWith("casualty:") || id.startsWith("captain:") || id.equals("abandon") || id.equals("disabled") || id.equals("eva")) return CMD;
        if (id.startsWith("sys:TURRET") || id.startsWith("sys:WEAPONS") || id.startsWith("sys:SHIELDS")) return TAC;
        if (id.startsWith("sys:HELM") || id.startsWith("sys:ENGINE")) return HELM;
        if (id.startsWith("sys:DOCK PORT")) return COMMS;
        return ENG;
    }

    /** The station a notice's speaker names ("Helm", "TAC"), or null. */
    static Station byTag(String who) {
        String w = who.toUpperCase(java.util.Locale.ROOT);
        for (Station s : values()) if (s.tag.equals(w)) return s;
        switch (w) {
            case "NAVIGATION": return NAV;
            case "TACTICAL": return TAC;
            case "ENGINEERING": return ENG;
            case "COMMAND": return CMD;
            default: return null;
        }
    }

    static Station byNumber(int n) {
        for (Station s : values()) if (s.number == n) return s;
        return null;
    }
}
