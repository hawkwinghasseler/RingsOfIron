package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * A full-screen mode (functional-context 4.3, 10): its frame, its tabs, and which of them the place
 * offers now. Each tab's key is its number in the title strip (the digits are the mode's while it
 * shows, when it has tabs), and the windows' own keys turn to their tab too (T, Y and J in the
 * station's terminal), through {@link #open}.
 */
abstract class Mode {
    final ModeFrame frame = new ModeFrame();
    /** Every tab it can have, in their order ({@link #tabs()}, read as it attaches). */
    ModeTab[] tabs = new ModeTab[0];
    /** The tabs offered now, in order, and the one showing. */
    final ModeTab[] offered = new ModeTab[ModeFrame.MAX_TABS];
    int count;
    ModeTab tab;
    Modes modes;

    Mode(boolean tabRow) {
        frame.tabRow = tabRow;
    }

    /** Every tab it can have, in their order. */
    abstract ModeTab[] tabs();

    void attach(Modes m) {
        modes = m;
        tabs = tabs();
        for (ModeTab t : tabs) t.modes = m;
    }

    /** Whether it can show at all now (the station's terminal only while docked). */
    boolean available(Boards b, HudData d) {
        return true;
    }

    /** The tab for {@code window}, or null when this mode doesn't serve it. */
    ModeTab tabFor(ClassicWindow window) {
        for (ModeTab t : tabs) if (t.window == window) return t;
        return null;
    }

    /** Whether the screen for {@code window} shows: the tab showing stands for it. */
    boolean showing(ClassicWindow window) {
        return tab != null && tab.window == window;
    }

    /**
     * Turns to the tab for {@code window} (or the first offered), which comes up from its start even
     * when it was the tab showing as the mode last closed; false when it can't show it.
     */
    boolean open(ClassicWindow window, Boards b, HudData d) {
        ModeTab t = tabFor(window);
        offer(b, d);
        if (t == null || !isOffered(t)) t = count > 0 ? offered[0] : null;
        if (t == null) return false;
        if (tab == t) t.shown();
        else turn(t);
        return true;
    }

    /** Reads which tabs the place offers; the tab showing moves to the first when it is no longer offered. */
    void offer(Boards b, HudData d) {
        count = 0;
        for (ModeTab t : tabs) if (count < offered.length && t.offered(b, d)) offered[count++] = t;
        if (tab != null && !isOffered(tab)) tab = null;
        if (tab == null && count > 0) turn(offered[0]);
    }

    boolean isOffered(ModeTab t) {
        for (int i = 0; i < count; i++) if (offered[i] == t) return true;
        return false;
    }

    void turn(ModeTab t) {
        if (tab == t) return;
        tab = t;
        t.shown();
    }

    /** The frame's title and subtitle, from the boards. */
    abstract void title(Boards b, HudData d);

    /** The keys that close it, printed in the foot before Esc: the tab's own key by default. */
    String closeKey() {
        return tab == null ? "" : tab.key;
    }

    /** Where its frame goes on a stage {@code w} x {@code h}: {@link ModeFrame#place}s the frame. */
    abstract void place(float w, float h);

    /** How wide the tab's content is: the frame's whole width, unless a rail takes the rest. */
    float contentWidth() {
        return frame.w;
    }

    void refresh(Boards b, HudData d) {
        offer(b, d);
        title(b, d);
        for (int i = 0; i < count; i++) offered[i].refresh(b, d);
    }

    void update(Boards b, HudData d, float delta) {
        if (tab != null) tab.update(b, d, delta);
    }

    /** The frame's tabs from the offered ones, and the content area for the tab showing. */
    void layout(float stageW, float stageH) {
        frame.tabCount = count;
        frame.active = -1;
        for (int i = 0; i < count; i++) {
            frame.tabs[i] = offered[i].name;
            if (offered[i] == tab) frame.active = i;
        }
        frame.closeKey = closeKey();
        place(stageW, stageH);
        if (tab != null) tab.place(frame.x, frame.top(), contentWidth(), frame.bottom() - frame.top());
    }

    void paint(Filament f, float time) {
        frame.paint(f);
        if (tab != null) tab.paint(f, time);
    }

    /** A key while it shows: a digit turns to that tab; the rest go to the tab. */
    boolean key(int keycode) {
        if (frame.tabRow && keycode >= Keys.NUM_1 && keycode <= Keys.NUM_9) {
            int i = keycode - Keys.NUM_1;
            if (i < count) turn(offered[i]);
            return true;
        }
        return tab != null && tab.key(keycode);
    }

    void boxes(FilamentActor a) {
        a.box(Modes.PLATE, frame.x, frame.y, frame.w, frame.h);
        frame.boxes(a);
        if (tab != null) tab.boxes(a);
    }

    /** A click on the frame's boxes (the tabs, the close keys) or the tab's. */
    void clicked(int id) {
        if (id >= ModeFrame.TAB && id < ModeFrame.TAB + count) {
            turn(offered[id - ModeFrame.TAB]);
        } else if (id == ModeFrame.CLOSE) {
            modes.closeAll();
        } else if (id >= Modes.TAB_IDS && tab != null) {
            tab.clicked(id);
        }
    }
}
