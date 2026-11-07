package com.ringsofiron.uihud.variants;

/**
 * The pieces of the classic HUD a variant can keep or hide ({@link HudVariant#keeps}). The windows
 * (market, yard, shipyard, missions, crew, boarding, the console window, the map's panels, the log,
 * the controls and the ship-lost screen) are not here: a variant shows its own screen for one in
 * place of the classic window with {@link HudVariant#replaces} ({@link ClassicWindow}). Nor are the
 * world overlays the game draws under the HUD (burn arrows, fire arcs, picture-in-picture), which
 * stay under every variant. The picture-in-picture bubbles keep off a variant's panels
 * ({@link HudVariant#panels}), and a variant can draw their rims itself ({@link HudVariant#drawsInsets}).
 */
public enum ClassicPart {
    /** The flight readout, top left. */
    FLIGHT_PANEL,
    /** The ship gauges under it. */
    GAUGES,
    /** The radar scope under those. */
    RADAR,
    /** The ship wireframe and alert list, bottom left. */
    SHIP_PANEL,
    /** The orbit panel, bottom right. */
    ORBIT_PANEL,
    /** The missile warning banner. */
    MISSILE_BANNER,
    /** Missile paths and point-defense flashes over the world. */
    THREAT_OVERLAY,
    /** Contact markers, their names and the prograde markers over the world. */
    WORLD_MARKERS,
    /** The game's status column, top right (clock, target, notices, docking, navigation, contracts, helm). */
    STATUS_COLUMN,
    /** The key hint line along the bottom. */
    KEY_HINTS,
    /** The docked banner under the clock ("Docked at Highport [T] Trade [Y] Yard"); its text is {@code HudData#dockHint}. */
    DOCK_BANNER,
    /** The E prompt above the bottom edge ("Press [E] to put on a pressure suit"); its words are {@code HudData#promptAction}. */
    PROMPT
}
