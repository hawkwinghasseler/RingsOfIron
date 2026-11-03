package com.ringsofiron.uihud.variants;

import java.util.EnumSet;
import java.util.Set;

/**
 * One way of drawing the HUD: the classic one, or one of the nine UI overhaul combinations (three
 * functional specs by three art styles; documentation/ui-hud/Variants.md). The player picks one in
 * Settings, Interface, HUD; {@link HudVariants} lists them.
 *
 * <p>Life cycle, all on the render thread: {@link #attach} when the variant is picked (or the game
 * starts with it), then each frame {@link #update} after the game has updated and
 * {@link com.ringsofiron.uihud.data.HudData#refresh} has read this frame's data, then the stage draws
 * the variant's actors; {@link #resize} when the window or the UI scale changes; {@link #detach}
 * when another variant is picked or the game closes. Everything a variant adds to the stage goes in
 * {@link HudVariantHost#layer()}, which the host clears on detach.
 */
public interface HudVariant {

    /** Builds the variant's actors in {@code host.layer()} and keeps {@code host} for later frames. */
    void attach(HudVariantHost host);

    /** Once a frame, before the stage acts and draws: read {@code host.data()} and lay out. */
    void update(float delta);

    /**
     * The window is {@code width} x {@code height} pixels and the UI is drawn {@code uiScale} times
     * larger than its units: rebuild fonts and layout. The stage's units are already resized.
     */
    default void resize(int width, int height, float uiScale) {
    }

    /**
     * A key the HUD did not use, before the game's own bindings see it: true when the variant used
     * it (a soft key, the master caution's acknowledge).
     */
    default boolean keyDown(int keycode) {
        return false;
    }

    /** Lets go of anything outside the layer (fonts, textures). The host clears the layer after. */
    default void detach() {
    }

    /** The classic HUD's pieces that stay up under this variant; the rest are hidden. None by default. */
    default Set<ClassicPart> keeps() {
        return EnumSet.noneOf(ClassicPart.class);
    }
}
