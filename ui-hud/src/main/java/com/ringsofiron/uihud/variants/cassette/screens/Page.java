package com.ringsofiron.uihud.variants.cassette.screens;

import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * One tab of a big console: the market on the station's, the crew on the ship's. It shows in place
 * of a classic window ({@link #window}), reads that window's provider and acts through it. The
 * console asks it to {@link #refresh} a few times a second while it shows and after each order, and
 * to {@link #draw} each frame into the body the console leaves it.
 */
abstract class Page implements Hits.Target {
    /** The tab's engraved legend: its spec's word for it, which a console's arrangement may set. */
    String label;
    /** The page's tag in the feed tube, five letters at most: TRADE, YARD, JOBS. */
    final String tag;
    /** The classic window it stands for: its key opens it, and the game's word on when it can open holds. */
    final ClassicWindow window;
    /** The console it is a tab of, set when it is added. */
    BigConsole console;

    Page(String label, String tag, ClassicWindow window) {
        this.label = label;
        this.tag = tag;
        this.window = window;
    }

    /** Whether the place offers it now (a shipyard only where ships are sold), as of the last refresh. */
    abstract boolean offered();

    /** Reads the boards again: they were just read from the providers. */
    abstract void refresh(Boards b);

    /** Paints the page into the body: console units, (x, y) its top left. */
    abstract void draw(YardPaint p, Hits hits, float x, float y, float w, float h);

    /** A key while the page shows: true when it used it. */
    boolean key(int keycode) {
        return false;
    }

    /** The wheel over the console: positive to scroll down. */
    void scroll(float amount) {
    }

    /**
     * Whether it can draw in a side panel's body, about 480 wide (the Stations spec's panels); else a
     * side panel on its tab draws across the screen, as the big console does.
     */
    boolean fitsSide() {
        return false;
    }

    /**
     * The key that opens this tab, for a side panel's foot, or null when its window's key opens
     * another tab now (Station link's hiring hall: I opens the slate's crew).
     */
    String windowKey() {
        return WindowScreens.keyOf(window);
    }

    /**
     * What Enter does on the page now, for a console panel's foot ("SWITCH OFF"), or null when it does
     * nothing.
     */
    String enterLabel() {
        return null;
    }

    /** Whether something on the page waits for the player: the amber jewel on its key. */
    boolean jewel() {
        return false;
    }

    /** The tab came up. */
    void shown() {
    }

    /** What the page says in the foot's feed tube, after an order: the provider's answer. */
    void say(String text) {
        if (console != null) console.say(tag, text);
    }

    @Override
    public void hit(int code) {
    }
}
