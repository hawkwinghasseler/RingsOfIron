package com.hawkwing.RingsOfIron.debug;

/**
 * Something drawn over the game for a developer while it is toggled on in the debug menu (or with
 * {@code overlay("id")}). Both halves are optional. Each is called with nothing begun: begin and end the
 * {@link OverlayView#shapes} or {@link OverlayView#batch} yourself, and leave blending as you found it.
 */
public interface DebugOverlay {
    /** In world meters, after the world is drawn and before the HUD; {@code view.shapes} is set to the world camera. */
    default void drawWorld(OverlayView view) {
    }

    /** In UI pixels, over the HUD; {@code view.shapes} and {@code view.batch} are set to the UI camera. */
    default void drawScreen(OverlayView view) {
    }
}
