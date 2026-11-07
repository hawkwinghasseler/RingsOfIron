package com.ringsofiron.uihud.variants.nasapunk;

import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * The Nasa-punk screens that the three Nasa-punk HUDs share (documentation/ui-overhaul/variations/
 * nasapunk-shell): the game's menus as pages of the flight computer CDU-1 ({@link NpShellLook}), and
 * the map as the system plot ({@link NpMapPainter}). A HUD keeps one and hands them on from its own
 * {@code shellLook()}, {@code mapPainter()}, {@code resize()} and {@code detach()}. It makes nothing
 * until asked, so a HUD made only to be asked for its menus' look costs nothing more.
 */
public final class NpScreens {
    private NpMapPainter painter;
    private float uiScale = -1f;

    /** The menus' look, new for each asking, as the game wants it; needs GL. */
    public ShellLook shellLook() {
        return new NpShellLook();
    }

    /** The map's painter, made the first time and kept until {@link #dispose}; needs GL. */
    public MapPainter mapPainter() {
        if (painter == null) {
            painter = NpMapPainter.create();
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
