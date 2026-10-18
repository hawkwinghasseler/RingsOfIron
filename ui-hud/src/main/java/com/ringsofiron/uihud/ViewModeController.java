package com.ringsofiron.uihud;

import com.badlogic.gdx.utils.Array;

/**
 * Holds the current {@link ViewMode} and tells listeners when it changes. Other modules react
 * through a listener registered by the game wiring (for example, audio-spatial switching its
 * listener between exterior and muffled interior mixes), so ui-hud does not depend on them.
 */
public class ViewModeController {

    public interface Listener {
        void viewModeChanged(ViewMode previous, ViewMode current);
    }

    private final Array<Listener> listeners = new Array<>();
    private ViewMode mode;

    public ViewModeController() {
        this(ViewMode.EXTERIOR);
    }

    public ViewModeController(ViewMode initial) {
        mode = initial;
    }

    public ViewMode getMode() {
        return mode;
    }

    public void setMode(ViewMode newMode) {
        if (newMode == null || newMode == mode) return;
        ViewMode previous = mode;
        mode = newMode;
        for (Listener l : listeners) l.viewModeChanged(previous, newMode);
    }

    public void toggle() {
        setMode(mode == ViewMode.EXTERIOR ? ViewMode.INTERIOR : ViewMode.EXTERIOR);
    }

    /** Adds a listener and immediately tells it the current mode (previous == current). */
    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.viewModeChanged(mode, mode);
    }

    public void removeListener(Listener listener) {
        listeners.removeValue(listener, true);
    }
}
