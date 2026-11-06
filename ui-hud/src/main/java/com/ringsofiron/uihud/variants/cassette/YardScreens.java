package com.ringsofiron.uihud.variants.cassette;

import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.cassette.screens.WindowScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * The Yard Standard's screens that the three Cassette HUDs share (style guide, section 9): the game's
 * menus in a yard office at night ({@link YardShellLook}), the map on a phosphor tube
 * ({@link YardMapPainter}), and the game's windows as tabs of big consoles ({@link WindowScreens}). A
 * HUD keeps one and hands them on from its own hooks: {@code shellLook()}, {@code mapPainter()},
 * {@code attach}, {@code update}, {@code resize}, {@code detach} and the screen hooks
 * ({@code replaces}, {@code open}, {@code showing}, {@code close}, {@code keyDown}). It makes nothing
 * until asked, so a HUD made only to be asked for its menus' look costs nothing more.
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
