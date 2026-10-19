package com.ringsofiron.interiorgrid;

import com.ringsofiron.coreengine.RailsWarpGuard;

/**
 * Refuses rails warp while a fire burns or air vents aboard one ship, normally the
 * player's, at a rate that matters ({@link ShipGridComponent#isVenting}). A patched or sealed
 * leak does not vent. Register it with {@code GameEngine.addRailsWarpGuard}; point it at a new
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
        if (grid.isVenting()) return ventingReason(grid);
        return null;
    }

    /** Refusal text while air vents, naming what will stop it. */
    public static final String VENTING = "Atmosphere venting";

    /**
     * Why warp is refused while {@code grid} vents: the crew are patching the leak, there are no
     * patches left for it (a yard must repair it), or plain venting (a breach or an open hole).
     */
    public static String ventingReason(ShipGridComponent grid) {
        if (LeakPatches.leaks(grid).size == 0) return VENTING;
        return grid.patchKits > 0 ? VENTING + ": the crew are patching the leak"
                : VENTING + ": no hull patches left, a yard must repair the leak";
    }
}
