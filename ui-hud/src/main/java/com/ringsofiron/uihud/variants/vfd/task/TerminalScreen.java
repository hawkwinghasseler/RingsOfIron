package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;

/**
 * A screen in a terminal's housing ({@link Terminal}): the station terminal across the screen under
 * the band (functional-task 7, TRM), its tabs on the keys along its top edge.
 */
abstract class TerminalScreen extends Screen {
    final Terminal frame = new Terminal();

    /** Where it goes on a {@code stageW} x {@code stageH} stage: across it under the band, by default. */
    void bounds(float stageW, float stageH) {
        x = Geo.SCREEN_X;
        y = Geo.SCREEN_Y;
        w = stageW - 2f * Geo.SCREEN_X;
        h = stageH - Geo.SCREEN_FOOT - y;
    }

    /** Whether its tab has a detail beside its list. */
    boolean split() {
        return true;
    }

    @Override
    void layout(float stageW, float stageH) {
        bounds(stageW, stageH);
        frame.count = Math.min(Terminal.MAX_TABS, tabs.length > 1 ? tabs.length : 0);
        for (int i = 0; i < frame.count; i++) {
            frame.names[i] = on[i] ? tabs[i].name : null;
            frame.caps[i] = tabs[i].cap;
        }
        frame.active = index(tab);
        frame.keys = tab == null ? null : tab.keys;
        frame.response = response;
        frame.split = split();
        frame.place(x, y, w, h);
        if (tab != null) tab.place(frame.at);
    }

    @Override
    void paint(Filament f, float time) {
        frame.paint(f);
        if (tab != null) tab.paint(f, time);
    }

    @Override
    void boxes(FilamentActor a) {
        super.boxes(a);
        frame.boxes(a);
        if (tab != null) tab.boxes(a);
    }

    @Override
    void clicked(int id) {
        if (id >= Terminal.TAB && id < Terminal.TAB + frame.count) {
            int i = id - Terminal.TAB;
            if (on[i]) turn(tabs[i]);
        } else if (id == Terminal.CLOSE) {
            windows.closeAll();
        } else if (id >= Windows.TAB_IDS && tab != null) {
            tab.clicked(id);
        }
    }
}
