package com.ringsofiron.uihud.variants.vfd;

import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.widgets.MapPainter;

/**
 * FILAMENT's screens that the three VFD HUDs share (style-vfd.md, section 18): the game's menus as the
 * ship's computer in setup mode ({@link VfdShellLook}), and the map's plot ({@link VfdMapPainter}). A
 * HUD keeps one and hands them on from its own {@code shellLook()}, {@code mapPainter()},
 * {@code resize()} and {@code detach()}. It makes nothing until asked, so a HUD made only to be asked
 * for its menus' look costs nothing more.
 */
public final class VfdScreens {
    private VfdMapPainter painter;
    private float uiScale = -1f;

    /** The menus' look, new for each asking, as the game wants it; needs GL. */
    public ShellLook shellLook() {
        return new VfdShellLook();
    }

    /** The map's painter, made the first time and kept until {@link #dispose}; needs GL. */
    public MapPainter mapPainter() {
        if (painter == null) {
            painter = new VfdMapPainter();
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
