package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * M4, boarding (functional-context 10.4; mockup 11.13): the whole screen under the rack while latched
 * to another ship or alongside it, titled with its name and how it stands. One tab, so no tab keys:
 * the digits stay the helm's. K opens and closes it.
 */
final class BoardingMode extends Mode {
    final BoardingTab tab0 = new BoardingTab();

    BoardingMode() {
        super(false);
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {tab0};
    }

    @Override
    boolean available(Boards b, HudData d) {
        return b.boarding.isOpen();
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = b.boarding.craftName.toUpperCase(Locale.ROOT);
        String state = b.boarding.state.toUpperCase(Locale.ROOT);
        frame.sub = (b.boarding.byEva ? "ALONGSIDE" : "LATCHED") + (state.isEmpty() ? "" : " · " + state);
    }

    @Override
    void place(float w, float h) {
        frame.place(ShipMode.MARGIN, ShipMode.Y, w - 2f * ShipMode.MARGIN, h - ShipMode.Y - ShipMode.MARGIN);
    }
}
