package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;

/**
 * A screen in the book's housing ({@link BookFrame}): the captain's LCD slate across the screen under
 * the band (functional-task 7, BOOK), its tabs on the keys down its left edge.
 */
abstract class BookScreen extends Screen {
    final BookFrame frame = new BookFrame();

    @Override
    void layout(float stageW, float stageH) {
        x = Geo.SCREEN_X;
        y = Geo.SCREEN_Y;
        w = stageW - 2f * Geo.SCREEN_X;
        h = stageH - Geo.SCREEN_FOOT - y;
        frame.count = Math.min(BookFrame.MAX_TABS, tabs.length);
        for (int i = 0; i < frame.count; i++) {
            frame.names[i] = on[i] ? tabs[i].name : null;
            frame.caps[i] = tabs[i].cap;
        }
        frame.active = index(tab);
        frame.keys = tab == null ? null : tab.keys;
        frame.response = response;
        frame.split = tab == null || split(tab);
        frame.place(x, y, w, h);
        if (tab != null) tab.place(frame.at);
    }

    /** Whether {@code t} has a detail beside its list. */
    boolean split(Tab t) {
        return true;
    }

    @Override
    void paint(Filament f, float time) {
        frame.paint(f);
        if (tab != null) tab.paint(f, time);
    }

    @Override
    void boxes(FilamentActor a) {
        a.box(Windows.PLATE, x, y, w, h);
        frame.boxes(a);
        if (tab != null) tab.boxes(a);
    }

    @Override
    void clicked(int id) {
        if (id >= BookFrame.TAB && id < BookFrame.TAB + frame.count) {
            int i = id - BookFrame.TAB;
            if (on[i]) turn(tabs[i]);
        } else if (id == BookFrame.CLOSE) {
            windows.closeAll();
        } else if (id >= Windows.TAB_IDS && tab != null) {
            tab.clicked(id);
        }
    }
}
