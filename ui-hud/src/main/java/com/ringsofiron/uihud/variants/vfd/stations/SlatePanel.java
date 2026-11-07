package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;

/**
 * A panel in the slate's housing ({@link SlateFrame}): the captain's own kit as a side panel, 480 wide
 * and 16 in from the right, down the screen on deck and over the band seated (style-vfd.md 18.4).
 */
abstract class SlatePanel extends Panel {
    final SlateFrame frame = new SlateFrame();

    @Override
    void layout(float stageW, float stageH, float floor) {
        x = Geo.panelX(stageW);
        y = Geo.PANEL_MARGIN;
        w = Geo.PANEL_W;
        h = floor - y;
        frame.count = Math.min(SlateFrame.MAX_TABS, pages.length);
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
        a.box(Panels.PLATE, x, y, w, h);
        frame.boxes(a);
        if (page != null) page.boxes(a);
    }

    @Override
    void clicked(int id) {
        if (id >= SlateFrame.TAB && id < SlateFrame.TAB + frame.count) {
            int i = id - SlateFrame.TAB;
            if (on[i]) turn(pages[i]);
        } else if (id == SlateFrame.CLOSE) {
            panels.closeAll();
        } else if (id >= Panels.PAGE_IDS && page != null) {
            page.clicked(id);
        }
    }
}
