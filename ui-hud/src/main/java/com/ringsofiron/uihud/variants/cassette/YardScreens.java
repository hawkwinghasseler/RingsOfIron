package com.ringsofiron.uihud.variants.cassette;

import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.cassette.screens.WindowScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * The Yard Standard's screens that the three Cassette HUDs share (style guide, section 9): the game's
 * menus in a yard office at night ({@link YardShellLook}), the map on a phosphor tube
 * ({@link YardMapPainter}) with the console's bays round it, and the game's windows as tabs of big
 * consoles ({@link WindowScreens}). A HUD keeps one and hands them on from its own hooks:
 * {@code shellLook()}, {@code mapPainter()}, {@code attach}, {@code update}, {@code resize},
 * {@code detach} and the screen hooks ({@code replaces}, {@code open}, {@code showing}, {@code close},
 * {@code keyDown}). It makes nothing until asked, so a HUD made only to be asked for its menus' look
 * costs nothing more.
 */
public final class YardScreens {
    private YardPaint paint;
    private YardMapPainter painter;
    private final WindowScreens windows = new WindowScreens();
    private float uiScale = -1f;

    /** The menus' look, new for each asking, as the game wants it; needs GL. */
    public ShellLook shellLook() {
        return new YardShellLook();
    }

    /**
     * Has the controls card rise over the screen under it, the map or a console on its tab, with Esc
     * or F1 closing it first (the Task spec); else it replaces the screen, one mode at a time.
     */
    public YardScreens controlsOver(boolean over) {
        windows.controlsOver(over);
        return this;
    }

    /**
     * Has the Log keep each condition on one line, revised as it worsens and stamped when it clears,
     * with filters for alerts, important news and the rest (the Task spec's book); else a line for
     * each step and each clearing, by severity and source (the Context spec's Log).
     */
    public YardScreens onceLog(boolean once) {
        windows.onceLog(once);
        return this;
    }

    /**
     * Has the windows be the Stations spec's panels: the Station link and the slate as side panels 480
     * wide at the right, the band and the world left in view, the yard's tabs across the screen.
     */
    public YardScreens panels(boolean on) {
        windows.panels(on);
        return this;
    }

    /**
     * Has the boarding console hang down the right side, the deck or the outside view in sight beside
     * it (the Task spec's BRD), the other windows across the screen.
     */
    public YardScreens sideBoarding(boolean on) {
        windows.sideBoarding(on);
        return this;
    }

    /** Where a side panel stops, from the top in design units: above the seated band, or 0 for 16 from the foot. */
    public void panelBottom(float y) {
        windows.panelBottom(y);
    }

    /** Where a side panel starts, from the top in design units: 16 unless the HUD keeps more above it. */
    public void panelTop(float y) {
        windows.panelTop(y);
    }

    /** Whether a side panel hangs at the right now, the band and the world beside it. */
    public boolean sideUp() {
        return windows.sideUp();
    }

    /** The left edge of the side panel up now, in design units. */
    public float sideLeft() {
        return windows.sideLeft();
    }

    /** The paint the map and the windows share, made the first time; needs GL. */
    private YardPaint paint() {
        if (paint == null) {
            paint = new YardPaint();
            if (uiScale > 0f) paint.fonts.setScale(uiScale);
        }
        return paint;
    }

    /**
     * Puts the windows' consoles in the host's screen layer; {@code top} is what the HUD keeps above
     * a console (its status strip), or 0 for none. Needs GL.
     */
    public void attach(HudVariantHost host, float top) {
        windows.attach(host, paint(), top);
    }

    /** The map's painter, made the first time and kept until {@link #dispose}; needs GL. */
    public MapPainter mapPainter() {
        if (painter == null) painter = new YardMapPainter(paint());
        return painter;
    }

    /** The UI is drawn {@code uiScale} times its units now. */
    public void resize(float uiScale) {
        this.uiScale = uiScale;
        if (paint != null) paint.fonts.setScale(uiScale);
    }

    /** Once a frame, after the data is read. */
    public void update(float delta) {
        windows.update(delta);
    }

    public boolean replaces(ClassicWindow window) {
        return windows.replaces(window);
    }

    public boolean open(ClassicWindow window) {
        return windows.open(window);
    }

    public boolean showing(ClassicWindow window) {
        return windows.showing(window);
    }

    public void close(ClassicWindow window) {
        windows.close(window);
    }

    /** A key while a console shows: true when it used it. */
    public boolean keyDown(int keycode) {
        return windows.keyDown(keycode);
    }

    /** Lets go of the map's painter and the windows, as the HUD detaches and the game takes the map back. */
    public void dispose() {
        windows.detach();
        if (painter != null) painter.dispose();
        painter = null;
        if (paint != null) paint.dispose();
        paint = null;
    }
}
