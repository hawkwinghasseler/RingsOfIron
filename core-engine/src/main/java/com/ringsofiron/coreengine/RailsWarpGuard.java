package com.ringsofiron.coreengine;

/**
 * Lets a module veto rails warp, e.g. interior-grid while a fire or atmosphere leak is active
 * aboard the player's ship. Register with {@link GameEngine#addRailsWarpGuard(RailsWarpGuard)}.
 */
public interface RailsWarpGuard {
    /** @return null when rails warp is allowed, otherwise a short reason for the player ("Fire aboard"). */
    String railsWarpBlockedReason();
}
