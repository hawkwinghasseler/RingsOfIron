package com.ringsofiron.uihud.variants;

/**
 * The pieces of the classic HUD a variant can keep or hide ({@link HudVariant#keeps}). Windows
 * (market, yard, missions, crew, boarding, the console window and the map), the console prompt and
 * the world overlays the game draws under the HUD (burn arrows, fire arcs, picture-in-picture) are not
 * here: they stay under every variant until a variant replaces them in its own PR.
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
    KEY_HINTS
}
