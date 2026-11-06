package com.ringsofiron.uihud.variants.cassette;

import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * The Yard Standard's screens that the three Cassette HUDs share (style guide, section 9): the game's
 * menus in a yard office at night ({@link YardShellLook}), and the map on a phosphor tube
 * ({@link YardMapPainter}). A HUD keeps one and hands them on from its own {@code shellLook()},
 * {@code mapPainter()}, {@code resize()} and {@code detach()}; it makes nothing until asked, so a
 * HUD made only to be asked for its menus' look costs nothing more.
 */
public final class YardScreens {
    private YardMapPainter painter;
    private float uiScale = -1f;

    /** The menus' look, new for each asking, as the game wants it; needs GL. */
    public ShellLook shellLook() {
        return new YardShellLook();
    }

    /** The map's painter, made the first time and kept until {@link #dispose}; needs GL. */
    public MapPainter mapPainter() {
        if (painter == null) {
            painter = new YardMapPainter();
            if (uiScale > 0f) painter.rescale(uiScale);
        }
        return painter;
    }

    /** The UI is drawn {@code uiScale} times its units now. */
    public void resize(float uiScale) {
        this.uiScale = uiScale;
        if (painter != null) painter.rescale(uiScale);
    }

    /** Lets go of the map's painter, as the HUD detaches and the game takes the map back. */
    public void dispose() {
        if (painter != null) painter.dispose();
        painter = null;
    }
}
