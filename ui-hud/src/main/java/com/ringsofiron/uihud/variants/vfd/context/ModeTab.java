package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * One tab of a full-screen mode (functional-context 10): what it shows between the frame's title and
 * foot strips, its keys, and its clicks. A tab is placed in the frame's content area each frame and
 * paints from there; its click ids start at {@link Modes#TAB_IDS} and are its own.
 */
abstract class ModeTab {
    /** Its name in the frame's title strip. */
    final String name;
    /** The classic window it stands for: Screens asks whether that window shows ({@code showing}). */
    final ClassicWindow window;
    /** The key that opens it from anywhere, printed in the foot to close it, or "" for none. */
    final String key;
    /** The content area, from the frame. */
    float x, y, w, h;
    /** The modes, for its orders' answers and the boards; and the frame of the mode it is a tab of. */
    Modes modes;
    ModeFrame frame;

    ModeTab(String name, ClassicWindow window, String key) {
        this.name = name;
        this.window = window;
        this.key = key;
    }

    /** Whether the place offers it now; a tab not offered has no key and no title. */
    boolean offered(Boards b, HudData d) {
        return true;
    }

    /** Reads what it shows from the boards (a few times a second, and after each order). */
    void refresh(Boards b, HudData d) {
    }

    /** Each frame, before it is placed: data that moves (the clock, the world). */
    void update(Boards b, HudData d, float delta) {
    }

    /** How tall its content wants to be in a frame {@code w} wide, for a mode as tall as its tab; any height by default. */
    float wantedHeight(float w) {
        return -1f;
    }

    /** Lays it out in the frame's content area. */
    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    abstract void paint(Filament f, float time);

    /** A key while it shows: true when it used it. */
    boolean key(int keycode) {
        return false;
    }

    /** The wheel over it: {@code amount} pages, positive down. */
    void scrolled(int amount) {
    }

    /** Its click boxes, from {@link Modes#TAB_IDS}. */
    void boxes(FilamentActor a) {
    }

    /** A click on one of its boxes, by the id it gave. */
    void clicked(int id) {
    }

    /** It comes up: shown on its own key, or turned to. */
    void shown() {
    }
}
