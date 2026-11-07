package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * One tab of a screen or panel (functional-task 7): a tab of the station terminal or the ship's book,
 * or the one page of a panel. Its name sits over its key (along the terminal's top edge, down the
 * book's left), the key's cap printing the game's key for it if it has one; it stands for one of the
 * game's windows, so the game's keys, Esc and its own checks treat the screen as that window while it
 * shows ({@link #standsFor}); and it has the screen's action keys while it shows. Its click ids start
 * at {@link Windows#TAB_IDS} and are its own.
 */
abstract class Tab {
    /** Its name over its key. */
    final String name;
    /** The window it stands for while it shows, and that opens it. */
    final ClassicWindow window;
    /** The cap of its key, the game's key that opens it ("T"), or "" for none. */
    final String cap;
    /**
     * The action keys it has while it shows: a slot each from the left, then the screen's CLOSE in the
     * last slot. The frame lays them out.
     */
    final SoftKeys keys = new SoftKeys();
    /** Where its list and its detail go on the screen. */
    final Place at = new Place();
    Windows windows;
    Screen screen;

    Tab(String name, ClassicWindow window, String cap) {
        this.name = name;
        this.window = window;
        this.cap = cap;
    }

    void attach(Windows w, Screen owner) {
        windows = w;
        screen = owner;
    }

    /** Whether the place offers it now: a tab not offered is a blank key over blank glass. */
    boolean offered(Boards b, HudData d) {
        return true;
    }

    /** Whether the screen stands for {@code w} while this tab shows: its own window. */
    boolean standsFor(ClassicWindow w) {
        return w == window;
    }

    /** Reads what it shows from the boards: a few times a second while it shows, and straight after each order. */
    void refresh(Boards b, HudData d) {
    }

    /** Each frame while it shows, before it is placed: what moves (the clock, the world). */
    void update(Boards b, HudData d, float delta) {
    }

    /** Lays it out where the frame puts its list and its detail. */
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

    /** Its click boxes, from {@link Windows#TAB_IDS}. */
    void boxes(FilamentActor a) {
    }

    /** A click on one of its boxes, by the id it gave. */
    void clicked(int id) {
    }

    /** It comes up: opened on its key, or turned to. */
    void shown() {
    }

    /**
     * Where a tab's content goes, as the frame lays out its glass (functional-task 7: a list on the left
     * and the selected item's detail on the right): the glass, the list's box, and the detail's box
     * beside it, each as left, top, right and bottom. The foot over the response line is the boxes'
     * bottom. A panel with no room beside its list puts the detail under it.
     */
    static final class Place {
        float gx, gy, gw, gh;
        float lx, ly, lr, lb;
        float dx, dy, dr, db;
        /** The book's LCD rather than the terminal's glass. */
        boolean lcd;

        void set(Place p) {
            gx = p.gx;
            gy = p.gy;
            gw = p.gw;
            gh = p.gh;
            lx = p.lx;
            ly = p.ly;
            lr = p.lr;
            lb = p.lb;
            dx = p.dx;
            dy = p.dy;
            dr = p.dr;
            db = p.db;
            lcd = p.lcd;
        }

        /** The list's width. */
        float lw() {
            return lr - lx;
        }

        /** The detail's width. */
        float dw() {
            return dr - dx;
        }
    }
}
