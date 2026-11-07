package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * A screen or a panel (functional-task 7): its tabs, which of them the place offers now, the one
 * showing, and what the last press said. A tab opens on the game's key for it, through {@link #open},
 * and the screen stands for the window of the tab showing, so the game's keys toggle it, Esc closes it
 * and the game's own checks see it ({@link #showing}). Tab steps to the next tab offered and Shift+Tab
 * back, for the tabs with no key of their own; the rest of the keys go to the tab.
 */
abstract class Screen {
    /** How much of the HUD a screen covers while it shows. */
    enum Cover {
        /** The whole view under the band: zones 3 to 9 go, CHR and the band stay over it (7). */
        SCREEN,
        /** A panel down the right: the right column's widgets go, the rest of the view stays. */
        RIGHT,
        /** A panel beside something in the world: nothing else goes. */
        NONE
    }

    /** Every tab it has, in the order of their keys. */
    Tab[] tabs = new Tab[0];
    /** Which of them the place offers now. */
    boolean[] on = new boolean[0];
    Tab tab;
    Windows windows;
    /** Its housing on the stage, the plate's pad outside it. */
    float x, y, w, h;
    /** What the last press did, for the response line. */
    String response = "";

    abstract Tab[] tabs();

    void attach(Windows ws) {
        windows = ws;
        tabs = tabs();
        on = new boolean[tabs.length];
        for (Tab t : tabs) t.attach(ws, this);
    }

    /** Whether it can show at all now (the station terminal only while docked). */
    boolean available(Boards b, HudData d) {
        return true;
    }

    /** Whether {@code w} can open it now; by default whenever it can show. */
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return available(b, d);
    }

    /** The tab for {@code w}, or null when it has none. */
    Tab tabFor(ClassicWindow w, Boards b, HudData d) {
        for (Tab t : tabs) if (t.window == w) return t;
        return null;
    }

    /** Whether it shows the screen for {@code w}: the tab showing stands for it. */
    boolean showing(ClassicWindow w) {
        return tab != null && tab.standsFor(w);
    }

    /** Turns to the tab for {@code w}, or the first offered; false when it has nothing to show. */
    boolean open(ClassicWindow w, Boards b, HudData d) {
        offer(b, d);
        Tab t = tabFor(w, b, d);
        if (t == null || !isOffered(t)) t = first();
        if (t == null) return false;
        if (tab == t) t.shown();
        else turn(t);
        return true;
    }

    /** Reads which tabs the place offers; the tab showing moves to the first when it is no longer offered. */
    void offer(Boards b, HudData d) {
        for (int i = 0; i < tabs.length; i++) on[i] = tabs[i].offered(b, d);
        if (tab != null && !isOffered(tab)) tab = null;
        if (tab == null) {
            Tab t = first();
            if (t != null) turn(t);
        }
    }

    boolean isOffered(Tab t) {
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == t) return on[i];
        return false;
    }

    Tab first() {
        for (int i = 0; i < tabs.length; i++) if (on[i]) return tabs[i];
        return null;
    }

    int index(Tab t) {
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == t) return i;
        return -1;
    }

    void turn(Tab t) {
        if (tab == t) return;
        tab = t;
        response = "";
        t.shown();
    }

    /** Tab: the next tab offered, round to the first; {@code dir} -1 for the one before. */
    void step(int dir) {
        int at = index(tab), n = tabs.length;
        for (int k = 1; k <= n; k++) {
            int i = ((at + dir * k) % n + n) % n;
            if (on[i]) {
                turn(tabs[i]);
                return;
            }
        }
    }

    /** Reads the place again: the tabs offered, the header, and the tab showing. */
    void refresh(Boards b, HudData d) {
        offer(b, d);
        title(b, d);
        if (tab != null) tab.refresh(b, d);
    }

    void update(Boards b, HudData d, float delta) {
        if (tab != null) tab.update(b, d, delta);
    }

    /** Its header, from the boards. */
    abstract void title(Boards b, HudData d);

    /** Lays it out on a {@code stageW} x {@code stageH} stage. */
    abstract void layout(float stageW, float stageH);

    abstract void paint(Filament f, float time);

    /** The plate over the whole housing first, so a click that misses everything on it stays on it. */
    void boxes(FilamentActor a) {
        a.box(Windows.PLATE, x - Terminal.PAD, y - Terminal.PAD, w + 2f * Terminal.PAD, h + 2f * Terminal.PAD);
    }

    abstract void clicked(int id);

    /** A key while it shows: Tab steps the tabs, the rest go to the tab. */
    boolean key(int keycode) {
        if (keycode == Keys.TAB && tabs.length > 1) {
            boolean shift = Gdx.input != null && (Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT));
            step(shift ? -1 : 1);
            return true;
        }
        return tab != null && tab.key(keycode);
    }

    /** How much of the HUD it covers. */
    Cover cover() {
        return Cover.SCREEN;
    }
}
