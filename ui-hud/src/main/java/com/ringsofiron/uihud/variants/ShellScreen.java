package com.ringsofiron.uihud.variants;

/** The game's menus around the HUD, as a {@link ShellLook} dresses them. */
public enum ShellScreen {
    /** The main menu: Continue, New game, First play, Load, Settings and Quit, with the build's version under it. */
    MAIN,
    /** A new career: the seed and the faction to serve. */
    NEW_GAME,
    /** Esc over a game: Resume, Call a tow (when stranded), Save, Load, Settings, Quit to menu and Quit game. */
    PAUSE,
    /** The saves, newest first, each with Load and Delete. */
    LOAD,
    /** A name to save under, or a save to overwrite. */
    SAVE,
    /** Sound, display, UI scale, the HUD, the gamepad, the walkthrough and the keys. */
    SETTINGS,
    /** The dialog that asks before mod scripts run with full access to the computer. */
    MOD_SCRIPTS
}
