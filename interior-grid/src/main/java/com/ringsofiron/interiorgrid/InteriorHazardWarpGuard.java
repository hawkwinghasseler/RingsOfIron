package com.ringsofiron.interiorgrid;

import com.ringsofiron.coreengine.RailsWarpGuard;

/**
 * Refuses rails warp while a fire burns or air vents aboard one ship, normally the
 * player's. Register it with {@code GameEngine.addRailsWarpGuard}; point it at a new
 * ship with {@link #setGrid} when the player changes ships.
 */
public class InteriorHazardWarpGuard implements RailsWarpGuard {

    private ShipGridComponent grid;

    public InteriorHazardWarpGuard(ShipGridComponent grid) {
        this.grid = grid;
    }

    public void setGrid(ShipGridComponent grid) {
        this.grid = grid;
    }

    @Override
    public String railsWarpBlockedReason() {
        if (grid == null) return null;
        if (grid.hasFire()) return "Fire aboard";
        if (grid.isVenting()) return "Atmosphere venting";
        return null;
    }
}
