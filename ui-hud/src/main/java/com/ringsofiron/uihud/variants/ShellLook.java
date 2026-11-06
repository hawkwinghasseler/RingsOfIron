package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;

/**
 * How the game's menus look under a HUD variant ({@link HudVariant#shellLook}): the main menu and
 * its backdrop, new game, pause, load, save, settings and the mod scripts dialog
 * ({@link ShellScreen}).
 *
 * <p>The game builds the menus as it always has, with the style names of the classic skin
 * ({@code HudSkin}). A look gives them its own skin under those names, so every label, button,
 * field and list takes its fonts, colours and frames. It may also draw the backdrops itself, and
 * dress each menu once it is built (a frame, a header, a legend of soft keys). What the menus hold
 * and do, and their keys (Esc, the arrow keys, Enter, the pad), stay the game's.
 *
 * <p>The game makes the look as soon as the variant is picked, before any game starts, and disposes
 * it when another is picked. The look owns what it makes: its fonts, textures and skin.
 */
public interface ShellLook extends Disposable {
    /**
     * The skin the menus are built with, kept for the look's life. It needs the classic skin's style
     * names: the {@code "panel"} drawable behind each menu; the label styles {@code "display"} (the
     * game's title), {@code "heading"}, {@code "title"}, {@code "default"}, {@code "dim"} and
     * {@code "warn"}; the text button styles {@code "menu"}, {@code "toggle"} and {@code "default"};
     * and the default styles of {@code TextField}, {@code SelectBox}, {@code CheckBox},
     * {@code Slider} ({@code "default-horizontal"}), {@code ScrollPane} and {@code Window} (the mod
     * scripts dialog).
     */
    Skin skin();

    /**
     * The UI scale is now {@code uiScale}: draw the skin's text at that scale, as
     * {@code HudSkin.rescale} does for the classic skin. True when the text changed size, so the
     * menus lay out again. Also called once right after the look is made.
     */
    default boolean rescale(float uiScale) {
        return false;
    }

    /**
     * Draws behind the main menu, over the whole screen: {@code width} by {@code height} UI units, y
     * up, {@code delta} seconds since the last frame. The batch has begun, with the menus'
     * projection; leave it begun. False keeps the classic drifting stars and planet.
     */
    default boolean backdrop(Batch batch, float width, float height, float delta) {
        return false;
    }

    /**
     * Draws over the paused game, under the pause menu and the menus it opens, as {@link #backdrop}
     * does. False keeps the classic dimming.
     */
    default boolean pauseBackdrop(Batch batch, float width, float height, float delta) {
        return false;
    }

    /**
     * Once a menu is built, dresses it. {@code panel} is the menu's own table, with the skin's
     * {@code "panel"} behind it (the dialog itself, for {@link ShellScreen#MOD_SCRIPTS}).
     * {@code root} is the table that fills the screen around it, or null for the dialog, which
     * floats over the game.
     */
    default void dress(ShellScreen screen, Table panel, Table root) {
    }
}
