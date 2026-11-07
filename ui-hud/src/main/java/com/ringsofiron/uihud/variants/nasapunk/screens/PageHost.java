package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;

/** What a page's orders reach through: the data, the screen's feed line, and the game's windows. */
public interface PageHost {
    HudData data();

    /** Shows what an order came to on the screen's feed line, under {@code source} (STATION, YARD, CREW). */
    void say(String source, String text);

    /** Reads the boards again now and rebuilds the pages, after an order. */
    void reread();

    /** Whether the game would open {@code window} now. */
    boolean available(ClassicWindow window);

    /** Opens the game's {@code window}, through the game so its rules hold; true when it shows. */
    boolean open(ClassicWindow window);

    /** Closes the screen the page is on: a furnishing is being placed, or the ship editor opened. */
    void closeScreen();
}
