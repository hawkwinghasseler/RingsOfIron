package com.ringsofiron.uihud.variants;

/**
 * The classic HUD's windows, the map and the game's full-screen text panels, which a variant can
 * show its own screens in place of ({@link HudVariant#replaces}). The game keeps deciding when each
 * can open ({@code UiHud#available}), and its keys still open them; a variant that replaces one is
 * asked to show its screen instead ({@link HudVariant#open}), and the classic window stays shut.
 *
 * <p>The window's data comes from the providers the classic window reads, which the game also
 * sets on {@link com.ringsofiron.uihud.data.HudData} (its {@code trade}, {@code yard} and the rest),
 * and its actions go through them too. documentation/ui-hud/Variants.md lists what each one reads.
 */
public enum ClassicWindow {
    /** The map (M): orbits, burns and traffic. A variant that replaces it shows its own panels round the game's orbit canvas, which stays up and keeps the mouse. */
    MAP,
    /** The window E opens at a console: its system's switch, and the seat's own action ({@code HudData#console}). */
    CONSOLE,
    /** The station market (T, while docked). */
    TRADE,
    /** The yard (Y): repairs, tanks, refits, furnishings, Rebuild decks; away from a yard, the crew's field refit. */
    YARD,
    /** Ships for sale and a prize to hand over (the yard's Ships for sale, or Y with a prize and no yard). */
    SHIPYARD,
    /** The mission board (J): offers while docked, and the contracts held. */
    MISSIONS,
    /** Boarding and salvage (K, while latched to another ship). */
    BOARDING,
    /** The crew roster (I): berths, wages, EVA orders, prisoners, hires. */
    CREW,
    /** The notice log (L): the last fifty notices with their day and time ({@code HudData#messages}). */
    LOG,
    /** The controls panel (F1): every key by where it applies ({@code KeyHintProvider#collectAll}). */
    HELP,
    /** Ship lost or career over, and how to go on ({@code HudData#gameOver}). Opens by itself, not by a key. */
    GAME_OVER;

    /**
     * The order Esc closes them in, one a press: the text panels, which draw over everything, then
     * the map, then the windows as the classic HUD closes them. Not the game-over screen, where Esc
     * is the game's (the pause menu, or quit).
     */
    static final ClassicWindow[] ESCAPE_ORDER = {LOG, HELP, MAP, TRADE, YARD, MISSIONS, SHIPYARD, BOARDING, CREW, CONSOLE};
}
