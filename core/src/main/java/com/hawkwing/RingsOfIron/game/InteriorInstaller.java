package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.InteriorGridSaveParticipant;
import com.ringsofiron.interiorgrid.InteriorHazardWarpGuard;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/** interior-grid in {@link GameWorld}: hazards, power and air aboard, and the propellant, thrust and mass they set. */
final class InteriorInstaller {
    private InteriorInstaller() {
    }

    static void install(GameWorld world) {
        GameEngine engine = world.engine;
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        // Thrust and planned burns burn propellant from the tanks piped to the engines
        engine.addSystem(new PropellantFeed());
        // ...and the engines themselves set how hard the ship can push, less as they take damage
        engine.addSystem(new EngineThrust(world::getContent));
        // ...and the RCS blocks on the hull how hard it turns, strafes and backs up
        engine.addSystem(new RcsThrust());
        world.orbitalEvents.burnBudget = (ship, dv) -> {
            ShipGridComponent.MassProperties m = world.shipMass(ship);
            return m == null ? dv : PropellantFeed.payForBurn(ship, m.mass, dv);
        };
        engine.getSerializationService().addParticipant(new InteriorGridSaveParticipant(engine));
        // Traded goods sit in the ships' cargo bays and weigh them down
        engine.addSystem(new ShipCargo(world::shipMass));
        // ...and so do the tanks, lighter as they run down
        engine.addSystem(new TankMass(world::shipMass));
        // Watches whichever ship the player is flying now.
        InteriorHazardWarpGuard hazards = new InteriorHazardWarpGuard(null);
        engine.addRailsWarpGuard(() -> {
            Entity player = world.getPlayer();
            hazards.setGrid(player == null ? null : player.getComponent(ShipGridComponent.class));
            return hazards.railsWarpBlockedReason();
        });
    }
}
