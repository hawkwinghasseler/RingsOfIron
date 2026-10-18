package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.RailsWarpGuard;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.util.function.Supplier;

/** Refuses rails warp while a fire or atmosphere leak is active aboard the player's ship. */
public class PlayerShipHazardGuard implements RailsWarpGuard {
    private final ComponentMapper<ShipGridComponent> grids = ComponentMapper.getFor(ShipGridComponent.class);
    private final Supplier<Entity> playerShip;

    public PlayerShipHazardGuard(Supplier<Entity> playerShip) {
        this.playerShip = playerShip;
    }

    @Override
    public String railsWarpBlockedReason() {
        Entity ship = playerShip.get();
        ShipGridComponent grid = ship == null ? null : grids.get(ship);
        return grid != null && grid.hasActiveHazard() ? "Fire or atmosphere leak aboard" : null;
    }
}
