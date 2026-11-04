package com.ringsofiron.uihud.variants.nasapunk.stations;

/**
 * The six stations (functional-stations.md section 5): jobs, numbered as the station board and
 * the Alt keys number them. Each has its pages (section 9.2), in the order its soft keys show.
 */
enum Station {
    HELM("HELM"), NAV("NAV"), TAC("TAC"), ENG("ENG"), COMMS("COMMS"), CMD("CMD");

    /** Its name on the board and its tag in the ticker. */
    final String tag;
    private Page[] pages;

    Station(String tag) {
        this.tag = tag;
    }

    /** Its number on the board, 1 to 6: Alt and this picks it. */
    int number() {
        return ordinal() + 1;
    }

    /** Its pages, first to last. */
    Page[] pages() {
        if (pages == null) {
            int n = 0;
            for (Page p : Page.values()) if (p.station == this) n++;
            Page[] out = new Page[n];
            int i = 0;
            for (Page p : Page.values()) if (p.station == this) out[i++] = p;
            pages = out;
        }
        return pages;
    }

    /** The station numbered {@code n} (1 to 6), or null. */
    static Station numbered(int n) {
        Station[] all = values();
        return n >= 1 && n <= all.length ? all[n - 1] : null;
    }
}
