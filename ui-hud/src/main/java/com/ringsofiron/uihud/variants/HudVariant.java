package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.widgets.MapPainter;

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
 * {@link HudVariantHost#layer()}, or for its screens {@link HudVariantHost#screenLayer()}, which the
 * host clears on detach.
 *
 * <p>Besides the HUD's panels ({@link #keeps}), a variant may show its own screens in place of the
 * classic windows, the map's panels and the game's text panels ({@link #replaces}, {@link ClassicWindow}).
 * While one of its screens shows, the keys the HUD does not use go to {@link #keyDown} first.
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

    /**
     * The variant's own panels as it last laid them out, into {@code out}: UI units, y up, as the UI
     * camera has them. What the game lays over the world under the HUD keeps off them: its magnified
     * insets (picture-in-picture) slide clear. None by default.
     */
    default void panels(Array<Rectangle> out) {
    }

    /**
     * Whether the variant draws the rims, marks and names of the game's magnified insets itself, from
     * {@link com.ringsofiron.uihud.data.HudData#insets}; the game then lays only the magnified
     * pictures. False by default: the game draws them as the classic HUD has them.
     */
    default boolean drawsInsets() {
        return false;
    }

    /**
     * How far the variant's own rim, marks and name reach past the edge of each inset's picture, into
     * {@code out} (cleared to none): the game keeps that room clear of the panels, the other insets
     * and the stage's edges. Read only when the variant {@link #drawsInsets}.
     */
    default void insetRoom(Insets.Room out) {
    }

    /**
     * Whether the game shows an inset for {@code role} at all (still only while the outside view draws
     * that craft too small to make out). All of them by default.
     */
    default boolean wantsInset(Insets.Role role) {
        return true;
    }

    // --- Screens in place of the classic windows ----------------------------------------------

    /**
     * Whether the variant shows a screen of its own in place of the classic {@code window}. The
     * classic window then never opens; the game asks the variant instead ({@link #open}). None by
     * default. Read once, when the variant is attached: the answer must not change after.
     */
    default boolean replaces(ClassicWindow window) {
        return false;
    }

    /**
     * Shows the variant's screen for {@code window}, or turns an open screen to it (a station
     * terminal to its market tab): its key was pressed, the game opened it, or another screen asked
     * ({@code UiHud#open}). Only for a window the variant {@link #replaces}, and only while the game
     * says it can open ({@code UiHud#available}). True when it shows now. A screen goes in
     * {@link HudVariantHost#screenLayer()}, over the classic windows and the map.
     */
    default boolean open(ClassicWindow window) {
        return false;
    }

    /** Whether the variant's screen for {@code window} shows now. A screen that serves several (a terminal's tabs) shows the one on its tab. */
    default boolean showing(ClassicWindow window) {
        return false;
    }

    /**
     * Closes the variant's screen for {@code window} if it shows: its key pressed again, Esc, or the
     * game (the ship undocked, so the market can no longer open). A screen that serves several may
     * close whole.
     */
    default void close(ClassicWindow window) {
    }

    /**
     * How the full-screen map looks under this variant: a {@link MapPainter} that draws every piece of
     * it (the backdrop, the orbits, the bodies, the marks, the labels, the traffic, the burn handles,
     * the scale bar and the title), or null for the classic look. Read once, after {@link #attach}.
     * The map keeps where everything goes, the zoom, the panning and the clicks. A variant that also
     * {@link #replaces} {@link ClassicWindow#MAP} shows the side panel's contents in its own panels,
     * and keeps the canvas clear of them with {@code host.hud().getManeuverMap().setCanvasInsets}.
     */
    default MapPainter mapPainter() {
        return null;
    }

    /**
     * How the game's menus look with this variant picked ({@link ShellLook}): the main menu, new game,
     * pause, load, save, settings and the mod scripts dialog. Null keeps the classic menus. The game
     * asks a fresh instance that it never attaches, as soon as the variant is picked (on the main
     * menu, before any game), so make the look's own fonts and skin in it, not in {@link #attach}.
     */
    default ShellLook shellLook() {
        return null;
    }
}
