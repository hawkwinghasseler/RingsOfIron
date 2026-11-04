package com.ringsofiron.uihud.variants.cassette.stations;

/** What a press on one unit asks of the others: the HUD answers these. */
interface Actions {
    /** The master lamps: stop the flashing; the tiles stay while their conditions hold (7.1). */
    void acknowledge();

    /** A tile or a callout: its home page, on the right MFD when seated (13.2). */
    void show(Page page);

    /** A station board tile: that station's first page on the right MFD, again its next (8.5). */
    void station(Station s);
}
