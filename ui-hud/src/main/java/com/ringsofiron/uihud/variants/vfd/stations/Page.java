package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * One page of a panel (functional-stations 10.1): a tab of the Station link, the slate, the Plot or a
 * console panel. Its name sits beside its key down the panel's edge (or over its key along the
 * Plot's top), its key's cap prints the game's key for it, if it has one; it stands for one of the
 * game's windows, so the game's keys, Esc and its own checks treat the panel as that window while it
 * shows ({@link #standsFor}); and it has the panel's soft keys while it shows. Its click ids start at
 * {@link Panels#PAGE_IDS} and are its own.
 */
abstract class Page {
    /** Its name beside its key. */
    final String name;
    /** The window it stands for while it shows, and that opens it. */
    final ClassicWindow window;
    /** The cap of its key, the game's key that opens it ("T"), or "" for none. */
    final String cap;
    /**
     * The soft keys it has while it shows: its actions, a slot each from the left (it defines as many
     * slots as keys, and the frame sizes them), then the panel's CLOSE in the last slot.
     */
    final SoftKeys keys = new SoftKeys();
    /** Where its content goes in the panel's glass. */
    final Place at = new Place();
    Panels panels;
    Panel panel;

    Page(String name, ClassicWindow window, String cap) {
        this.name = name;
        this.window = window;
        this.cap = cap;
    }

    void attach(Panels p, Panel owner) {
        panels = p;
        panel = owner;
    }

    /** Whether the place offers it now: a page not offered is a blank key over blank glass. */
    boolean offered(Boards b, HudData d) {
        return true;
    }

    /** Whether it needs the screen's width (the Yard, the Shipyard: full screen, 10.1). */
    boolean wide() {
        return false;
    }

    /** Whether the panel stands for {@code w} while this page shows: its own window. */
    boolean standsFor(ClassicWindow w) {
        return w == window;
    }

    /** Reads what it shows from the boards: a few times a second while it shows, and straight after each order. */
    void refresh(Boards b, HudData d) {
    }

    /** Each frame while it shows, before it is placed: what moves (the clock, the world). */
    void update(Boards b, HudData d, float delta) {
    }

    /** Lays it out where the panel's frame puts its content. */
    void place(Place p) {
        at.set(p);
    }

    abstract void paint(Filament f, float time);

    /** A key while it shows: true when it used it. */
    boolean key(int keycode) {
        return false;
    }

    /** The wheel over it: {@code amount} pages, positive down. */
    void scrolled(int amount) {
    }

    /** Its click boxes, from {@link Panels#PAGE_IDS}. */
    void boxes(FilamentActor a) {
    }

    /** A click on one of its boxes, by the id it gave. */
    void clicked(int id) {
    }

    /** It comes up: opened on its key, or turned to. */
    void shown() {
    }

    /**
     * Where a page's content goes, as the panel's frame lays out its glass: the glass, its margins, the
     * content beside the page keys' names (its left and top), the row under the names from which the
     * glass's whole width is the page's, and the content's bottom over the response line.
     */
    static final class Place {
        float gx, gy, gw, gh;
        float x0, x1;
        float left, top, split, bottom;
        /** The slate's LCD rather than the terminals' glass. */
        boolean lcd;

        void set(Place p) {
            gx = p.gx;
            gy = p.gy;
            gw = p.gw;
            gh = p.gh;
            x0 = p.x0;
            x1 = p.x1;
            left = p.left;
            top = p.top;
            split = p.split;
            bottom = p.bottom;
            lcd = p.lcd;
        }
    }
}
