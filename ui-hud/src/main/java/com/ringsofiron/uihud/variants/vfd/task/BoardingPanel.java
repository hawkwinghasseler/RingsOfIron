package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The boarding panel (functional-task 7, BRD; mockup tk-boarding): a terminal slab down the right side
 * under the band, so the deck or the latched ship stays in sight beside it while the boarders work. K
 * opens and closes it, as Esc does; it closes itself when the ships part. Only the right column's
 * widgets give way to it.
 */
final class BoardingPanel extends TerminalScreen {
    /** The detail's room under the list. */
    static final float DETAIL = 5f * SectionTab.LINE + 24f;

    final BoardingTab board = new BoardingTab();

    BoardingPanel() {
        frame.closeCap = "K";
        frame.keySlots = 4;
        frame.under = DETAIL;
        frame.titleH = 14f;
    }

    @Override
    Tab[] tabs() {
        return new Tab[] {board};
    }

    @Override
    boolean available(Boards b, HudData d) {
        return b.boarding.isOpen();
    }

    @Override
    void bounds(float stageW, float stageH) {
        w = Geo.BRD_W;
        x = stageW - Geo.SCREEN_X - w;
        y = Geo.SCREEN_Y;
        h = stageH - Geo.BRD_FOOT - y;
    }

    @Override
    boolean split() {
        return false;
    }

    @Override
    Cover cover() {
        return Cover.RIGHT;
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = b.boarding.craftName.isEmpty() ? "BOARDING" : b.boarding.craftName.toUpperCase(Locale.ROOT);
        frame.owner = "BOARDING";
        frame.noFigures();
    }
}
