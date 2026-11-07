package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;

/**
 * A panel in a terminal's housing ({@link Terminal}): the Station link and the console panels at the
 * side, 480 wide and 16 in from the right, on deck down the screen and seated over the band; a page
 * that needs the room ({@link Page#wide}) takes the screen's width under the top column instead, as
 * the Yard and the Shipyard do (10.1: full screen).
 */
abstract class TerminalPanel extends Panel {
    final Terminal frame = new Terminal();

    /** Its housing when no page needs the screen: a side panel. */
    Terminal.Kind kind() {
        return Terminal.Kind.SIDE;
    }

    @Override
    boolean fullScreen() {
        return page != null && page.wide();
    }

    @Override
    void layout(float stageW, float stageH, float floor) {
        frame.kind = fullScreen() ? Terminal.Kind.WIDE : kind();
        if (frame.kind == Terminal.Kind.WIDE) {
            x = Geo.PANEL_MARGIN + Terminal.PAD;
            y = Geo.WIDE_Y + Terminal.PAD;
            w = stageW - 2f * x;
            h = stageH - Geo.PANEL_MARGIN - Terminal.PAD - y;
        } else {
            x = Geo.panelX(stageW);
            y = Geo.PANEL_MARGIN;
            w = Geo.PANEL_W;
            h = floor - Terminal.PAD - y;
        }
        frame.count = Math.min(Terminal.MAX_PAGES, pages.length);
        for (int i = 0; i < frame.count; i++) {
            frame.names[i] = on[i] ? pages[i].name : null;
            frame.caps[i] = pages[i].cap;
        }
        frame.active = index(page);
        frame.keys = page == null ? null : page.keys;
        frame.response = response;
        frame.place(x, y, w, h);
        if (page != null) page.place(frame.at);
    }

    @Override
    void paint(Filament f, float time) {
        frame.paint(f);
        if (page != null) page.paint(f, time);
    }

    @Override
    void boxes(FilamentActor a) {
        super.boxes(a);
        frame.boxes(a);
        if (page != null) page.boxes(a);
    }

    @Override
    void clicked(int id) {
        if (id >= Terminal.PAGE && id < Terminal.PAGE + frame.count) {
            int i = id - Terminal.PAGE;
            if (on[i]) turn(pages[i]);
        } else if (id == Terminal.CLOSE) {
            panels.closeAll();
        } else if (id >= Panels.PAGE_IDS && page != null) {
            page.clicked(id);
        }
    }
}
